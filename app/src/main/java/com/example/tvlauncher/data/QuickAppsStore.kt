package com.example.tvlauncher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class QuickAppsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 快捷栏上限（用户实测 720p 屏 11 个应用 + "+"按钮恰好完整放下） */
    val maxApps: Int = 11

    /** addQuickApp 的添加结果 */
    enum class AddResult {
        ADDED,          // 添加成功
        ALREADY_EXISTS, // 已存在，未重复添加
        FULL            // 已达上限，拒绝添加
    }

    fun getQuickApps(): List<String> {
        val json = prefs.getString(KEY_QUICK_APPS, "[]") ?: "[]"
        val list = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        // 一次性迁移：旧版本上限更大，存量超过现上限时直接清空，
        // 避免快捷栏溢出被切割；清空后下次读取即恢复正常
        if (list.size > maxApps) {
            save(emptyList())
            return emptyList()
        }
        return list
    }

    fun addQuickApp(packageName: String): AddResult {
        val current = getQuickApps()
        if (current.contains(packageName)) return AddResult.ALREADY_EXISTS
        // 满了直接拒绝，不淘汰任何已固定的应用
        if (current.size >= maxApps) return AddResult.FULL
        current.toMutableList().apply {
            add(packageName)
            save(this)
        }
        return AddResult.ADDED
    }

    fun removeQuickApp(packageName: String) {
        val current = getQuickApps().toMutableList()
        current.remove(packageName)
        save(current)
    }

    fun contains(packageName: String): Boolean {
        return getQuickApps().contains(packageName)
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_QUICK_APPS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "tv_launcher_prefs"
        private const val KEY_QUICK_APPS = "quick_apps"
    }
}
