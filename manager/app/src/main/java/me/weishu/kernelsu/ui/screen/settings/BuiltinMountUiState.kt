package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.BuiltinMountBackend
import me.weishu.kernelsu.data.model.BuiltinMountStatus
import me.weishu.kernelsu.data.model.BuiltinMountStorage

@Immutable
data class BuiltinMountUiState(
    val isLoading: Boolean = true,
    val status: BuiltinMountStatus = BuiltinMountStatus(),
) {
    val selectedBackendIndex: Int
        get() = BuiltinMountBackend.entries.indexOf(status.backend).coerceAtLeast(0)

    val selectedStorageIndex: Int
        get() = BuiltinMountStorage.entries.indexOf(status.overlayStorage).coerceAtLeast(0)
}

@Immutable
data class BuiltinMountActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSetBackendIndex: (Int) -> Unit,
    val onSetStorageIndex: (Int) -> Unit,
    val onSetModuleBackendIndex: (String, Int) -> Unit,
    val onAddPartition: (String) -> Unit,
    val onRemovePartition: (String) -> Unit,
)
