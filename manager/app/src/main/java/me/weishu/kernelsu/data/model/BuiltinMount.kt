package me.weishu.kernelsu.data.model

import androidx.compose.runtime.Immutable

enum class BuiltinMountBackend(val value: String) {
    Auto("auto"),
    MagicMount("magic_mount"),
    OverlayFs("overlayfs"),
    Disabled("disabled");

    companion object {
        val DEFAULT = Auto

        fun fromValue(value: String?): BuiltinMountBackend {
            return entries.firstOrNull { it.value == value } ?: DEFAULT
        }
    }
}

enum class BuiltinMountStorage(val value: String) {
    Tmpfs("tmpfs"),
    Ext4("ext4");

    companion object {
        val DEFAULT = Tmpfs

        fun fromValue(value: String?): BuiltinMountStorage {
            return entries.firstOrNull { it.value == value } ?: DEFAULT
        }
    }
}

@Immutable
data class BuiltinMountModuleStatus(
    val dirId: String,
    val id: String,
    val name: String,
    val enabled: Boolean,
    val remove: Boolean,
    val skipMount: Boolean,
    val metamodule: Boolean,
    val needsMount: Boolean,
    val partitions: List<String>,
    val configuredBackend: BuiltinMountBackend?,
    val effectiveBackend: BuiltinMountBackend,
)

@Immutable
data class BuiltinMountLastRun(
    val status: String,
    val mountMode: String,
    val backend: BuiltinMountBackend,
    val overlayStorage: BuiltinMountStorage,
    val actualOverlayStorage: BuiltinMountStorage?,
    val startedAt: Long,
    val finishedAt: Long,
    val moduleCount: Int,
    val mountableModuleCount: Int,
    val activeMounts: List<String>,
    val fallbackPartitions: List<String>,
    val warnings: List<String>,
    val failedModules: List<String>,
    val error: String?,
)

@Immutable
data class BuiltinMountStatus(
    val backend: BuiltinMountBackend = BuiltinMountBackend.DEFAULT,
    val overlayStorage: BuiltinMountStorage = BuiltinMountStorage.DEFAULT,
    val customPartitions: List<String> = emptyList(),
    val knownPartitions: List<String> = emptyList(),
    val modules: List<BuiltinMountModuleStatus> = emptyList(),
    val lastRun: BuiltinMountLastRun? = null,
) {
    val mountableModules: List<BuiltinMountModuleStatus>
        get() = modules.filter { it.needsMount }
}
