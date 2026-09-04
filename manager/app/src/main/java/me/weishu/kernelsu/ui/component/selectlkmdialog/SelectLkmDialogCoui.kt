package me.weishu.kernelsu.ui.component.selectlkmdialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.install.LkmVariant
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.CheckboxLocation
import io.github.suqi8.coui.kmp.preference.CheckboxPreference

@Composable
fun SelectLkmDialogCoui(
    show: Boolean,
    currentVariant: LkmVariant,
    onDismissRequest: () -> Unit,
    onSelectVariant: (LkmVariant) -> Unit
) {
    val variants = listOf(
        LkmVariant.KOWSU to R.string.install_lkm_kowsu,
        LkmVariant.XXKSU to R.string.install_lkm_xxksu,
        LkmVariant.CUSTOM to R.string.install_upload_lkm_file
    )

    val currentSelection = rememberSaveable { mutableStateOf(currentVariant) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.install_select_lkm_variant),
        onDismissRequest = onDismissRequest,
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(variants) { (variant, stringRes) ->
                        CheckboxPreference(
                            title = stringResource(stringRes),
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentSelection.value == variant,
                            holdDownState = currentSelection.value == variant,
                            onCheckedChange = { _ ->
                                currentSelection.value = variant
                            }
                        )
                    }
                }
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            onDismissRequest()
                            currentSelection.value = currentVariant
                        },
                    ),
                    positive = DialogButtonBarAction(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            onSelectVariant(currentSelection.value)
                            onDismissRequest()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    )
}
