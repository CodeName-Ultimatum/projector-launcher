package com.example.tvlauncher.data

/** 后端下发的全局配置（data.json 顶层 config 字段，JSON 字符串内嵌） */
data class LauncherConfig(
    val screenColor: String? = null,    // "#RRGGBB"
    val panelGradientTop: String? = null,    // "#RRGGBB" 常用应用面板渐变顶色
    val panelGradientBottom: String? = null, // "#RRGGBB" 常用应用面板渐变底色
    val quickBarBg: String? = null,    // "#RRGGBB" 快捷栏格子背景色,缺失时按背景深浅自适应
    val lightMode: Boolean = false,
    val smallIcon: Boolean = false,
    val displayDesc: Boolean = false,
    val displayHead: Boolean = false,
    val displayTitle: Boolean = false
)
