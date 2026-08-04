package com.example.tvlauncher.data

/** 后端配置的单张卡片。字段均为 null 时表示未配置该属性 */
data class CardConfig(
    val slotIndex: Int,               // 0=IVI, 1-3=上排, 4-5=中排, 6-8=下排
    val packageName: String? = null,  // 点击启动的应用包名,可由后端下发
    val imageUrl: String? = null,     // 整卡图片 URL,由 Glide 加载
    val overlayStartColor: String? = null, // 覆盖层起始色 "#RRGGBBAA"
    val overlayEndColor: String? = null,   // 覆盖层结束色
    val overlayOrientation: String? = null // 渐变方向 "TL_BR"/"LEFT_RIGHT"/"TOP_BOTTOM"
)
