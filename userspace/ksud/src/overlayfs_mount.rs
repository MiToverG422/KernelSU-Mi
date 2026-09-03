use anyhow::{Context, Result, bail};
use extattr::{Flags as XattrFlags, lgetxattr, lsetxattr};
use log::{info, warn};
use rustix::{
    fd::AsFd,
    fs::CWD,
    mount::{
        FsMountFlags, FsOpenFlags, MountAttrFlags, MountFlags, MountPropagationFlags,
        MoveMountFlags, OpenTreeFlags, UnmountFlags, fsconfig_create, fsconfig_set_string, fsmount,
        fsopen, mount, mount_change, move_mount, open_tree, unmount,
    },
};
use std::collections::{HashMap, HashSet};
use std::ffi::CString;
use std::fs;
use std::io;
use std::os::unix::ffi::OsStrExt;
use std::os::unix::fs::{FileTypeExt, MetadataExt, PermissionsExt, symlink};
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::defs::{
    DISABLE_FILE_NAME, KSU_OVERLAY_SOURCE, REMOVE_FILE_NAME, SKIP_MOUNT_FILE_NAME, SYSTEM_RW_DIR,
    WORKING_DIR,
};
use crate::mount_config::OverlayStorageMode;
use crate::restorecon::{lgetfilecon, lsetfilecon};
use crate::utils::ensure_dir_exists;

const OVERLAY_STAGE_DIR_NAME: &str = "overlayfs";
const OVERLAY_STAGE_IMAGE_PREFIX: &str = ".misu_overlay_stage";
const OVERLAY_STAGE_IMAGE_SUFFIX: &str = ".img";
const OVERLAY_STAGE_EXT4_MIN_SIZE: u64 = 64 * 1024 * 1024;
const OVERLAY_STAGE_EXT4_EXTRA_SIZE: u64 = 32 * 1024 * 1024;
const OVERLAY_STAGE_EXT4_MAX_SIZE: u64 = 2048 * 1024 * 1024;
const MIB: u64 = 1024 * 1024;
const OVERLAY_OPAQUE_XATTR: &str = "trusted.overlay.opaque";
const OVERLAY_PARTITIONS: &[&str] = &["vendor", "product", "system_ext", "odm", "oem"];

fn configured_partitions(custom_partitions: &[String]) -> Vec<String> {
    let mut partitions = OVERLAY_PARTITIONS
        .iter()
        .map(|partition| (*partition).to_string())
        .collect::<Vec<_>>();

    for partition in custom_partitions {
        if !partitions.iter().any(|existing| existing == partition) {
            partitions.push(partition.clone());
        }
    }

    partitions
}

#[derive(Debug, Default)]
pub struct OverlayMountResult {
    pub mount_points: Vec<String>,
    pub fallback_partitions: Vec<String>,
    pub staging_storage: Option<OverlayStorageMode>,
    pub warnings: Vec<String>,
}

struct OverlayStage {
    root: PathBuf,
    image: Option<PathBuf>,
    storage: OverlayStorageMode,
}

pub fn mount_overlayfs(
    lower_dirs: &[String],
    lowest: &str,
    upperdir: Option<PathBuf>,
    workdir: Option<PathBuf>,
    dest: impl AsRef<Path>,
) -> Result<()> {
    let lowerdir_config = lower_dirs
        .iter()
        .map(String::as_str)
        .chain(std::iter::once(lowest))
        .collect::<Vec<_>>()
        .join(":");
    info!(
        "mount overlayfs on {}, lowerdir={}, upperdir={:?}, workdir={:?}",
        dest.as_ref().display(),
        lowerdir_config,
        upperdir,
        workdir
    );

    let upperdir = upperdir
        .filter(|up| up.exists())
        .map(|e| e.display().to_string());
    let workdir = workdir
        .filter(|wd| wd.exists())
        .map(|e| e.display().to_string());

    let result = (|| {
        let fs = fsopen("overlay", FsOpenFlags::FSOPEN_CLOEXEC)?;
        let fs = fs.as_fd();
        fsconfig_set_string(fs, "lowerdir", &lowerdir_config)?;
        if let (Some(upperdir), Some(workdir)) = (&upperdir, &workdir) {
            fsconfig_set_string(fs, "upperdir", upperdir)?;
            fsconfig_set_string(fs, "workdir", workdir)?;
        }
        fsconfig_set_string(fs, "source", KSU_OVERLAY_SOURCE)?;
        fsconfig_create(fs)?;
        let mount = fsmount(fs, FsMountFlags::FSMOUNT_CLOEXEC, MountAttrFlags::empty())?;
        move_mount(
            mount.as_fd(),
            "",
            CWD,
            dest.as_ref(),
            MoveMountFlags::MOVE_MOUNT_F_EMPTY_PATH,
        )
    })();

    if let Err(e) = result {
        warn!("fsopen mount failed: {e:#}, fallback to mount");
        let mut data = format!("lowerdir={lowerdir_config}");
        if let (Some(upperdir), Some(workdir)) = (upperdir, workdir) {
            data = format!("{data},upperdir={upperdir},workdir={workdir}");
        }
        let data = CString::new(data)?;
        mount(
            KSU_OVERLAY_SOURCE,
            dest.as_ref(),
            "overlay",
            MountFlags::empty(),
            data.as_c_str(),
        )?;
    }
    Ok(())
}

pub fn bind_mount(from: impl AsRef<Path>, to: impl AsRef<Path>) -> Result<()> {
    info!(
        "bind mount {} -> {}",
        from.as_ref().display(),
        to.as_ref().display()
    );
    let tree = open_tree(
        CWD,
        from.as_ref(),
        OpenTreeFlags::OPEN_TREE_CLOEXEC
            | OpenTreeFlags::OPEN_TREE_CLONE
            | OpenTreeFlags::AT_RECURSIVE,
    )?;
    move_mount(
        tree.as_fd(),
        "",
        CWD,
        to.as_ref(),
        MoveMountFlags::MOVE_MOUNT_F_EMPTY_PATH,
    )?;
    Ok(())
}

fn mount_overlay_child(
    mount_point: &str,
    relative: &str,
    module_roots: &[String],
    stock_root: &str,
) -> Result<bool> {
    if !module_roots
        .iter()
        .any(|lower| Path::new(&format!("{lower}{relative}")).exists())
    {
        bind_mount(stock_root, mount_point)?;
        return Ok(true);
    }
    if !Path::new(stock_root).is_dir() {
        return Ok(false);
    }
    let mut lower_dirs: Vec<String> = vec![];
    for lower in module_roots {
        let lower_dir = format!("{lower}{relative}");
        let path = Path::new(&lower_dir);
        if path.is_dir() {
            lower_dirs.push(lower_dir);
        } else if path.exists() {
            return Ok(false);
        }
    }
    if lower_dirs.is_empty() {
        return Ok(false);
    }
    if let Err(e) = mount_overlayfs(&lower_dirs, stock_root, None, None, mount_point) {
        warn!("failed: {e:#}, fallback to bind mount");
        bind_mount(stock_root, mount_point)?;
    }
    Ok(true)
}

pub fn mount_overlay(
    root: &str,
    module_roots: &[String],
    workdir: Option<PathBuf>,
    upperdir: Option<PathBuf>,
) -> Result<Vec<String>> {
    info!("mount overlay for {root}");
    std::env::set_current_dir(root).with_context(|| format!("failed to chdir to {root}"))?;
    let stock_root = ".";

    let mut mount_seq = collect_child_mount_points(root).with_context(|| "get mountinfo")?;
    mount_seq.sort();
    mount_seq.dedup();

    mount_overlayfs(module_roots, root, upperdir, workdir, root)
        .with_context(|| "mount overlayfs for root failed")?;

    let mut mounted_points = vec![root.to_string()];

    for mount_point in mount_seq {
        let relative = mount_point.replacen(root, "", 1);
        let stock_root = format!("{stock_root}{relative}");
        if !Path::new(&stock_root).exists() {
            continue;
        }
        match mount_overlay_child(&mount_point, &relative, module_roots, &stock_root) {
            Ok(true) => mounted_points.push(mount_point),
            Ok(false) => {}
            Err(e) => {
                warn!("failed to mount overlay for child {mount_point}: {e:#}, revert");
                umount_dir(root).with_context(|| format!("failed to revert {root}"))?;
                return Err(e);
            }
        }
    }

    Ok(mounted_points)
}

pub fn umount_dir(src: impl AsRef<Path>) -> Result<()> {
    unmount(src.as_ref(), UnmountFlags::empty())
        .with_context(|| format!("Failed to umount {}", src.as_ref().display()))?;
    Ok(())
}

fn mount_partition(partition_name: &str, lowerdir: &[String]) -> Result<Vec<String>> {
    if lowerdir.is_empty() {
        warn!("partition: {partition_name} lowerdir is empty");
        return Ok(Vec::new());
    }

    let partition = format!("/{partition_name}");

    if Path::new(&partition).read_link().is_ok() {
        warn!("partition: {partition} is a symlink");
        return Ok(Vec::new());
    }

    let mut workdir = None;
    let mut upperdir = None;
    let system_rw_dir = Path::new(SYSTEM_RW_DIR);
    if system_rw_dir.exists() {
        workdir = Some(system_rw_dir.join(partition_name).join("workdir"));
        upperdir = Some(system_rw_dir.join(partition_name).join("upperdir"));
    }

    mount_overlay(&partition, lowerdir, workdir, upperdir)
}

fn module_is_metamodule(module_path: &Path) -> bool {
    crate::module::read_module_prop(module_path)
        .is_ok_and(|props| crate::metamodule::is_metamodule(&props))
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

fn collect_enabled_modules(
    metadata_dir: &str,
    module_filter: Option<&HashSet<String>>,
) -> Result<Vec<String>> {
    let dir = std::fs::read_dir(metadata_dir)
        .with_context(|| format!("Failed to read metadata directory: {metadata_dir}"))?;

    let mut enabled = Vec::new();

    for entry in dir.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }

        let module_id = match entry.file_name().to_str() {
            Some(id) => id.to_string(),
            None => continue,
        };

        if !module_matches_filter(&path, &module_id, module_filter) {
            continue;
        }

        if module_id == ".rw"
            || path.join(DISABLE_FILE_NAME).exists()
            || path.join(REMOVE_FILE_NAME).exists()
            || path.join(SKIP_MOUNT_FILE_NAME).exists()
            || module_is_metamodule(&path)
        {
            continue;
        }

        if !path.join("module.prop").exists() {
            warn!("Module {module_id} has no module.prop, skipping");
            continue;
        }

        info!("Module {module_id} enabled");
        enabled.push(module_id);
    }

    Ok(enabled)
}

fn path_to_cstring(path: &Path) -> Result<CString> {
    CString::new(path.as_os_str().as_bytes())
        .with_context(|| format!("path contains NUL byte: {}", path.display()))
}

fn lchown_path(path: &Path, uid: u32, gid: u32) -> Result<()> {
    let path = path_to_cstring(path)?;
    // SAFETY: The CString pointers are valid for this call and are NUL-terminated.
    let ret = unsafe { libc::lchown(path.as_ptr(), uid, gid) };
    if ret == 0 {
        Ok(())
    } else {
        Err(io::Error::last_os_error()).context("lchown failed")
    }
}

fn create_whiteout(path: &Path, mode: u32) -> Result<()> {
    let path_cstr = path_to_cstring(path)?;
    let node_mode = (libc::S_IFCHR as libc::mode_t) | ((mode & 0o7777) as libc::mode_t);
    // SAFETY: The CString pointer is valid for this call and is NUL-terminated.
    let ret = unsafe { libc::mknod(path_cstr.as_ptr(), node_mode, 0) };
    if ret == 0 {
        Ok(())
    } else {
        Err(io::Error::last_os_error())
            .with_context(|| format!("create whiteout {}", path.display()))
    }
}

fn copy_optional_xattr(src: &Path, dst: &Path, name: &str) {
    if let Ok(value) = lgetxattr(src, name)
        && let Err(err) = lsetxattr(dst, name, value, XattrFlags::empty())
    {
        warn!(
            "failed to copy xattr {name} {} -> {}: {err}",
            src.display(),
            dst.display()
        );
    }
}

fn copy_selinux_context(src: &Path, dst: &Path) {
    if let Ok(context) = lgetfilecon(src)
        && let Err(err) = lsetfilecon(dst, context.as_str())
    {
        warn!(
            "failed to copy selinux context {} -> {}: {err:#}",
            src.display(),
            dst.display()
        );
    }
}

fn apply_staged_metadata(
    src: &Path,
    dst: &Path,
    metadata: &fs::Metadata,
    set_mode: bool,
) -> Result<()> {
    if set_mode {
        fs::set_permissions(dst, fs::Permissions::from_mode(metadata.mode() & 0o7777))
            .with_context(|| format!("chmod {}", dst.display()))?;
    }
    lchown_path(dst, metadata.uid(), metadata.gid())
        .with_context(|| format!("chown {}", dst.display()))?;
    copy_selinux_context(src, dst);
    copy_optional_xattr(src, dst, OVERLAY_OPAQUE_XATTR);
    Ok(())
}

fn copy_entry_recursive(src: &Path, dst: &Path) -> Result<()> {
    let metadata = fs::symlink_metadata(src).with_context(|| format!("stat {}", src.display()))?;
    let file_type = metadata.file_type();

    if file_type.is_dir() {
        fs::create_dir(dst).with_context(|| format!("mkdir {}", dst.display()))?;
        apply_staged_metadata(src, dst, &metadata, true)?;

        let entries = fs::read_dir(src).with_context(|| format!("read_dir {}", src.display()))?;
        for entry in entries.flatten() {
            copy_entry_recursive(&entry.path(), &dst.join(entry.file_name()))?;
        }
    } else if file_type.is_file() {
        fs::copy(src, dst)
            .with_context(|| format!("copy staged file {} -> {}", src.display(), dst.display()))?;
        apply_staged_metadata(src, dst, &metadata, true)?;
    } else if file_type.is_symlink() {
        let target = fs::read_link(src).with_context(|| format!("read_link {}", src.display()))?;
        symlink(&target, dst).with_context(|| {
            format!(
                "create staged symlink {} -> {}",
                dst.display(),
                target.display()
            )
        })?;
        apply_staged_metadata(src, dst, &metadata, false)?;
    } else if file_type.is_char_device() && metadata.rdev() == 0 {
        create_whiteout(dst, metadata.mode())?;
        apply_staged_metadata(src, dst, &metadata, true)?;
    } else {
        warn!("unsupported module node {}, skipping", src.display());
    }

    Ok(())
}

fn is_mount_point(path: &Path) -> Result<bool> {
    let mountinfo = std::fs::read_to_string("/proc/self/mountinfo")?;
    let path = path.display().to_string();

    for line in mountinfo.lines() {
        let Some(mount_point) = line.split(' ').nth(4) else {
            continue;
        };
        if unescape_mountinfo_field(mount_point) == path {
            return Ok(true);
        }
    }

    Ok(false)
}

fn cleanup_overlay_stage(stage_root: &Path, image_path: Option<&Path>) {
    if is_mount_point(stage_root).unwrap_or(false)
        && let Err(err) = unmount(stage_root, UnmountFlags::DETACH)
    {
        warn!(
            "failed to unmount overlayfs staging {}: {err}",
            stage_root.display()
        );
    }
    if let Err(err) = fs::remove_dir_all(stage_root) {
        warn!(
            "failed to remove overlayfs staging {}: {err}",
            stage_root.display()
        );
    }
    if let Some(image_path) = image_path
        && let Err(err) = fs::remove_file(image_path)
        && err.kind() != io::ErrorKind::NotFound
    {
        warn!(
            "failed to remove overlayfs staging image {}: {err}",
            image_path.display()
        );
    }
}

fn stage_image_path(stage_root: &Path, base_stage_root: &Path) -> PathBuf {
    let suffix = if stage_root == base_stage_root {
        String::new()
    } else {
        format!("-{}", std::process::id())
    };
    Path::new(WORKING_DIR).join(format!(
        "{OVERLAY_STAGE_IMAGE_PREFIX}{suffix}{OVERLAY_STAGE_IMAGE_SUFFIX}"
    ))
}

fn estimate_entry_size(path: &Path) -> Result<u64> {
    let metadata =
        fs::symlink_metadata(path).with_context(|| format!("stat {}", path.display()))?;
    let file_type = metadata.file_type();

    if file_type.is_dir() {
        let mut size = 4096;
        for entry in fs::read_dir(path).with_context(|| format!("read_dir {}", path.display()))? {
            let entry = entry?;
            size += estimate_entry_size(&entry.path())?;
        }
        Ok(size)
    } else if file_type.is_file() {
        Ok(metadata.len().max(4096))
    } else if file_type.is_symlink() {
        Ok(fs::read_link(path)
            .map_or(0, |target| target.as_os_str().as_bytes().len() as u64)
            .max(4096))
    } else {
        Ok(4096)
    }
}

fn estimate_stage_image_size(sources: &[(String, PathBuf)]) -> Result<u64> {
    let mut total = 0_u64;
    for (_, source) in sources {
        if source.exists() {
            total = total.saturating_add(estimate_entry_size(source)?);
        }
    }

    let requested = total
        .saturating_mul(2)
        .saturating_add(OVERLAY_STAGE_EXT4_EXTRA_SIZE)
        .clamp(OVERLAY_STAGE_EXT4_MIN_SIZE, OVERLAY_STAGE_EXT4_MAX_SIZE);
    Ok(requested.div_ceil(MIB) * MIB)
}

fn run_format_ext4(image_path: &Path) -> Result<()> {
    let candidates: [(&str, &[&str]); 4] = [
        ("/system/bin/mke2fs", &["-t", "ext4", "-F"]),
        ("mke2fs", &["-t", "ext4", "-F"]),
        ("/system/bin/mkfs.ext4", &["-F"]),
        ("mkfs.ext4", &["-F"]),
    ];
    let mut errors = Vec::new();

    for (binary, args) in candidates {
        let output = match Command::new(binary).args(args).arg(image_path).output() {
            Ok(output) => output,
            Err(err) if err.kind() == io::ErrorKind::NotFound => {
                errors.push(format!("{binary}: not found"));
                continue;
            }
            Err(err) => {
                errors.push(format!("{binary}: {err}"));
                continue;
            }
        };

        if output.status.success() {
            return Ok(());
        }

        let stderr = String::from_utf8_lossy(&output.stderr);
        let stdout = String::from_utf8_lossy(&output.stdout);
        errors.push(format!(
            "{binary}: exit={} {} {}",
            output.status,
            stdout.trim(),
            stderr.trim()
        ));
    }

    bail!("format ext4 staging image failed: {}", errors.join("; "))
}

fn run_mount_ext4_image(image_path: &Path, stage_root: &Path) -> Result<()> {
    let candidates: [(&str, &[&str]); 4] = [
        ("/system/bin/mount", &[]),
        ("mount", &[]),
        ("/system/bin/toybox", &["mount"]),
        ("toybox", &["mount"]),
    ];
    let mut errors = Vec::new();

    for (binary, prefix_args) in candidates {
        let output = match Command::new(binary)
            .args(prefix_args)
            .args(["-t", "ext4", "-o", "loop,noatime"])
            .arg(image_path)
            .arg(stage_root)
            .output()
        {
            Ok(output) => output,
            Err(err) if err.kind() == io::ErrorKind::NotFound => {
                errors.push(format!("{binary}: not found"));
                continue;
            }
            Err(err) => {
                errors.push(format!("{binary}: {err}"));
                continue;
            }
        };

        if output.status.success() {
            return Ok(());
        }

        let stderr = String::from_utf8_lossy(&output.stderr);
        let stdout = String::from_utf8_lossy(&output.stdout);
        errors.push(format!(
            "{binary}: exit={} {} {}",
            output.status,
            stdout.trim(),
            stderr.trim()
        ));
    }

    bail!("mount ext4 staging image failed: {}", errors.join("; "))
}

fn mount_tmpfs_stage(stage_root: &Path) -> Result<()> {
    let mount_data = CString::new("mode=0755")?;
    mount(
        KSU_OVERLAY_SOURCE,
        stage_root,
        "tmpfs",
        MountFlags::empty(),
        mount_data.as_c_str(),
    )
    .with_context(|| format!("mount overlayfs staging tmpfs at {}", stage_root.display()))?;
    mount_change(stage_root, MountPropagationFlags::PRIVATE)
        .with_context(|| format!("make overlayfs staging private at {}", stage_root.display()))?;
    Ok(())
}

fn mount_ext4_stage(
    stage_root: &Path,
    image_path: &Path,
    sources: &[(String, PathBuf)],
) -> Result<()> {
    if image_path.exists() {
        fs::remove_file(image_path)
            .with_context(|| format!("remove stale staging image {}", image_path.display()))?;
    }
    let image_size = estimate_stage_image_size(sources)?;
    let image = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create_new(true)
        .open(image_path)
        .with_context(|| format!("create staging image {}", image_path.display()))?;
    image
        .set_len(image_size)
        .with_context(|| format!("resize staging image {}", image_path.display()))?;
    drop(image);

    run_format_ext4(image_path)?;
    run_mount_ext4_image(image_path, stage_root)?;
    mount_change(stage_root, MountPropagationFlags::PRIVATE)
        .with_context(|| format!("make overlayfs staging private at {}", stage_root.display()))?;

    if let Err(err) = crate::ksucalls::nuke_ext4_sysfs(&stage_root.to_string_lossy()) {
        warn!(
            "failed to hide overlayfs ext4 staging sysfs {}: {err:#}",
            stage_root.display()
        );
    }

    info!(
        "overlayfs staging ext4 image prepared at {}, size={} MiB",
        stage_root.display(),
        image_size / MIB
    );
    Ok(())
}

fn prepare_overlay_stage(
    enabled_modules: &[String],
    content_dir: &str,
    storage_mode: OverlayStorageMode,
) -> Result<OverlayStage> {
    let sources = enabled_modules
        .iter()
        .map(|module_id| (module_id.clone(), Path::new(content_dir).join(module_id)))
        .collect::<Vec<_>>();
    prepare_overlay_stage_sources(&sources, storage_mode)
}

fn prepare_overlay_stage_sources(
    sources: &[(String, PathBuf)],
    storage_mode: OverlayStorageMode,
) -> Result<OverlayStage> {
    let base_stage_root = Path::new(WORKING_DIR).join(OVERLAY_STAGE_DIR_NAME);
    let stage_root = if is_mount_point(&base_stage_root).unwrap_or(false) {
        Path::new(WORKING_DIR).join(format!("{}-{}", OVERLAY_STAGE_DIR_NAME, std::process::id()))
    } else {
        base_stage_root.clone()
    };

    if stage_root.exists() {
        if stage_root.is_dir() {
            fs::remove_dir_all(&stage_root)
                .with_context(|| format!("remove stale staging {}", stage_root.display()))?;
        } else {
            fs::remove_file(&stage_root)
                .with_context(|| format!("remove stale staging file {}", stage_root.display()))?;
        }
    }
    ensure_dir_exists(&stage_root)?;

    let mut image_path = None;
    let actual_storage = match storage_mode {
        OverlayStorageMode::Tmpfs => {
            if let Err(err) = mount_tmpfs_stage(&stage_root) {
                cleanup_overlay_stage(&stage_root, None);
                return Err(err);
            }
            OverlayStorageMode::Tmpfs
        }
        OverlayStorageMode::Ext4 => {
            let image = stage_image_path(&stage_root, &base_stage_root);
            if let Err(err) = mount_ext4_stage(&stage_root, &image, sources) {
                cleanup_overlay_stage(&stage_root, Some(&image));
                return Err(err);
            }
            image_path = Some(image);
            OverlayStorageMode::Ext4
        }
    };

    let copy_result = (|| {
        for (module_id, src) in sources {
            if !src.exists() {
                warn!("Module {module_id} has no content directory, skipping stage copy");
                continue;
            }

            let dst = stage_root.join(module_id);
            info!("Staging module {module_id} to {}", dst.display());
            copy_entry_recursive(src, &dst).with_context(|| format!("stage module {module_id}"))?;
        }
        Ok(())
    })();

    if let Err(err) = copy_result {
        cleanup_overlay_stage(&stage_root, image_path.as_deref());
        return Err(err);
    }

    info!(
        "overlayfs staging {} prepared at {}",
        actual_storage,
        stage_root.display(),
    );
    Ok(OverlayStage {
        root: stage_root,
        image: image_path,
        storage: actual_storage,
    })
}

fn collect_partition_lowerdirs(
    enabled_modules: &[String],
    content_dir: &str,
    partitions: &[String],
) -> (Vec<String>, HashMap<String, Vec<String>>) {
    let mut system_lowerdir: Vec<String> = Vec::new();
    let mut partition_lowerdir: HashMap<String, Vec<String>> = HashMap::new();

    for part in partitions {
        partition_lowerdir.insert(part.clone(), Vec::new());
    }

    for module_id in enabled_modules {
        let module_content_path = Path::new(content_dir).join(module_id);

        if !module_content_path.exists() {
            warn!("Module {module_id} has no content directory, skipping");
            continue;
        }

        info!("Processing module: {module_id}");

        let system_path = module_content_path.join("system");
        if system_path.is_dir() {
            system_lowerdir.push(system_path.display().to_string());
            info!("  + system/");
        }

        for part in partitions {
            let part_path = module_content_path.join(part);
            let system_part_path = system_path.join(part);
            let partition_path = if part_path.is_dir() {
                Some(part_path)
            } else if system_part_path.is_dir() {
                Some(system_part_path)
            } else {
                None
            };
            if let Some(partition_path) = partition_path
                && let Some(v) = partition_lowerdir.get_mut(part)
            {
                v.push(partition_path.display().to_string());
                info!("  + {part}/");
            }
        }
    }

    (system_lowerdir, partition_lowerdir)
}

fn first_lowerdir<'a>(
    system_lowerdir: &'a [String],
    partition_lowerdir: &'a HashMap<String, Vec<String>>,
) -> Option<&'a String> {
    system_lowerdir
        .iter()
        .chain(
            partition_lowerdir
                .values()
                .flat_map(|lowerdirs| lowerdirs.iter()),
        )
        .next()
}

fn test_overlay_lowerdir(lowerdir: &str) -> Result<()> {
    let test_root = Path::new(WORKING_DIR).join(format!(
        "{}-test-{}",
        OVERLAY_STAGE_DIR_NAME,
        std::process::id()
    ));
    let merged = test_root.join("merged");

    if test_root.exists() {
        fs::remove_dir_all(&test_root)
            .with_context(|| format!("remove stale overlayfs test {}", test_root.display()))?;
    }
    ensure_dir_exists(&merged)?;

    let result = mount_overlayfs(&[], lowerdir, None, None, &merged)
        .with_context(|| format!("test overlay lowerdir {lowerdir}"));

    if result.is_ok()
        && let Err(err) = unmount(&merged, UnmountFlags::DETACH)
    {
        warn!(
            "failed to unmount overlayfs test {}: {err}",
            merged.display()
        );
    }

    if let Err(err) = fs::remove_dir_all(&test_root) {
        warn!(
            "failed to remove overlayfs test {}: {err}",
            test_root.display()
        );
    }

    result
}

fn lowerdirs_need_stage(
    system_lowerdir: &[String],
    partition_lowerdir: &HashMap<String, Vec<String>>,
) -> bool {
    let Some(lowerdir) = first_lowerdir(system_lowerdir, partition_lowerdir) else {
        return false;
    };

    if let Err(err) = test_overlay_lowerdir(lowerdir) {
        warn!("overlayfs cannot use module lowerdir directly, using staging: {err:#}");
        true
    } else {
        false
    }
}

fn mount_partition_with_fallback(
    result: &mut OverlayMountResult,
    partition_name: &str,
    lowerdir: &[String],
) {
    if lowerdir.is_empty() {
        warn!("partition: {partition_name} lowerdir is empty");
        return;
    }

    match mount_partition(partition_name, lowerdir) {
        Ok(points) if points.is_empty() => {
            warn!("partition {partition_name} was not mounted by overlayfs");
            result.fallback_partitions.push(partition_name.to_string());
        }
        Ok(points) => result.mount_points.extend(points),
        Err(e) => {
            warn!("mount {partition_name} failed: {e:#}");
            result.fallback_partitions.push(partition_name.to_string());
        }
    }
}

pub fn mount_modules_systemlessly_detailed(
    metadata_dir: &str,
    content_dir: &str,
    custom_partitions: &[String],
    storage_mode: OverlayStorageMode,
) -> Result<OverlayMountResult> {
    mount_selected_modules_systemlessly_detailed(
        metadata_dir,
        content_dir,
        &[],
        custom_partitions,
        storage_mode,
    )
}

pub fn mount_selected_modules_systemlessly_detailed(
    metadata_dir: &str,
    content_dir: &str,
    module_ids: &[String],
    custom_partitions: &[String],
    storage_mode: OverlayStorageMode,
) -> Result<OverlayMountResult> {
    info!("Scanning modules for built-in overlayfs");
    info!("  Metadata: {metadata_dir}");
    info!("  Content: {content_dir}");

    let module_filter = if module_ids.is_empty() {
        None
    } else {
        Some(module_ids.iter().cloned().collect::<HashSet<_>>())
    };
    let enabled_modules = collect_enabled_modules(metadata_dir, module_filter.as_ref())?;

    if enabled_modules.is_empty() {
        info!("No enabled modules found");
        return Ok(OverlayMountResult::default());
    }

    info!("Found {} enabled module(s)", enabled_modules.len());

    let partitions = configured_partitions(custom_partitions);
    let (mut system_lowerdir, mut partition_lowerdir) =
        collect_partition_lowerdirs(&enabled_modules, content_dir, &partitions);
    let mut result = OverlayMountResult::default();
    let stage = if lowerdirs_need_stage(&system_lowerdir, &partition_lowerdir) {
        let stage = match prepare_overlay_stage(&enabled_modules, content_dir, storage_mode) {
            Ok(stage) => stage,
            Err(err) if storage_mode == OverlayStorageMode::Ext4 => {
                let warning = format!("ext4 staging failed, fallback to tmpfs staging: {err:#}");
                warn!("{warning}");
                result.warnings.push(warning);
                prepare_overlay_stage(&enabled_modules, content_dir, OverlayStorageMode::Tmpfs)?
            }
            Err(err) => return Err(err),
        };
        (system_lowerdir, partition_lowerdir) = collect_partition_lowerdirs(
            &enabled_modules,
            &stage.root.to_string_lossy(),
            &partitions,
        );
        result.staging_storage = Some(stage.storage);
        Some(stage)
    } else {
        None
    };

    mount_partition_with_fallback(&mut result, "system", &system_lowerdir);

    for partition in &partitions {
        if let Some(lowerdir) = partition_lowerdir.get(partition) {
            mount_partition_with_fallback(&mut result, partition, lowerdir);
        }
    }

    info!("All partitions processed");
    if result.mount_points.is_empty() {
        if let Some(stage) = stage.as_ref() {
            warn!("No overlayfs mount point created; removing staging");
            cleanup_overlay_stage(&stage.root, stage.image.as_deref());
        }
    } else if let Some(stage) = stage.as_ref() {
        result
            .mount_points
            .insert(0, stage.root.to_string_lossy().to_string());
    }
    Ok(result)
}

fn collect_child_mount_points(root: &str) -> Result<Vec<String>> {
    let root_path = Path::new(root);
    let mountinfo = std::fs::read_to_string("/proc/self/mountinfo")?;
    let mut result = Vec::new();

    for line in mountinfo.lines() {
        let Some(mount_point) = line.split(' ').nth(4) else {
            continue;
        };
        let mount_point = unescape_mountinfo_field(mount_point);
        let mount_path = Path::new(&mount_point);
        if mount_path.starts_with(root_path) && !root_path.starts_with(mount_path) {
            result.push(mount_point);
        }
    }

    Ok(result)
}

fn unescape_mountinfo_field(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = String::with_capacity(value.len());
    let mut i = 0;

    while i < bytes.len() {
        if bytes[i] == b'\\' && i + 3 < bytes.len() {
            let octal = &value[i + 1..i + 4];
            if octal.bytes().all(|b| (b'0'..=b'7').contains(&b))
                && let Ok(v) = u8::from_str_radix(octal, 8)
            {
                out.push(char::from(v));
                i += 4;
                continue;
            }
        }

        out.push(char::from(bytes[i]));
        i += 1;
    }

    out
}
