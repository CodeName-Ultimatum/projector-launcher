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
open class ApiCardDataSource(
    private val context: Context?,
    private val apiUrl: String,
    private val storage: LongStorage,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 8000
) : CardDataSource {

    private val channelStore = ChannelStore(storage)

    /** 快照仍写本地，供离线时恢复上次联网内容（与 FileCardDataSource 同目录） */
    private val snapshotFile: File
        get() = File(File(context?.getExternalFilesDir(null), "data"), "last_launcher_data.json")

    /** 可被单测覆写的网络获取 */
    open fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
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

    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()

    override suspend fun getLauncherConfig(): LauncherConfig {
        return loadLauncherData()?.config ?: LauncherConfig()
    }

    override suspend fun loadLauncherData(): LauncherData? = withContext(Dispatchers.IO) {
        val json = fetch(apiUrl) ?: return@withContext null
        try {
            val data = LauncherDataParser.parse(json)
            // channel 比对：不是发给本设备的配置直接忽略
            if (data.channel != ChannelStore.CHANNEL) return@withContext null
            // utc 门控：仅当新配置（utc > lastUtc）才应用并更新 lastUtc
            val lastUtc = channelStore.lastUtc
            if (data.utc == null || data.utc > lastUtc) {
                channelStore.lastUtc = data.utc ?: lastUtc
                try { snapshotFile.writeText(json) } catch (e: Exception) { /* 写失败不影响本次使用 */ }
                data
            } else {
                null // 配置未变，由调用方走快照缓存路径
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
}
