package com.example.tvlauncher.data

import android.content.Context
import java.io.File

/** 卡片区数据源接口 */
interface CardDataSource {
    suspend fun getCardConfigs(): List<CardConfig>
    suspend fun getLauncherConfig(): LauncherConfig
    /** 读取 data 文件夹的 data.json 并解析；失败返回 null */
    suspend fun loadLauncherData(): LauncherData?
}

/** 读取设备 data 文件夹（getExternalFilesDir/data/）的 data.json */
class FileCardDataSource(private val context: Context) : CardDataSource {

    private val dataDir: File
        get() = File(context.getExternalFilesDir(null), "data")

    private val dataFile: File
        get() = File(dataDir, "data.json")

    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()

    override suspend fun getLauncherConfig(): LauncherConfig {
        return loadLauncherData()?.config ?: LauncherConfig()
    }

    override suspend fun loadLauncherData(): LauncherData? {
        return try {
            val json = dataFile.readText()
            LauncherDataParser.parse(json)
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
}
