package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBar
import me.weishu.kernelsu.ui.util.rootAvailable

@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val mainPagerState = LocalMainPagerState.current

    if (!fullFeatured) return

    val items = listOf(
        Triple(R.string.home, Icons.Filled.Home, Icons.Outlined.Home),
        Triple(R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield),
        Triple(R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension),
        Triple(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    if (LocalEnableFloatingBottomBar.current) {
        FloatingBottomBarMaterial(
            items = items,
            selectedIndex = mainPagerState.selectedPage,
            navigationBadge = navigationBadge,
            onSelected = mainPagerState::animateToPage,
        )
        return
    }

    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            ShortNavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    NavigationIconWithBadge(
                        icon = if (selected) selectedIcon else unselectedIcon,
                        contentDescription = stringResource(label),
                        badge = badgeFor(index, navigationBadge),
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun FloatingBottomBarMaterial(
    items: List<Triple<Int, ImageVector, ImageVector>>,
    selectedIndex: Int,
    navigationBadge: NavigationBadgeState,
    onSelected: (Int) -> Unit,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
        .let { inset -> if (inset > 0.dp) inset + 8.dp else 16.dp }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
                    val selected = selectedIndex == index
                    val background by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "floatingBottomBarBackground"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        label = "floatingBottomBarContent"
                    )
                    val labelText = stringResource(label)

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .defaultMinSize(minWidth = 48.dp)
                            .clip(CircleShape)
                            .background(background)
                            .clickable(enabled = !selected) { onSelected(index) }
                            .padding(horizontal = if (selected) 14.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NavigationIconWithBadge(
                            icon = if (selected) selectedIcon else unselectedIcon,
                            contentDescription = labelText,
                            badge = badgeFor(index, navigationBadge),
                            tint = contentColor,
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                expandFrom = Alignment.Start
                            ) + fadeIn(animationSpec = tween(250)),
                            exit = shrinkHorizontally(
                                animationSpec = tween(250, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Start
                            ) + fadeOut(animationSpec = tween(250))
                        ) {
                            Text(
                                text = labelText,
                                modifier = Modifier.padding(start = 8.dp),
                                color = contentColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavigationIconWithBadge(
    icon: ImageVector,
    contentDescription: String?,
    badge: NavBadge?,
    tint: Color = LocalContentColor.current,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            Icon(icon, contentDescription, tint = tint)
        }
    } else {
        Icon(icon, contentDescription, tint = tint)
    }
}
