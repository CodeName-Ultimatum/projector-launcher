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
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val isSystem =
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystem &&
                        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    ) {
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val isMediaApp = label.isNotEmpty() &&
                            (pkg.contains("media") || pkg.contains("player") ||
                                pkg.contains("video") || pkg.contains("music") ||
                                pkg.contains("gallery") || pkg.contains("camera"))
                        if (!isMediaApp) return@mapNotNull null
                    }
                    AppInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getAppInfo(packageName: String): AppInfo? {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai)
            )
        } catch (e: Exception) {
            null
        }
    }
}
