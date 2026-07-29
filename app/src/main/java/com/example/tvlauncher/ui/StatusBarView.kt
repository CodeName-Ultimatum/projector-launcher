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
        setPadding(context.dpToPx(12), 0, context.dpToPx(12), 0)

        // 左侧图标占位
        val leftIcon = TextView(context).apply {
            text = ""
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(leftIcon)

        // WiFi 图标（替换原来的文字）
        wifiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LayoutParams(
                context.dpToPx(20), context.dpToPx(20)
            ).apply {
                rightMargin = context.dpToPx(8)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        addView(wifiIcon)

        // 时间：水平排列（TextClock | 星期 | 日期）
        val timeGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            )
        }

        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL
        }
        timeGroup.addView(timeText)

        weekdayText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(8) }
        }
        timeGroup.addView(weekdayText)

        dateText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(8) }
        }
        timeGroup.addView(dateText)

        addView(timeGroup)
    }

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

    fun stopListening() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(tickReceiver)
            } catch (e: Exception) {
                // already unregistered
            }
            receiverRegistered = false
        }
    }

    private fun updateDateAndWeekday() {
        val now = Date()
        weekdayText.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
        dateText.text = DateFormat.getDateFormat(context).format(now)
    }

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
