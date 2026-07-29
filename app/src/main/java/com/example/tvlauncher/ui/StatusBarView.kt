package com.example.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部状态栏 — 显示 WiFi 信号强度、时间、星期和日期
 *
 * 结构：水平 LinearLayout，两端分布
 *   - 左侧无内容（预留位置）
 *   - WiFi 图标（80x80dp，白色着色）
 *   - 时间 TextClock（56sp，白色，自动 12/24 小时制）
 *   - 星期文字（40sp，80%白色）
 *   - 日期文字（40sp，80%白色）
 *
 * 通过广播监听时间变化（ACTION_TIME_TICK）和 WiFi 信号变化（RSSI_CHANGED_ACTION）
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
    private var receiverRegistered = false

    /** 接收时间变化和WiFi信号变化的广播 */
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK ||
                intent?.action == WifiManager.RSSI_CHANGED_ACTION
            ) {
                updateDateAndWeekday()
                updateWifi()
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // 上下各24dp内边距，左右各24dp
        setPadding(context.dpToPx(24), context.dpToPx(24), context.dpToPx(24), context.dpToPx(24))

        // ─── WiFi 图标 80x80dp ───
        wifiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LayoutParams(context.dpToPx(80), context.dpToPx(80)).apply {
                rightMargin = context.dpToPx(28)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        addView(wifiIcon)

        // ─── 时间 + 星期 + 日期（水平排列）───
        val timeGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        // 时间 56sp，自动根据系统设置切换12/24小时制
        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 56f)
            gravity = Gravity.CENTER_VERTICAL
        }
        timeGroup.addView(timeText)

        // 星期 40sp，80%白色（#CCFFFFFF）
        weekdayText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 40f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(20) }
        }
        timeGroup.addView(weekdayText)

        // 日期 40sp，80%白色（#CCFFFFFF）
        dateText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 40f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(20) }
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
     * - 已连接：显示信号等级 ic_wifi_0~4，全白着色
     * - 断开连接：显示 ic_wifi_0，40%白色着色
     */
    private fun updateWifi() {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val isConnected = wifiInfo != null && wifiInfo.networkId != -1

        if (!isConnected) {
            wifiIcon.setImageResource(R.drawable.ic_wifi_0)
            wifiIcon.setColorFilter(
                Color.parseColor("#66FFFFFF"),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            val level = WifiManager.calculateSignalLevel(wifiInfo!!.rssi, 5)
            val iconRes = when (level) {
                0 -> R.drawable.ic_wifi_0
                1 -> R.drawable.ic_wifi_1
                2 -> R.drawable.ic_wifi_2
                3 -> R.drawable.ic_wifi_3
                else -> R.drawable.ic_wifi_4
            }
            wifiIcon.setImageResource(iconRes)
            wifiIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
    }
}
