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

    /** 稳定的格子边长(px):布局确定后在监听里缓存,onCreateViewHolder 复用,避免每次 parent.width 现算导致抖动 */
    private var cellSizePx = 0

    /** 面板展开时的上下边缘阴影带(展开显示,收起隐藏) */
    private var shadowTop: View? = null
    private var shadowBottom: View? = null

    init {
        // 用屏幕宽度预计算初始列数/格宽,确保 onCreateViewHolder 先于布局监听触发时也有正确尺寸
        val screenW = context.resources.displayMetrics.widthPixels
        val screenWDp = screenW / context.resources.displayMetrics.density
        currentSpanCount = (screenWDp / targetCellSize).toInt().coerceAtLeast(1)
        cellSizePx = Math.round(screenW.toFloat() / currentSpanCount)

        // 面板自身不获焦,焦点给格子;收起时阻止子项获焦
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        // 面板根透明,露出 panel_container 的 panel_bg 底色
        setBackgroundColor(Color.TRANSPARENT)
        // 允许阴影带越界绘制到面板边缘之外(面板外向上/向下扩散)
        clipChildren = false
        clipToPadding = false

        recyclerView = RecyclerView(context).apply {
            layoutManager = object : GridLayoutManager(context, currentSpanCount, RecyclerView.VERTICAL, false) {
                // 焦点移动时即时跳转到目标行,不做平滑滚动(与卡片区的即时焦点切换一致,避免按键延迟感)
                override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
                    scrollToPosition(position)
                }
            }
            adapter = AppPanelAdapter()
            setHasFixedSize(true)
            // 关闭默认 item 动画:格子自带 scale 动画,默认 animator 在焦点/刷新时叠加动画导致卡顿
            itemAnimator = null
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = true
            // RecyclerView 透明,不盖住面板底色
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 宽度确定后自适应列数:目标格宽 targetCellSize,格子=可用宽/列数(正方形)
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val widthPx = r - l
                if (widthPx <= 0) return@addOnLayoutChangeListener
                val widthDp = widthPx / resources.displayMetrics.density
                val span = (widthDp / targetCellSize).toInt().coerceAtLeast(1)
                // 用 Math.round 避免整数除法截断,格宽 = 可用宽/列数(正方形)
                val cellPx = Math.round(widthPx.toFloat() / span)
                val changed = span != currentSpanCount || cellPx != cellSizePx
                currentSpanCount = span
                cellSizePx = cellPx
                if (changed) {
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
     * 创建一条阴影带 View 并添加到面板,阴影从面板边缘向面板内部渐隐
     * @param isTop true=顶部阴影带(贴面板顶边,向下渐隐), false=底部阴影带(贴面板底边,向上渐隐)
     */
    private fun createShadowBar(isTop: Boolean): View {
        val barHeight = context.dpToPx(4)
        // 顶部阴影稍浓(受光面在上,边缘更清晰),底部阴影偏淡避免在深色底上过重
        val colors = if (isTop) {
            intArrayOf(
                Color.parseColor("#66000000"),
                Color.parseColor("#33000000"),
                Color.TRANSPARENT
            )
        } else {
            intArrayOf(
                Color.parseColor("#40000000"),
                Color.parseColor("#20000000"),
                Color.TRANSPARENT
            )
        }
        val bar = View(context).apply {
            background = GradientDrawable(
                if (isTop) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
                colors
            )
            visibility = View.INVISIBLE
        }
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, barHeight).apply {
            if (isTop) {
                gravity = Gravity.TOP
                topMargin = 0 // 贴面板顶边,阴影向面板内部渐隐
            } else {
                gravity = Gravity.BOTTOM
                bottomMargin = 0 // 贴面板底边,阴影向面板内部渐隐
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
            // 底部阴影带随展开动画一起画出;顶部阴影带由 MainActivity 在完全展开后显示
            shadowTop?.visibility = View.INVISIBLE
            shadowBottom?.visibility = View.VISIBLE
        } else {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            // 禁用 RecyclerView 使收起态格子不可聚焦,不拦截卡片区/快捷栏的 D-pad 导航
            recyclerView.isEnabled = false
            recyclerView.isFocusable = false
            recyclerView.clearFocus()
            // 顶部阴影立即隐藏;底部阴影保持显示,直到收起动画结束由 MainActivity 隐藏(否则下滑动画开头就消失)
            shadowTop?.visibility = View.INVISIBLE
        }
    }

    /** 控制底部阴影带显示(收起动画结束后调用) */
    fun setBottomShadowVisible(visible: Boolean) {
        shadowBottom?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /** 控制顶部阴影带显示(完全展开后调用) */
    fun setTopShadowVisible(visible: Boolean) {
        shadowTop?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
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
            // 格子为正方形,复用布局监听里缓存的稳定边长;未就绪时兜底用 RecyclerView 当前宽/列数
            val cellWidth = if (cellSizePx > 0) cellSizePx
                else Math.round(parent.width.toFloat() / currentSpanCount.coerceAtLeast(1))
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
                // 初始尺寸,onBind 时按最新 cellSizePx 校正
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
                // marquee:聚焦时通过 isSelected 触发横向滚动,长应用名完整显示
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSingleLine = true
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

            // 外层容器承载格子,初始高度用当前格宽,onBind 时再按最新 cellSizePx 校正
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cellHeight
                )
                // 透明背景,露出面板底色
                setBackgroundColor(Color.TRANSPARENT)
                // 不裁切子视图:root 聚焦放大 1.06 后白框/边缘超出格子边界,需越界显示
                clipChildren = false
                clipToPadding = false
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
                // 聚焦时触发名字 marquee 滚动,长应用名完整显示;失焦停止
                nameText.isSelected = hasFocus
            }

            return Holder(cell, iconView, nameText, checkView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            // 校正格子高度为当前格宽(正方形):view holder 可能复用旧高度,绑定时刷新
            if (cellSizePx > 0) {
                val lp = holder.itemView.layoutParams
                if (lp != null && lp.height != cellSizePx) {
                    lp.height = cellSizePx
                    holder.itemView.layoutParams = lp
                }
                // 校正图标尺寸为格子宽 70%:与格子高度同理,避免复用旧尺寸
                val iconSize = (cellSizePx * 0.7f).toInt()
                val iconLp = holder.iconView.layoutParams
                if (iconLp != null && iconLp.width != iconSize) {
                    iconLp.width = iconSize
                    iconLp.height = iconSize
                    holder.iconView.layoutParams = iconLp
                }
            }
            // 复制 Drawable:图标实例与快捷栏/应用列表共享,AdaptiveIconDrawable 可变状态竞争会致图标闪烁
            holder.iconView.setImageDrawable(app.icon.constantState?.newDrawable() ?: app.icon)
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
