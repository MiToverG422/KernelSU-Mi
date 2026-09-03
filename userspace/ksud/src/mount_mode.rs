use anyhow::{Context, Result, bail};
use std::{fmt, fs, io::ErrorKind, path::Path, str::FromStr};

use crate::{defs, restorecon, utils};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MountMode {
    MetaModule,
    MisuMount,
}

impl MountMode {
    pub const DEFAULT: Self = Self::MetaModule;

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::MetaModule => "meta_module",
            Self::MisuMount => "misu_mount",
        }
    }

    pub const fn uses_metamodule(self) -> bool {
        matches!(self, Self::MetaModule)
    }
}

impl fmt::Display for MountMode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for MountMode {
    type Err = anyhow::Error;

    fn from_str(value: &str) -> Result<Self> {
        match value.trim().to_ascii_lowercase().as_str() {
            "meta_module" | "metamodule" | "meta-module" | "meta" => Ok(Self::MetaModule),
            "misu_mount" | "misu-mount" | "misu" | "magic_mount" | "magic-mount" | "magic"
            | "overlayfs" | "overlay_fs" | "overlay-fs" | "overlay" => Ok(Self::MisuMount),
            _ => bail!("unknown mount mode: {value}"),
        }
    }
}

pub fn current() -> MountMode {
    match fs::read_to_string(defs::MOUNT_MODE_FILE) {
        Ok(value) => match MountMode::from_str(&value) {
            Ok(mode) => mode,
            Err(err) => {
                log::warn!("{err:#}, using default mount mode");
                MountMode::DEFAULT
            }
        },
        Err(err) if err.kind() == ErrorKind::NotFound => MountMode::DEFAULT,
        Err(err) => {
            log::warn!(
                "failed to read {}: {err}, using default mount mode",
                defs::MOUNT_MODE_FILE
            );
            MountMode::DEFAULT
        }
    }
}

pub fn uses_metamodule() -> bool {
    current().uses_metamodule()
}

pub fn set(mode: MountMode) -> Result<()> {
    utils::ensure_dir_exists(Path::new(defs::WORKING_DIR))?;
    fs::write(defs::MOUNT_MODE_FILE, format!("{}\n", mode.as_str()))
        .with_context(|| format!("failed to write {}", defs::MOUNT_MODE_FILE))?;
    if let Err(err) = restorecon::lsetfilecon(defs::MOUNT_MODE_FILE, restorecon::KSU_CON) {
        log::warn!(
            "failed to set context on {}: {err:#}",
            defs::MOUNT_MODE_FILE
        );
    }
    Ok(())
}

pub fn print_current() {
    println!("{}", current().as_str());
}

pub fn set_from_cli(value: &str) -> Result<()> {
    let mode = MountMode::from_str(value)?;
    set(mode)?;
    println!("{}", mode.as_str());
    Ok(())
}

pub fn print_modes() {
    for mode in [MountMode::MetaModule, MountMode::MisuMount] {
        println!("{}", mode.as_str());
    }
}
