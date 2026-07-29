package com.example.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.getWifiSignalLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val wifiText: TextView
    private val dateText: TextView
    private val weekdayText: TextView
    private val timeText: TextClock
    private var receiverRegistered = false

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK ||
                intent?.action == android.net.wifi.WifiManager.RSSI_CHANGED_ACTION
            ) {
                updateDateAndWeekday()
                updateWifi()
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#CC1A1A2E"))
        setPadding(context.dpToPx(12), 0, context.dpToPx(12), 0)

        // Left icon placeholder
        val leftIcon = TextView(context).apply {
            text = ""
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(leftIcon)

        // WiFi signal
        wifiText = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = context.dpToPx(16)
            }
        }
        addView(wifiText)

        // Right-side time group (vertical)
        val timeGroup = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.END
        }

        weekdayText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.END
        }

        dateText = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.END
        }

        timeGroup.addView(timeText)
        timeGroup.addView(weekdayText)
        timeGroup.addView(dateText)
        addView(timeGroup)
    }

    fun startListening() {
        updateDateAndWeekday()
        updateWifi()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION)
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
        val level = context.getWifiSignalLevel()
        val bars = when (level) {
            0 -> "○"
            1 -> "◔"
            2 -> "◑"
            3 -> "◕"
            else -> "●"
        }
        wifiText.text = "WiFi $bars"
    }
}
