package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener

/**
 * 底部快捷栏 — 水平可滚动的应用快捷入口列表
 *
 * 结构：HorizontalScrollView > LinearLayout（浅蓝背景框 WRAP_CONTENT）
 *   - 每个快捷应用项：[应用图标 48x48dp]，蓝色底块，项间 6dp 间隙露出主界面背景
 *   - 末尾：+ 按钮（点击展开应用面板,由 MainActivity 处理）
 *   - 聚焦时显示白色描边
 *   - 数据通过 QuickAppsStore 持久化到 SharedPreferences
 *   - 删除快捷应用统一在应用面板内按 OK 切换
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
        // 不裁切子视图,让聚焦放大的应用项/阴影能越界显示
        clipChildren = false
        clipToPadding = false
        // QuickBar 自身不获焦，焦点给子项
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        // 内部水平容器，垂直居中，背景透明（蓝色背景块移到每个应用项上，项间间隙透出主界面背景）
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            // 不裁切子视图,让聚焦放大的应用项/阴影能越界显示
            clipChildren = false
            clipToPadding = false
        }
        addView(
            container,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                context.dpToPx(74)
            ).apply {
                // 18 + 每项 6dp 左边距 = 首项距屏左 24dp
                leftMargin = context.dpToPx(18)
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
            // 每项左侧留 6dp 间隙（间隙透明，露出主界面背景）
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(6) }
        }

        // 正常状态：蓝底透明态；聚焦状态：蓝底 + 白色描边
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#FF354D96"))
            cornerRadius = 0f
        }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#FF354D96"))
            cornerRadius = 0f
            setStroke(context.dpToPx(2), Color.WHITE)
        }
        item.background = normalBg

        // 聚焦阴影:圆角 outline + elevation,与卡片阴影观感一致
        item.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, context.dpToPx(8).toFloat())
            }
        }
        item.outlineSpotShadowColor = Color.argb(0xFF, 0, 0, 0)   // 纯黑
        item.outlineAmbientShadowColor = Color.argb(0xE6, 0, 0, 0) // 0.9
        item.clipToOutline = false

        // 应用图标 48x48dp，无文字标签
        // 复制 Drawable:图标实例可能被面板格子/应用列表共享,AdaptiveIconDrawable 有可变状态,
        // 多个 ImageView 复用同一实例会因 setBounds 竞争导致图案闪烁/消失
        val icon = ImageView(context).apply {
            setImageDrawable(info.icon.constantState?.newDrawable() ?: info.icon)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
            isFocusable = false
        }
        item.addView(icon)

        item.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            item.background = if (hasFocus) focusedBg else normalBg
            if (hasFocus) {
                item.elevation = context.dpToPx(12).toFloat()
                item.animate().scaleX(1.10f).scaleY(1.10f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            } else {
                item.elevation = 0f
                item.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }

        // 点击启动应用
        item.setSafeOnClickListener {
            onAppSelected?.invoke(info.packageName)
        }

        return item
    }

    /** 将焦点落到末尾的 + 添加按钮（收起面板刷新后调用,替代失效的旧视图引用） */
    fun requestFocusOnAddButton() {
        val addBtn = container.getChildAt(container.childCount - 1) ?: return
        addBtn.requestFocus()
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
            // 与前面的应用项之间留 6dp 间隙
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(6) }
        }

        // 蓝底 + 聚焦白框（与应用项一致）
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#FF354D96"))
            cornerRadius = 0f
        }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#FF354D96"))
            cornerRadius = 0f
            setStroke(context.dpToPx(2), Color.WHITE)
        }
        addBtn.background = normalBg

        // 聚焦阴影:圆角 outline + elevation,与应用项一致
        addBtn.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, context.dpToPx(8).toFloat())
            }
        }
        addBtn.outlineSpotShadowColor = Color.argb(0xFF, 0, 0, 0)   // 纯黑
        addBtn.outlineAmbientShadowColor = Color.argb(0xE6, 0, 0, 0) // 0.9
        addBtn.clipToOutline = false

        // + 图标 48x48dp
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(48))
            isFocusable = false
        }
        addBtn.addView(icon)

        addBtn.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            addBtn.background = if (hasFocus) focusedBg else normalBg
            if (hasFocus) {
                addBtn.elevation = context.dpToPx(12).toFloat()
                addBtn.animate().scaleX(1.10f).scaleY(1.10f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            } else {
                addBtn.elevation = 0f
                addBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }

        addBtn.setSafeOnClickListener {
            onAddRequested?.invoke()
        }

        return addBtn
    }
}
