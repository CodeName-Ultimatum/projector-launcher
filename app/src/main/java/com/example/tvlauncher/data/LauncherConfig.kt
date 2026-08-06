package com.example.tvlauncher.data

/** 后端下发的全局配置。字段为 null 时表示使用本地默认值 */
data class LauncherConfig(
    val bgColor: String? = null  // 主界面背景色 "#RRGGBB",未下发时用本地 main_bg
)
