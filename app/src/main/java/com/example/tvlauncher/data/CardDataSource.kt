package com.example.tvlauncher.data

import android.content.Context
import java.io.File

/** 卡片区数据源接口 */
interface CardDataSource {
    suspend fun getCardConfigs(): List<CardConfig>
    suspend fun getLauncherConfig(): LauncherConfig
    /** 读取设备 data 文件夹的 data.json 并解析；失败返回 null */
    suspend fun loadLauncherData(): LauncherData?

    /** 读取上次联网时保存的 data.json 快照并解析；不存在或解析失败返回 null */
    suspend fun loadLauncherDataSnapshot(): LauncherData?
}

/** 读取设备 data 文件夹（getExternalFilesDir/data/）的 data.json */
class FileCardDataSource(private val context: Context) : CardDataSource {

    private val dataDir: File
        get() = File(context.getExternalFilesDir(null), "data")

    private val dataFile: File
        get() = File(dataDir, "data.json")

    /** 上次联网成功解析的 data.json 快照，供离线时恢复上次内容 */
    private val snapshotFile: File
        get() = File(dataDir, "last_launcher_data.json")

    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()

    override suspend fun getLauncherConfig(): LauncherConfig {
        return loadLauncherData()?.config ?: LauncherConfig()
    }

    override suspend fun loadLauncherData(): LauncherData? {
        return try {
            val json = dataFile.readText()
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
}

/** 后端未接入/无 data 文件时的兜底实现 */
class LocalCardDataSource : CardDataSource {
    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()
    override suspend fun getLauncherConfig(): LauncherConfig = LauncherConfig()
    override suspend fun loadLauncherData(): LauncherData? = null
    override suspend fun loadLauncherDataSnapshot(): LauncherData? = null
}
