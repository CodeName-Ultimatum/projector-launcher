package com.example.tvlauncher.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 应用更新下载安装器：DownloadManager 下载 apkUrl → FileProvider 暴露 → 系统安装器。
 * 非系统签名应用，需用户确认安装。
 */
class AppUpdater(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val downloadIds = mutableSetOf<Long>()
    private val pendingApks = mutableMapOf<Long, String>() // downloadId -> apk 绝对路径
    private var receiverRegistered = false

    private val completeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val path = pendingApks.remove(id) ?: return
            installApk(path)
        }
    }

    /** 下载并安装单个应用 APK；apkUrl 为空则跳过 */
    fun downloadAndInstall(app: GroupApp) {
        val url = app.apkUrl ?: return
        val cacheDir = context.getExternalCacheDir() ?: return
        val dir = File(cacheDir, "apk").apply { mkdirs() }
        // 包名转安全文件名
        val fileName = "${app.packageName ?: "app"}_${System.currentTimeMillis()}.apk"
        val destFile = File(dir, fileName)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("正在更新: ${app.appName ?: app.packageName ?: "应用"}")
            setDescription("下载完成后自动安装")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destFile))
        }
        val id = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            return
        }
        downloadIds.add(id)
        pendingApks[id] = destFile.absolutePath
        registerReceiver()
    }

    /** 启动系统安装器安装 APK */
    private fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.tvlauncher.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        context.registerReceiver(completeReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        receiverRegistered = true
    }

    /** onDestroy 调用，取消下载并注销广播避免泄漏 */
    fun cleanup() {
        for (id in downloadIds) {
            downloadManager.remove(id)
            pendingApks.remove(id)
        }
        downloadIds.clear()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(completeReceiver)
            } catch (e: Exception) { /* 已注销则忽略 */ }
            receiverRegistered = false
        }
    }
}
