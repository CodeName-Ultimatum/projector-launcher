package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener
import com.bumptech.glide.Glide

/**
 * 启动器卡片视图 — 每张卡片包含：
 *   - 背景图片（从全局背景图裁剪的图块，或通过 setCardImageUrl 加载的整图）
 *   - 半透明彩色覆盖层（纯色或渐变，带 alpha 通道）
 *   - 聚焦时的白色矩形边框 + Google 原生双层阴影
 */
class LauncherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val overlayLayer: View
    private val borderView: View

    // 在 onSizeChanged 中初始化的轮廓半径（dp 转 px）
    private var outlineRadius = 0f

    var onCardClicked: (() -> Unit)? = null
    var onCardLongClicked: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        // 不裁切子视图，允许聚焦放大时边框超出卡片边界
        clipChildren = false
        clipToPadding = false

        // ─── Google 原生双层阴影（elevation + OutlineProvider） ───
        // spot 接触阴影 40% | ambient 环境阴影 12% | 比例 ≈ 3.3:1
        outlineSpotShadowColor = Color.argb(0x66, 0, 0, 0)   // rgba(0,0,0, 0.4) ≈ 0x66
        outlineAmbientShadowColor = Color.argb(0x1F, 0, 0, 0) // rgba(0,0,0, 0.12) ≈ 0x1F

        // 设置圆角轮廓，系统据此绘制阴影形状
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (outlineRadius > 0f) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height,
                        outlineRadius
                    )
                }
            }
        }
        // clipToOutline = false：阴影用轮廓形状，但背景图片/覆盖层不被裁切
        clipToOutline = false

        // ─── 第1层：半透明彩色覆盖层（位于背景图之上）───
        // 优先级低于 border，高于背景图
        overlayLayer = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(overlayLayer)

        // ─── 最上层：聚焦白色边框，默认隐藏 ───
        borderView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            val border = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // 3dp 纯白描边，紧贴卡片边缘
                setStroke(context.dpToPx(3), Color.WHITE)
            }
            background = border
            visibility = View.INVISIBLE
        }
        addView(borderView)

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

        // ─── 聚焦状态切换：放大动画 + 原生阴影 + 白色边框 ───
        onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 提升 Z 轴高度，触发原生双层阴影，同时防止放大后被相邻卡片遮挡
                elevation = context.dpToPx(12).toFloat()
                // 放大至110%，150ms弹性动画
                animate().scaleX(1.10f).scaleY(1.10f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                borderView.visibility = View.VISIBLE
            } else {
                // 恢复 Z 轴高度
                elevation = 0f
                // 恢复原始大小
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                borderView.visibility = View.INVISIBLE
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 初始化圆角轮廓半径：与卡片视觉圆角（8dp）一致，系统据此绘制阴影
        outlineRadius = context.dpToPx(8).toFloat()
        // 触发 outline 重新计算
        invalidateOutline()
    }

    // ─── 外部调用的设置方法 ───

    /** 设置卡片的背景裁剪图块 */
    fun setCardBackground(bitmap: Bitmap) {
        val bgDrawable = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.FILL
        }
        background = bgDrawable
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
                    background = resource
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
