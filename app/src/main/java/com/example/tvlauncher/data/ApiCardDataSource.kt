package com.example.tvlauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从真实后端 API 读取 data.json 的数据源。
 *
 * 与 FileCardDataSource（读设备本地文件）并列，实现同一 CardDataSource 接口。
 * 网络请求成功且解析成功 → 返回 LauncherData 并写快照；
 * 任何失败（超时/HTTP错误/解析失败）→ 返回 null，由 MainActivity 落入离线兜底。
 *
 * 接入方式：MainActivity 里把
 *   cardDataSource = FileCardDataSource(this)
 * 改为
 *   cardDataSource = ApiCardDataSource(this, "https://你的域名/path/data.json")
 */
class ApiCardDataSource(
    private val context: Context,
    private val apiUrl: String,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 8000
) : CardDataSource {

    /** 快照仍写本地，供离线时恢复上次联网内容（与 FileCardDataSource 同目录） */
    private val snapshotFile: File
        get() = File(File(context.getExternalFilesDir(null), "data"), "last_launcher_data.json")

    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()

    override suspend fun getLauncherConfig(): LauncherConfig {
        return loadLauncherData()?.config ?: LauncherConfig()
    }

    override suspend fun loadLauncherData(): LauncherData? = withContext(Dispatchers.IO) {
        val json = httpGet(apiUrl) ?: return@withContext null
        try {
            LauncherDataParser.parse(json).also {
                // 解析成功即写快照，离线时恢复上次联网内容
                try { snapshotFile.writeText(json) } catch (e: Exception) { /* 写失败不影响本次使用 */ }
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLauncherDataSnapshot(): LauncherData? {
        return try {
            LauncherDataParser.parse(snapshotFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    /** 同步 GET 请求，返回响应体字符串；任何失败返回 null。须在 IO 线程调用。 */
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
