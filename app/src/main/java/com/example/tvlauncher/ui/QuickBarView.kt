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
import android.widget.TextView
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

        // 内部水平容器，垂直居中，背景透明（蓝色背景块移到每个应用项上，项间间隙透出主界面背景）
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        addView(
            container,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                context.dpToPx(68)
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
        // 不带主题创建 Dialog,窗口包裹内容并居中,用系统 dim 蒙层覆盖窗口外区域(含四角)
        val dlg = android.app.Dialog(context)

        // 深色面板(与主界面应用选择弹窗一致):深灰底 + 细描边,四角用系统 dim 蒙层压灰
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3E4349"))
                setStroke(context.dpToPx(1), Color.parseColor("#4E5359"))
            }
            setPadding(context.dpToPx(28), context.dpToPx(20), context.dpToPx(28), context.dpToPx(20))
            layoutParams = ViewGroup.LayoutParams(
                context.dpToPx(620),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题:应用名
        panel.addView(TextView(context).apply {
            text = info.label
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, context.dpToPx(14))
        })

        // 消息
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.confirm_remove)
            setTextColor(Color.parseColor("#F2F5F9"))
            textSize = 17f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, context.dpToPx(20))
        })

        // 底部按钮行:确定 | 取消
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setBackgroundColor(Color.TRANSPARENT)
        }
        val okBtn = createDialogButton(context.getString(android.R.string.ok)) {
            store?.removeQuickApp(info.packageName)
            refresh()
            dlg.dismiss()
        }
        buttonRow.addView(okBtn)
        buttonRow.addView(
            createDialogButton(context.getString(android.R.string.cancel)) {
                dlg.dismiss()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = context.dpToPx(12) }
        )
        panel.addView(buttonRow)

        dlg.setContentView(panel)
        dlg.window?.apply {
            decorView.setBackgroundColor(Color.TRANSPARENT)
            setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(android.view.Gravity.CENTER)
            setDimAmount(0.4f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dlg.show()
        // 默认焦点落在"确定",白色描边提示用户按 OK 即可确认
        okBtn.requestFocus()
    }

    /** 深色底块按钮:聚焦时白描边 + 白字,失焦时灰字 */
    private fun createDialogButton(text: String, onClick: () -> Unit): TextView {
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2A3442"))
            setStroke(context.dpToPx(2), Color.TRANSPARENT)
        }
        return TextView(context).apply {
            this.text = text
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(context.dpToPx(28), context.dpToPx(12), context.dpToPx(28), context.dpToPx(12))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            background = normalBg
            setTextColor(Color.parseColor("#8A94A6"))
            setSafeOnClickListener { onClick() }
            onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
                val bg = view.background as GradientDrawable
                bg.setStroke(context.dpToPx(2), if (hasFocus) Color.WHITE else Color.TRANSPARENT)
                setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#8A94A6"))
            }
        }
    }
}
