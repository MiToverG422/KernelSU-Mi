package me.weishu.kernelsu.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiMode(val value: String, val label: String) {
    Miuix("miuix", "MIUIX"),
    Coui("coui", "COUI（测试版）"),
    Material("material", "Material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Miuix.value -> Miuix
            Coui.value -> Coui
            else -> Material
        }

        val DEFAULT_VALUE = Material.value
    }

    val isMiuixFamily: Boolean
        get() = this == Miuix || this == Coui
}

val LocalUiMode = staticCompositionLocalOf { UiMode.Material }
