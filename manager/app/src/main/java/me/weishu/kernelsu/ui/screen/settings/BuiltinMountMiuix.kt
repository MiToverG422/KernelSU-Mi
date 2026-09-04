package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.BuiltinMountBackend
import me.weishu.kernelsu.data.model.BuiltinMountLastRun
import me.weishu.kernelsu.data.model.BuiltinMountModuleStatus
import me.weishu.kernelsu.data.model.BuiltinMountStorage
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.DateFormat
import java.util.Date

@Composable
fun BuiltinMountScreenMiuix(
    state: BuiltinMountUiState,
    actions: BuiltinMountActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    var showAddPartitionDialog by rememberSaveable { mutableStateOf(false) }
    var partitionInput by rememberSaveable { mutableStateOf("") }
    var selectedModuleId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.settings_builtin_mount),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    BackendCardMiuix(state, actions)
                    LastRunCardMiuix(state.status.lastRun)
                    ModuleStatusCardMiuix(
                        modules = state.status.mountableModules,
                        onModuleClick = { selectedModuleId = it }
                    )
                    PartitionCardMiuix(
                        partitions = state.status.customPartitions,
                        onAdd = {
                            partitionInput = ""
                            showAddPartitionDialog = true
                        },
                        onRemove = actions.onRemovePartition
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (state.isLoading) {
                InfiniteProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showAddPartitionDialog) {
        WindowDialog(
            show = true,
            onDismissRequest = { showAddPartitionDialog = false },
            content = {
                Column {
                    Text(stringResource(id = R.string.settings_builtin_mount_add_partition))
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = partitionInput,
                        onValueChange = { partitionInput = it },
                        label = stringResource(id = R.string.settings_builtin_mount_partition_name),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = stringResource(id = android.R.string.cancel),
                            onClick = { showAddPartitionDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(id = android.R.string.ok),
                            onClick = {
                                actions.onAddPartition(partitionInput)
                                showAddPartitionDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }

    val selectedModule =
        selectedModuleId?.let { moduleId ->
            state.status.mountableModules.firstOrNull { it.id == moduleId }
        }
    if (selectedModule != null) {
        WindowDialog(
            show = true,
            onDismissRequest = { selectedModuleId = null },
            content = {
                ModuleBackendDialogMiuix(
                    module = selectedModule,
                    onSelect = { index ->
                        actions.onSetModuleBackendIndex(selectedModule.id, index)
                        selectedModuleId = null
                    }
                )
            }
        )
    }
}

@Composable
private fun BackendCardMiuix(
    state: BuiltinMountUiState,
    actions: BuiltinMountActions,
) {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        OverlayDropdownPreference(
            title = stringResource(id = R.string.settings_builtin_mount_backend),
            summary = stringResource(id = R.string.settings_builtin_mount_backend_summary),
            items = BuiltinMountBackend.entries.map { backend -> stringResource(backend.titleRes()) },
            startAction = {
                Icon(
                    Icons.Rounded.Storage,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_builtin_mount_backend),
                    tint = colorScheme.onBackground
                )
            },
            selectedIndex = state.selectedBackendIndex,
            onSelectedIndexChange = actions.onSetBackendIndex
        )
        OverlayDropdownPreference(
            title = stringResource(id = R.string.settings_builtin_mount_storage),
            summary = stringResource(id = R.string.settings_builtin_mount_storage_summary),
            items = BuiltinMountStorage.entries.map { storage -> stringResource(storage.titleRes()) },
            startAction = {
                Icon(
                    Icons.Rounded.Storage,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_builtin_mount_storage),
                    tint = colorScheme.onBackground
                )
            },
            selectedIndex = state.selectedStorageIndex,
            onSelectedIndexChange = actions.onSetStorageIndex
        )
    }
}

@Composable
private fun LastRunCardMiuix(lastRun: BuiltinMountLastRun?) {
    SectionTitleMiuix(
        title = stringResource(id = R.string.settings_builtin_mount_last_run),
        summary = stringResource(id = R.string.settings_builtin_mount_last_run_summary)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        if (lastRun == null) {
            MountInfoRowMiuix(
                title = stringResource(id = R.string.settings_builtin_mount_last_run_empty),
                summary = null,
            )
            return@Card
        }

        MountInfoRowMiuix(
            title = stringResource(id = statusTitleRes(lastRun.status)),
            summary = lastRunSummary(lastRun),
            trailing = {
                LastRunPillMiuix(status = lastRun.status)
            }
        )
        if (!lastRun.error.isNullOrBlank()) {
            MountInfoRowMiuix(
                title = stringResource(id = R.string.settings_builtin_mount_last_run_error),
                summary = lastRun.error,
            )
        }
        if (lastRun.warnings.isNotEmpty()) {
            MountInfoRowMiuix(
                title = stringResource(id = R.string.settings_builtin_mount_last_run_warnings),
                summary = lastRun.warnings.compactList(),
            )
        }
        if (lastRun.activeMounts.isNotEmpty()) {
            MountInfoRowMiuix(
                title = stringResource(id = R.string.settings_builtin_mount_last_run_mounts),
                summary = lastRun.activeMounts.compactList(),
            )
        }
    }
}

@Composable
private fun ModuleStatusCardMiuix(
    modules: List<BuiltinMountModuleStatus>,
    onModuleClick: (String) -> Unit,
) {
    SectionTitleMiuix(
        title = stringResource(id = R.string.settings_builtin_mount_modules),
        summary = stringResource(id = R.string.settings_builtin_mount_modules_summary, modules.size)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        if (modules.isEmpty()) {
            MountInfoRowMiuix(
                title = stringResource(id = R.string.settings_builtin_mount_modules_empty),
                summary = null,
            )
        } else {
            modules.forEach { module ->
                MountInfoRowMiuix(
                    title = module.name.ifBlank { module.id },
                    summary = "${module.id}\n${module.partitions.joinToString(", ")}",
                    onClick = { onModuleClick(module.id) },
                    trailing = {
                        BackendPillMiuix(backend = module.effectiveBackend)
                    }
                )
            }
        }
    }
}

@Composable
private fun PartitionCardMiuix(
    partitions: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    SectionTitleMiuix(
        title = stringResource(id = R.string.settings_builtin_mount_partitions),
        summary = stringResource(id = R.string.settings_builtin_mount_partitions_summary)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        MountInfoRowMiuix(
            icon = Icons.Rounded.Add,
            title = stringResource(id = R.string.settings_builtin_mount_add_partition),
            summary = null,
            onClick = onAdd,
        )
        if (partitions.isEmpty()) {
            MountInfoRowMiuix(
                icon = Icons.Rounded.FolderDelete,
                title = stringResource(id = R.string.settings_builtin_mount_no_custom_partitions),
                summary = null,
            )
        } else {
            partitions.forEach { partition ->
                MountInfoRowMiuix(
                    icon = Icons.Rounded.FolderDelete,
                    title = "/$partition",
                    summary = null,
                    trailing = {
                        IconButton(onClick = { onRemove(partition) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModuleBackendDialogMiuix(
    module: BuiltinMountModuleStatus,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(text = module.name.ifBlank { module.id }, fontSize = 20.sp, color = colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = module.id, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(12.dp))
        BuiltinMountBackend.entries.forEachIndexed { index, backend ->
            val selected = backend == module.effectiveBackend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = backend.label(),
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                if (selected) {
                    BackendPillMiuix(backend = backend)
                }
            }
        }
    }
}

@Composable
private fun MountInfoRowMiuix(
    icon: ImageVector? = null,
    title: String,
    summary: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.padding(end = 12.dp),
                tint = colorScheme.onBackground,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 17.sp, color = colorScheme.onBackground)
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = summary, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun BackendPillMiuix(backend: BuiltinMountBackend) {
    val enabled = backend != BuiltinMountBackend.Disabled
    val backgroundColor = if (enabled) {
        colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val textColor = if (enabled) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurfaceVariantSummary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = backend.label(), fontSize = 12.sp, color = textColor)
    }
}

@Composable
private fun SectionTitleMiuix(
    title: String,
    summary: String,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)
    ) {
        Text(text = title, fontSize = 15.sp, color = colorScheme.onBackground)
        Text(text = summary, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun BuiltinMountBackend.label(): String {
    return stringResource(id = titleRes())
}

private fun BuiltinMountBackend.titleRes(): Int = when (this) {
    BuiltinMountBackend.Auto -> R.string.settings_builtin_mount_backend_auto
    BuiltinMountBackend.MagicMount -> R.string.settings_builtin_mount_backend_magic
    BuiltinMountBackend.OverlayFs -> R.string.settings_builtin_mount_backend_overlayfs
    BuiltinMountBackend.Disabled -> R.string.settings_builtin_mount_backend_disabled
}

private fun BuiltinMountStorage.titleRes(): Int = when (this) {
    BuiltinMountStorage.Tmpfs -> R.string.settings_builtin_mount_storage_tmpfs
    BuiltinMountStorage.Ext4 -> R.string.settings_builtin_mount_storage_ext4
}

private fun statusTitleRes(status: String): Int = when (status) {
    "success" -> R.string.settings_builtin_mount_last_run_success
    "failed" -> R.string.settings_builtin_mount_last_run_failed
    "running" -> R.string.settings_builtin_mount_last_run_running
    "skipped" -> R.string.settings_builtin_mount_last_run_skipped
    else -> R.string.settings_builtin_mount_last_run_unknown
}

@Composable
private fun lastRunSummary(lastRun: BuiltinMountLastRun): String {
    val actualStorage = lastRun.actualOverlayStorage
    val storage = if (actualStorage == null) {
        stringResource(lastRun.overlayStorage.titleRes())
    } else {
        "${stringResource(lastRun.overlayStorage.titleRes())} → ${stringResource(actualStorage.titleRes())}"
    }
    return stringResource(
        id = R.string.settings_builtin_mount_last_run_detail,
        lastRun.mountableModuleCount,
        lastRun.activeMounts.size,
        lastRun.fallbackPartitions.size,
        storage,
        formatUnixSeconds(lastRun.finishedAt),
    )
}

private fun formatUnixSeconds(seconds: Long): String {
    if (seconds <= 0) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(seconds * 1000))
}

private fun List<String>.compactList(limit: Int = 6): String {
    if (isEmpty()) return "—"
    val prefix = take(limit).joinToString("\n")
    val remaining = size - limit
    return if (remaining > 0) "$prefix\n+ $remaining" else prefix
}

@Composable
private fun LastRunPillMiuix(status: String) {
    val failed = status == "failed"
    val backgroundColor = if (failed) {
        colorScheme.errorContainer.copy(alpha = 0.9f)
    } else {
        colorScheme.primaryContainer.copy(alpha = 0.9f)
    }
    val textColor = if (failed) {
        colorScheme.onErrorContainer
    } else {
        colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = stringResource(id = statusTitleRes(status)), fontSize = 12.sp, color = textColor)
    }
}
