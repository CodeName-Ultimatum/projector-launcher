package com.example.tvlauncher.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 深色提示浮层 — 替代 Toast（API 30+ 自定义 Toast 被系统强制为标准样式）
 *
 * 在 Activity 的根 DecorView 上叠加一个居中的深灰圆角浮层，自动消失。
 * 与深色主题统一，不受系统 Toast 样式影响。
 */
private var overlayRef: View? = null

fun Context.showDarkToast(message: String) {
    val activity = this as? Activity ?: return
    val decor = activity.window.decorView as? FrameLayout ?: return

    // 移除旧的浮层，避免叠加
    overlayRef?.let { decor.removeView(it) }
    overlayRef = null

    val overlay = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dpToPx(24), dpToPx(14), dpToPx(24), dpToPx(14))
        // 纯白背景 + 黑字（经典 Toast 样式）
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dpToPx(12).toFloat()
        }
    }

    overlay.addView(TextView(this).apply {
        text = message
        setTextColor(Color.BLACK)
        textSize = 15f
        maxLines = 2
        // 必须显式透明背景，否则继承主题的窗口背景（main_bg 深蓝）形成文字下的蓝块
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    })

    // 点击浮层可立即关闭
    overlay.setOnClickListener { dismissOverlay(decor) }

    // 定位到屏幕底部（避开主页彩色卡片区域），居中
    val lp = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    )
    lp.bottomMargin = dpToPx(24)
    decor.addView(overlay, lp)
    overlayRef = overlay

    // 2秒后自动消失
    Handler(Looper.getMainLooper()).postDelayed({
        dismissOverlay(decor)
    }, 2000L)
}

/** 显示深色提示浮层（资源字符串重载） */
fun Context.showDarkToast(resId: Int) {
    showDarkToast(getString(resId))
}

private fun dismissOverlay(decor: ViewGroup) {
    overlayRef?.let {
        if (it.parent === decor) decor.removeView(it)
    }
    overlayRef = null
}

/** 供 Activity 在 onDestroy/onStop 时清理，避免泄漏 */
fun Context.dismissDarkToastOverlay() {
    val activity = this as? Activity ?: return
    val decor = activity.window.decorView as? FrameLayout ?: return
    dismissOverlay(decor)
}
