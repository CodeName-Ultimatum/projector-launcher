package com.example.tvlauncher.data

import android.content.Context
import android.content.SharedPreferences

/** 长期整数值存储接口,便于单测注入内存实现 */
interface LongStorage {
    fun getLong(key: String, def: Long): Long
    fun putLong(key: String, value: Long)
}

/** SharedPreferences 的 LongStorage 实现 */
class PrefsLongStorage(context: Context) : LongStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun getLong(key: String, def: Long): Long = prefs.getLong(key, def)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    companion object {
        const val PREFS_NAME = "tv_launcher_prefs"
    }
}

/** 本机 launcher 渠道标识 + 上次应用配置的 utc 时间戳 */
class ChannelStore(private val storage: LongStorage) {

    val channel: String = CHANNEL

    var lastUtc: Long
        get() = storage.getLong(KEY_LAST_UTC, 0L)
        set(value) = storage.putLong(KEY_LAST_UTC, value)

    companion object {
        const val CHANNEL = "H313_launch_test"
        private const val KEY_LAST_UTC = "last_utc"
    }
}
