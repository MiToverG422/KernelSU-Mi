package me.weishu.kernelsu.ui.screen.colorpalette

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.ui.screen.settings.SettingsScreenActions
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState
import me.weishu.kernelsu.ui.theme.ColorMode

fun SettingsUiState.toColorPaletteUiState(): ColorPaletteUiState {
    val currentPaletteStyle = try {
        PaletteStyle.valueOf(colorStyle)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val currentColorSpec = try {
        ColorSpec.SpecVersion.valueOf(colorSpec)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2025
    }
    return ColorPaletteUiState(
        uiState = this,
        currentColorMode = ColorMode.fromValue(themeMode),
        currentPaletteStyle = currentPaletteStyle,
        currentColorSpec = currentColorSpec,
    )
}

fun SettingsScreenActions.toColorPaletteActions(): ColorPaletteScreenActions {
    return ColorPaletteScreenActions(
        onBack = onBack,
        onSetUiModeIndex = onSetUiModeIndex,
        onSetThemeMode = onSetThemeMode,
        onSetMiuixMonet = onSetMiuixMonet,
        onSetKeyColor = onSetKeyColor,
        onSetColorMode = onSetColorMode,
        onSetColorStyle = onSetColorStyle,
        onSetColorSpec = onSetColorSpec,
        onSetEnableOfficialLauncher = onSetEnableOfficialLauncher,
        onSetClassicUi = onSetClassicUi,
        onSetShowSwitchIcon = onSetShowSwitchIcon,
        onSetScrollAnimation = onSetScrollAnimation,
        onSetEnableBlur = onSetEnableBlur,
        onSetEnableFloatingBottomBar = onSetEnableFloatingBottomBar,
        onSetEnableFloatingBottomBarBlur = onSetEnableFloatingBottomBarBlur,
        onSetEnableNavigationBadge = onSetEnableNavigationBadge,
        onSetEnablePredictiveBack = onSetEnablePredictiveBack,
        onSetPageScale = onSetPageScale,
    )
}
