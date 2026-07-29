package com.example.tvlauncher.util

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.View

/** dp 转 px（像素），根据设备屏幕密度自动换算 */
fun Context.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

/** 获取当前 WiFi 信号等级（0-5） */
fun Context.getWifiSignalLevel(): Int {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val rssi = wifiManager?.connectionInfo?.rssi ?: -100
    return WifiManager.calculateSignalLevel(rssi, 5)
}

/**
 * 视图聚焦缩放动画
 * @param scale 目标缩放比例（如 1.05f = 放大至105%）
 * @param duration 动画时长（毫秒），默认150ms
 */
fun View.setFocusZoom(scale: Float, duration: Long = 150L) {
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}

/**
 * 设置安全点击监听器（300ms防抖）
 * 防止遥控器快速连按导致多次触发
 */
fun View.setSafeOnClickListener(action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { view ->
        val elapsed = SystemClock.elapsedRealtime() - lastClickTime
        if (elapsed > 300) {
            lastClickTime = SystemClock.elapsedRealtime()
            action(view)
        }
    }
}

/** 设置长按监听器 */
fun View.setSafeOnLongClickListener(action: (View) -> Boolean) {
    setOnLongClickListener { view -> action(view) }
}

/**
 * 从 Bitmap 中裁剪指定区域
 * 自动将坐标和尺寸约束在有效范围内，防止越界崩溃
 */
fun Bitmap.cropRegion(x: Int, y: Int, w: Int, h: Int): Bitmap {
    return Bitmap.createBitmap(
        this,
        x.coerceIn(0, width - 1),
        y.coerceIn(0, height - 1),
        w.coerceAtMost(width - x),
        h.coerceAtMost(height - y)
    )
}
