package me.weishu.kernelsu.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.markdown.MarkdownContent
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog

@Composable
fun LoadingDialogCoui(
    showDialog: MutableState<Boolean>,
) {
    WindowDialog(
        show = showDialog.value,
        content = {
            // Consume the back gesture before the dialog's own handler
            val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
            NavigationBackHandler(
                state = navEventState,
                isBackEnabled = true,
                onBackCompleted = { },
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    InfiniteProgressIndicator(
                        color = COUITheme.colorScheme.onBackground
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = stringResource(R.string.processing),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

@Composable
fun ConfirmDialogCoui(
    visuals: ConfirmDialogVisuals,
    confirm: () -> Unit,
    dismiss: () -> Unit,
    showDialog: MutableState<Boolean>
) {
    WindowDialog(
        show = showDialog.value,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
        title = visuals.title,
        onDismissRequest = {
            dismiss()
            showDialog.value = false
        },
        content = {
            val dismissState = LocalDismissState.current
            Column {
                visuals.content?.let { content ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        when {
                            visuals.isMarkdown -> MarkdownContent(content = content, isMarkdown = true)
                            visuals.isHtml -> MarkdownContent(content = content, isMarkdown = false)
                            else -> Text(text = content)
                        }
                    }
                }
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = visuals.dismiss ?: stringResource(id = android.R.string.cancel),
                        onClick = {
                            dismiss()
                            dismissState?.invoke()
                        }
                    ),
                    positive = DialogButtonBarAction(
                        text = visuals.confirm ?: stringResource(id = android.R.string.ok),
                        onClick = {
                            confirm()
                            dismissState?.invoke()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    hasContentAbove = visuals.title.isNotBlank() || !visuals.content.isNullOrBlank(),
                )
            }
        }
    )
}
