package com.example.tvlauncher.ui

import android.graphics.Color
import com.example.tvlauncher.data.LauncherConfig

/** 全局主题色板管理器:按后端 screenColor/lightMode 计算并分发主题色 */
object ThemeManager {

    data class Palette(
        val screenColor: Int,
        val panelGradientTop: Int,
        val panelGradientBottom: Int,
        val quickBarBg: Int,
        val statusBarIconTint: Int,
        val cardFocusBorder: Int,
        val statusBarTextColor: Int
    )

    private val darkPalette = Palette(
        screenColor = Color.parseColor("#373778"),
        panelGradientTop = Color.parseColor("#4A4A9C"),
        panelGradientBottom = Color.parseColor("#2B2B5C"),
        quickBarBg = Color.parseColor("#FF354D96"),
        statusBarIconTint = Color.parseColor("#E6E6E6"),
        cardFocusBorder = Color.WHITE,
        statusBarTextColor = Color.WHITE
    )

    private val lightPalette = Palette(
        screenColor = Color.parseColor("#F2F2F5"),
        panelGradientTop = Color.parseColor("#D6D6E8"),
        panelGradientBottom = Color.parseColor("#B8B8D0"),
        quickBarBg = Color.parseColor("#E0E0F0"),
        statusBarIconTint = Color.parseColor("#3A3A4A"),
        cardFocusBorder = Color.parseColor("#1A1A1A"),
        statusBarTextColor = Color.parseColor("#1A1A1A")
    )

    var current: LauncherConfig? = null

    fun apply(config: LauncherConfig) {
        current = config
    }

    fun palette(): Palette = if (current?.lightMode == true) lightPalette else darkPalette

    fun screenColor(): Int = current?.screenColor
        ?.takeIf { it.startsWith("#") }
        ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        ?: palette().screenColor

    fun lightMode(): Boolean = current?.lightMode == true
}
