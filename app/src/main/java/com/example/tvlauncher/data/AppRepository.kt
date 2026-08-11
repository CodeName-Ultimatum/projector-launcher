package com.example.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppRepository(private val context: Context) {

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    /**
     * 已加载应用信息内存缓存(包名 → AppInfo)
     * 图标 Drawable 一旦从 PackageManager 取出就常驻内存,避免在主线程重复查询
     */
    private val appCache = mutableMapOf<String, AppInfo>()

    /** 应用列表磁盘缓存文件(包名+标签元数据,不含图标) */
    private val listCacheFile: File
        get() = File(context.filesDir, "app_list_cache.json")

    /**
     * 读磁盘缓存的应用列表(仅包名+标签)。无缓存或解析失败返回 null。
     * 图标不在缓存内,需按需从 PackageManager 取(单次 getApplicationIcon 很快)。
     */
    fun readCachedAppList(): List<AppInfo>? {
        val file = listCacheFile
        if (!file.exists()) return null
        return try {
            val arr = JSONArray(file.readText())
            val result = mutableListOf<AppInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("pkg")
                val label = o.getString("label")
                // 图标按需取;应用可能已卸载,取不到图标则跳过该项
                val info = getAppInfo(pkg) ?: continue
                result.add(AppInfo(pkg, label, info.icon))
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /** 把应用列表元数据写入磁盘缓存 */
    private fun writeCachedAppList(apps: List<AppInfo>) {
        try {
            val arr = JSONArray()
            apps.forEach {
                arr.put(JSONObject().put("pkg", it.packageName).put("label", it.label))
            }
            listCacheFile.writeText(arr.toString())
        } catch (e: Exception) {
            // 写缓存失败不影响主流程
        }
    }

    /** 全量查询已安装的可启动应用(昂贵,应在 IO 线程调用)。成功后刷新磁盘缓存。 */
    fun getInstalledLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
        val selfPackage = context.packageName

        val apps = resolveInfoList
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == selfPackage) return@mapNotNull null
                try {
                    AppInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0)
                        ).toString(),
                        icon = pm.getApplicationIcon(pkg)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .onEach { appCache[it.packageName] = it }
            .sortedBy { it.label.lowercase() }
        writeCachedAppList(apps)
        return apps
    }

    /** 应用安装/卸载/更新后调用:清磁盘+内存缓存,下次重新全量查询 */
    fun invalidateCache() {
        appCache.clear()
        try { listCacheFile.delete() } catch (e: Exception) { }
    }

    fun getAppInfo(packageName: String): AppInfo? {
        // 命中缓存直接返回,避免主线程同步查 PackageManager
        appCache[packageName]?.let { return it }
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val info = AppInfo(
                packageName = packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai)
            )
            appCache[packageName] = info
            info
        } catch (e: Exception) {
            null
        }
    }
}
