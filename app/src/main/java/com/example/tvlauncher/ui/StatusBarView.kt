package com.example.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.example.tvlauncher.R
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.getWifiSignalLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部状态栏 — 显示投影仪图标、WiFi 信号强度、时间、星期和日期
 *
 * 结构：水平 LinearLayout，投影仪图标固定左侧，weight 弹簧分隔 WiFi（中偏右）和时间组（右）
 *   - 投影仪图标（36dp，#E6E6E6）
 *   - 弹性占位 View（weight=1）
 *   - WiFi 图标（40dp，#E6E6E6）
 *   - 弹性占位 View（weight=0.5）
 *   - 时间 TextClock（24sp，白色，自动 12/24 小时制）
 *   - 星期文字（16sp，100%白色）
 *   - 日期文字（16sp，100%白色）
 *
 * 通过广播监听时间变化（ACTION_TIME_TICK）、WiFi 信号变化（RSSI_CHANGED_ACTION）
 * 和网络连接状态变化（NETWORK_STATE_CHANGED_ACTION）
 */
class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val wifiIcon: ImageView
    private val dateText: TextView
    private val weekdayText: TextView
    private val timeText: TextClock
    private val connectivityManager: ConnectivityManager
    private var receiverRegistered = false

    /** 接收时间变化、WiFi信号变化和网络状态变化的广播 */
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK -> {
                    updateDateAndWeekday()
                }
                WifiManager.RSSI_CHANGED_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    updateWifi()
                }
            }
        }
    }

    init {
        connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM

        // ─── 投影仪图标 32x32dp，左上角固定 ───
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_projector)
            layoutParams = LayoutParams(context.dpToPx(44), context.dpToPx(41)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        })

        // ─── 弹性占位（左侧）— 把 WiFi 推到中间偏右 ───
        addView(android.view.View(context).apply {
            layoutParams = LayoutParams(0, 0, 1f)
        })

        // ─── WiFi 图标 40x40dp，居中靠右，默认隐藏 ───
        wifiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.parseColor("#E6E6E6"))
            visibility = GONE
            layoutParams = LayoutParams(context.dpToPx(40), context.dpToPx(40)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_VERTICAL
                leftMargin = context.dpToPx(48)
            }
        }
        addView(wifiIcon)

        // ─── 弹性占位（右侧）— 把时间组推右 ───
        addView(android.view.View(context).apply {
            layoutParams = LayoutParams(0, 0, 0.5f)
        })

        // ─── 时间 + 星期 + 日期（水平排列，底部基线对齐）───
        val timeGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        // 时间 24sp，自动根据系统设置切换12/24小时制
        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.BOTTOM
        }
        timeGroup.addView(timeText)

        // 星期 16sp，100%白色
        weekdayText = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.BOTTOM
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(16) }
        }
        timeGroup.addView(weekdayText)

        // 日期 16sp，100%白色
        dateText = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.BOTTOM
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(16) }
        }
        timeGroup.addView(dateText)

        addView(timeGroup)
    }

    /** 注册广播接收器，开始更新时间和WiFi */
    fun startListening() {
        updateDateAndWeekday()
        updateWifi()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(tickReceiver, filter)
        receiverRegistered = true
    }

    /** 注销广播接收器，停止更新 */
    fun stopListening() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(tickReceiver)
            } catch (e: Exception) {
                // 已被注销则忽略
            }
            receiverRegistered = false
        }
    }

    /** 更新星期和日期文字 */
    private fun updateDateAndWeekday() {
        val now = Date()
        weekdayText.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
        dateText.text = DateFormat.getDateFormat(context).format(now)
    }

    /**
     * 更新 WiFi 图标
     * 通过 ConnectivityManager 判断当前活跃网络是否为 WiFi，
     * 规避 H313 主板对 WifiInfo.networkId 的净化返回
     * - 已连接 WiFi 且为活跃网络：显示对应信号等级的图标
     * - 未连接或活跃网络非 WiFi：隐藏图标（GONE）
     */
    private fun updateWifi() {
        val activeNetwork = connectivityManager.activeNetwork ?: return run { wifiIcon.visibility = GONE }
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return run { wifiIcon.visibility = GONE }

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            wifiIcon.visibility = GONE
            return
        }

        val level = context.getWifiSignalLevel()
        wifiIcon.setImageResource(
            when (level) {
                0 -> R.drawable.ic_wifi_0
                1 -> R.drawable.ic_wifi_1
                2 -> R.drawable.ic_wifi_2
                3 -> R.drawable.ic_wifi_3
                else -> R.drawable.ic_wifi_4
            }
        )
        wifiIcon.visibility = VISIBLE
    }
}
