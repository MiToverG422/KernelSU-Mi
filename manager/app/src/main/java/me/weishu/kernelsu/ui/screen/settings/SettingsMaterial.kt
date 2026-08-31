package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.MountMode
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SendLogBottomSheet
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteContentMaterial
import me.weishu.kernelsu.ui.screen.colorpalette.toColorPaletteActions
import me.weishu.kernelsu.ui.screen.colorpalette.toColorPaletteUiState
import me.weishu.kernelsu.ui.component.uninstalldialog.UninstallDialog

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    section: SettingsSection? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    UninstallDialog(
        show = showUninstallDialog.value,
        onDismissRequest = { showUninstallDialog.value = false }
    )

    ExpressiveScaffold(
        topBar = {
            TopBar(
                title = stringResource(section?.titleRes ?: R.string.settings),
                onBack = if (section == null) null else actions.onBack,
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.padding(bottom = bottomInnerPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            if (section == null) {
                SettingsSectionListMaterial(actions)
                SettingsAboutEntryMaterial(actions)
            }

            if (section == SettingsSection.General) {
                KsuIsValid {
                    val profileTemplate = stringResource(id = R.string.settings_profile_template)
                    SegmentedColumn(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                        content = listOf(
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.SystemUpdate,
                                    title = stringResource(id = R.string.settings_check_update),
                                    summary = stringResource(id = R.string.settings_check_update_summary),
                                    checked = uiState.checkUpdate,
                                    onCheckedChange = actions.onSetCheckUpdate
                                )
                            },
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.SystemUpdateAlt,
                                    title = stringResource(id = R.string.settings_module_check_update),
                                    summary = stringResource(id = R.string.settings_check_update_summary),
                                    checked = uiState.checkModuleUpdate,
                                    onCheckedChange = actions.onSetCheckModuleUpdate
                                )
                            },
                            {
                                SegmentedListItem(
                                    onClick = actions.onOpenProfileTemplate,
                                    headlineContent = { Text(profileTemplate) },
                                    supportingContent = { Text(stringResource(id = R.string.settings_profile_template_summary)) },
                                    leadingContent = { Icon(Icons.Filled.Description, profileTemplate) },
                                    trailingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            null
                                        )
                                    }
                                )
                            },
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.DeveloperMode,
                                    title = stringResource(id = R.string.enable_web_debugging),
                                    summary = stringResource(id = R.string.enable_web_debugging_summary),
                                    checked = uiState.enableWebDebugging,
                                    onCheckedChange = actions.onSetEnableWebDebugging
                                )
                            },
                        )
                    )
                }
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = buildList {
                        add {
                            SegmentedListItem(
                                onClick = { showBottomSheet = true },
                                headlineContent = { Text(stringResource(id = R.string.send_log)) },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.BugReport,
                                        stringResource(id = R.string.send_log)
                                    )
                                },
                            )
                        }
                        if (uiState.isLkmMode) add {
                            val uninstall = stringResource(id = R.string.settings_uninstall)
                            SegmentedListItem(
                                onClick = { showUninstallDialog.value = true },
                                enabled = !uiState.isLateLoadMode,
                                headlineContent = { Text(uninstall) },
                                leadingContent = { Icon(Icons.Filled.Delete, uninstall) }
                            )
                        }
                    }
                )
            }

            if (section == SettingsSection.Appearance) {
                ColorPaletteContentMaterial(
                    state = uiState.toColorPaletteUiState(),
                    actions = actions.toColorPaletteActions(),
                    bottomPadding = 13.dp
                )
            }

            if (section == SettingsSection.Mount) KsuIsValid {
                val builtinMountEnabled = MountMode.fromValue(uiState.mountMode) == MountMode.MisuMount
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                ) {
                    item(key = "mount_mode") {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Build,
                            title = stringResource(id = R.string.settings_mount_mode),
                            summary = stringResource(id = R.string.settings_mount_mode_summary),
                            checked = builtinMountEnabled,
                            onCheckedChange = actions.onSetMountMode
                        )
                    }
                    item(key = "builtin_mount", visible = builtinMountEnabled) {
                        SegmentedListItem(
                            onClick = actions.onOpenBuiltinMount,
                            headlineContent = { Text(stringResource(id = R.string.settings_builtin_mount)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_builtin_mount_summary)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.FolderDelete,
                                    stringResource(id = R.string.settings_builtin_mount)
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        )
                    }
                    item(key = "kernel_umount") {
                        val umountSummary = when (uiState.kernelUmountStatus) {
                            "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                            "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                            else -> stringResource(id = R.string.settings_kernel_umount_summary)
                        }
                        SegmentedSwitchItem(
                            icon = Icons.Filled.LayersClear,
                            title = stringResource(id = R.string.settings_kernel_umount),
                            summary = umountSummary,
                            enabled = uiState.kernelUmountStatus == "supported",
                            checked = uiState.isKernelUmountEnabled,
                            onCheckedChange = actions.onSetKernelUmountEnabled
                        )
                    }
                    item(key = "default_umount_modules") {
                        SegmentedSwitchItem(
                            icon = Icons.AutoMirrored.Filled.Rule,
                            title = stringResource(id = R.string.settings_umount_modules_default),
                            summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                            checked = uiState.isDefaultUmountModules,
                            onCheckedChange = actions.onSetDefaultUmountModules
                        )
                    }
                }
            }

            if (section == SettingsSection.Kernel) KsuIsValid {
                val suCompatModeItems = listOf(
                    stringResource(id = R.string.settings_mode_enable_by_default),
                    stringResource(id = R.string.settings_mode_disable_until_reboot),
                    stringResource(id = R.string.settings_mode_disable_always),
                )
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = listOf(
                        {
                            val suSummary = when (uiState.suCompatStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_sucompat_summary)
                            }
                            SegmentedDropdownItem(
                                icon = Icons.Filled.AdminPanelSettings,
                                title = stringResource(id = R.string.settings_sucompat),
                                summary = suSummary,
                                items = suCompatModeItems,
                                enabled = uiState.suCompatStatus == "supported",
                                selectedIndex = uiState.suCompatMode,
                                onItemSelected = actions.onSetSuCompatMode
                            )
                        },
                        {
                            val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_selinux_hide_summary)
                            }
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Security,
                                title = stringResource(id = R.string.settings_selinux_hide),
                                summary = selinuxHideSummary,
                                enabled = uiState.selinuxHideStatus == "supported",
                                checked = uiState.isSelinuxHideEnabled,
                                onCheckedChange = actions.onSetSelinuxHideEnabled
                            )
                        },
                        {
                            val sulogSummary = when (uiState.sulogStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_sulog_summary)
                            }
                            SegmentedSwitchItem(
                                icon = Icons.AutoMirrored.Filled.Article,
                                title = stringResource(id = R.string.settings_sulog),
                                summary = sulogSummary,
                                enabled = uiState.sulogStatus == "supported",
                                checked = uiState.isSulogEnabled,
                                onCheckedChange = actions.onSetSulogEnabled
                            )
                        },
                        {
                            val adbRootSummary = when (uiState.adbRootStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_adb_root_summary)
                            }
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Adb,
                                title = stringResource(id = R.string.settings_adb_root),
                                summary = adbRootSummary,
                                enabled = uiState.adbRootStatus == "supported",
                                checked = uiState.isAdbRootEnabled,
                                onCheckedChange = actions.onSetAdbRootEnabled
                            )
                        },
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.FlashOn,
                                title = stringResource(id = R.string.settings_auto_jailbreak),
                                summary = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                enabled = uiState.isLateLoadMode,
                                checked = uiState.autoJailbreak,
                                onCheckedChange = actions.onSetAutoJailbreak
                            )
                        },
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.RestartAlt,
                                title = stringResource(id = R.string.settings_soft_reboot),
                                summary = stringResource(id = R.string.settings_soft_reboot_summary),
                                enabled = !uiState.isLateLoadMode,
                                checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                                onCheckedChange = actions.onSetUseSoftReboot
                            )
                        },
                        {
                            val avcSpoofSummary = when (uiState.avcSpoofStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_avc_spoof_summary)
                            }
                            SegmentedSwitchItem(
                                icon = Icons.Filled.EditNote,
                                title = stringResource(id = R.string.settings_avc_spoof),
                                summary = avcSpoofSummary,
                                enabled = uiState.avcSpoofStatus == "supported",
                                checked = uiState.isAvcSpoofEnabled,
                                onCheckedChange = actions.onSetAvcSpoofEnabled
                            )
                        },
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showBottomSheet) {
                SendLogBottomSheet(
                    onDismiss = { showBottomSheet = false },
                    snackbarHostState = snackBarHost,
                )
            }
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun SettingsSectionListMaterial(
    actions: SettingsScreenActions,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = SettingsSection.entries.map { section ->
            {
                val title = stringResource(section.titleRes)
                SegmentedListItem(
                    onClick = { actions.onOpenSettingsSection(section) },
                    headlineContent = { Text(title) },
                    supportingContent = { Text(stringResource(section.summaryRes)) },
                    leadingContent = { Icon(section.materialIcon(), title) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null
                        )
                    }
                )
            }
        }
    )
}

@Composable
private fun SettingsAboutEntryMaterial(
    actions: SettingsScreenActions,
) {
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf {
            val about = stringResource(id = R.string.about)
            SegmentedListItem(
                onClick = actions.onOpenAbout,
                headlineContent = { Text(about) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Info,
                        about
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null
                    )
                }
            )
        }
    )
}

private fun SettingsSection.materialIcon(): ImageVector = when (this) {
    SettingsSection.General -> Icons.Filled.Settings
    SettingsSection.Appearance -> Icons.Filled.Palette
    SettingsSection.Kernel -> Icons.Filled.Build
    SettingsSection.Mount -> Icons.Filled.FolderDelete
}

@Composable
private fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        navigationIcon = {
            if (onBack != null) {
                TopBarBackButton(onClick = onBack)
            }
        },
        title = { Text(title) },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
