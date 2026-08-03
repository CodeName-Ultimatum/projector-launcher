package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener

/**
 * 底部快捷栏 — 水平可滚动的应用快捷入口列表
 *
 * 结构：HorizontalScrollView > LinearLayout（浅蓝背景框 WRAP_CONTENT）
 *   - 每个快捷应用项：[应用图标 48x48dp]
 *   - 末尾：+ 按钮
 *   - 聚焦时显示白色描边
 *   - 长按弹出删除确认对话框
 *   - 数据通过 QuickAppsStore 持久化到 SharedPreferences
 *   - 背景框随添加应用数量自动变长
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

        // 内部水平容器，垂直居中，浅蓝背景框随内容变长
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF354D96"))
                cornerRadius = 0f
            }
        }
        addView(
            container,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                context.dpToPx(68)
            ).apply {
                leftMargin = context.dpToPx(24)
            }
        )
    }

    /**
     * 绑定数据源并刷新UI
     * @param store 快捷应用数据存储
     * @param repo  应用信息仓库（查询图标）
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

        // 为每个已保存的包名创建纯图标应用视图
        val packages = quickStore.getQuickApps()
        val stale = mutableListOf<String>()
        for (pkg in packages) {
            val appInfo = appRepo.getAppInfo(pkg)
            if (appInfo != null) {
                container.addView(createAppItemView(appInfo))
            } else {
                // 应用已被卸载，收集起来在刷新结束后统一清理
                stale.add(pkg)
            }
        }
        stale.forEach { quickStore.removeQuickApp(it) }

        // + 按钮始终在末尾，位于背景框内
        container.addView(createAddButton())
    }

    /**
     * 创建单个快捷应用视图（仅图标，无文字）
     */
    private fun createAppItemView(info: AppRepository.AppInfo): View {
        val padding = context.dpToPx(10)

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        // 正常状态：透明背景；聚焦状态：白色描边
        val normalBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(context.dpToPx(2), Color.WHITE)
        }
        item.background = normalBg

        // 应用图标 48x48dp，无文字标签
        val icon = ImageView(context).apply {
            setImageDrawable(info.icon)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
            isFocusable = false
        }
        item.addView(icon)

        item.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            item.background = if (hasFocus) focusedBg else normalBg
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
     * 创建 + 添加按钮，位于背景框末尾
     * 尺寸与应用图标一致（48x48dp），保持整体和谐
     */
    private fun createAddButton(): View {
        val padding = context.dpToPx(10)

        val addBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }

        // 聚焦白框
        val normalBg = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(context.dpToPx(2), Color.WHITE)
        }
        addBtn.background = normalBg

        // + 图标 48x48dp
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
            isFocusable = false
        }
        addBtn.addView(icon)

        addBtn.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            addBtn.background = if (hasFocus) focusedBg else normalBg
        }

        addBtn.setSafeOnClickListener {
            onAddRequested?.invoke()
        }

        return addBtn
    }

    /** 弹出对话框确认删除快捷应用 */
    private fun showRemoveDialog(info: AppRepository.AppInfo) {
        android.app.AlertDialog.Builder(context, R.style.DarkDialogTheme)
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
