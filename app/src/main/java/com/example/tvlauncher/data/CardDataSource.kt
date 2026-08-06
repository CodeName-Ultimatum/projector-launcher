package com.example.tvlauncher.data

/** 卡片区数据源。后端接入前使用 LocalCardDataSource，接入后替换为 RemoteCardDataSource */
interface CardDataSource {
    suspend fun getCardConfigs(): List<CardConfig>

    /** 获取全局配置(背景色等)。后端未接入时返回默认配置 */
    suspend fun getLauncherConfig(): LauncherConfig = LauncherConfig()
}

/** 后端未接入时的默认实现：返回空，所有卡片显示占位 */
class LocalCardDataSource : CardDataSource {
    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()
}
