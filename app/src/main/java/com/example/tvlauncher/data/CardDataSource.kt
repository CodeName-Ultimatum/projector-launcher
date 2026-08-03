package com.example.tvlauncher.data

/** 卡片区数据源。后端接入前使用 LocalCardDataSource，接入后替换为 RemoteCardDataSource */
interface CardDataSource {
    suspend fun getCardConfigs(): List<CardConfig>
}

/** 后端未接入时的默认实现：返回空，所有卡片显示占位 */
class LocalCardDataSource : CardDataSource {
    override suspend fun getCardConfigs(): List<CardConfig> = emptyList()
}
