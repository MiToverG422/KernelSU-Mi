package me.weishu.kernelsu.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.MountMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.theme.ColorMode

enum class SettingsSection(
    val value: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
) {
    General(
        "general",
        R.string.settings_section_general,
        R.string.settings_section_general_summary
    ),
    Appearance(
        "appearance",
        R.string.settings_section_appearance,
        R.string.settings_section_appearance_summary
    ),
    Kernel(
        "kernel",
        R.string.settings_section_kernel,
        R.string.settings_section_kernel_summary
    ),
    Mount(
        "mount",
        R.string.settings_section_mount,
        R.string.settings_section_mount_summary
    );

    companion object {
        fun fromValue(value: String?): SettingsSection? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val checkModuleUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enableOfficialLauncher: Boolean = false,
    val classicUi: Boolean = false,
    val showSwitchIcon: Boolean = false,
    val scrollAnimation: Boolean = false,
    val enablePredictiveBack: Boolean = true,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val enableNavigationBadge: Boolean = true,
    val pageScale: Float = 1.0f,
    val enableWebDebugging: Boolean = false,

    // Su Compat
    val mountMode: String = MountMode.DEFAULT.value,
    val suCompatStatus: String = "",
    val suCompatMode: Int = 0, // 0: enable default, 1: disable until reboot, 2: disable always
    val isSuEnabled: Boolean = false,

    // Kernel Umount
    val kernelUmountStatus: String = "",
    val isKernelUmountEnabled: Boolean = false,

    // SELinux Hide
    val selinuxHideStatus: String = "",
    val isSelinuxHideEnabled: Boolean = false,

    // SU Log
    val sulogStatus: String = "",
    val isSulogEnabled: Boolean = false,

    // Avc spoof
    val avcSpoofStatus: String = "",
    val isAvcSpoofEnabled: Boolean = true,

    // Umount Modules
    val isDefaultUmountModules: Boolean = false,

    // ADB Root
    val adbRootStatus: String = "",
    val isAdbRootEnabled: Boolean = false,

    val isLkmMode: Boolean = false,
    val isLateLoadMode: Boolean = false,

    // Auto Jailbreak
    val autoJailbreak: Boolean = false,

    // Soft Reboot
    val useSoftReboot: Boolean = false
)

@Immutable
data class SettingsScreenActions(
    val onBack: () -> Unit,
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetCheckModuleUpdate: (Boolean) -> Unit,
    val onOpenSettingsSection: (SettingsSection) -> Unit,
    val onOpenBuiltinMount: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onSetThemeMode: (Int) -> Unit,
    val onSetMiuixMonet: (Boolean) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
    val onSetEnableOfficialLauncher: (Boolean) -> Unit,
    val onSetClassicUi: (Boolean) -> Unit,
    val onSetShowSwitchIcon: (Boolean) -> Unit,
    val onSetScrollAnimation: (Boolean) -> Unit,
    val onSetEnableBlur: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBar: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBarBlur: (Boolean) -> Unit,
    val onSetEnableNavigationBadge: (Boolean) -> Unit,
    val onSetEnablePredictiveBack: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
    val onOpenProfileTemplate: () -> Unit,
    val onSetSuCompatMode: (Int) -> Unit,
    val onSetMountMode: (Boolean) -> Unit,
    val onSetKernelUmountEnabled: (Boolean) -> Unit,
    val onSetSelinuxHideEnabled: (Boolean) -> Unit,
    val onSetSulogEnabled: (Boolean) -> Unit,
    val onSetAdbRootEnabled: (Boolean) -> Unit,
    val onSetAvcSpoofEnabled: (Boolean) -> Unit,
    val onSetDefaultUmountModules: (Boolean) -> Unit,
    val onSetEnableWebDebugging: (Boolean) -> Unit,
    val onSetAutoJailbreak: (Boolean) -> Unit,
    val onSetUseSoftReboot: (Boolean) -> Unit,
    val onOpenAbout: () -> Unit,
)
