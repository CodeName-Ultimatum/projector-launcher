package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.R
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setSafeOnClickListener

/**
 * 应用面板 — 常用应用添加/删除器
 *
 * 平时被卡片区覆盖,点击底部快捷栏"+"后卡片区与状态栏上移,露出本面板。
 * 2行横向排(每行从左到右),面板上下滑动可浏览更多应用。
 * 每格:应用图标在上 + 名称在下。
 * 按OK切换"加入/移出"底部常用应用栏,已加入的格子右上角显示绿色勾选角标。
 */
class AppPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 目标格子尺寸(dp):自适应列数使每格尽量接近此宽度 */
    private val targetCellSize = 108

    /** 面板展开时的勾选回调,参数为(包名, 是否已加入常用栏) */
    var onToggle: ((String, Boolean) -> Unit)? = null

    /** 面板高度回调:两排正方形格子算出的面板需要高度(px),供 MainActivity 动态设置 panel_container 高度 */
    var onPanelHeight: ((Int) -> Unit)? = null

    private var store: QuickAppsStore? = null
    private var apps = listOf<AppRepository.AppInfo>()
    private val addedPackages = mutableSetOf<String>()
    private lateinit var recyclerView: RecyclerView
    private var currentSpanCount = 6

    /** 面板展开时的上下边缘阴影带(展开显示,收起隐藏) */
    private var shadowTop: View? = null
    private var shadowBottom: View? = null

    init {
        // 面板自身不获焦,焦点给格子;收起时阻止子项获焦
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        // 面板根透明,露出 panel_container 的 panel_bg 底色
        setBackgroundColor(Color.TRANSPARENT)
        // 允许阴影带越界绘制到面板边缘之外(面板外向上/向下扩散)
        clipChildren = false
        clipToPadding = false

        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, currentSpanCount, RecyclerView.VERTICAL, false)
            adapter = AppPanelAdapter()
            setHasFixedSize(true)
            // 关闭默认 item 动画:格子自带 scale 动画,默认 animator 在焦点/刷新时叠加动画导致卡顿
            itemAnimator = null
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            // RecyclerView 透明,不盖住面板底色
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 宽度确定后自适应列数:目标格宽 targetCellSize,格子=可用宽/列数(正方形)
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val widthDp = (r - l) / resources.displayMetrics.density
                val span = (widthDp / targetCellSize).toInt().coerceAtLeast(1)
                val cellPx = (r - l) / span
                if (span != currentSpanCount) {
                    currentSpanCount = span
                    (layoutManager as GridLayoutManager).spanCount = span
                    adapter?.notifyDataSetChanged()
                }
                // 面板高度 = 两排正方形格子高度
                onPanelHeight?.invoke(cellPx * 2)
            }
        }
        addView(recyclerView)

        // 面板上下边缘阴影带:展开时显示,从面板边缘向外扩散,营造面板从底部浮出的层次感
        shadowTop = createShadowBar(isTop = true)
        shadowBottom = createShadowBar(isTop = false)
    }

    /**
     * 创建一条阴影带 View 并添加到面板,阴影从面板边缘向面板外渐隐
     * @param isTop true=顶部阴影带(画在面板上边缘外侧,向上渐隐), false=底部阴影带(向下渐隐)
     */
    private fun createShadowBar(isTop: Boolean): View {
        val barHeight = context.dpToPx(12)
        val bar = View(context).apply {
            background = GradientDrawable(
                if (isTop) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#66000000"), Color.TRANSPARENT)
            )
            visibility = View.INVISIBLE
        }
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, barHeight).apply {
            if (isTop) {
                gravity = Gravity.TOP
                topMargin = -barHeight // 负边距:整条阴影带移到面板顶边之外,从边缘向上渐隐
            } else {
                gravity = Gravity.BOTTOM
                bottomMargin = -barHeight // 整条阴影带移到面板底边之外,从边缘向下渐隐
            }
        }
        addView(bar, lp)
        return bar
    }

    fun bind(store: QuickAppsStore) {
        this.store = store
        refresh()
    }

    /** 设置应用列表(在后台加载完成后调用) */
    fun setApps(apps: List<AppRepository.AppInfo>) {
        this.apps = apps
        (recyclerView.adapter as? AppPanelAdapter)?.notifyDataSetChanged()
    }

    /** 从 QuickAppsStore 重读已加入集合,刷新勾选角标 */
    fun refresh() {
        val quickStore = store ?: return
        addedPackages.clear()
        addedPackages.addAll(quickStore.getQuickApps())
        (recyclerView.adapter as? AppPanelAdapter)?.notifyDataSetChanged()
    }

    /** 切换面板展开状态:展开时允许聚焦,收起时彻底阻止聚焦避免拦截卡片区焦点 */
    fun setExpanded(expanded: Boolean) {
        if (expanded) {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            recyclerView.isFocusable = true
            recyclerView.isEnabled = true
            shadowTop?.visibility = View.VISIBLE
            shadowBottom?.visibility = View.VISIBLE
        } else {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            // 禁用 RecyclerView 使收起态格子不可聚焦,不拦截卡片区/快捷栏的 D-pad 导航
            recyclerView.isEnabled = false
            recyclerView.isFocusable = false
            recyclerView.clearFocus()
            shadowTop?.visibility = View.INVISIBLE
            shadowBottom?.visibility = View.INVISIBLE
        }
    }

    /** 将焦点落到第一个应用格子上 */
    fun requestFocusOnFirst() {
        val child = recyclerView.getChildAt(0) ?: return
        child.requestFocus()
    }

    inner class AppPanelAdapter : RecyclerView.Adapter<AppPanelAdapter.Holder>() {

        init {
            // stable ids:包名作为稳定标识,保证局部刷新(getItemId)时 item 位置身份稳定,焦点不跳
            setHasStableIds(true)
        }

        inner class Holder(
            cell: View,
            val iconView: ImageView,
            val nameText: TextView,
            val checkView: ImageView
        ) : RecyclerView.ViewHolder(cell)

        override fun getItemCount(): Int = apps.size

        /** 包名哈希作为稳定 id（同一应用刷新前后身份一致） */
        override fun getItemId(position: Int): Long {
            val pkg = apps.getOrNull(position)?.packageName ?: return RecyclerView.NO_ID
            return pkg.hashCode().toLong()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            // 格子做成自适应正方形:宽 = RecyclerView宽/列数,高 = 同宽
            val rvWidth = parent.width
            val cellWidth = (rvWidth / currentSpanCount).coerceAtLeast(1)
            val cellHeight = cellWidth
            // 图标放大到格子宽的 70%
            val iconSize = (cellWidth * 0.7f).toInt()

            val normalBg = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            val focusedBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2A4A"))
                setStroke(parent.context.dpToPx(2), Color.WHITE)
            }

            // 格子根:图标+名称,获得焦点
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                background = normalBg
            }

            val iconView = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                scaleType = ImageView.ScaleType.FIT_CENTER
                isFocusable = false
            }
            root.addView(iconView)

            val nameText = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = parent.context.dpToPx(4) }
                isFocusable = false
            }
            root.addView(nameText)

            // 勾选角标:右上角
            val checkView = ImageView(parent.context).apply {
                setImageResource(R.drawable.ic_check)
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    parent.context.dpToPx(22),
                    parent.context.dpToPx(22)
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = parent.context.dpToPx(2)
                    rightMargin = parent.context.dpToPx(2)
                }
                isFocusable = false
            }

            // 外层容器承载格子,固定高度决定行高
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cellHeight
                )
                // 透明背景,露出面板底色
                setBackgroundColor(Color.TRANSPARENT)
            }
            cell.addView(
                root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            cell.addView(checkView)

            cell.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
                root.background = if (hasFocus) focusedBg else normalBg
                if (hasFocus) {
                    root.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).start()
                } else {
                    root.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }

            return Holder(cell, iconView, nameText, checkView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            holder.iconView.setImageDrawable(app.icon)
            holder.nameText.text = app.label
            holder.checkView.visibility =
                if (addedPackages.contains(app.packageName)) View.VISIBLE else View.GONE
            holder.itemView.setSafeOnClickListener {
                val pos = holder.bindingAdapterPosition
                val current = apps.getOrNull(pos) ?: return@setSafeOnClickListener
                toggleApp(current, pos)
            }
        }

        /** OK 切换加入/移出常用应用栏 */
        private fun toggleApp(app: AppRepository.AppInfo, position: Int) {
            val quickStore = store ?: return
            val wasAdded = quickStore.contains(app.packageName)
            if (wasAdded) {
                quickStore.removeQuickApp(app.packageName)
                addedPackages.remove(app.packageName)
                onToggle?.invoke(app.packageName, false)
            } else {
                if (quickStore.addQuickApp(app.packageName)) {
                    addedPackages.add(app.packageName)
                    onToggle?.invoke(app.packageName, true)
                }
            }
            // 仅刷新当前格子(勾选角标),不重绑全部,焦点保持在当前格子
            notifyItemChanged(position)
        }
    }
}
