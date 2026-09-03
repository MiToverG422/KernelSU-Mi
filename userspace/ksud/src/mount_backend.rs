use anyhow::Result;
use log::{info, warn};
use serde_json::{Value, json};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::{
    ksucalls, metamodule,
    mount_config::{self, BuiltinMountBackend, ModuleMountInfo, OverlayStorageMode},
    mount_mode::MountMode,
};

const UMOUNT_DETACH: u32 = libc::MNT_DETACH as u32;

#[derive(Debug)]
struct MisuMountResult {
    mount_points: Vec<String>,
    flags: u32,
    fallback_partitions: Vec<String>,
    warnings: Vec<String>,
    staging_storage: Option<OverlayStorageMode>,
}

impl Default for MisuMountResult {
    fn default() -> Self {
        Self {
            mount_points: Vec::new(),
            flags: UMOUNT_DETACH,
            fallback_partitions: Vec::new(),
            warnings: Vec::new(),
            staging_storage: None,
        }
    }
}

impl MisuMountResult {
    fn from_mount_points(mount_points: Vec<String>) -> Self {
        Self {
            mount_points,
            ..Self::default()
        }
    }

    fn add_warning(&mut self, warning: impl Into<String>) {
        self.warnings.push(warning.into());
    }
}

fn reset_umount_list() {
    if let Err(err) = ksucalls::umount_list_wipe() {
        warn!("failed to wipe kernel umount list: {err}");
    }
}

fn register_umount_points(points: &[String], flags: u32) {
    if points.is_empty() {
        return;
    }

    info!("registering {} module mount point(s)", points.len());
    for point in points {
        if point.is_empty() {
            continue;
        }
        if let Err(err) = ksucalls::umount_list_add(point, flags) {
            warn!("failed to register module mount point {point}: {err:#}");
        }
    }
    ksucalls::report_module_mounted();
}

fn mount_magic(module_dir: &str, custom_partitions: &[String]) -> Result<Vec<String>> {
    let _ = module_dir;
    let tmp_path = crate::utils::find_tmp_path();
    crate::magic_mount::magic_mount(&tmp_path, custom_partitions)
}

fn mount_magic_modules(
    module_dir: &str,
    module_ids: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let _ = module_dir;
    let tmp_path = crate::utils::find_tmp_path();
    crate::magic_mount::magic_mount_modules(&tmp_path, module_ids, custom_partitions)
}

fn mount_magic_partitions(
    module_dir: &str,
    partitions: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let _ = module_dir;
    let tmp_path = crate::utils::find_tmp_path();
    crate::magic_mount::magic_mount_partitions(&tmp_path, partitions, custom_partitions)
}

fn mount_magic_module_partitions(
    module_dir: &str,
    module_ids: &[String],
    partitions: &[String],
    custom_partitions: &[String],
) -> Result<Vec<String>> {
    let _ = module_dir;
    let tmp_path = crate::utils::find_tmp_path();
    crate::magic_mount::magic_mount_module_partitions(
        &tmp_path,
        module_ids,
        partitions,
        custom_partitions,
    )
}

fn mount_overlay(
    module_dir: &str,
    custom_partitions: &[String],
    storage_mode: OverlayStorageMode,
) -> Result<crate::overlayfs_mount::OverlayMountResult> {
    crate::overlayfs_mount::mount_modules_systemlessly_detailed(
        module_dir,
        module_dir,
        custom_partitions,
        storage_mode,
    )
}

fn mount_overlay_modules(
    module_dir: &str,
    module_ids: &[String],
    custom_partitions: &[String],
    storage_mode: OverlayStorageMode,
) -> Result<crate::overlayfs_mount::OverlayMountResult> {
    crate::overlayfs_mount::mount_selected_modules_systemlessly_detailed(
        module_dir,
        module_dir,
        module_ids,
        custom_partitions,
        storage_mode,
    )
}

fn mount_misu_auto(
    module_dir: &str,
    config: &mount_config::MountConfig,
) -> Result<MisuMountResult> {
    info!("mounting modules with MISU Mount auto backend");

    match crate::overlayfs_mount::mount_modules_systemlessly_detailed(
        module_dir,
        module_dir,
        &config.custom_partitions,
        config.overlay_storage,
    ) {
        Ok(result) => {
            let fallback_partitions = result.fallback_partitions.clone();
            let mut run = MisuMountResult::default();
            merge_overlay_result(&mut run, result);

            if fallback_partitions.is_empty() {
                if run.mount_points.is_empty() {
                    warn!("MISU Mount created no mount points");
                } else {
                    info!("MISU Mount selected OverlayFS for all mountable partitions");
                }
                return Ok(run);
            }

            warn!(
                "MISU Mount falling back to Magic Mount for partition(s): {}",
                fallback_partitions.join(", ")
            );
            run.add_warning(format!(
                "MISU Mount falling back to Magic Mount for partition(s): {}",
                fallback_partitions.join(", ")
            ));

            match mount_magic_partitions(
                module_dir,
                &fallback_partitions,
                &config.custom_partitions,
            ) {
                Ok(points) => {
                    push_unique(&mut run.mount_points, points);
                    Ok(run)
                }
                Err(err) if run.mount_points.is_empty() => Err(err),
                Err(err) => {
                    let warning = format!(
                        "MISU Mount Magic Mount fallback failed, keeping OverlayFS mount points: {err:#}"
                    );
                    warn!("{warning}");
                    run.add_warning(warning);
                    Ok(run)
                }
            }
        }
        Err(err) => {
            let warning = format!(
                "MISU Mount OverlayFS planner failed, falling back to full Magic Mount: {err:#}"
            );
            warn!("{warning}");
            let points = mount_magic(module_dir, &config.custom_partitions)?;
            let mut run = MisuMountResult::from_mount_points(points);
            run.add_warning(warning);
            Ok(run)
        }
    }
}

fn push_unique(target: &mut Vec<String>, points: Vec<String>) {
    for point in points {
        if !target.iter().any(|existing| existing == &point) {
            target.push(point);
        }
    }
}

fn extend_unique(target: &mut Vec<String>, values: &[String]) {
    for value in values {
        if !target.iter().any(|existing| existing == value) {
            target.push(value.clone());
        }
    }
}

fn merge_overlay_result(
    target: &mut MisuMountResult,
    result: crate::overlayfs_mount::OverlayMountResult,
) {
    push_unique(&mut target.mount_points, result.mount_points);
    extend_unique(&mut target.fallback_partitions, &result.fallback_partitions);
    target.warnings.extend(result.warnings);
    if result.staging_storage.is_some() {
        target.staging_storage = result.staging_storage;
    }
}

fn active_mountable_modules(modules: &[ModuleMountInfo]) -> Vec<String> {
    modules
        .iter()
        .filter(|module| module.needs_mount)
        .map(|module| module.id.clone())
        .collect()
}

fn unix_time_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |duration| duration.as_secs())
}

#[allow(clippy::too_many_arguments)]
fn mount_state_json(
    status: &str,
    mode: MountMode,
    config: &mount_config::MountConfig,
    started_at: u64,
    finished_at: Option<u64>,
    modules: &[ModuleMountInfo],
    result: Option<&MisuMountResult>,
    error: Option<&str>,
) -> Value {
    let mountable_modules = active_mountable_modules(modules);
    let failed_modules = if error.is_some() {
        mountable_modules.clone()
    } else {
        Vec::new()
    };
    let actual_overlay_storage = result
        .and_then(|result| result.staging_storage)
        .map(OverlayStorageMode::as_str);
    json!({
        "schema": 1,
        "status": status,
        "mountMode": mode.as_str(),
        "backend": config.backend.as_str(),
        "overlayStorage": config.overlay_storage.as_str(),
        "actualOverlayStorage": actual_overlay_storage,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "moduleCount": modules.len(),
        "mountableModuleCount": mountable_modules.len(),
        "activeMounts": result
            .map(|result| result.mount_points.clone())
            .unwrap_or_default(),
        "fallbackPartitions": result
            .map(|result| result.fallback_partitions.clone())
            .unwrap_or_default(),
        "warnings": result
            .map(|result| result.warnings.clone())
            .unwrap_or_default(),
        "failedModules": failed_modules,
        "error": error,
    })
}

fn save_mount_state(state: &Value) {
    if let Err(err) = mount_config::save_mount_state(state) {
        warn!("failed to save mount state: {err:#}");
    }
}

fn mount_misu(module_dir: &str) -> Result<MisuMountResult> {
    let config = mount_config::load();
    info!(
        "mounting modules with MISU Mount, backend={}, custom_partitions={}",
        config.backend,
        config.custom_partitions.join(",")
    );

    let module_infos = if config.module_backends.is_empty() {
        None
    } else {
        Some(mount_config::scan_module_mount_info(&config)?)
    };

    if config.module_backends.is_empty() {
        return match config.backend {
            BuiltinMountBackend::Auto => mount_misu_auto(module_dir, &config),
            BuiltinMountBackend::MagicMount => {
                let points = mount_magic(module_dir, &config.custom_partitions)?;
                Ok(MisuMountResult::from_mount_points(points))
            }
            BuiltinMountBackend::OverlayFs => {
                let overlay = mount_overlay(
                    module_dir,
                    &config.custom_partitions,
                    config.overlay_storage,
                )?;
                let mut run = MisuMountResult::default();
                merge_overlay_result(&mut run, overlay);
                Ok(run)
            }
            BuiltinMountBackend::Disabled => {
                info!("MISU Mount disabled by built-in mount config");
                Ok(MisuMountResult::default())
            }
        };
    }

    let module_infos = module_infos.unwrap_or_else(|| {
        warn!("module backend overrides were expected but module info cache was empty");
        Vec::new()
    });
    let mut auto_modules = Vec::new();
    let mut overlay_modules = Vec::new();
    let mut magic_modules = Vec::new();

    for module in module_infos {
        if !module.needs_mount {
            continue;
        }
        match module.effective_backend {
            BuiltinMountBackend::Auto => auto_modules.push(module.dir_id),
            BuiltinMountBackend::OverlayFs => overlay_modules.push(module.dir_id),
            BuiltinMountBackend::MagicMount => magic_modules.push(module.dir_id),
            BuiltinMountBackend::Disabled => {
                info!("module {} is disabled in built-in mount config", module.id);
            }
        }
    }

    info!(
        "MISU Mount module backend groups: auto={}, overlayfs={}, magic_mount={}",
        auto_modules.len(),
        overlay_modules.len(),
        magic_modules.len()
    );

    let mut run = MisuMountResult::default();

    let mut overlay_attempt_modules = overlay_modules.clone();
    extend_unique(&mut overlay_attempt_modules, &auto_modules);
    if !overlay_attempt_modules.is_empty() {
        match mount_overlay_modules(
            module_dir,
            &overlay_attempt_modules,
            &config.custom_partitions,
            config.overlay_storage,
        ) {
            Ok(result) => {
                let fallback_partitions = result.fallback_partitions.clone();
                merge_overlay_result(&mut run, result);
                if !fallback_partitions.is_empty() {
                    warn!(
                        "MISU Mount overlayfs fallback partition(s): {}",
                        fallback_partitions.join(", ")
                    );
                    if !auto_modules.is_empty() {
                        match mount_magic_module_partitions(
                            module_dir,
                            &auto_modules,
                            &fallback_partitions,
                            &config.custom_partitions,
                        ) {
                            Ok(points) => push_unique(&mut run.mount_points, points),
                            Err(err) => {
                                let warning = format!(
                                    "MISU Mount per-module Magic Mount fallback failed: {err:#}"
                                );
                                warn!("{warning}");
                                run.add_warning(warning);
                            }
                        }
                    }
                    if !overlay_modules.is_empty() {
                        let warning = format!(
                            "forced OverlayFS module(s) cannot fall back automatically on partition(s): {}",
                            fallback_partitions.join(", ")
                        );
                        warn!("{warning}");
                        run.add_warning(warning);
                    }
                }
            }
            Err(err) => {
                let warning = format!("MISU Mount per-module OverlayFS planner failed: {err:#}");
                warn!("{warning}");
                if auto_modules.is_empty() {
                    return Err(err);
                }
                run.add_warning(warning);
                if !overlay_modules.is_empty() {
                    let warning =
                        "forced OverlayFS module(s) skipped because OverlayFS planner failed"
                            .to_string();
                    warn!("{warning}");
                    run.add_warning(warning);
                }
                let points =
                    mount_magic_modules(module_dir, &auto_modules, &config.custom_partitions)?;
                push_unique(&mut run.mount_points, points);
            }
        }
    }

    if !magic_modules.is_empty() {
        let points = mount_magic_modules(module_dir, &magic_modules, &config.custom_partitions)?;
        push_unique(&mut run.mount_points, points);
    }

    if run.mount_points.is_empty() {
        warn!("MISU Mount created no mount points");
    }

    Ok(run)
}

#[allow(dead_code)]
fn mount_misu_legacy(module_dir: &str) -> Result<(Vec<String>, u32)> {
    let config = mount_config::load();
    let result = match config.backend {
        BuiltinMountBackend::Auto => mount_misu_auto(module_dir, &config)?,
        BuiltinMountBackend::MagicMount => {
            let points = mount_magic(module_dir, &config.custom_partitions)?;
            MisuMountResult::from_mount_points(points)
        }
        BuiltinMountBackend::OverlayFs => {
            let overlay = mount_overlay(
                module_dir,
                &config.custom_partitions,
                config.overlay_storage,
            )?;
            let mut run = MisuMountResult::default();
            merge_overlay_result(&mut run, overlay);
            run
        }
        BuiltinMountBackend::Disabled => {
            info!("MISU Mount disabled by built-in mount config");
            MisuMountResult::default()
        }
    };
    Ok((result.mount_points, result.flags))
}

pub fn mount_modules_systemlessly(module_dir: &str, mode: MountMode) -> Result<()> {
    reset_umount_list();
    let started_at = unix_time_secs();
    let config = mount_config::load();
    let modules = match mount_config::scan_module_mount_info(&config) {
        Ok(modules) => modules,
        Err(err) => {
            warn!("failed to scan module mount info for mount state: {err:#}");
            Vec::new()
        }
    };

    save_mount_state(&mount_state_json(
        "running", mode, &config, started_at, None, &modules, None, None,
    ));

    match mode {
        MountMode::MetaModule => {
            let result = metamodule::exec_mount_script(module_dir);
            match &result {
                Ok(()) => save_mount_state(&mount_state_json(
                    "skipped",
                    mode,
                    &config,
                    started_at,
                    Some(unix_time_secs()),
                    &modules,
                    None,
                    None,
                )),
                Err(err) => save_mount_state(&mount_state_json(
                    "failed",
                    mode,
                    &config,
                    started_at,
                    Some(unix_time_secs()),
                    &modules,
                    None,
                    Some(&format!("{err:#}")),
                )),
            }
            result?;
        }
        MountMode::MisuMount => {
            let result = mount_misu(module_dir);
            match result {
                Ok(result) => {
                    register_umount_points(&result.mount_points, result.flags);
                    save_mount_state(&mount_state_json(
                        "success",
                        mode,
                        &config,
                        started_at,
                        Some(unix_time_secs()),
                        &modules,
                        Some(&result),
                        None,
                    ));
                }
                Err(err) => {
                    save_mount_state(&mount_state_json(
                        "failed",
                        mode,
                        &config,
                        started_at,
                        Some(unix_time_secs()),
                        &modules,
                        None,
                        Some(&format!("{err:#}")),
                    ));
                    return Err(err);
                }
            }
        }
    }

    Ok(())
}
