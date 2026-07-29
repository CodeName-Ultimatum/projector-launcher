package com.example.tvlauncher.util

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.View

fun Context.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun Context.getWifiSignalLevel(): Int {
    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val rssi = wifiManager?.connectionInfo?.rssi ?: -100
    return WifiManager.calculateSignalLevel(rssi, 5)
}

fun View.setFocusZoom(scale: Float, duration: Long = 150L) {
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}

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

fun View.setSafeOnLongClickListener(action: (View) -> Boolean) {
    setOnLongClickListener { view -> action(view) }
}

fun Bitmap.cropRegion(x: Int, y: Int, w: Int, h: Int): Bitmap {
    return Bitmap.createBitmap(
        this,
        x.coerceIn(0, width - 1),
        y.coerceIn(0, height - 1),
        w.coerceAtMost(width - x),
        h.coerceAtMost(height - y)
    )
}
