package com.example.tvlauncher.data

/** 后端下发的全局配置（data.json 顶层 config 字段，JSON 字符串内嵌） */
data class LauncherConfig(
    val screenColor: String? = null,    // "#RRGGBB"
    val lightMode: Boolean = false,
    val smallIcon: Boolean = false,
    val displayDesc: Boolean = false,
    val displayHead: Boolean = false,
    val displayTitle: Boolean = false
)
