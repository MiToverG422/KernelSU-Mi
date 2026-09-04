package me.weishu.kernelsu.ui.component.choosekmidialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.getCurrentKmi
import me.weishu.kernelsu.ui.util.getSupportedKmis
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.CheckboxLocation
import io.github.suqi8.coui.kmp.preference.CheckboxPreference

@Composable
fun ChooseKmiDialogCoui(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val supportedKMIs by produceState(initialValue = emptyList()) {
        value = getSupportedKmis()
    }
    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }
    val currentSelection = rememberSaveable(currentKmi) { mutableStateOf(currentKmi) }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.select_kmi),
        summary = stringResource(R.string.current_kmi, currentKmi.let { it.ifBlank { "Unknown" } }),
        onDismissRequest = {
            onDismissRequest()
            currentSelection.value = currentKmi
        },
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(supportedKMIs) { kmi ->
                        CheckboxPreference(
                            title = kmi,
                            summary = if (kmi == currentKmi) stringResource(R.string.current_device_kmi) else null,
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentSelection.value == kmi,
                            holdDownState = currentSelection.value == kmi,
                            onCheckedChange = { _ ->
                                currentSelection.value = kmi
                            }
                        )
                    }
                }
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            onDismissRequest()
                            currentSelection.value = currentKmi
                        },
                    ),
                    positive = DialogButtonBarAction(
                        text = stringResource(R.string.confirm),
                        enabled = supportedKMIs.contains(currentSelection.value),
                        onClick = {
                            onSelected(currentSelection.value)
                            onDismissRequest()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    )
}
