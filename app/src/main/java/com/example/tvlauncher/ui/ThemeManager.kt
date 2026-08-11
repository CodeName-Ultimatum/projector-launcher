package com.example.tvlauncher.ui

import android.graphics.Color
import com.example.tvlauncher.data.LauncherConfig

/**
 * 全局主题色板管理器:所有组件色从后端 screenColor 派生,消灭写死的深蓝。
 *
 * 派生策略(HSL 亮度调整):
 * - 深色模式(lightMode=false):背景=screenColor,面板渐变=screenColor 亮/暗变体,
 *   quickBar=screenColor 更亮一档,图标/文字=白色系,聚焦边框=白。
 * - 浅色模式(lightMode=true):背景=screenColor 提亮到很浅,面板渐变=浅色变体,
 *   quickBar=浅色,图标/文字=深色系,聚焦边框=深。
 */
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

    var current: LauncherConfig? = null

    fun apply(config: LauncherConfig) {
        current = config
    }

    /** 主屏背景色:lightMode=true 用浅色派生,否则用配置 screenColor(缺省深蓝)。 */
    fun screenColor(): Int = if (current?.lightMode == true) {
        baseColor().lighten(0.55f)
    } else {
        baseColor()
    }

    /** 配置的 screenColor(校验后),无则默认深蓝 #373778 */
    private fun baseColor(): Int = current?.screenColor
        ?.takeIf { it.startsWith("#") }
        ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        ?: Color.parseColor("#373778")

    fun lightMode(): Boolean = current?.lightMode == true

    /** 按 screenColor + lightMode 计算整套色板(每次实时派生,随主题变化) */
    fun palette(): Palette {
        val base = baseColor()
        // 面板渐变:后端 panelGradientTop/Bottom 有值用下发色,缺失用派生色
        val derivedTop = if (current?.lightMode == true) base.lighten(0.40f) else base.lighten(0.12f)
        val derivedBottom = if (current?.lightMode == true) base.lighten(0.30f) else base.darken(0.15f)
        // 快捷栏格子:后端 quickBarBg 有值用下发色;缺失时按背景深浅自适应(深背景→浅格子,浅背景→深格子)
        val derivedQuickBar = if (current?.lightMode == true) base.darken(0.35f) else base.lighten(0.35f)
        return if (current?.lightMode == true) {
            // 浅色模式:背景提亮到很浅,组件更深以形成对比,文字深色
            Palette(
                screenColor = base.lighten(0.55f),
                panelGradientTop = current?.panelGradientTop?.parseColor() ?: derivedTop,
                panelGradientBottom = current?.panelGradientBottom?.parseColor() ?: derivedBottom,
                quickBarBg = current?.quickBarBg?.parseColor() ?: derivedQuickBar,
                statusBarIconTint = Color.parseColor("#2A2A3A"),
                cardFocusBorder = Color.WHITE,  // 卡片聚焦边框始终白色(深浅色一致)
                statusBarTextColor = Color.parseColor("#1A1A1A")
            )
        } else {
            // 深色模式:组件从 screenColor 派生(亮/暗变体),保持同色系,文字白
            Palette(
                screenColor = base,
                panelGradientTop = current?.panelGradientTop?.parseColor() ?: derivedTop,
                panelGradientBottom = current?.panelGradientBottom?.parseColor() ?: derivedBottom,
                quickBarBg = current?.quickBarBg?.parseColor() ?: derivedQuickBar,
                statusBarIconTint = Color.parseColor("#E6E6E6"),
                cardFocusBorder = Color.WHITE,
                statusBarTextColor = Color.WHITE
            )
        }
    }

    /** 解析 "#RRGGBB" 颜色字符串,非法返回 null */
    private fun String.parseColor(): Int? =
        takeIf { startsWith("#") }?.let { runCatching { Color.parseColor(it) }.getOrNull() }

    /** 按比例提亮亮度(0..1,保持色相饱和) */
    private fun Int.lighten(factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(this, hsv)
        hsv[2] = (hsv[2] + (1f - hsv[2]) * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    /** 按比例压暗亮度(0..1) */
    private fun Int.darken(factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(this, hsv)
        hsv[2] = (hsv[2] * (1f - factor)).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}
