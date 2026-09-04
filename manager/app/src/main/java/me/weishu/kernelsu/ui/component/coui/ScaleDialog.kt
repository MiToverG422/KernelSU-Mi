package me.weishu.kernelsu.ui.component.coui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme.colorScheme

@Composable
fun ScaleDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    volumeState: () -> Float,
    onVolumeChange: (Float) -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_page_scale),
        summary = "80% - 110%",
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) {
                mutableStateOf((volumeState() * 100).toInt().toString())
            }
            Column {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    value = text,
                    maxLines = 1,
                    trailingIcon = {
                        Text(
                            text = "%",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onValueChange = { newValue ->
                        if (newValue.isEmpty()) {
                            text = ""
                        } else {
                            val valid = newValue.all { it.isDigit() }
                            if (valid) {
                                text = newValue
                            }
                        }
                    },
                )
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismissRequest,
                    ),
                    positive = DialogButtonBarAction(
                        text = stringResource(android.R.string.ok),
                        onClick = {
                            val parsed = text.toIntOrNull()
                            val clamped = parsed?.coerceIn(80, 110) ?: (volumeState() * 100).toInt()
                            onVolumeChange(clamped / 100f)
                            onDismissRequest()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    )
}
