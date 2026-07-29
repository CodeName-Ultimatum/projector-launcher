package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setFocusZoom
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener

class QuickBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout
    private var store: QuickAppsStore? = null
    private var repo: AppRepository? = null

    var onAppSelected: ((String) -> Unit)? = null
    var onAddRequested: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.parseColor("#CC0D1117"))
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dpToPx(8), 0, context.dpToPx(8), 0)
        }
        addView(
            container,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun bind(store: QuickAppsStore, repo: AppRepository) {
        this.store = store
        this.repo = repo
        refresh()
    }

    fun refresh() {
        val quickStore = store ?: return
        val appRepo = repo ?: return

        container.removeAllViews()

        // Add app views for each saved package name
        val packages = quickStore.getQuickApps()
        for (pkg in packages) {
            val appInfo = appRepo.getAppInfo(pkg)
            if (appInfo != null) {
                container.addView(createAppItemView(appInfo))
            }
        }

        // + button always at the end
        container.addView(createAddButton())
    }

    private fun createAppItemView(info: AppRepository.AppInfo): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dpToPx(6), context.dpToPx(4), context.dpToPx(6), context.dpToPx(4))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        val icon = ImageView(context).apply {
            setImageDrawable(info.icon)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(28), context.dpToPx(28))
            isFocusable = false
        }
        item.addView(icon)

        val label = TextView(context).apply {
            text = info.label
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(4) }
            isFocusable = false
        }
        item.addView(label)

        // Focus effect
        item.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) item.setFocusZoom(1.1f) else item.setFocusZoom(1.0f)
        }

        item.setSafeOnClickListener {
            onAppSelected?.invoke(info.packageName)
        }

        item.setSafeOnLongClickListener {
            showRemoveDialog(info)
            true
        }

        return item
    }

    private fun createAddButton(): View {
        val addBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dpToPx(8), context.dpToPx(4), context.dpToPx(8), context.dpToPx(4))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(28), context.dpToPx(28))
            isFocusable = false
        }
        addBtn.addView(icon)

        addBtn.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) addBtn.setFocusZoom(1.1f) else addBtn.setFocusZoom(1.0f)
        }

        addBtn.setSafeOnClickListener {
            onAddRequested?.invoke()
        }

        return addBtn
    }

    private fun showRemoveDialog(info: AppRepository.AppInfo) {
        android.app.AlertDialog.Builder(context)
            .setTitle(info.label)
            .setMessage(R.string.confirm_remove)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                store?.removeQuickApp(info.packageName)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
