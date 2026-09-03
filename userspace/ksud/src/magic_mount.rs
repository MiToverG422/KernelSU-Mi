use crate::defs::{
    DISABLE_FILE_NAME, KSU_MOUNT_SOURCE, MODULE_DIR, REMOVE_FILE_NAME, SKIP_MOUNT_FILE_NAME,
};
use crate::magic_mount::NodeFileType::{Directory, RegularFile, Symlink, Whiteout};
use crate::restorecon::{lgetfilecon, lsetfilecon};
use crate::utils::ensure_dir_exists;
use anyhow::{Context, Result, bail};
use extattr::lgetxattr;
use rustix::fs::{Gid, MetadataExt, Mode, Uid, chmod, chown};
use rustix::mount::{
    MountFlags, MountPropagationFlags, UnmountFlags, mount, mount_bind as bind_mount, mount_change,
    mount_move as move_mount, mount_remount as remount, unmount,
};
use std::collections::hash_map::Entry;
use std::collections::{HashMap, HashSet};
use std::ffi::CString;
use std::fs;
use std::fs::{DirEntry, FileType, create_dir, create_dir_all, read_dir, read_link};
use std::os::unix::fs::{FileTypeExt, symlink};
use std::path::{Path, PathBuf};

const REPLACE_DIR_XATTR: &str = "trusted.overlay.opaque";
const BUILTIN_PARTITIONS: &[(&str, bool)] = &[
    ("vendor", true),
    ("system_ext", true),
    ("product", true),
    ("odm", false),
    ("oem", false),
];

fn configured_partitions(custom_partitions: &[String]) -> Vec<(String, bool)> {
    let mut partitions = BUILTIN_PARTITIONS
        .iter()
        .map(|(partition, require_system_symlink)| {
            ((*partition).to_string(), *require_system_symlink)
        })
        .collect::<Vec<_>>();

    for partition in custom_partitions {
        if partitions.iter().any(|(existing, _)| existing == partition) {
            continue;
        }
        partitions.push((partition.clone(), false));
    }

    partitions
}

fn dir_is_replaced(path: &Path) -> bool {
    lgetxattr(path, REPLACE_DIR_XATTR).is_ok_and(|v| String::from_utf8_lossy(&v) == "y")
}

#[derive(PartialEq, Eq, Hash, Clone, Debug)]
enum NodeFileType {
    RegularFile,
    Directory,
    Symlink,
    Whiteout,
}

impl NodeFileType {
    fn from_file_type(file_type: FileType) -> Option<Self> {
        if file_type.is_file() {
            Some(RegularFile)
        } else if file_type.is_dir() {
            Some(Directory)
        } else if file_type.is_symlink() {
            Some(Symlink)
        } else {
            None
        }
    }
}

#[derive(Debug)]
struct Node {
    name: String,
    file_type: NodeFileType,
    children: HashMap<String, Self>,
    module_path: Option<PathBuf>,
    replace: bool,
    skip: bool,
}

impl Node {
    fn collect_module_files<T: AsRef<Path>>(&mut self, module_dir: T) -> Result<bool> {
        let dir = module_dir.as_ref();
        let mut has_file = false;
        for entry in dir.read_dir()?.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();

            let node = match self.children.entry(name.clone()) {
                Entry::Occupied(o) => Some(o.into_mut()),
                Entry::Vacant(v) => Self::new_module(&name, &entry).map(|it| v.insert(it)),
            };

            if let Some(node) = node {
                has_file |= if node.file_type == Directory {
                    node.collect_module_files(dir.join(&node.name))? || node.replace
                } else {
                    true
                };
            }
        }

        Ok(has_file)
    }

    fn new_root(name: &str) -> Self {
        Self {
            name: name.to_owned(),
            file_type: Directory,
            children: HashMap::default(),
            module_path: None,
            replace: false,
            skip: false,
        }
    }

    fn new_module_dir(name: &str, path: &Path) -> Self {
        Self {
            name: name.to_owned(),
            file_type: Directory,
            children: HashMap::default(),
            module_path: Some(path.to_path_buf()),
            replace: dir_is_replaced(path),
            skip: false,
        }
    }

    fn new_module(name: &str, entry: &DirEntry) -> Option<Self> {
        if let Ok(metadata) = entry.metadata() {
            let path = entry.path();
            let file_type = if metadata.file_type().is_char_device() && metadata.rdev() == 0 {
                Some(Whiteout)
            } else {
                NodeFileType::from_file_type(metadata.file_type())
            };
            if let Some(file_type) = file_type {
                let replace = file_type == Directory && dir_is_replaced(&path);
                return Some(Self {
                    name: name.to_owned(),
                    file_type,
                    children: HashMap::default(),
                    module_path: Some(path),
                    replace,
                    skip: false,
                });
            }
        }

        None
    }
}

fn module_is_metamodule(module_path: &Path) -> bool {
    crate::module::read_module_prop(module_path)
        .is_ok_and(|props| crate::metamodule::is_metamodule(&props))
}

fn partition_should_mount(partition: &str, require_system_symlink: bool) -> bool {
    let path_of_root = Path::new("/").join(partition);
    let path_of_system = Path::new("/system").join(partition);
    path_of_root.is_dir() && (!require_system_symlink || path_of_system.is_symlink())
}

fn target_contains(targets: Option<&HashSet<String>>, target: &str) -> bool {
    targets.is_none_or(|targets| targets.contains(target))
}

fn module_matches_filter(
    module_path: &Path,
    dir_id: &str,
    module_filter: Option<&HashSet<String>>,
) -> bool {
    let Some(module_filter) = module_filter else {
        return true;
    };
    if module_filter.contains(dir_id) {
        return true;
    }
    crate::module::read_module_prop(module_path)
        .ok()
        .and_then(|props| props.get("id").cloned())
        .is_some_and(|id| module_filter.contains(&id))
}

fn collect_builtin_partition_files(
    root: &mut Node,
    module_path: &Path,
    targets: Option<&HashSet<String>>,
    partitions: &[(String, bool)],
) -> Result<bool> {
    let mod_system = module_path.join("system");
    let mut has_file = false;

    for (partition, require_system_symlink) in partitions {
        if !target_contains(targets, partition) {
            continue;
        }

        if !partition_should_mount(partition, *require_system_symlink) {
            continue;
        }

        let module_partition = module_path.join(partition);
        let system_partition = mod_system.join(partition);
        let partition_path = if module_partition.is_dir() {
            module_partition
        } else if (!require_system_symlink || system_partition.is_symlink())
            && system_partition.is_dir()
        {
            system_partition
        } else {
            continue;
        };

        log::debug!(
            "collecting builtin partition {partition} from {}",
            partition_path.display()
        );

        let name = partition.clone();
        let node = match root.children.entry(name.clone()) {
            Entry::Occupied(o) => o.into_mut(),
            Entry::Vacant(v) => v.insert(Node::new_module_dir(&name, &partition_path)),
        };

        if node.file_type != Directory {
            log::warn!("builtin partition node {partition} is not a directory, skipping");
            continue;
        }

        node.replace |= dir_is_replaced(&partition_path);
        if node.module_path.is_none() {
            node.module_path = Some(partition_path.clone());
        }
        has_file |= node.collect_module_files(&partition_path)? || node.replace;
    }

    Ok(has_file)
}

fn collect_module_files_with_targets(
    targets: Option<&HashSet<String>>,
    module_filter: Option<&HashSet<String>>,
    custom_partitions: &[String],
) -> Result<Option<Node>> {
    let module_root = Path::new(MODULE_DIR);
    let mut sources = Vec::new();
    for entry in module_root.read_dir()?.flatten() {
        let path = entry.path();
        if !entry.file_type()?.is_dir() {
            continue;
        }
        let dir_id = entry.file_name().to_string_lossy().to_string();
        if !module_matches_filter(&path, &dir_id, module_filter) {
            continue;
        }

        if path.join(DISABLE_FILE_NAME).exists()
            || path.join(REMOVE_FILE_NAME).exists()
            || path.join(SKIP_MOUNT_FILE_NAME).exists()
            || module_is_metamodule(&path)
        {
            continue;
        }

        sources.push((dir_id, path));
    }

    collect_module_files_from_sources(&sources, targets, custom_partitions)
}

fn collect_module_files_from_sources(
    sources: &[(String, PathBuf)],
    targets: Option<&HashSet<String>>,
    custom_partitions: &[String],
) -> Result<Option<Node>> {
    let partitions = configured_partitions(custom_partitions);
    let mut root = Node::new_root("");
    let mut system = Node::new_root("system");
    let mut has_file = false;

    for (module_id, path) in sources {
        let mod_system = path.join("system");
        if target_contains(targets, "system") && mod_system.is_dir() {
            log::debug!("collecting module source {module_id}: {}", path.display());
            has_file |= system.collect_module_files(&mod_system)?;
        }

        has_file |= collect_builtin_partition_files(&mut root, path, targets, &partitions)?;
    }

    if has_file {
        for (partition, require_system_symlink) in &partitions {
            if target_contains(targets, partition)
                && partition_should_mount(partition, *require_system_symlink)
            {
                let name = partition.clone();
                if let Some(node) = system.children.remove(&name) {
                    match root.children.entry(name) {
                        Entry::Occupied(_) => {}
                        Entry::Vacant(v) => {
                            v.insert(node);
                        }
                    }
                }
            } else if targets.is_some() {
                system.children.remove(partition);
            }
        }
        if targets.is_none() || !system.children.is_empty() {
            root.children.insert("system".to_string(), system);
        }
        if root.children.is_empty() {
            Ok(None)
        } else {
            Ok(Some(root))
        }
    } else {
        Ok(None)
    }
}

fn clone_symlink<Src: AsRef<Path>, Dst: AsRef<Path>>(src: Src, dst: Dst) -> Result<()> {
    let src_symlink = read_link(src.as_ref())?;
    symlink(&src_symlink, dst.as_ref())?;
    lsetfilecon(dst.as_ref(), lgetfilecon(src.as_ref())?.as_str())?;
    log::debug!(
        "clone symlink {} -> {}({})",
        dst.as_ref().display(),
        dst.as_ref().display(),
        src_symlink.display()
    );
    Ok(())
}

fn mount_mirror<P: AsRef<Path>, WP: AsRef<Path>>(
    path: P,
    work_dir_path: WP,
    entry: &DirEntry,
) -> Result<()> {
    let path = path.as_ref().join(entry.file_name());
    let work_dir_path = work_dir_path.as_ref().join(entry.file_name());
    let file_type = entry.file_type()?;

    if file_type.is_file() {
        log::debug!(
            "mount mirror file {} -> {}",
            path.display(),
            work_dir_path.display()
        );
        fs::File::create(&work_dir_path)?;
        bind_mount(&path, &work_dir_path)?;
    } else if file_type.is_dir() {
        log::debug!(
            "mount mirror dir {} -> {}",
            path.display(),
            work_dir_path.display()
        );
        create_dir(&work_dir_path)?;
        let metadata = entry.metadata()?;
        chmod(&work_dir_path, Mode::from_raw_mode(metadata.mode()))?;
        chown(
            &work_dir_path,
            Some(Uid::from_raw(metadata.uid())),
            Some(Gid::from_raw(metadata.gid())),
        )?;
        lsetfilecon(&work_dir_path, lgetfilecon(&path)?.as_str())?;
        for entry in read_dir(&path)?.flatten() {
            mount_mirror(&path, &work_dir_path, &entry)?;
        }
    } else if file_type.is_symlink() {
        log::debug!(
            "create mirror symlink {} -> {}",
            path.display(),
            work_dir_path.display()
        );
        clone_symlink(&path, &work_dir_path)?;
    }

    Ok(())
}

fn do_magic_mount<P: AsRef<Path>, WP: AsRef<Path>>(
    path: P,
    work_dir_path: WP,
    current: Node,
    has_tmpfs: bool,
    mount_points: &mut Vec<String>,
) -> Result<()> {
    let mut current = current;
    let path = path.as_ref().join(&current.name);
    let work_dir_path = work_dir_path.as_ref().join(&current.name);
    match current.file_type {
        RegularFile => {
            let target_path = if has_tmpfs {
                fs::File::create(&work_dir_path)?;
                &work_dir_path
            } else {
                &path
            };
            if let Some(module_path) = &current.module_path {
                log::debug!(
                    "mount module file {} -> {}",
                    module_path.display(),
                    work_dir_path.display()
                );
                bind_mount(module_path, target_path).with_context(|| {
                    format!(
                        "mount module file {} -> {}",
                        module_path.display(),
                        work_dir_path.display()
                    )
                })?;
                if !has_tmpfs {
                    mount_points.push(path.display().to_string());
                }
                if let Err(e) = remount(target_path, MountFlags::RDONLY | MountFlags::BIND, "") {
                    log::warn!("make file {} ro: {e:#?}", target_path.display());
                }
            } else {
                bail!("cannot mount root file {}!", path.display());
            }
        }
        Symlink => {
            if let Some(module_path) = &current.module_path {
                log::debug!(
                    "create module symlink {} -> {}",
                    module_path.display(),
                    work_dir_path.display()
                );
                clone_symlink(module_path, &work_dir_path).with_context(|| {
                    format!(
                        "create module symlink {} -> {}",
                        module_path.display(),
                        work_dir_path.display()
                    )
                })?;
            } else {
                bail!("cannot mount root symlink {}!", path.display());
            }
        }
        Directory => {
            let mut create_tmpfs = !has_tmpfs && current.replace && current.module_path.is_some();
            if !has_tmpfs && !create_tmpfs {
                for it in &mut current.children {
                    let (name, node) = it;
                    let real_path = path.join(name);
                    let need = match node.file_type {
                        Symlink => true,
                        Whiteout => real_path.exists(),
                        _ => {
                            if let Ok(metadata) = real_path.symlink_metadata() {
                                let file_type = NodeFileType::from_file_type(metadata.file_type())
                                    .unwrap_or(Whiteout);
                                file_type != node.file_type || file_type == Symlink
                            } else {
                                true
                            }
                        }
                    };
                    if need {
                        if current.module_path.is_none() {
                            log::error!(
                                "cannot create tmpfs on {}, ignore: {name}",
                                path.display()
                            );
                            node.skip = true;
                            continue;
                        }
                        create_tmpfs = true;
                        break;
                    }
                }
            }

            let has_tmpfs = has_tmpfs || create_tmpfs;

            if has_tmpfs {
                log::debug!(
                    "creating tmpfs skeleton for {} at {}",
                    path.display(),
                    work_dir_path.display()
                );
                create_dir_all(&work_dir_path)?;
                let (metadata, path) = if path.exists() {
                    (path.metadata()?, &path)
                } else if let Some(module_path) = &current.module_path {
                    (module_path.metadata()?, module_path)
                } else {
                    bail!("cannot mount root dir {}!", path.display());
                };
                chmod(&work_dir_path, Mode::from_raw_mode(metadata.mode()))?;
                chown(
                    &work_dir_path,
                    Some(Uid::from_raw(metadata.uid())),
                    Some(Gid::from_raw(metadata.gid())),
                )?;
                lsetfilecon(&work_dir_path, lgetfilecon(path)?.as_str())?;
            }

            if create_tmpfs {
                log::debug!(
                    "creating tmpfs for {} at {}",
                    path.display(),
                    work_dir_path.display()
                );
                bind_mount(&work_dir_path, &work_dir_path)
                    .context("bind self")
                    .with_context(|| {
                        format!(
                            "creating tmpfs for {} at {}",
                            path.display(),
                            work_dir_path.display()
                        )
                    })?;
            }

            if path.exists() && !current.replace {
                for entry in path.read_dir()?.flatten() {
                    let name = entry.file_name().to_string_lossy().to_string();
                    let result = if let Some(node) = current.children.remove(&name) {
                        if node.skip {
                            continue;
                        }
                        do_magic_mount(&path, &work_dir_path, node, has_tmpfs, mount_points)
                            .with_context(|| format!("magic mount {}/{name}", path.display()))
                    } else if has_tmpfs {
                        mount_mirror(&path, &work_dir_path, &entry)
                            .with_context(|| format!("mount mirror {}/{name}", path.display()))
                    } else {
                        Ok(())
                    };

                    if let Err(e) = result {
                        if has_tmpfs {
                            return Err(e);
                        }
                        log::error!("mount child {}/{name} failed: {e:#?}", path.display());
                    }
                }
            }

            if current.replace {
                if current.module_path.is_none() {
                    bail!(
                        "dir {} is declared as replaced but it is root!",
                        path.display()
                    );
                }
                log::debug!("dir {} is replaced", path.display());
            }

            for (name, node) in current.children {
                if node.skip {
                    continue;
                }
                if let Err(e) = do_magic_mount(&path, &work_dir_path, node, has_tmpfs, mount_points)
                    .with_context(|| format!("magic mount {}/{name}", path.display()))
                {
                    if has_tmpfs {
                        return Err(e);
                    }
                    log::error!("mount child {}/{name} failed: {e:#?}", path.display());
                }
            }

            if create_tmpfs {
                log::debug!(
                    "moving tmpfs {} -> {}",
                    work_dir_path.display(),
                    path.display()
                );
                if let Err(e) = remount(&work_dir_path, MountFlags::RDONLY | MountFlags::BIND, "") {
                    log::warn!("make dir {} ro: {e:#?}", path.display());
                }
                move_mount(&work_dir_path, &path)
                    .context("move self")
                    .with_context(|| {
                        format!(
                            "moving tmpfs {} -> {}",
                            work_dir_path.display(),
                            path.display()
                        )
                    })?;
                mount_points.push(path.display().to_string());
                if let Err(e) = mount_change(&path, MountPropagationFlags::PRIVATE) {
                    log::warn!("make dir {} private: {e:#?}", path.display());
                }
            }
        }
        Whiteout => {
            log::debug!("file {} is removed", path.display());
        }
    }

    Ok(())
}

pub fn magic_mount(tmp_path: &str, custom_partitions: &[String]) -> Result<Vec<String>> {
    magic_mount_with_targets(tmp_path, None, None, custom_partitions)
}

pub fn magic_mount_partitions(
    tmp_path: &str,
    partitions: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let targets = partitions.iter().cloned().collect::<HashSet<_>>();
    magic_mount_with_targets(tmp_path, Some(&targets), None, custom_partitions)
}

pub fn magic_mount_modules(
    tmp_path: &str,
    module_ids: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let module_filter = module_ids.iter().cloned().collect::<HashSet<_>>();
    magic_mount_with_targets(tmp_path, None, Some(&module_filter), custom_partitions)
}

pub fn magic_mount_module_partitions(
    tmp_path: &str,
    module_ids: &[String],
    partitions: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let targets = partitions.iter().cloned().collect::<HashSet<_>>();
    let module_filter = module_ids.iter().cloned().collect::<HashSet<_>>();
    magic_mount_with_targets(
        tmp_path,
        Some(&targets),
        Some(&module_filter),
        custom_partitions,
    )
}

fn magic_mount_with_targets(
    tmp_path: &str,
    targets: Option<&HashSet<String>>,
    module_filter: Option<&HashSet<String>>,
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    collect_module_files_with_targets(targets, module_filter, custom_partitions)?.map_or_else(
        || {
            log::info!("no modules to mount, skipping!");
            Ok(Vec::new())
        },
        |root| mount_collected_root(tmp_path, root),
    )
}

fn mount_collected_root(tmp_path: &str, root: Node) -> Result<Vec<String>> {
    log::debug!("collected: {root:#?}");
    let tmp_dir = Path::new(tmp_path).join("workdir");
    ensure_dir_exists(&tmp_dir)?;
    let empty_data = CString::new("")?;
    mount(
        KSU_MOUNT_SOURCE,
        &tmp_dir,
        "tmpfs",
        MountFlags::empty(),
        empty_data.as_c_str(),
    )
    .context("mount tmp")?;
    mount_change(&tmp_dir, MountPropagationFlags::PRIVATE).context("make tmp private")?;
    let mut mount_points = Vec::new();
    let result = do_magic_mount("/", &tmp_dir, root, false, &mut mount_points);
    if let Err(e) = unmount(&tmp_dir, UnmountFlags::DETACH) {
        log::error!("failed to unmount tmp {e}");
    }
    fs::remove_dir(tmp_dir).ok();
    result.map(|()| mount_points)
}
