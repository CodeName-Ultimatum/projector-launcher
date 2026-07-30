package com.example.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
 * 结构：水平 LinearLayout，内容靠右排列
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
        gravity = Gravity.END or Gravity.BOTTOM
        // 整体向左移动 40dp
        setPadding(0, 0, context.dpToPx(40), 0)

        // ─── WiFi 图标 40x40dp（24dp * 5/3），靠左对齐 ───
        wifiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(context.dpToPx(40), context.dpToPx(40)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_VERTICAL
            }
        }
        addView(wifiIcon)

        // ─── 时间 + 星期 + 日期（水平排列，底部基线对齐）───
        val timeGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.BOTTOM
            }
        }

        // 时间 32sp，自动根据系统设置切换12/24小时制
        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.BOTTOM
        }
        timeGroup.addView(timeText)

        // 星期 16sp，80%白色
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
     * - 已连接：显示完整 WiFi 图标，100%不透明
     * - 断开连接：显示完整 WiFi 图标，40%透明度
     */
    private fun updateWifi() {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val isConnected = wifiInfo != null && wifiInfo.networkId != -1

        wifiIcon.setImageResource(R.drawable.ic_wifi_4)
        wifiIcon.alpha = if (isConnected) 1.0f else 0.4f
    }
}
