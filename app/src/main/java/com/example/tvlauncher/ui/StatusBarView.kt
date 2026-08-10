package com.example.tvlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
 * 顶部状态栏 — 显示投影仪图标、WiFi/蓝牙/HDMI 连接状态、时间、星期和日期
 *
 * 结构：水平 LinearLayout，投影仪图标固定左侧，weight 弹簧分隔连接图标组（中偏右）和时间组（右）
 *   - 投影仪图标（36dp，#E6E6E6）
 *   - 弹性占位 View（weight=1）
 *   - WiFi 图标（40dp，#E6E6E6）
 *   - 蓝牙图标（40dp，#E6E6E6）
 *   - HDMI 图标（40dp，#E6E6E6）
 *   - 弹性占位 View（weight=0.5）
 *   - 时间 TextClock（24sp，白色，自动 12/24 小时制）
 *   - 星期文字（16sp，100%白色）
 *   - 日期文字（16sp，100%白色）
 *
 * 通过广播监听时间变化（ACTION_TIME_TICK）、网络连接状态变化（NETWORK_STATE_CHANGED_ACTION）、
 * 蓝牙连接状态变化（ACTION_ACL_CONNECTED/ACTION_ACL_DISCONNECTED）和 HDMI 插拔广播（ACTION_HDMI_PLUGGED）
 */
class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val wifiIcon: ImageView
    private val ethernetIcon: ImageView
    private val bluetoothIcon: ImageView
    private val hdmiIcon: ImageView
    private val dateText: TextView
    private val weekdayText: TextView
    private val timeText: TextClock
    private val connectivityManager: ConnectivityManager
    private var receiverRegistered = false

    /** 以太网插拔无专用广播，用网络能力回调监听。回调在 ConnectivityThread，UI 更新须切回主线程 */
    private val ethernetCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            post { updateNetworkIcons() }
        }
        override fun onLost(network: android.net.Network) {
            post { updateNetworkIcons() }
        }
        override fun onCapabilitiesChanged(
            network: android.net.Network,
            caps: NetworkCapabilities
        ) {
            post { updateNetworkIcons() }
        }
    }

    private val bluetoothManager: BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter?

    /** 接收时间变化、WiFi/蓝牙/HDMI 连接状态变化的广播 */
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK -> {
                    updateDateAndWeekday()
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    updateNetworkIcons()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    updateBluetooth()
                }
                "android.intent.action.HDMI_PLUGGED" -> {
                    val state = intent.getBooleanExtra("state", false)
                    updateHdmi(state)
                }
            }
        }
    }

    init {
        connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        bluetoothManager = context.applicationContext
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM

        // ─── 科技感图标 32x32dp，左上角固定 ───
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_projector_sci)
            layoutParams = LayoutParams(context.dpToPx(44), context.dpToPx(41)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        })

        // ─── 弹性占位（左侧）— 把连接图标组推到中间偏右 ───
        addView(android.view.View(context).apply {
            layoutParams = LayoutParams(0, 0, 1f)
        })

        // ─── 蓝牙图标 40x40dp，默认隐藏 ───
        bluetoothIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.parseColor("#E6E6E6"))
            visibility = GONE
            layoutParams = LayoutParams(context.dpToPx(40), context.dpToPx(40)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = context.dpToPx(12)
            }
        }
        addView(bluetoothIcon)

        // ─── HDMI 图标 44x44dp，默认隐藏 ───
        hdmiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.parseColor("#E6E6E6"))
            visibility = GONE
            layoutParams = LayoutParams(context.dpToPx(44), context.dpToPx(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = context.dpToPx(12)
            }
        }
        addView(hdmiIcon)

        // ─── WiFi 图标 36x28dp，默认隐藏；与以太网互斥，同在 HDMI 右侧槽位 ───
        wifiIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.parseColor("#E6E6E6"))
            visibility = GONE
            layoutParams = LayoutParams(context.dpToPx(36), context.dpToPx(28)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = context.dpToPx(12)
            }
        }
        addView(wifiIcon)

        // ─── 以太网图标 28x28dp，默认隐藏；插上网线点亮，与 WiFi 互斥同槽位 ───
        ethernetIcon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(Color.parseColor("#E6E6E6"))
            visibility = GONE
            layoutParams = LayoutParams(context.dpToPx(28), context.dpToPx(28)).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = context.dpToPx(12)
            }
        }
        addView(ethernetIcon)

        // ─── 弹性占位（右侧）— 把时间组推右 ───
        addView(android.view.View(context).apply {
            layoutParams = LayoutParams(0, 0, 0.5f)
        })

        // ─── 时间 + 星期 + 日期（水平排列，垂直居中）───
        val timeGroup = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }

        // 时间 24sp，自动根据系统设置切换12/24小时制
        timeText = TextClock(context).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER_VERTICAL
        }
        timeGroup.addView(timeText)

        // 星期 16sp，100%白色
        weekdayText = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(16) }
        }
        timeGroup.addView(weekdayText)

        // 日期 16sp，100%白色
        dateText = TextView(context).apply {
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(16) }
        }
        timeGroup.addView(dateText)

        addView(timeGroup)
    }

    /** 注册广播接收器，开始更新时间和连接状态 */
    fun startListening() {
        updateDateAndWeekday()
        updateNetworkIcons()
        updateBluetooth()
        updateHdmi()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.intent.action.HDMI_PLUGGED")
        }
        context.registerReceiver(tickReceiver, filter)
        receiverRegistered = true
        // 以太网插拔监听
        try {
            connectivityManager.registerDefaultNetworkCallback(ethernetCallback)
        } catch (e: Exception) {
            // 注册失败则仅保留启动时的一次性状态
        }
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
        try {
            connectivityManager.unregisterNetworkCallback(ethernetCallback)
        } catch (e: Exception) {
            // 未注册则忽略
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
     * - 已连接 WiFi 且为活跃网络：点亮满格图标
     * - 未连接或活跃网络非 WiFi：隐藏图标（GONE）
     */
    private fun updateWifi() {
        val activeNetwork = connectivityManager.activeNetwork ?: return run { wifiIcon.visibility = GONE }
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return run { wifiIcon.visibility = GONE }

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            wifiIcon.visibility = GONE
            return
        }

        wifiIcon.setImageResource(R.drawable.ic_wifi_4)
        wifiIcon.visibility = VISIBLE
    }

    /**
     * 更新以太网图标 — 当前活跃网络为以太网(插了网线)时点亮，否则隐藏。
     * 与 WiFi 互斥：同一时刻活跃网络只有一种传输类型。
     */
    private fun updateEthernet() {
        val activeNetwork = connectivityManager.activeNetwork ?: return run { ethernetIcon.visibility = GONE }
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return run { ethernetIcon.visibility = GONE }

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            ethernetIcon.visibility = GONE
            return
        }

        ethernetIcon.setImageResource(R.drawable.ic_ethernet)
        ethernetIcon.visibility = VISIBLE
    }

    /**
     * 网络图标统一刷新 — WiFi 与以太网互斥，同一时刻只显示活跃网络对应的那个。
     * 任何网络变化(广播或 NetworkCallback)都同时刷新两者，避免一方状态残留导致双图标同显。
     */
    private fun updateNetworkIcons() {
        updateWifi()
        updateEthernet()
    }

    /** 更新蓝牙图标 — 有已连接的蓝牙 Profile 时点亮，否则隐藏 */
    private fun updateBluetooth() {
        if (bluetoothAdapter == null) {
            bluetoothIcon.visibility = GONE
            return
        }
        val profiles = intArrayOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET,
            BluetoothProfile.GATT
        )
        for (p in profiles) {
            @Suppress("DEPRECATION")
            val connected = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    bluetoothAdapter.getProfileConnectionState(p) == BluetoothProfile.STATE_CONNECTED
            } else {
                bluetoothAdapter.getProfileConnectionState(p) == BluetoothProfile.STATE_CONNECTED
            }
            if (connected) {
                bluetoothIcon.setImageResource(R.drawable.ic_bluetooth)
                bluetoothIcon.visibility = VISIBLE
                return
            }
        }
        bluetoothIcon.visibility = GONE
    }

    /**
     * 更新 HDMI 图标 — 收到 HDMI 插拔广播时点亮或隐藏。
     * 启动时无法主动查询 HDMI 状态，因此默认隐藏，等待广播。
     */
    private fun updateHdmi(state: Boolean = false) {
        if (state) {
            hdmiIcon.setImageResource(R.drawable.ic_hdmi)
            hdmiIcon.visibility = VISIBLE
        } else {
            hdmiIcon.visibility = GONE
        }
    }
}

