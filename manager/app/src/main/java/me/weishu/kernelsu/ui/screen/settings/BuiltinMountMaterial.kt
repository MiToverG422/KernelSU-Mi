package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.BuiltinMountBackend
import me.weishu.kernelsu.data.model.BuiltinMountLastRun
import me.weishu.kernelsu.data.model.BuiltinMountModuleStatus
import me.weishu.kernelsu.data.model.BuiltinMountStorage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import java.text.DateFormat
import java.util.Date

@Composable
fun BuiltinMountScreenMaterial(
    state: BuiltinMountUiState,
    actions: BuiltinMountActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showAddPartitionDialog by rememberSaveable { mutableStateOf(false) }
    var partitionInput by rememberSaveable { mutableStateOf("") }
    var selectedModuleId by rememberSaveable { mutableStateOf<String?>(null) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                title = { Text(stringResource(id = R.string.settings_builtin_mount)) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
            ) {
                BackendCardMaterial(state, actions)
                LastRunCardMaterial(state.status.lastRun)
                ModuleStatusCardMaterial(
                    modules = state.status.mountableModules,
                    onModuleClick = { selectedModuleId = it }
                )
                PartitionCardMaterial(
                    partitions = state.status.customPartitions,
                    onAdd = {
                        partitionInput = ""
                        showAddPartitionDialog = true
                    },
                    onRemove = actions.onRemovePartition
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showAddPartitionDialog) {
        AlertDialog(
            onDismissRequest = { showAddPartitionDialog = false },
            title = { Text(stringResource(id = R.string.settings_builtin_mount_add_partition)) },
            text = {
                OutlinedTextField(
                    value = partitionInput,
                    onValueChange = { partitionInput = it },
                    label = { Text(stringResource(id = R.string.settings_builtin_mount_partition_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onAddPartition(partitionInput)
                        showAddPartitionDialog = false
                    }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPartitionDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }

    val selectedModule =
        selectedModuleId?.let { moduleId ->
            state.status.mountableModules.firstOrNull { it.id == moduleId }
        }
    if (selectedModule != null) {
        AlertDialog(
            onDismissRequest = { selectedModuleId = null },
            title = { Text(stringResource(id = R.string.settings_builtin_mount_module_mode)) },
            text = {
                Column {
                    Text(selectedModule.name.ifBlank { selectedModule.id })
                    Text(
                        text = selectedModule.id,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    BuiltinMountBackend.entries.forEachIndexed { index, backend ->
                        val selected = backend == selectedModule.effectiveBackend
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable {
                                    actions.onSetModuleBackendIndex(selectedModule.id, index)
                                    selectedModuleId = null
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(backend.label())
                            if (selected) {
                                BackendPillMaterial(backend = backend)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedModuleId = null }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BackendCardMaterial(
    state: BuiltinMountUiState,
    actions: BuiltinMountActions,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf<@Composable () -> Unit>(
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(id = R.string.settings_builtin_mount_backend),
                    summary = stringResource(id = R.string.settings_builtin_mount_backend_summary),
                    items = BuiltinMountBackend.entries.map { backend -> stringResource(backend.titleRes()) },
                    selectedIndex = state.selectedBackendIndex,
                    onItemSelected = actions.onSetBackendIndex
                )
            },
            {
                SegmentedDropdownItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(id = R.string.settings_builtin_mount_storage),
                    summary = stringResource(id = R.string.settings_builtin_mount_storage_summary),
                    items = BuiltinMountStorage.entries.map { storage -> stringResource(storage.titleRes()) },
                    selectedIndex = state.selectedStorageIndex,
                    onItemSelected = actions.onSetStorageIndex
                )
            }
        )
    )
}

@Composable
private fun LastRunCardMaterial(lastRun: BuiltinMountLastRun?) {
    SectionTitleMaterial(
        title = stringResource(id = R.string.settings_builtin_mount_last_run),
        summary = stringResource(id = R.string.settings_builtin_mount_last_run_summary)
    )
    Surface(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (lastRun == null) {
                Text(
                    text = stringResource(id = R.string.settings_builtin_mount_last_run_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = statusTitleRes(lastRun.status)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastRunSummary(lastRun),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LastRunPillMaterial(status = lastRun.status)
                }

                if (!lastRun.error.isNullOrBlank()) {
                    MountDetailBlockMaterial(
                        title = stringResource(id = R.string.settings_builtin_mount_last_run_error),
                        body = lastRun.error,
                        isError = true,
                    )
                }
                if (lastRun.warnings.isNotEmpty()) {
                    MountDetailBlockMaterial(
                        title = stringResource(id = R.string.settings_builtin_mount_last_run_warnings),
                        body = lastRun.warnings.compactList(limit = 4),
                    )
                }
                if (lastRun.activeMounts.isNotEmpty()) {
                    MountDetailBlockMaterial(
                        title = stringResource(id = R.string.settings_builtin_mount_last_run_mounts),
                        body = lastRun.activeMounts.compactList(limit = 5),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCardMaterial(
    modules: List<BuiltinMountModuleStatus>,
    onModuleClick: (String) -> Unit,
) {
    SectionTitleMaterial(
        title = stringResource(id = R.string.settings_builtin_mount_modules),
        summary = stringResource(id = R.string.settings_builtin_mount_modules_summary, modules.size)
    )
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = if (modules.isEmpty()) {
            listOf {
                SegmentedListItem(
                    headlineContent = { Text(stringResource(id = R.string.settings_builtin_mount_modules_empty)) },
                )
            }
        } else {
            modules.map { module ->
                {
                    SegmentedListItem(
                        onClick = { onModuleClick(module.id) },
                        headlineContent = {
                            Text(
                                text = module.name.ifBlank { module.id },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = module.summaryText(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            BackendPillMaterial(backend = module.effectiveBackend)
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun PartitionCardMaterial(
    partitions: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    SectionTitleMaterial(
        title = stringResource(id = R.string.settings_builtin_mount_partitions),
        summary = stringResource(id = R.string.settings_builtin_mount_partitions_summary)
    )
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = buildList {
            add {
                SegmentedListItem(
                    onClick = onAdd,
                    headlineContent = { Text(stringResource(id = R.string.settings_builtin_mount_add_partition)) },
                    leadingContent = { Icon(Icons.Filled.Add, null) }
                )
            }
            if (partitions.isEmpty()) {
                add {
                    SegmentedListItem(
                        headlineContent = { Text(stringResource(id = R.string.settings_builtin_mount_no_custom_partitions)) },
                        leadingContent = { Icon(Icons.Filled.FolderDelete, null) }
                    )
                }
            } else {
                partitions.forEach { partition ->
                    add {
                        SegmentedListItem(
                            headlineContent = { Text("/$partition") },
                            leadingContent = { Icon(Icons.Filled.FolderDelete, null) },
                            trailingContent = {
                                IconButton(onClick = { onRemove(partition) }) {
                                    Icon(Icons.Filled.Delete, null)
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BackendPillMaterial(backend: BuiltinMountBackend) {
    val enabled = backend != BuiltinMountBackend.Disabled
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = backend.label(),
            modifier = Modifier
                .widthIn(min = 64.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MountDetailBlockMaterial(
    title: String,
    body: String,
    isError: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SectionTitleMaterial(
    title: String,
    summary: String,
) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BuiltinMountModuleStatus.summaryText(): String {
    val partitions = partitions.joinToString(", ") { "/$it" }
    return if (partitions.isBlank()) id else "$id\n$partitions"
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
    val finished = formatUnixSeconds(lastRun.finishedAt)
    return stringResource(
        id = R.string.settings_builtin_mount_last_run_detail,
        lastRun.mountableModuleCount,
        lastRun.activeMounts.size,
        lastRun.fallbackPartitions.size,
        storage,
        finished,
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
private fun LastRunPillMaterial(status: String) {
    val failed = status == "failed"
    Surface(
        shape = RoundedCornerShape(50),
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Text(
            text = stringResource(id = statusTitleRes(status)),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
