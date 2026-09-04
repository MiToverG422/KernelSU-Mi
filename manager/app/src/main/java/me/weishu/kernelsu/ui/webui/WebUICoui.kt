package me.weishu.kernelsu.ui.webui

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.window.WindowDialog

@Composable
fun HandleWebUIEventCoui(
    webUIState: WebUIState,
    fileLauncher: ActivityResultLauncher<Intent>
) {
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.ShowAlert -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            WindowDialog(
                show = showDialog.value,
                content = {
                    Column {
                        Text(
                            text = event.message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        DialogButtonBar(
                            negative = null,
                            positive = DialogButtonBarAction(
                                text = stringResource(R.string.confirm),
                                onClick = {
                                    webUIState.onAlertResult()
                                    showDialog.value = false
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            )
        }

        is WebUIEvent.ShowConfirm -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            WindowDialog(
                show = showDialog.value,
                onDismissRequest = { webUIState.onConfirmResult(false) },
                content = {
                    Column {
                        Text(
                            text = event.message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        DialogButtonBar(
                            negative = DialogButtonBarAction(
                                text = stringResource(android.R.string.cancel),
                                onClick = {
                                    webUIState.onConfirmResult(false)
                                    showDialog.value = false
                                }
                            ),
                            positive = DialogButtonBarAction(
                                text = stringResource(R.string.confirm),
                                onClick = {
                                    webUIState.onConfirmResult(true)
                                    showDialog.value = false
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            )
        }

        is WebUIEvent.ShowPrompt -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            val state = rememberTextFieldState(event.defaultValue)
            WindowDialog(
                show = showDialog.value,
                onDismissRequest = { webUIState.onPromptResult(null) },
                content = {
                    Column {
                        Text(
                            text = event.message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                        TextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            state = state
                        )
                        DialogButtonBar(
                            negative = DialogButtonBarAction(
                                text = stringResource(android.R.string.cancel),
                                onClick = {
                                    webUIState.onPromptResult(null)
                                    showDialog.value = false
                                }
                            ),
                            positive = DialogButtonBarAction(
                                text = stringResource(R.string.confirm),
                                onClick = {
                                    webUIState.onPromptResult(state.text.toString())
                                    showDialog.value = false
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            )
        }

        is WebUIEvent.ShowFileChooser -> {
            LaunchedEffect(event) {
                try {
                    fileLauncher.launch(event.intent)
                } catch (_: Exception) {
                    webUIState.onFileChooserResult(null)
                }
            }
        }

        else -> {}
    }
}
