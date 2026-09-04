package me.weishu.kernelsu.ui.viewmodel

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.BuiltinMountBackend
import me.weishu.kernelsu.data.model.BuiltinMountStorage
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.screen.settings.BuiltinMountUiState
import me.weishu.kernelsu.ui.util.addBuiltinMountPartition
import me.weishu.kernelsu.ui.util.getBuiltinMountStatus
import me.weishu.kernelsu.ui.util.normalizePartitionName
import me.weishu.kernelsu.ui.util.removeBuiltinMountPartition
import me.weishu.kernelsu.ui.util.setBuiltinMountBackend
import me.weishu.kernelsu.ui.util.setBuiltinMountModuleBackend
import me.weishu.kernelsu.ui.util.setBuiltinMountStorage

class BuiltinMountViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BuiltinMountUiState())
    val uiState: StateFlow<BuiltinMountUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val status = runCatching { getBuiltinMountStatus() }.getOrDefault(_uiState.value.status)
            _uiState.update { it.copy(isLoading = false, status = status) }
        }
    }

    fun setBackendIndex(index: Int) {
        val backend = BuiltinMountBackend.entries.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (setBuiltinMountBackend(backend)) {
                _uiState.update { it.copy(status = it.status.copy(backend = backend)) }
                showToast(R.string.reboot_to_apply)
            } else {
                showToast(R.string.settings_builtin_mount_save_failed)
            }
        }
    }

    fun setStorageIndex(index: Int) {
        val storage = BuiltinMountStorage.entries.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (setBuiltinMountStorage(storage)) {
                _uiState.update { it.copy(status = it.status.copy(overlayStorage = storage)) }
                showToast(R.string.reboot_to_apply)
            } else {
                showToast(R.string.settings_builtin_mount_save_failed)
            }
        }
    }

    fun setModuleBackend(moduleId: String, index: Int) {
        val backend = BuiltinMountBackend.entries.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (setBuiltinMountModuleBackend(moduleId, backend)) {
                refresh()
                showToast(R.string.reboot_to_apply)
            } else {
                showToast(R.string.settings_builtin_mount_save_failed)
            }
        }
    }

    fun addPartition(partition: String) {
        val normalized = normalizePartitionName(partition)
        if (normalized == null) {
            viewModelScope.launch { showToast(R.string.settings_builtin_mount_invalid_partition) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (addBuiltinMountPartition(normalized)) {
                refresh()
                showToast(R.string.reboot_to_apply)
            } else {
                showToast(R.string.settings_builtin_mount_save_failed)
            }
        }
    }

    fun removePartition(partition: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (removeBuiltinMountPartition(partition)) {
                refresh()
                showToast(R.string.reboot_to_apply)
            } else {
                showToast(R.string.settings_builtin_mount_save_failed)
            }
        }
    }

    private suspend fun showToast(messageRes: Int) = withContext(Dispatchers.Main) {
        Toast.makeText(ksuApp, messageRes, Toast.LENGTH_LONG).show()
    }
}
