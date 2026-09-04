package me.weishu.kernelsu.ui.component.coui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.filter.FilterNumber
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference

@Composable
fun SuperEditArrow(
    modifier: Modifier = Modifier,
    title: String,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    defaultValue: Int = -1,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    onValueChange: ((Int) -> Unit)? = null
) {
    val showDialog = remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        titleColor = titleColor,
        summary = defaultValue.toString(),
        summaryColor = summaryColor,
        startAction = startAction,
        modifier = modifier,
        insideMargin = insideMargin,
        onClick = {
            showDialog.value = true
        },
        holdDownState = showDialog.value,
        enabled = enabled
    )

    EditDialog(
        title = title,
        show = showDialog.value,
        onDismissRequest = { showDialog.value = false },
        dialogTextFieldValue = defaultValue,
        onValueChange = {
            onValueChange?.invoke(it)
        }
    )

}

@Composable
private fun EditDialog(
    title: String,
    show: Boolean,
    onDismissRequest: () -> Unit,
    dialogTextFieldValue: Int,
    onValueChange: (Int) -> Unit,
) {
    val filter = remember(dialogTextFieldValue) { FilterNumber(dialogTextFieldValue) }

    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = {
            onDismissRequest()
            filter.setInputValue(dialogTextFieldValue.toString())
        },
        content = {
            Column {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    value = filter.getInputValue(),
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    onValueChange = filter.onValueChange()
                )
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            onDismissRequest()
                            filter.setInputValue(dialogTextFieldValue.toString())
                        },
                    ),
                    positive = DialogButtonBarAction(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            onDismissRequest()
                            with(filter.getInputValue().text) {
                                if (isEmpty()) {
                                    onValueChange(0)
                                    filter.setInputValue("0")
                                } else {
                                    onValueChange(this@with.toInt())
                                }
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    )
}
