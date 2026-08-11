package com.example.tvlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 监听应用安装/卸载/更新,使应用列表磁盘缓存失效。
 * PACKAGE_* 是显式广播,manifest 注册的接收器不受后台隐式广播限制。
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // 仅当变化的是其它应用时失效(自身更新不触发列表变化)
                val changedPkg = intent.data?.schemeSpecificPart
                if (changedPkg != null && changedPkg != context.packageName) {
                    AppRepository(context).invalidateCache()
                }
            }
        }
    }
}
