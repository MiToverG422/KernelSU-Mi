package me.weishu.kernelsu.ui.screen.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.KernelSUApplication
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = settingsScreenActions(navigator, viewModel)

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Coui -> SettingPagerCoui(uiState, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}

@Composable
fun SettingsSubpageScreen(sectionValue: String) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val section = SettingsSection.fromValue(sectionValue) ?: SettingsSection.General
    val navigator = LocalNavigator.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = settingsScreenActions(navigator, viewModel)

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, 0.dp, section)
        UiMode.Coui -> SettingPagerCoui(uiState, actions, 0.dp, section)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, 0.dp, section)
    }
}

@Composable
private fun settingsScreenActions(
    navigator: Navigator,
    viewModel: SettingsViewModel,
): SettingsScreenActions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    return SettingsScreenActions(
        onBack = { navigator.pop() },
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onOpenSettingsSection = { section -> navigator.push(Route.SettingsSubpage(section.value)) },
        onOpenBuiltinMount = { navigator.push(Route.BuiltinMount) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(UiMode.entries.getOrNull(index)?.value ?: UiMode.DEFAULT_VALUE)
        },
        onSetThemeMode = viewModel::setThemeMode,
        onSetMiuixMonet = viewModel::setMiuixMonet,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorMode = viewModel::setColorMode,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetEnableOfficialLauncher = viewModel::setEnableOfficialLauncher,
        onSetClassicUi = viewModel::setClassicUi,
        onSetShowSwitchIcon = viewModel::setShowSwitchIcon,
        onSetScrollAnimation = viewModel::setScrollAnimation,
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetEnableFloatingBottomBarBlur = viewModel::setEnableFloatingBottomBarBlur,
        onSetEnableNavigationBadge = viewModel::setEnableNavigationBadge,
        onSetEnablePredictiveBack = {
            viewModel.setEnablePredictiveBack(it)
            KernelSUApplication.setEnableOnBackInvokedCallback(context.applicationInfo, it)
            activity?.recreate()
        },
        onSetPageScale = viewModel::setPageScale,
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetMountMode = viewModel::setMountMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetAvcSpoofEnabled = viewModel::setAvcSpoofEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onOpenAbout = { navigator.push(Route.About) },
    )
}
