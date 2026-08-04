package com.example.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class AppRepository(private val context: Context) {

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    /**
     * 已加载应用信息缓存(包名 → AppInfo)
     * 图标 Drawable 一旦从 PackageManager 取出就常驻内存,避免在主线程重复查询
     */
    private val appCache = mutableMapOf<String, AppInfo>()

    fun getInstalledLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
        val selfPackage = context.packageName

        return resolveInfoList
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
