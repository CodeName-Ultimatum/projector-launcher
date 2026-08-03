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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener
import com.bumptech.glide.Glide

/**
 * 启动器卡片视图 — 每张卡片包含：
 *   - 背景图片（从全局背景图裁剪的图块）
 *   - 半透明彩色覆盖层（纯色或渐变，带 alpha 通道）
 *   - 应用图标 + 应用名称
 *   - 聚焦时的白色矩形边框 + Google 原生双层阴影
 *
 * 支持两种布局方向：
 *   - 竖卡（iconAbove=true）：图标在上，文字在下
 *   - 横卡（iconAbove=false）：图标在左，文字在右
 */
class LauncherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val labelView: TextView
    private val overlayLayer: View
    private val borderView: View
    private var contentContainer: LinearLayout? = null

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
        // clipToOutline = false：阴影用轮廓形状，但内容（图标/文字）不被裁切
        clipToOutline = false

        // ─── 第1层：半透明彩色覆盖层（位于背景图之上）───
        // 优先级低于 icon/label/border，高于背景图
        overlayLayer = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(overlayLayer)

        // ─── 应用图标 ───
        iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isClickable = false
        }

        // ─── 应用名称 ───
        labelView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isFocusable = false
            isClickable = false
        }

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

        // 默认使用竖卡布局
        setupVerticalLayout()

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

    /**
     * 手动切换图标与文字的排列方向
     * @param iconAbove true=图标在上方（竖卡），false=图标在左侧（横卡）
     */
    fun setIconLayout(iconAbove: Boolean) {
        // 解除当前容器绑定
        (iconView.parent as? android.view.ViewGroup)?.removeView(iconView)
        (labelView.parent as? android.view.ViewGroup)?.removeView(labelView)
        contentContainer?.let { removeView(it) }
        contentContainer = null

        if (iconAbove) {
            setupVerticalLayout()
        } else {
            setupHorizontalLayout()
        }
    }

    // ─── 竖卡布局：图标在上（56x56dp），文字在下 ───
    private fun setupVerticalLayout() {
        val margin = context.dpToPx(14)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                // 内容四周边距，替代 FrameLayout 的 setPadding
                leftMargin = margin
                topMargin = margin
                rightMargin = margin
                bottomMargin = margin
            }
        }
        container.addView(
            iconView,
            LinearLayout.LayoutParams(context.dpToPx(56), context.dpToPx(56))
        )
        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dpToPx(8)
            })
        addView(container)
        contentContainer = container
    }

    // ─── 横卡布局：图标在左（48x48dp），文字在右 ───
    private fun setupHorizontalLayout() {
        val margin = context.dpToPx(14)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                // 内容四周边距，替代 FrameLayout 的 setPadding
                leftMargin = margin
                topMargin = margin
                rightMargin = margin
                bottomMargin = margin
            }
        }
        container.addView(
            iconView,
            LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
        )
        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = context.dpToPx(12)
            })
        addView(container)
        contentContainer = container
    }

    // ─── 外部调用的设置方法 ───

    /** 设置卡片的背景裁剪图块 */
    fun setCardBackground(bitmap: Bitmap) {
        val bgDrawable = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.FILL
        }
        background = bgDrawable
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

    /** 设置应用信息（图标 + 名称），传入null时显示默认占位图标 */
    fun setAppInfo(info: AppRepository.AppInfo?) {
        if (info != null) {
            iconView.setImageDrawable(info.icon)
            labelView.text = info.label
        } else {
            iconView.setImageResource(R.drawable.ic_default_app)
            labelView.text = context.getString(R.string.no_app)
        }
    }

    /** 直接设置文字（用于固定功能卡片） */
    fun setLabel(text: String) {
        labelView.text = text
    }

    /** 直接设置图标资源 */
    fun setIconResource(resId: Int) {
        iconView.setImageResource(resId)
    }

    /** 使用 Glide 加载网络图标 */
    fun setIconUrl(url: String) {
        Glide.with(context)
            .load(url)
            .into(iconView)
    }
}
