package me.weishu.kernelsu.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.FloatingBottomBarCoui
import me.weishu.kernelsu.ui.component.FloatingBottomBarItemCoui
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBar
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBarBlur
import me.weishu.kernelsu.ui.util.CouiBlurredBar
import me.weishu.kernelsu.ui.util.rootAvailable
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.BadgeBox
import io.github.suqi8.coui.kmp.basic.BadgeDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun BottomBarCoui(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier,
) {
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    if (!fullFeatured) return

    val mainState = LocalMainPagerState.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    val items = CouiBottomBarDestination.entries.map { destination ->
        NavigationItem(
            label = stringResource(destination.label),
            icon = destination.icon,
        )
    }
    if (!enableFloatingBottomBar) {
        CouiBlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else COUITheme.colorScheme.surface,
                content = {
                    items.forEachIndexed { index, item ->
                        NavigationBarItemWithBadgeCoui(
                            icon = item.icon,
                            label = item.label,
                            selected = mainState.selectedPage == index,
                            onClick = {
                                mainState.animateToPage(index)
                            },
                            badge = navigationBadgeForCoui(index, navigationBadge),
                        )
                    }
                }
            )
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }
        FloatingBottomBarCoui(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
            .padding(start = 28.dp, end = 28.dp, bottom = bottomPadding),
            selectedIndex = mainState.selectedPage,
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) { activateTab ->
            items.forEachIndexed { index, item ->
                FloatingBottomBarItemCoui(
                    selected = mainState.selectedPage == index,
                    onClick = {
                        activateTab(index)
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    // Icon and label take LocalContentColor so the FloatingBottomBar backdrop copy
                    // can recolor them to the accent tone inside the indicator pill.
                    val badge = navigationBadgeForCoui(index, navigationBadge, floating = true)
                    val icon: @Composable () -> Unit = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    }
                    if (badge != null) {
                        BadgeBox(badge = badge) { icon() }
                    } else {
                        icon()
                    }
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

internal enum class CouiBottomBarDestination(
    @get:StringRes val label: Int,
    val icon: ImageVector,
) {
    Home(R.string.home, Icons.Rounded.Cottage),
    SuperUser(R.string.superuser, Icons.Rounded.Security),
    Module(R.string.module, Icons.Rounded.Extension),
    Setting(R.string.settings, Icons.Rounded.Settings)
}

@Composable
private fun RowScope.NavigationBarItemWithBadgeCoui(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    badge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = COUITheme.colorScheme

    val labelColor = when {
        !enabled -> colorScheme.disabledOnSurface
        selected -> colorScheme.onSurfaceContainer
        else -> colorScheme.onSurfaceSecondary
    }
    val iconTargetTint = when {
        !enabled -> colorScheme.disabledOnSurface
        selected || isPressed -> colorScheme.onSurfaceContainer
        else -> colorScheme.onSurfaceSecondary
    }
    val iconTint by animateColorAsState(
        targetValue = iconTargetTint,
        animationSpec = tween(
            durationMillis = NavigationBarDefaults.IconFadeDurationMillis,
            easing = LinearEasing
        ),
        label = "navigationItemIconTint",
    )

    Box(
        modifier = modifier
            .height(NavigationBarDefaults.ItemHeight)
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = NavigationBarDefaults.ItemHorizontalPadding),
    ) {
        val iconContent: @Composable () -> Unit = {
            Icon(
                modifier = Modifier.size(NavigationBarDefaults.IconSize),
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
            )
        }
        if (badge != null) {
            val badgeOverhang = BadgeDefaults.CountOverhang
            BadgeBox(
                badge = badge,
                overhang = badgeOverhang,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = NavigationBarDefaults.IconTopPadding - badgeOverhang),
            ) {
                iconContent()
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = NavigationBarDefaults.IconTopPadding)
            ) {
                iconContent()
            }
        }
        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = NavigationBarDefaults.LabelBottomPadding),
            text = label,
            color = labelColor,
            textAlign = TextAlign.Center,
            fontSize = NavigationBarDefaults.LabelFontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun navigationBadgeForCoui(
    index: Int,
    state: NavigationBadgeState,
    floating: Boolean = false,
): (@Composable () -> Unit)? {
    val badge = badgeFor(index, state) ?: return null
    return when (badge.tone) {
        BadgeTone.Alert -> {
            {
                Badge(count = badge.count)
            }
        }

        BadgeTone.Accent -> {
            {
                Badge(
                    count = badge.count,
                    colors = BadgeDefaults.badgeColors(
                        containerColor = if (floating) {
                            COUITheme.colorScheme.primaryContainer
                        } else {
                            COUITheme.colorScheme.primary
                        },
                        contentColor = if (floating) {
                            COUITheme.colorScheme.onPrimaryContainer
                        } else {
                            COUITheme.colorScheme.onPrimary
                        },
                    ),
                )
            }
        }
    }
}
