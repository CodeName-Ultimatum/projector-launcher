package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener
import com.bumptech.glide.Glide

/**
 * 启动器卡片视图 — 继承 CardView 提供聚焦阴影，内部 contentView 承载内容：
 *   - 背景图片（从全局背景图裁剪的图块，或通过 setCardImageUrl 加载的整图）
 *   - 半透明彩色覆盖层（纯色或渐变，带 alpha 通道）
 *   - 聚焦时的白色矩形边框 + CardView 原生阴影（仅聚焦时 elevation 8dp）
 *
 * 阴影圆角 8dp，内容保持直角（clipToOutline = false），与旧版自定义阴影观感一致。
 */
class LauncherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val contentView: FrameLayout
    private val overlayLayer: View
    private val borderView: View

    // 聚焦时卡片放大比例
    private val focusScale = 1.10f

    var onCardClicked: (() -> Unit)? = null
    var onCardLongClicked: (() -> Unit)? = null
    /** 焦点变化回调：true=获焦, false=失焦。供 MainActivity 做 Z 轴抬升 */
    var onCardFocusChanged: ((Boolean) -> Unit)? = null

    init {
        // 阴影只画在卡片四周，圆角 8dp；内容不被裁切，保持直角矩形
        radius = context.dpToPx(8).toFloat()
        clipToOutline = false
        // CardView 的背景透明；阴影颜色调深，让聚焦阴影更明显
        setCardBackgroundColor(Color.TRANSPARENT)
        outlineSpotShadowColor = Color.argb(0xFF, 0, 0, 0)   // rgba(0,0,0,1.0) 接近纯黑
        outlineAmbientShadowColor = Color.argb(0x80, 0, 0, 0) // rgba(0,0,0,0.5)
        // API 28+ 阴影绘制在视图边界外，父容器已 clipChildren=false，无需 compat padding

        // 不裁切子视图，允许聚焦放大时边框超出卡片边界
        clipChildren = false
        clipToPadding = false

        // ─── contentView：承载 背景图 → 覆盖层 → 白框 三层 ───
        contentView = FrameLayout(context).apply {
            // 背景图/覆盖层由外部方法设置，这里仅建立结构
        }
        addView(contentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // ─── 第1层：半透明彩色覆盖层（位于背景图之上）───
        overlayLayer = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        contentView.addView(overlayLayer)

        // ─── 最上层：聚焦白色边框，默认隐藏 ───
        borderView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            val border = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // 3dp 纯白描边，紧贴卡片边缘
                setStroke(context.dpToPx(3), Color.WHITE)
            }
            background = border
            visibility = View.INVISIBLE
        }
        contentView.addView(borderView)

        // ─── 卡片的交互配置 ───
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // 阻止子视图抢焦点，卡片整体作为一个获焦单元
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS

        // 点击 / 长按（带300ms防抖）
        setSafeOnClickListener {
            onCardClicked?.invoke()
        }

        setSafeOnLongClickListener {
            onCardLongClicked?.invoke()
            true
        }

        // ─── 聚焦状态切换：放大动画 + CardView 阴影 + 白色边框 ───
        onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            onCardFocusChanged?.invoke(hasFocus)
            if (hasFocus) {
                // 提升 CardView 阴影高度，触发原生双层阴影
                cardElevation = context.dpToPx(12).toFloat()
                // 放大至110%，150ms弹性动画
                animate().scaleX(focusScale).scaleY(focusScale).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                borderView.visibility = View.VISIBLE
            } else {
                // 恢复 CardView 阴影高度
                cardElevation = 0f
                // 恢复原始大小
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                borderView.visibility = View.INVISIBLE
            }
        }
    }

    // ─── 外部调用的设置方法 ───

    /** 设置卡片的背景裁剪图块 */
    fun setCardBackground(bitmap: Bitmap) {
        val bgDrawable = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.FILL
        }
        contentView.background = bgDrawable
    }

    /** 使用 Glide 加载整卡图片到卡片背景,替代背景图块 */
    fun setCardImageUrl(url: String) {
        Glide.with(context)
            .load(url)
            .centerCrop()
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                ) {
                    contentView.background = resource
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // 不清空现有背景
                }
            })
    }

    /** 设置纯色覆盖层 */
    fun setOverlayColor(color: Int) {
        val overlayDrawable = GradientDrawable().apply {
            setColor(color)
        }
        overlayLayer.background = overlayDrawable
    }

    /** 设置渐变覆盖层 */
    fun setOverlayGradient(
        startColor: Int,
        endColor: Int,
        orientation: GradientDrawable.Orientation
    ) {
        val overlayDrawable = GradientDrawable(orientation, intArrayOf(startColor, endColor))
        overlayLayer.background = overlayDrawable
    }
}
