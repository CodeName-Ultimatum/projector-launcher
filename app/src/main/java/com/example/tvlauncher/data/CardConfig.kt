package com.example.tvlauncher.data

/** 后端配置的单张卡片。字段均为 null 时表示未配置该属性 */
data class CardConfig(
    val slotIndex: Int,               // 0=IVI, 1-3=上排, 4-5=中排
    val packageName: String? = null,  // 应用包名
    val label: String? = null,        // 显示名称
    val iconUrl: String? = null,      // 图标URL
    val overlayStartColor: String? = null, // 覆盖层起始色 "#RRGGBBAA"
    val overlayEndColor: String? = null,   // 覆盖层结束色
    val overlayOrientation: String? = null // 渐变方向 "TL_BR"/"LEFT_RIGHT"/"TOP_BOTTOM"
)
