package me.weishu.kernelsu.data.model

enum class MountMode(val value: String) {
    MetaModule("meta_module"),
    MisuMount("misu_mount");

    companion object {
        val DEFAULT = MetaModule

        fun fromValue(value: String?): MountMode {
            return when (value) {
                MetaModule.value -> MetaModule
                MisuMount.value,
                "magic_mount",
                "overlayfs" -> MisuMount
                else -> DEFAULT
            }
        }

    }
}
