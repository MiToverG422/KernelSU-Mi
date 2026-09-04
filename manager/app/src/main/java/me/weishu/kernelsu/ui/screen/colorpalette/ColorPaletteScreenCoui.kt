package me.weishu.kernelsu.ui.screen.colorpalette

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.bottombar.useNavigationRail
import me.weishu.kernelsu.ui.component.coui.ScaleDialog
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.keyColorOptions
import me.weishu.kernelsu.ui.util.CouiBlurredBar
import me.weishu.kernelsu.ui.util.rememberCouiBlurBackdrop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme.colorScheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun ColorPaletteScreenCoui(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
) {
    val scrollBehavior = COUIScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val backdrop = rememberCouiBlurBackdrop(enableBlurState)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            CouiBlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_theme),
                    navigationIcon = {
                        IconButton(
                            onClick = actions.onBack
                        ) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceSecondary
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    ColorPaletteContentCoui(state, actions)
                }
                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                    12.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPaletteContentCoui(
    state: ColorPaletteUiState,
    actions: ColorPaletteScreenActions,
) {
    val uiState = state.uiState
    val context = LocalContext.current
    val showScaleDialog = rememberSaveable { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(32.dp))
    ThemePreviewCardCoui(
        enableFloatingBottomBar = uiState.enableFloatingBottomBar,
        enableFloatingBottomBarBlur = uiState.enableFloatingBottomBarBlur,
    )
    Spacer(modifier = Modifier.height(72.dp))

    val themeItems = listOf(
        stringResource(id = R.string.settings_theme_mode_system),
        stringResource(id = R.string.settings_theme_mode_light),
        stringResource(id = R.string.settings_theme_mode_dark),
    )
    TabRowWithContour(
        tabs = themeItems,
        selectedTabIndex = (if (uiState.themeMode >= 3) uiState.themeMode - 3 else uiState.themeMode).coerceIn(0, 2),
        onTabSelected = { index ->
            actions.onSetThemeMode(index)
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    )

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .fillMaxWidth(),
    ) {
        OverlayDropdownPreference(
            title = stringResource(id = R.string.settings_ui_mode),
            summary = stringResource(id = R.string.settings_ui_mode_summary),
            items = UiMode.entries.map { it.label },
            startAction = {
                Icon(
                    Icons.Rounded.DisplaySettings,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_ui_mode),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            selectedIndex = UiMode.entries.indexOf(UiMode.fromValue(uiState.uiMode)).coerceAtLeast(0),
            onSelectedIndexChange = actions.onSetUiModeIndex
        )

        PreferenceDividerCoui()
        SwitchPreference(
            title = stringResource(id = R.string.settings_monet),
            startAction = {
                Icon(
                    Icons.Rounded.Wallpaper,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_monet),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            checked = uiState.miuixMonet,
            onCheckedChange = {
                actions.onSetMiuixMonet(it)
            }
        )

        AnimatedVisibility(
            visible = uiState.miuixMonet
        ) {
            Column {
                PreferenceDividerCoui()
                val colorItems = listOf(
                    stringResource(id = R.string.settings_key_color_default),
                    stringResource(id = R.string.color_red),
                    stringResource(id = R.string.color_pink),
                    stringResource(id = R.string.color_purple),
                    stringResource(id = R.string.color_deep_purple),
                    stringResource(id = R.string.color_indigo),
                    stringResource(id = R.string.color_blue),
                    stringResource(id = R.string.color_cyan),
                    stringResource(id = R.string.color_teal),
                    stringResource(id = R.string.color_green),
                    stringResource(id = R.string.color_yellow),
                    stringResource(id = R.string.color_amber),
                    stringResource(id = R.string.color_orange),
                    stringResource(id = R.string.color_brown),
                    stringResource(id = R.string.color_blue_grey),
                    stringResource(id = R.string.color_sakura),
                )
                val colorValues = listOf(0) + keyColorOptions
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.settings_key_color),
                    items = colorItems,
                    startAction = {
                        Icon(
                            Icons.Rounded.Colorize,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(id = R.string.settings_key_color),
                            tint = colorScheme.onSurfaceSecondary
                        )
                    },
                    selectedIndex = colorValues.indexOf(uiState.keyColor).takeIf { it >= 0 } ?: 0,
                    onSelectedIndexChange = { index ->
                        actions.onSetKeyColor(colorValues[index])
                    }
                )

                AnimatedVisibility(
                    visible = uiState.keyColor != 0
                ) {
                    Column {
                        val styles = PaletteStyle.entries
                        PreferenceDividerCoui()
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_color_style),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Style,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_color_style),
                                    tint = colorScheme.onSurfaceSecondary
                                )
                            },
                            items = styles.map { it.name },
                            selectedIndex = styles.indexOfFirst { it.name == uiState.colorStyle }.coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                actions.onSetColorStyle(styles[index].name)
                            }
                        )

                        val specs = ColorSpec.SpecVersion.entries
                        PreferenceDividerCoui()
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_color_spec),
                            startAction = {
                                Icon(
                                    Icons.Rounded.DesignServices,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_color_spec),
                                    tint = colorScheme.onSurfaceSecondary
                                )
                            },
                            items = specs.map { it.name },
                            selectedIndex = specs.indexOfFirst { it.name == uiState.colorSpec }.coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                actions.onSetColorSpec(specs[index].name)
                            }
                        )
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .fillMaxWidth(),
    ) {
        SwitchPreference(
            title = stringResource(id = R.string.settings_official_icon),
            startAction = {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_monochrome),
                    contentDescription = stringResource(id = R.string.settings_official_icon),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(24.dp)
                        .wrapContentSize(unbounded = true)
                        .requiredSize(48.dp),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            checked = uiState.enableOfficialLauncher,
            onCheckedChange = { enabled ->
                actions.onSetEnableOfficialLauncher(enabled)
                val pm = context.packageManager
                val mainComponent = ComponentName(context, MainActivity::class.java)
                val aliasComponent = ComponentName(context, "me.weishu.kernelsu.MainActivityOfficial")
                val (enableComp, disableComp) = if (enabled) aliasComponent to mainComponent else mainComponent to aliasComponent

                pm.setComponentEnabledSetting(enableComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(disableComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        )

        PreferenceDividerCoui()
        SwitchPreference(
            title = stringResource(id = R.string.settings_scroll_animation),
            startAction = {
                Icon(
                    Icons.Rounded.ViewCarousel,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_scroll_animation),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            checked = uiState.scrollAnimation,
            onCheckedChange = { enabled ->
                actions.onSetScrollAnimation(enabled)
            }
        )
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .fillMaxWidth(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SwitchPreference(
                title = stringResource(id = R.string.settings_enable_blur),
                summary = stringResource(id = R.string.settings_enable_blur_summary),
                startAction = {
                    Icon(
                        Icons.Rounded.BlurOn,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(id = R.string.settings_enable_blur),
                        tint = colorScheme.onSurfaceSecondary
                    )
                },
                checked = uiState.enableBlur,
                onCheckedChange = {
                    actions.onSetEnableBlur(it)
                }
            )
            PreferenceDividerCoui()
        }
        SwitchPreference(
            title = stringResource(id = R.string.settings_floating_bottom_bar),
            summary = stringResource(id = R.string.settings_floating_bottom_bar_summary),
            startAction = {
                Icon(
                    Icons.Rounded.CallToAction,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_floating_bottom_bar),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            checked = uiState.enableFloatingBottomBar,
            onCheckedChange = {
                actions.onSetEnableFloatingBottomBar(it)
            }
        )
        AnimatedVisibility(visible = uiState.enableFloatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Column {
                PreferenceDividerCoui()
                SwitchPreference(
                    title = stringResource(id = R.string.settings_enable_glass),
                    summary = stringResource(id = R.string.settings_enable_glass_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.WaterDrop,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(id = R.string.settings_enable_glass),
                            tint = colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = uiState.enableFloatingBottomBarBlur,
                    onCheckedChange = {
                        actions.onSetEnableFloatingBottomBarBlur(it)
                    }
                )
            }
        }
        PreferenceDividerCoui()
        SwitchPreference(
            title = stringResource(id = R.string.settings_navigation_badge),
            summary = stringResource(id = R.string.settings_navigation_badge_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Pin,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_navigation_badge),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            checked = uiState.enableNavigationBadge,
            onCheckedChange = {
                actions.onSetEnableNavigationBadge(it)
            }
        )
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .fillMaxWidth(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SwitchPreference(
                title = stringResource(id = R.string.settings_enable_predictive_back),
                summary = stringResource(id = R.string.settings_enable_predictive_back_summary),
                startAction = {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuOpen,
                        modifier = Modifier.padding(end = 6.dp),
                        contentDescription = stringResource(id = R.string.settings_enable_predictive_back),
                        tint = colorScheme.onSurfaceSecondary
                    )
                },
                checked = uiState.enablePredictiveBack,
                onCheckedChange = {
                    actions.onSetEnablePredictiveBack(it)
                }
            )
            PreferenceDividerCoui()
        }

        var sliderValue by remember(uiState.pageScale) { mutableFloatStateOf(uiState.pageScale) }
        ArrowPreference(
            title = stringResource(id = R.string.settings_page_scale),
            summary = stringResource(id = R.string.settings_page_scale_summary),
            startAction = {
                Icon(
                    Icons.Rounded.AspectRatio,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(id = R.string.settings_page_scale),
                    tint = colorScheme.onSurfaceSecondary
                )
            },
            endActions = {
                Text(
                    text = "${(sliderValue * 100).toInt()}%",
                    color = colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = { showScaleDialog.value = !showScaleDialog.value },
            holdDownState = showScaleDialog.value,
            bottomAction = {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        actions.onSetPageScale(sliderValue)
                    },
                    valueRange = 0.8f..1.1f,
                    showKeyPoints = true,
                    keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                    magnetThreshold = 0.01f,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
            },
        )
        ScaleDialog(
            show = showScaleDialog.value,
            onDismissRequest = { showScaleDialog.value = false },
            volumeState = { uiState.pageScale },
            onVolumeChange = {
                actions.onSetPageScale(it)
            }
        )
    }
}

@Composable
private fun PreferenceDividerCoui() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCardCoui(
    enableFloatingBottomBar: Boolean = false,
    enableFloatingBottomBarBlur: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight
    val useRail = useNavigationRail(enableFloatingBottomBar)

    val textColor = colorScheme.onSurface
    val bgColor = colorScheme.surface
    val accentCardColor = colorScheme.tertiaryContainer
    val cardColor = colorScheme.surfaceVariant
    val navBarColor = colorScheme.background
    val iconColor = colorScheme.primary
    val navSelectedColor = colorScheme.primary
    val navUnselectedColor = colorScheme.onSurfaceVariantSummary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            val content = @Composable {
                Column {
                    Row(
                        modifier = Modifier
                            .height(if (useRail) 36.dp else 48.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = if (useRail) 12.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontSize = 12.sp,
                            color = textColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor)
                    )

                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val smallCardHeight = 12.dp
                        val smallCardCount = when {
                            maxHeight >= 96.dp -> 2
                            maxHeight >= 72.dp -> 1
                            else -> 0
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cardColor)
                            )
                            repeat(smallCardCount) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(smallCardHeight)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cardColor)
                                )
                            }
                        }
                    }
                }
            }

            if (useRail) {
                Row {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(30.dp)
                            .background(navBarColor),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            } else {
                content()
            }

            if (!useRail && enableFloatingBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enableFloatingBottomBarBlur) navBarColor.copy(alpha = 0.5f)
                                else navBarColor
                            )
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor)
                            )
                        }
                    }
                }
            } else if (!useRail) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
