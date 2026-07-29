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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setFocusZoom
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener

class LauncherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val labelView: TextView
    private val overlayLayer: View
    private var contentContainer: LinearLayout? = null

    var onCardClicked: (() -> Unit)? = null
    var onCardLongClicked: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = true
        clipToPadding = true

        // Overlay layer on top of background
        overlayLayer = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(overlayLayer)

        // Icon and label
        iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = false
            isClickable = false
        }

        labelView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isFocusable = false
            isClickable = false
        }

        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS

        setSafeOnClickListener {
            onCardClicked?.invoke()
        }

        setSafeOnLongClickListener {
            onCardLongClicked?.invoke()
            true
        }

        setupVerticalLayout()

        onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setFocusZoom(1.05f)
            } else {
                setFocusZoom(1.0f)
            }
        }
    }

    fun setIconLayout(iconAbove: Boolean) {
        // Remove the old container (which holds iconView and labelView)
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

    private fun setupVerticalLayout() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
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
                topMargin = context.dpToPx(6)
            })
        addView(container)
        contentContainer = container
    }

    private fun setupHorizontalLayout() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(
            iconView,
            LinearLayout.LayoutParams(context.dpToPx(40), context.dpToPx(40))
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

    /** Set cropped background bitmap */
    fun setCardBackground(bitmap: Bitmap) {
        val bgDrawable = BitmapDrawable(resources, bitmap).apply {
            gravity = Gravity.FILL
        }
        background = bgDrawable
    }

    /** Set solid color overlay (with alpha) */
    fun setOverlayColor(color: Int) {
        val overlayDrawable = GradientDrawable().apply {
            setColor(color)
        }
        overlayLayer.background = overlayDrawable
    }

    /** Set gradient overlay */
    fun setOverlayGradient(
        startColor: Int,
        endColor: Int,
        orientation: GradientDrawable.Orientation
    ) {
        val overlayDrawable = GradientDrawable(orientation, intArrayOf(startColor, endColor))
        overlayLayer.background = overlayDrawable
    }

    /** Set app info (icon and text) */
    fun setAppInfo(info: AppRepository.AppInfo?) {
        if (info != null) {
            iconView.setImageDrawable(info.icon)
            labelView.text = info.label
        } else {
            iconView.setImageResource(R.drawable.ic_default_app)
            labelView.text = context.getString(R.string.no_app)
        }
    }

    /** Directly set the label text (for fixed-function cards) */
    fun setLabel(text: String) {
        labelView.text = text
    }

    /** Directly set the icon resource */
    fun setIconResource(resId: Int) {
        iconView.setImageResource(resId)
    }
}
