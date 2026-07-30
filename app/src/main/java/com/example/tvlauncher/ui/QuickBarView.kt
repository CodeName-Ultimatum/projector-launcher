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

/**
 * 底部快捷栏 — 水平可滚动的应用快捷入口列表
 *
 * 结构：HorizontalScrollView > LinearLayout（水平容器）
 *   - 每个快捷应用项：[应用图标 48x48dp] [应用名称 16sp]
 *   - 末尾：+ 按钮（添加应用到快捷栏）
 *   - 聚焦时缩放至110%
 *   - 长按弹出删除确认对话框
 *   - 数据通过 QuickAppsStore 持久化到 SharedPreferences
 */
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
        // 隐藏滚动条，禁用过度滚动效果
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        // QuickBar 自身不获焦，焦点给子项
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        // 内部水平容器，垂直居中
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        addView(
            container,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * 绑定数据源并刷新UI
     * @param store 快捷应用数据存储
     * @param repo  应用信息仓库（查询图标/名称）
     */
    fun bind(store: QuickAppsStore, repo: AppRepository) {
        this.store = store
        this.repo = repo
        refresh()
    }

    /** 清空并重建所有快捷应用项，末尾追加+按钮 */
    fun refresh() {
        val quickStore = store ?: return
        val appRepo = repo ?: return

        container.removeAllViews()

        // 为每个已保存的包名创建应用视图
        val packages = quickStore.getQuickApps()
        for (pkg in packages) {
            val appInfo = appRepo.getAppInfo(pkg)
            if (appInfo != null) {
                container.addView(createAppItemView(appInfo))
            }
        }

        // + 按钮始终在末尾
        container.addView(createAddButton())
    }

    /**
     * 创建单个快捷应用视图
     * 布局：水平排列 [图标 48x48dp] [名称 16sp]
     */
    private fun createAppItemView(info: AppRepository.AppInfo): View {
        // 每个应用项是一个水平LinearLayout，内边距10dp
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dpToPx(10), context.dpToPx(10), context.dpToPx(10), context.dpToPx(10))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            // 子视图不单独获焦
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        // 应用图标 48x48dp
        val icon = ImageView(context).apply {
            setImageDrawable(info.icon)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
            isFocusable = false
        }
        item.addView(icon)

        // 应用名称 16sp，白色，单行省略
        val label = TextView(context).apply {
            text = info.label
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(8) }
            isFocusable = false
        }
        item.addView(label)

        // 聚焦：缩放至110%
        item.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) item.setFocusZoom(1.1f) else item.setFocusZoom(1.0f)
        }

        // 点击启动应用
        item.setSafeOnClickListener {
            onAppSelected?.invoke(info.packageName)
        }

        // 长按弹出删除确认
        item.setSafeOnLongClickListener {
            showRemoveDialog(info)
            true
        }

        return item
    }

    /**
     * 创建右侧 + 添加按钮
     * 点击时触发 onAddRequested 回调（弹出应用选择对话框）
     */
    private fun createAddButton(): View {
        val addBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dpToPx(12), context.dpToPx(10), context.dpToPx(12), context.dpToPx(10))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        // + 图标 48x48dp
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
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

    /** 弹出对话框确认删除快捷应用 */
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
