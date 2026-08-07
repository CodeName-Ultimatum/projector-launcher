package com.example.tvlauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** 将 data.json 字符串解析为 LauncherData。格式非法时抛出 JSONException 由调用方处理 */
object LauncherDataParser {

    fun parse(json: String): LauncherData {
        val root = JSONObject(json)
        return LauncherData(
            config = parseConfig(root.optJSONObject("config")),
            modules = root.optJSONArray("modules")?.let(::parseModules) ?: emptyList()
        )
    }

    private fun parseConfig(o: JSONObject?): LauncherConfig? {
        if (o == null) return null
        return LauncherConfig(
            screenColor = o.optString("screenColor").takeIf { it.isNotEmpty() },
            lightMode = o.optBoolean("lightMode", false),
            smallIcon = o.optBoolean("smallIcon", false),
            displayDesc = o.optBoolean("displayDesc", false),
            displayHead = o.optBoolean("displayHead", false),
            displayTitle = o.optBoolean("displayTitle", false)
        )
    }

    private fun parseModules(arr: JSONArray): List<LauncherModule> {
        val modules = mutableListOf<LauncherModule>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            modules.add(LauncherModule(
                moduleName = o.optString("moduleName"),
                sort = o.optInt("sort", 0),
                productGroups = o.optJSONArray("productGroups")?.let(::parseProductGroups) ?: emptyList()
            ))
        }
        return modules
    }

    private fun parseProductGroups(arr: JSONArray): List<ProductGroup> {
        val groups = mutableListOf<ProductGroup>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            groups.add(ProductGroup(
                groupName = o.optString("groupName"),
                sort = o.optInt("sort", 0),
                config = o.optString("config").takeIf { it.isNotEmpty() },
                groupApps = o.optJSONArray("groupApps")?.let(::parseGroupApps) ?: emptyList()
            ))
        }
        return groups
    }

    private fun parseGroupApps(arr: JSONArray): List<GroupApp> {
        val apps = mutableListOf<GroupApp>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            apps.add(GroupApp(
                appName = o.optString("appName").takeIf { it.isNotEmpty() },
                packageName = o.optString("packageName").takeIf { it.isNotEmpty() },
                iconUrl = o.optString("iconUrl").takeIf { it.isNotEmpty() },
                iconBgUrl = o.optString("iconBgUrl").takeIf { it.isNotEmpty() },
                md5 = o.optString("md5").takeIf { it.isNotEmpty() },
                intents = o.optString("intents").takeIf { it.isNotEmpty() },
                sort = o.optInt("sort", 0),
                config = o.optJSONObject("config")?.let { cfg ->
                    AppConfig(
                        top = cfg.optInt("top", 0),
                        left = cfg.optInt("left", 0),
                        width = cfg.optInt("width", 0),
                        height = cfg.optInt("height", 0),
                        behavior = cfg.optString("behavior").takeIf { it.isNotEmpty() },
                        displayName = cfg.optInt("displayName", 0)
                    )
                },
                apkUrl = o.optString("apkUrl").takeIf { it.isNotEmpty() },
                isCheckVer = o.optInt("isCheckVer", 0),
                versionName = o.optString("versionName").takeIf { it.isNotEmpty() },
                versionCode = o.optInt("versionCode", 0),
                isApk = o.optInt("isApk", 0)
            ))
        }
        return apps
    }
}
