package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.BuiltinMountViewModel

@Composable
fun BuiltinMountScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<BuiltinMountViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = BuiltinMountActions(
        onBack = navigator::pop,
        onRefresh = viewModel::refresh,
        onSetBackendIndex = viewModel::setBackendIndex,
        onSetStorageIndex = viewModel::setStorageIndex,
        onSetModuleBackendIndex = viewModel::setModuleBackend,
        onAddPartition = viewModel::addPartition,
        onRemovePartition = viewModel::removePartition,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> BuiltinMountScreenMiuix(uiState, actions)
        UiMode.Coui -> BuiltinMountScreenCoui(uiState, actions)
        UiMode.Material -> BuiltinMountScreenMaterial(uiState, actions)
    }
}
