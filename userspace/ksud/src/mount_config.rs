use anyhow::{Context, Result, anyhow, bail};
use serde_json::{Value, json};
use std::collections::{BTreeMap, BTreeSet};
use std::fmt;
use std::fs;
use std::io::ErrorKind;
use std::path::Path;
use std::str::FromStr;

use crate::{defs, metamodule, restorecon, utils};

pub const SYSTEM_PARTITION: &str = "system";
pub const DEFAULT_PARTITIONS: &[&str] = &["vendor", "system_ext", "product", "odm", "oem"];

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BuiltinMountBackend {
    Auto,
    MagicMount,
    OverlayFs,
    Disabled,
}

impl BuiltinMountBackend {
    pub const DEFAULT: Self = Self::Auto;

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::MagicMount => "magic_mount",
            Self::OverlayFs => "overlayfs",
            Self::Disabled => "disabled",
        }
    }
}

impl fmt::Display for BuiltinMountBackend {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for BuiltinMountBackend {
    type Err = anyhow::Error;

    fn from_str(value: &str) -> Result<Self> {
        match value.trim().to_ascii_lowercase().as_str() {
            "auto" | "misu_mount" | "misu-mount" | "misu" => Ok(Self::Auto),
            "magic_mount" | "magic-mount" | "magic" => Ok(Self::MagicMount),
            "overlayfs" | "overlay_fs" | "overlay-fs" | "overlay" => Ok(Self::OverlayFs),
            "disabled" | "disable" | "off" | "none" => Ok(Self::Disabled),
            _ => bail!("unknown built-in mount backend: {value}"),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OverlayStorageMode {
    Tmpfs,
    Ext4,
}

impl OverlayStorageMode {
    pub const DEFAULT: Self = Self::Tmpfs;

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Tmpfs => "tmpfs",
            Self::Ext4 => "ext4",
        }
    }
}

impl fmt::Display for OverlayStorageMode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for OverlayStorageMode {
    type Err = anyhow::Error;

    fn from_str(value: &str) -> Result<Self> {
        match value.trim().to_ascii_lowercase().as_str() {
            "tmpfs" | "ram" | "memory" => Ok(Self::Tmpfs),
            "ext4" | "image" | "loop" | "img" => Ok(Self::Ext4),
            _ => bail!("unknown overlayfs staging storage: {value}"),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct MountConfig {
    pub backend: BuiltinMountBackend,
    pub overlay_storage: OverlayStorageMode,
    pub custom_partitions: Vec<String>,
    pub module_backends: BTreeMap<String, BuiltinMountBackend>,
}

impl Default for MountConfig {
    fn default() -> Self {
        Self {
            backend: BuiltinMountBackend::DEFAULT,
            overlay_storage: OverlayStorageMode::DEFAULT,
            custom_partitions: Vec::new(),
            module_backends: BTreeMap::new(),
        }
    }
}

impl MountConfig {
    pub fn all_partitions(&self) -> Vec<String> {
        all_partitions(&self.custom_partitions)
    }

    pub fn module_backend(&self, module_id: &str, dir_id: &str) -> Option<BuiltinMountBackend> {
        self.module_backends
            .get(module_id)
            .or_else(|| self.module_backends.get(dir_id))
            .copied()
    }

    pub fn backend_for_module(&self, module_id: &str, dir_id: &str) -> BuiltinMountBackend {
        self.module_backend(module_id, dir_id)
            .unwrap_or(self.backend)
    }

    fn to_json(&self) -> Value {
        let module_backends = self
            .module_backends
            .iter()
            .map(|(module_id, backend)| (module_id.clone(), json!(backend.as_str())))
            .collect::<serde_json::Map<_, _>>();
        json!({
            "backend": self.backend.as_str(),
            "overlay_storage": self.overlay_storage.as_str(),
            "custom_partitions": self.custom_partitions,
            "module_backends": module_backends,
        })
    }
}

#[allow(clippy::struct_excessive_bools)]
#[derive(Clone, Debug)]
pub struct ModuleMountInfo {
    pub dir_id: String,
    pub id: String,
    pub name: String,
    pub enabled: bool,
    pub remove: bool,
    pub skip_mount: bool,
    pub metamodule: bool,
    pub needs_mount: bool,
    pub partitions: Vec<String>,
    pub configured_backend: Option<BuiltinMountBackend>,
    pub effective_backend: BuiltinMountBackend,
}

pub fn all_partitions(custom_partitions: &[String]) -> Vec<String> {
    let mut partitions = Vec::with_capacity(DEFAULT_PARTITIONS.len() + custom_partitions.len());
    partitions.extend(
        DEFAULT_PARTITIONS
            .iter()
            .map(|partition| (*partition).to_string()),
    );
    for partition in custom_partitions {
        if !partitions.iter().any(|it| it == partition) {
            partitions.push(partition.clone());
        }
    }
    partitions
}

fn parse_config(value: &Value) -> MountConfig {
    let backend = value
        .get("backend")
        .and_then(Value::as_str)
        .and_then(|it| BuiltinMountBackend::from_str(it).ok())
        .unwrap_or(BuiltinMountBackend::DEFAULT);
    let overlay_storage = value
        .get("overlay_storage")
        .or_else(|| value.get("overlayStorage"))
        .and_then(Value::as_str)
        .and_then(|it| OverlayStorageMode::from_str(it).ok())
        .unwrap_or(OverlayStorageMode::DEFAULT);

    let custom_partitions = value
        .get("custom_partitions")
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .filter_map(Value::as_str)
                .filter_map(|it| normalize_partition(it).ok())
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    let module_backends = value
        .get("module_backends")
        .or_else(|| value.get("moduleBackends"))
        .and_then(Value::as_object)
        .map(|values| {
            values
                .iter()
                .filter_map(|(module_id, backend)| {
                    let module_id = normalize_module_id(module_id).ok()?;
                    let backend = backend
                        .as_str()
                        .and_then(|it| BuiltinMountBackend::from_str(it).ok())?;
                    Some((module_id, backend))
                })
                .collect::<BTreeMap<_, _>>()
        })
        .unwrap_or_default();
    if value
        .get("module_path_backends")
        .or_else(|| value.get("modulePathBackends"))
        .is_some()
    {
        log::info!("ignoring deprecated built-in mount path backend rules");
    }

    MountConfig {
        backend,
        overlay_storage,
        custom_partitions: dedupe_partitions(custom_partitions),
        module_backends,
    }
}

pub fn load() -> MountConfig {
    match fs::read_to_string(defs::MOUNT_CONFIG_FILE) {
        Ok(content) => match serde_json::from_str::<Value>(&content) {
            Ok(value) => parse_config(&value),
            Err(err) => {
                log::warn!(
                    "failed to parse {}: {err}, using default mount config",
                    defs::MOUNT_CONFIG_FILE
                );
                MountConfig::default()
            }
        },
        Err(err) if err.kind() == ErrorKind::NotFound => MountConfig::default(),
        Err(err) => {
            log::warn!(
                "failed to read {}: {err}, using default mount config",
                defs::MOUNT_CONFIG_FILE
            );
            MountConfig::default()
        }
    }
}

pub fn save(config: &MountConfig) -> Result<()> {
    utils::ensure_dir_exists(Path::new(defs::WORKING_DIR))?;
    let content = serde_json::to_string_pretty(&config.to_json())?;
    fs::write(defs::MOUNT_CONFIG_FILE, format!("{content}\n"))
        .with_context(|| format!("failed to write {}", defs::MOUNT_CONFIG_FILE))?;
    if let Err(err) = restorecon::lsetfilecon(defs::MOUNT_CONFIG_FILE, restorecon::KSU_CON) {
        log::warn!(
            "failed to set context on {}: {err:#}",
            defs::MOUNT_CONFIG_FILE
        );
    }
    Ok(())
}

pub fn load_mount_state() -> Option<Value> {
    match fs::read_to_string(defs::MOUNT_STATE_FILE) {
        Ok(content) => match serde_json::from_str::<Value>(&content) {
            Ok(value) => Some(value),
            Err(err) => {
                log::warn!("failed to parse {}: {err}", defs::MOUNT_STATE_FILE);
                None
            }
        },
        Err(err) if err.kind() == ErrorKind::NotFound => None,
        Err(err) => {
            log::warn!("failed to read {}: {err}", defs::MOUNT_STATE_FILE);
            None
        }
    }
}

pub fn save_mount_state(state: &Value) -> Result<()> {
    utils::ensure_dir_exists(Path::new(defs::WORKING_DIR))?;
    let content = serde_json::to_string_pretty(state)?;
    fs::write(defs::MOUNT_STATE_FILE, format!("{content}\n"))
        .with_context(|| format!("failed to write {}", defs::MOUNT_STATE_FILE))?;
    if let Err(err) = restorecon::lsetfilecon(defs::MOUNT_STATE_FILE, restorecon::KSU_CON) {
        log::warn!(
            "failed to set context on {}: {err:#}",
            defs::MOUNT_STATE_FILE
        );
    }
    Ok(())
}

pub fn normalize_partition(value: &str) -> Result<String> {
    let value = value.trim().trim_start_matches('/');
    if value.is_empty() {
        bail!("partition name is empty");
    }
    if value == "." || value == ".." || value.contains('/') || value.contains('\\') {
        bail!("invalid partition name: {value}");
    }
    if !value
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-' || b == b'.')
    {
        bail!("invalid partition name: {value}");
    }
    if value == SYSTEM_PARTITION {
        bail!("system is always mounted automatically and cannot be added");
    }
    Ok(value.to_ascii_lowercase())
}

pub fn normalize_module_id(value: &str) -> Result<String> {
    let value = value.trim();
    if value.is_empty() {
        bail!("module id is empty");
    }
    if value == "." || value == ".." || value.contains('/') || value.contains('\\') {
        bail!("invalid module id: {value}");
    }
    if !value
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-' || b == b'.')
    {
        bail!("invalid module id: {value}");
    }
    Ok(value.to_string())
}

fn dedupe_partitions(partitions: Vec<String>) -> Vec<String> {
    let mut seen = BTreeSet::new();
    let mut out = Vec::new();
    for partition in partitions {
        if DEFAULT_PARTITIONS.contains(&partition.as_str()) {
            continue;
        }
        if seen.insert(partition.clone()) {
            out.push(partition);
        }
    }
    out
}

pub fn set_backend(value: &str) -> Result<()> {
    let mut config = load();
    config.backend = BuiltinMountBackend::from_str(value)?;
    save(&config)?;
    println!("{}", config.backend.as_str());
    Ok(())
}

pub fn print_backend() {
    println!("{}", load().backend.as_str());
}

pub fn set_overlay_storage(value: &str) -> Result<()> {
    let mut config = load();
    config.overlay_storage = OverlayStorageMode::from_str(value)?;
    save(&config)?;
    println!("{}", config.overlay_storage.as_str());
    Ok(())
}

pub fn print_overlay_storage() {
    println!("{}", load().overlay_storage.as_str());
}

pub fn print_overlay_storage_modes() {
    for mode in [OverlayStorageMode::Tmpfs, OverlayStorageMode::Ext4] {
        println!("{}", mode.as_str());
    }
}

pub fn print_backends() {
    for backend in [
        BuiltinMountBackend::Auto,
        BuiltinMountBackend::MagicMount,
        BuiltinMountBackend::OverlayFs,
        BuiltinMountBackend::Disabled,
    ] {
        println!("{}", backend.as_str());
    }
}

pub fn add_partition(value: &str) -> Result<()> {
    let partition = normalize_partition(value)?;
    let mut config = load();
    if !config
        .custom_partitions
        .iter()
        .any(|existing| existing == &partition)
    {
        config.custom_partitions.push(partition.clone());
    }
    save(&config)?;
    println!("{partition}");
    Ok(())
}

pub fn remove_partition(value: &str) -> Result<()> {
    let partition = normalize_partition(value)?;
    let mut config = load();
    config.custom_partitions.retain(|it| it != &partition);
    save(&config)?;
    println!("{partition}");
    Ok(())
}

pub fn clear_partitions() -> Result<()> {
    let mut config = load();
    config.custom_partitions.clear();
    save(&config)?;
    Ok(())
}

pub fn print_partitions() {
    for partition in load().custom_partitions {
        println!("{partition}");
    }
}

pub fn set_module_backend(module_id: &str, backend: &str) -> Result<()> {
    let module_id = normalize_module_id(module_id)?;
    let backend = BuiltinMountBackend::from_str(backend)?;
    let mut config = load();
    config.module_backends.insert(module_id.clone(), backend);
    save(&config)?;
    println!("{module_id}={}", backend.as_str());
    Ok(())
}

pub fn remove_module_backend(module_id: &str) -> Result<()> {
    let module_id = normalize_module_id(module_id)?;
    let mut config = load();
    config.module_backends.remove(&module_id);
    save(&config)?;
    println!("{module_id}");
    Ok(())
}

pub fn print_module_backend(module_id: &str) -> Result<()> {
    let module_id = normalize_module_id(module_id)?;
    let config = load();
    let backend = config
        .module_backends
        .get(&module_id)
        .copied()
        .unwrap_or(config.backend);
    println!("{}", backend.as_str());
    Ok(())
}

pub fn print_module_backends() {
    for (module_id, backend) in load().module_backends {
        println!("{module_id}={}", backend.as_str());
    }
}

pub fn print_status() -> Result<()> {
    let config = load();
    let modules = scan_module_mount_info(&config)?
        .iter()
        .map(module_mount_info_to_json)
        .collect::<Vec<_>>();
    let module_backends = config
        .module_backends
        .iter()
        .map(|(module_id, backend)| (module_id.clone(), json!(backend.as_str())))
        .collect::<serde_json::Map<_, _>>();
    let status = json!({
        "backend": config.backend.as_str(),
        "overlayStorage": config.overlay_storage.as_str(),
        "customPartitions": config.custom_partitions,
        "moduleBackends": module_backends,
        "knownPartitions": std::iter::once(SYSTEM_PARTITION.to_string())
            .chain(config.all_partitions())
            .collect::<Vec<_>>(),
        "modules": modules,
        "lastRun": load_mount_state(),
    });
    println!("{}", serde_json::to_string_pretty(&status)?);
    Ok(())
}

pub fn scan_module_mount_info(config: &MountConfig) -> Result<Vec<ModuleMountInfo>> {
    let module_root = Path::new(defs::MODULE_DIR);
    let dir = match fs::read_dir(module_root) {
        Ok(dir) => dir,
        Err(err) if err.kind() == ErrorKind::NotFound => return Ok(Vec::new()),
        Err(err) => return Err(err).with_context(|| format!("read_dir {}", module_root.display())),
    };

    let partitions = config.all_partitions();
    let mut modules = Vec::new();
    for entry in dir.flatten() {
        let path = entry.path();
        if !path.is_dir() || path.file_name().is_some_and(|name| name == ".rw") {
            continue;
        }
        if !path.join("module.prop").exists() {
            continue;
        }

        let props = crate::module::read_module_prop(&path)
            .with_context(|| format!("read module.prop for {}", path.display()))?;
        let dir_id = entry
            .file_name()
            .to_str()
            .ok_or_else(|| anyhow!("invalid module directory name"))?
            .to_string();
        let id = props
            .get("id")
            .filter(|id| !id.trim().is_empty())
            .cloned()
            .unwrap_or_else(|| dir_id.clone());
        let name = props.get("name").cloned().unwrap_or_else(|| id.clone());
        let enabled = !path.join(defs::DISABLE_FILE_NAME).exists();
        let remove = path.join(defs::REMOVE_FILE_NAME).exists();
        let skip_mount = path.join(defs::SKIP_MOUNT_FILE_NAME).exists();
        let metamodule = metamodule::is_metamodule(&props);
        let module_partitions = detect_module_partitions(&path, &partitions);
        let needs_mount =
            enabled && !remove && !skip_mount && !metamodule && !module_partitions.is_empty();
        let configured_backend = config.module_backend(&id, &dir_id);
        let effective_backend = if needs_mount {
            config.backend_for_module(&id, &dir_id)
        } else {
            BuiltinMountBackend::Disabled
        };

        modules.push(ModuleMountInfo {
            dir_id,
            id,
            name,
            enabled,
            remove,
            skip_mount,
            metamodule,
            needs_mount,
            partitions: module_partitions,
            configured_backend,
            effective_backend,
        });
    }

    Ok(modules)
}

fn module_mount_info_to_json(info: &ModuleMountInfo) -> Value {
    json!({
        "dirId": &info.dir_id,
        "id": &info.id,
        "name": &info.name,
        "enabled": info.enabled,
        "remove": info.remove,
        "skipMount": info.skip_mount,
        "metamodule": info.metamodule,
        "needsMount": info.needs_mount,
        "partitions": &info.partitions,
        "configuredBackend": info.configured_backend.map(BuiltinMountBackend::as_str),
        "effectiveBackend": info.effective_backend.as_str(),
    })
}

pub fn detect_module_partitions(module_path: &Path, partitions: &[String]) -> Vec<String> {
    let mut out = Vec::new();
    let system_path = module_path.join(SYSTEM_PARTITION);
    if system_path.is_dir() {
        out.push(SYSTEM_PARTITION.to_string());
    }

    for partition in partitions {
        let top_level = module_path.join(partition);
        let under_system = system_path.join(partition);
        if top_level.is_dir() || under_system.exists() {
            out.push(partition.clone());
        }
    }

    out.sort();
    out.dedup();
    out
}
