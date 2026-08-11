package com.example.tvlauncher.data

/** data.json 根节点 */
data class LauncherData(
    val config: LauncherConfig? = null,
    val channel: String? = null,
    val utc: Long? = null,
    val logoUrl: String? = null,
    val modules: List<LauncherModule> = emptyList()
)

/** 模块（data.json modules[]） */
data class LauncherModule(
    val moduleName: String = "",
    val sort: Int = 0,
    val productGroups: List<ProductGroup> = emptyList()
)

/** 产品组（modules[].productGroups[]） */
data class ProductGroup(
    val groupName: String = "",
    val sort: Int = 0,
    val config: String? = null,        // JSON 字符串
    val groupApps: List<GroupApp> = emptyList()
)

/** 组内应用（productGroups[].groupApps[]） */
data class GroupApp(
    val appName: String? = null,
    val packageName: String? = null,
    val iconUrl: String? = null,
    val iconBgUrl: String? = null,
    val md5: String? = null,
    val intents: String? = null,
    val sort: Int = 0,
    val config: AppConfig? = null,
    val apkUrl: String? = null,
    val isCheckVer: Int = 0,
    val versionName: String? = null,
    val versionCode: Int = 0,
    val isApk: Int = 0,
    val language: String? = null,
    val bannerConfig: BannerConfig? = null
) {
    /** intents 内置行为 → 目标包名；无则 null */
    fun resolveIntent(): String? = when (intents) {
        "FILE_MANAGER" -> "com.example.tvlauncher"  // 实际文件管理器包名见 Task 3 的 MainActivity 常量
        "SETTINGS" -> "com.android.settings"
        else -> null
    }
}

/** 应用 config 对象（data.json groupApps[].config） */
data class AppConfig(
    val top: Int = 0,
    val left: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val behavior: String? = null,
    val displayName: Int = 0
)

/** 卡片 Banner 配置(data.json groupApps[].bannerConfig)。只解析+存储,暂不渲染 */
data class BannerConfig(
    val button: String? = null,
    val content: String? = null,
    val title: String? = null,
    val viewType: String? = null
)
