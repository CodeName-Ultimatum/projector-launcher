package com.example.tvlauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** 将 data.json 字符串解析为 LauncherData。格式非法时抛出 JSONException 由调用方处理 */
object LauncherDataParser {

    fun parse(json: String): LauncherData {
        val root = JSONObject(json)
        return LauncherData(
            config = parseConfig(root.opt("config")),
            channel = root.optString("channel").takeIf { it.isNotEmpty() && it != "null" },
            utc = if (root.has("utc")) root.optLong("utc") else null,
            logoUrl = root.optString("logoUrl").takeIf { it.isNotEmpty() && it != "null" },
            modules = root.optJSONArray("modules")?.let(::parseModules) ?: emptyList()
        )
    }

    /**
     * 解析顶层 config。兼容两种形式:
     * 1. 对象: "config": {"screenColor": "..."}
     * 2. 字符串(后端实际下发格式): "config": "{\"screenColor\": \"...\"}"
     */
    private fun parseConfig(o: Any?): LauncherConfig? {
        val obj = when (o) {
            is JSONObject -> o
            is String -> try { JSONObject(o) } catch (e: Exception) { null }
            else -> null
        } ?: return null
        return LauncherConfig(
            screenColor = obj.optString("screenColor").takeIf { it.isNotEmpty() && it != "null" },
            panelGradientTop = obj.optString("panelGradientTop").takeIf { it.isNotEmpty() && it != "null" },
            panelGradientBottom = obj.optString("panelGradientBottom").takeIf { it.isNotEmpty() && it != "null" },
            quickBarBg = obj.optString("quickBarBg").takeIf { it.isNotEmpty() && it != "null" },
            lightMode = obj.optBoolean("lightMode", false),
            smallIcon = obj.optBoolean("smallIcon", false),
            displayDesc = obj.optBoolean("displayDesc", false),
            displayHead = obj.optBoolean("displayHead", false),
            displayTitle = obj.optBoolean("displayTitle", false)
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
                appName = o.optString("appName").takeIf { it.isNotEmpty() && it != "null" },
                packageName = o.optString("packageName").takeIf { it.isNotEmpty() && it != "null" },
                iconUrl = o.optString("iconUrl").takeIf { it.isNotEmpty() && it != "null" },
                iconBgUrl = o.optString("iconBgUrl").takeIf { it.isNotEmpty() && it != "null" },
                md5 = o.optString("md5").takeIf { it.isNotEmpty() && it != "null" },
                intents = o.optString("intents").takeIf { it.isNotEmpty() && it != "null" },
                sort = o.optInt("sort", 0),
                config = o.optJSONObject("config")?.let { cfg ->
                    AppConfig(
                        top = cfg.optInt("top", 0),
                        left = cfg.optInt("left", 0),
                        width = cfg.optInt("width", 0),
                        height = cfg.optInt("height", 0),
                        behavior = cfg.optString("behavior").takeIf { it.isNotEmpty() && it != "null" },
                        displayName = cfg.optInt("displayName", 0)
                    )
                },
                apkUrl = o.optString("apkUrl").takeIf { it.isNotEmpty() && it != "null" },
                isCheckVer = o.optInt("isCheckVer", 0),
                versionName = o.optString("versionName").takeIf { it.isNotEmpty() && it != "null" },
                versionCode = o.optInt("versionCode", 0),
                isApk = o.optInt("isApk", 0),
                language = o.optString("language").takeIf { it.isNotEmpty() && it != "null" },
                bannerConfig = o.optJSONObject("bannerConfig")?.let { bc ->
                    BannerConfig(
                        button = bc.optString("button").takeIf { it.isNotEmpty() && it != "null" },
                        content = bc.optString("content").takeIf { it.isNotEmpty() && it != "null" },
                        title = bc.optString("title").takeIf { it.isNotEmpty() && it != "null" },
                        viewType = bc.optString("viewType").takeIf { it.isNotEmpty() && it != "null" }
                    )
                }
            ))
        }
        return apps
    }
}
