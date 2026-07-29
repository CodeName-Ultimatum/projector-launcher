package com.example.tvlauncher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class QuickAppsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val maxApps: Int = 12

    fun getQuickApps(): List<String> {
        val json = prefs.getString(KEY_QUICK_APPS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuickApp(packageName: String): Boolean {
        val current = getQuickApps().toMutableList()
        if (current.contains(packageName)) return false
        if (current.size >= maxApps) {
            current.removeAt(0)
        }
        current.add(packageName)
        save(current)
        return true
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
