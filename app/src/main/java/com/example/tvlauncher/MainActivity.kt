package com.example.tvlauncher

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.CardConfig
import com.example.tvlauncher.data.ApiCardDataSource
import com.example.tvlauncher.data.CardDataSource
import com.example.tvlauncher.data.FileCardDataSource
import com.example.tvlauncher.data.GroupApp
import com.example.tvlauncher.data.LauncherData
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.ui.AppPanelView
import com.example.tvlauncher.ui.LauncherCardView
import com.example.tvlauncher.ui.QuickBarView
import com.example.tvlauncher.ui.StatusBarView
import com.example.tvlauncher.util.BackgroundCutter
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.showDarkToast
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV Launcher 主 Activity — 投影仪/电视启动器首页
 *
 * 页面结构（从 activity_main.xml 定义）：
 *   - 顶部状态栏（60dp）：投影仪图标 + WiFi 图标 + 时间 + 星期 + 日期
 *   - 主内容区（weight=1）：
 *     - IVI 卡片（左 1/4，左边距 32dp，右边距 8dp）
 *     - 右侧卡片网格（右 3/4）：上排3竖卡 + 中排2横卡 + 下排3功能卡
 *     - 所有内部间距统一 8dp
 *   - 底部快捷栏（80dp）：水平可滚动的快捷应用入口
 *   - 容器外侧 36dp 安全边距，防 TV 过扫描裁切
 *   - 应用面板：平时被卡片区(sheet)覆盖,点击"+"后卡片区上移露出
 *
 * 阴影：CardView 原生阴影，聚焦时 elevation 8dp，失焦无阴影
 * * 核心流程：
 *   1. setupUI    — 创建状态栏、快捷栏、应用面板、IVI面板、卡片网格
 *   2. loadApps   — 异步查询已安装应用，分配到卡片
 *   3. cutBg      — 从全局背景图裁剪9个图块，分别设给9张卡片
 *   4. navigation — 设置D-pad焦点导航路径
 */
class MainActivity : AppCompatActivity() {

    // ─── UI 组件 ─────────────────────────────────────────────────

    private lateinit var statusBar: StatusBarView
    private lateinit var quickBar: QuickBarView
    private lateinit var appRepo: AppRepository
    private lateinit var quickStore: QuickAppsStore
    private var backgroundCutter: BackgroundCutter? = null

    /** 卡片配置数据源（后端接入前使用本地空实现） */
    private lateinit var cardDataSource: CardDataSource

    /** 8张右侧卡片（索引：[0-2]上排 [3-4]中排 [5-7]下排） */
    private val cardViews = mutableListOf<LauncherCardView>()

    /** IVI面板卡片 */
    private lateinit var iviCard: LauncherCardView

    /** 应用面板（常用应用添加/删除器）,平时被卡片区覆盖 */
    private lateinit var panelView: AppPanelView

    /** 应用面板容器(展开时抬升 elevation 盖过快捷栏,使面板底部阴影不被盖住) */
    private lateinit var panelContainer: View

    /** 面板上下边缘阴影带(展开显示,收起隐藏),画在 panel_container 上不受内边距影响 */
    private var shadowTop: View? = null
    private var shadowBottom: View? = null

    /** 面板展开时覆盖卡片区的天蓝亮蒙版(营造面板凸起的光感) */
    private var panelGlow: View? = null

    /** 承载状态栏+卡片区的sheet,点击"+"后上移露出面板 */
    private lateinit var sheet: View

    /** 三行卡片容器（Z 轴抬升对象） */
    private lateinit var rowTop: ViewGroup
    private lateinit var rowMiddle: ViewGroup
    private lateinit var rowBottom: ViewGroup

    /** 面板是否已展开 */
    private var panelExpanded = false

    /** 面板内按OK变更过常用应用,退出面板后才刷新快捷栏 */
    private var quickBarDirty = false

    /** 面板展开后需要恢复焦点的卡片（返回时） */
    private var focusRestoreView: View? = null

    /** 默认文件管理器包名（设备软通文件管理器）,可被后端 CardConfig 覆盖 */
    private val defaultFileManagerPackage = "com.softwinner.TvdFileManager"

    // ─── 常量 ─────────────────────────────────────────────────────

    companion object {
        /** 后端 data.json 接口地址（GET）。返回结构须与 data.json 一致 */
        private const val CARD_API_URL = "http://192.168.2.156:4523/m1/8695853-8480549-default/data.json"
    }

    // ─── 生命周期 ───────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 覆盖主题的深蓝窗口背景，避免透出（深色极简风格）
        window.decorView.setBackgroundColor(Color.parseColor("#0F1419"))

        appRepo = AppRepository(this)
        quickStore = QuickAppsStore(this)
        // TODO: 替换为真实后端 API 地址（GET，返回与 data.json 相同结构的 JSON）
        cardDataSource = ApiCardDataSource(this, CARD_API_URL)

        sheet = findViewById<View>(R.id.sheet)
        rowTop = findViewById(R.id.row_top)
        rowMiddle = findViewById(R.id.row_middle)
        rowBottom = findViewById(R.id.row_bottom)
        // 行容器/sheet 只用于 Z 轴排序,不画自己的矩形阴影（空 outline 阻止整块阴影）
        setContainerNoShadow(sheet)
        setContainerNoShadow(rowTop)
        setContainerNoShadow(rowMiddle)
        setContainerNoShadow(rowBottom)
        setupStatusBar()
        setupQuickBar()
        setupPanel()
        setupIviPanel()
        buildCards()
        loadAppsAndBackground()

        // 面板展开时,返回键先收起面板;否则退出启动器
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (panelExpanded) {
                    collapsePanel()
                } else {
                    finish()
                }
            }
        })
    }

    // ─── Status Bar ───────────────────────────────────────────────

    /** 创建状态栏并添加到容器（容器高度120dp由XML定义） */
    private fun setupStatusBar() {
        statusBar = StatusBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val container = findViewById<View>(R.id.status_bar_container)
        (container as android.widget.FrameLayout).addView(statusBar)
    }

    // ─── Quick Bar ───────────────────────────────────────────────

    /** 创建快捷栏并绑定数据源（容器高度140dp由XML定义） */
    private fun setupQuickBar() {
        quickBar = QuickBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            bind(quickStore, appRepo)
            onAppSelected = { pkg ->
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    showDarkToast("应用无法启动")
                }
            }
            onAddRequested = {
                expandPanel()
            }
        }
        val container = findViewById<View>(R.id.quick_bar_container)
        (container as android.widget.FrameLayout).addView(quickBar)
    }

    // ─── App Panel ───────────────────────────────────────────────

    /** 创建应用面板并绑定数据源（面板平时被卡片区覆盖） */
    private fun setupPanel() {
        panelContainer = findViewById<View>(R.id.panel_container)
        // 面板容器抬升 Z 轴时不画自己的矩形阴影(背景不透明,否则会投出被快捷栏裁剪的原生阴影)
        setContainerNoShadow(panelContainer)
        // 面板底色:上亮下暗垂直渐变,呼应面板从卡片区下方浮起的受光感
        // 与 main_bg(#373778) 同色系:顶亮蓝紫 → 底深蓝紫
        panelContainer.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#4A4A9C"),  // 顶:亮蓝紫
                Color.parseColor("#2B2B5C")   // 底:深蓝紫
            )
        )
        panelView = AppPanelView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            bind(quickStore)
            onToggle = { _, _ ->
                // 面板内不刷快捷栏(避免主线程重建卡顿),退出面板后再刷新
                quickBarDirty = true
            }
            // 两排正方形格子的高度即面板高度,动态调整 panel_container 高度
            // 需加上容器自身上下 padding,否则内容区比两排矮,聚焦下排时 RecyclerView 会滑动对齐
            onPanelHeight = { heightPx ->
                val lp = panelContainer.layoutParams
                val totalHeight = heightPx + panelContainer.paddingTop + panelContainer.paddingBottom
                if (lp.height != totalHeight) {
                    lp.height = totalHeight
                    panelContainer.layoutParams = lp
                }
                // 面板高度变化后同步阴影位置
                panelContainer.post { updateShadowPosition() }
            }
        }
        (panelContainer as android.widget.FrameLayout).addView(panelView)

        // 面板上下边缘阴影带:加到根容器,贴面板外边缘,不受 panel_container 内边距影响
        val rootContainer = findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout
        shadowTop = createPanelShadowBar(rootContainer, isTop = true)
        shadowBottom = createPanelShadowBar(rootContainer, isTop = false)
        // 初始布局完成后同步阴影位置
        panelContainer.post { updateShadowPosition() }

        // 面板展开时覆盖卡片区的天蓝亮蒙版:加在内容根最顶层,盖住卡片区(含卡片)
        val contentRoot = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
        panelGlow = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#33E0F7FF"),  // 上:亮天蓝半透明
                    Color.parseColor("#00000000")   // 下:透明
                )
            )
            visibility = View.INVISIBLE
            isClickable = true       // 拦截点击,防止误触下层卡片
            isFocusable = false      // 不抢焦点,焦点保持在面板内
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        contentRoot?.addView(panelGlow)

        // 异步加载应用列表
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }
            panelView.setApps(apps)
        }
    }

    /**
     * 创建面板阴影带:加到根容器,贴面板外边缘,不受 panel_container 内边距影响
     * 阴影带位置由 updateShadowPosition() 在面板布局完成后同步
     * @param isTop true=顶部阴影带(贴面板顶边,向下渐隐), false=底部阴影带(贴面板底边,向上渐隐)
     */
    private fun createPanelShadowBar(container: android.widget.FrameLayout, isTop: Boolean): View {
        val barHeight = dpToPx(4)
        val colors = if (isTop) {
            intArrayOf(Color.parseColor("#66000000"), Color.parseColor("#33000000"), Color.TRANSPARENT)
        } else {
            intArrayOf(Color.parseColor("#40000000"), Color.parseColor("#20000000"), Color.TRANSPARENT)
        }
        val bar = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                if (isTop) android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                else android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                colors
            )
            visibility = View.INVISIBLE
        }
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, barHeight
        ).apply {
            gravity = android.view.Gravity.TOP
        }
        container.addView(bar, lp)
        return bar
    }

    /** 同步阴影带位置到面板外边缘(面板高度/位置变化后调用) */
    private fun updateShadowPosition() {
        val top = panelContainer.top
        val bottom = panelContainer.bottom
        shadowTop?.let { bar ->
            val lp = bar.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.topMargin = top
            bar.layoutParams = lp
        }
        shadowBottom?.let { bar ->
            val lp = bar.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.topMargin = bottom - dpToPx(4)
            bar.layoutParams = lp
        }
    }

    /** 点击"+"后:卡片区和状态栏上移露出应用面板 */
    private fun expandPanel() {
        if (panelExpanded) return
        panelExpanded = true
        panelView.setExpanded(true)
        // 底部阴影带随展开动画一起画出;顶部阴影带完全展开后显示
        shadowBottom?.visibility = View.VISIBLE
        shadowTop?.visibility = View.INVISIBLE
        // 屏蔽卡片区/状态栏/快捷栏的焦点,焦点只能停留在面板内,仅返回键能跳出
        setSheetFocusable(false)
        setQuickBarFocusable(false)

        // 记录当前焦点,返回时恢复
        focusRestoreView = currentFocus
        // 立即把焦点落到面板第一个格子,避免上滑动画期间焦点空白
        panelView.post { panelView.requestFocusOnFirst() }
        // 上移 sheet(状态栏+卡片区) 露出底部面板(高度=两排格子);快捷栏不动
        val shift = panelContainer.height.coerceAtLeast(dpToPx(100))
        sheet.animate().translationY(-shift.toFloat())
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 动画被中断(快速连按返回)时跳过:不把焦点硬塞给已被禁用的面板
                if (!panelExpanded) return@withEndAction
                // Z 轴层级:卡片区最高(24dp) > 快捷栏 > 面板最下层(0)
                // 面板 shadow 朝内(1dp),不依赖盖过快捷栏
                sheet.elevation = dpToPx(24).toFloat()
                panelContainer.elevation = 0f
                // 顶部阴影带完全展开后显示
                shadowTop?.visibility = View.VISIBLE
                // 面板完全展开后才盖卡片区蒙版:只罩卡片区(屏幕顶部到面板顶边)
                showPanelGlow()
            }
            .start()
    }

    /** 按返回键:卡片区和状态栏下移复位,盖回面板 */
    private fun collapsePanel() {
        if (!panelExpanded) return
        panelExpanded = false
        panelView.setExpanded(false)
        // 顶部阴影立即隐藏;底部阴影保持显示,直到收起动画结束(否则下滑动画开头就消失)
        shadowTop?.visibility = View.INVISIBLE
        // 降回 Z 轴,让卡片区(main_container)重新盖住面板;sheet 复位到默认层级
        panelContainer.elevation = 0f
        sheet.elevation = 0f
        // 隐藏天蓝亮蒙版,卡片区恢复原色
        panelGlow?.visibility = View.INVISIBLE
        sheet.animate().translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 动画被中断(快速连按展开)时跳过:面板可能已被再次展开
                if (panelExpanded) return@withEndAction
                // 面板已被卡片区盖住,此时才隐藏底部阴影(动画期间保持显示)
                shadowBottom?.visibility = View.INVISIBLE
                // 恢复卡片区/状态栏/快捷栏焦点能力
                setSheetFocusable(true)
                setQuickBarFocusable(true)
                // 动画结束后才刷新快捷栏,反映面板内的添加/取消变更
                val quickBarRebuilt = if (quickBarDirty) {
                    quickBarDirty = false
                    quickBar.refresh()
                    true
                } else {
                    false
                }
                // 恢复之前的焦点:快捷栏重建过则聚焦 + 按钮,否则恢复原焦点视图
                if (quickBarRebuilt) {
                    quickBar.requestFocusOnAddButton()
                } else {
                    focusRestoreView?.requestFocus()
                }
            }
            .start()
    }

    /**
     * 面板完全展开后显示蒙版:只罩卡片区(屏幕顶部到面板顶边),不罩面板。
     * 蒙版高度 = 面板容器顶边 y,即卡片区上移后占用的区域。
     */
    private fun showPanelGlow() {
        val glow = panelGlow ?: return
        val top = panelContainer.top.coerceAtLeast(0)
        val lp = glow.layoutParams
        if (lp.height != top) {
            lp.height = top
            glow.layoutParams = lp
        }
        // 蒙版 elevation 高于卡片区(24dp),确保盖住整个卡片区不被 sheet 遮挡
        glow.elevation = dpToPx(30).toFloat()
        glow.visibility = View.VISIBLE
    }

    /** 切换 sheet(状态栏+卡片区)是否可聚焦 */
    private fun setSheetFocusable(focusable: Boolean) {
        (sheet as? ViewGroup)?.setDescendantFocusability(
            if (focusable) ViewGroup.FOCUS_AFTER_DESCENDANTS
            else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        )
    }

    /** 切换快捷栏是否可聚焦 */
    private fun setQuickBarFocusable(focusable: Boolean) {
        quickBar.setDescendantFocusability(
            if (focusable) ViewGroup.FOCUS_AFTER_DESCENDANTS
            else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        )
        if (focusable) {
            quickBar.isFocusable = false
        }
    }

    // ─── IVI Panel ─────────────────────────────────────────────

    /** 创建IVI面板卡片 — 使用 LauncherCardView，与右侧卡片统一外观 */
    private fun setupIviPanel() {
        iviCard = LauncherCardView(this).apply {
            id = View.generateViewId()
            // 整图模式：无图标无名称,由 imageUrl 驱动
        }

        iviCard.onCardClicked = {
            showDarkToast(R.string.not_configured)
        }
        // IVI 卡聚焦时抬升 sheet,使其放大后的阴影/边框盖过快捷栏
        iviCard.onCardFocusChanged = { hasFocus ->
            setSheetRaised(hasFocus)
        }

        val mainContent = findViewById<LinearLayout>(R.id.main_content)
        // IVI 和右侧卡片是一个整体，内部间距统一 8dp；right_panel 的右边距移至此处作为 leftMargin
        mainContent.addView(
            iviCard, 0,
            LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
            ).apply {
                leftMargin = dpToPx(24)
                rightMargin = dpToPx(8)
            }
        )
    }

    // ─── Card Building ─────────────────────────────────────────────

    /** 构建8张右侧卡片（上排3张+中排2张+下排3张），设置间隙 */
    private fun buildCards() {
        val gapH = dpToPx(8)  // card_gap_horizontal
        val halfGapH = gapH / 2

        // 上排：3张竖卡（图标在上）
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            card.onCardFocusChanged = { hasFocus -> setRowRaised(rowTop, hasFocus) }
            rowTop.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    leftMargin = halfGapH
                    rightMargin = halfGapH
                })
        }

        // 中排：2张横卡（图标在左）
        for (i in 0 until 2) {
            val card = createCard(isWide = true)
            card.onCardFocusChanged = { hasFocus -> setRowRaised(rowMiddle, hasFocus) }
            rowMiddle.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    leftMargin = halfGapH
                    rightMargin = halfGapH
                })
        }

        // 下排：3张功能卡片（应用列表/设置/文件管理）——整图模式,无图标无名称
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            card.onCardFocusChanged = { hasFocus ->
                setRowRaised(rowBottom, hasFocus)
                // 底排放大溢出到快捷栏区域,同步抬升 sheet 使阴影不被快捷栏切断
                setSheetRaised(hasFocus)
            }
            rowBottom.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    leftMargin = halfGapH
                    rightMargin = halfGapH
                })

            when (i) {
                0 -> card.onCardClicked = { openAppList() }
                1 -> card.onCardClicked = { openSettings() }
                2 -> card.onCardClicked = { openFileManager() }
            }        }
    }

    /** 创建一张卡片并加入 cardViews 列表 */
    private fun createCard(isWide: Boolean): LauncherCardView {
        val card = LauncherCardView(this).apply {
            id = View.generateViewId()
            // 整图模式:无图标无名称,由 imageUrl 驱动
        }
        cardViews.add(card)
        return card
    }

    // ─── Z 轴抬升（解决卡片放大/阴影被相邻卡片遮挡） ─────────────────

    /**
     * 让容器不再画自己的矩形阴影,只保留 Z 轴排序能力。
     * 无背景的 View 设 elevation 会用完整边界矩形画一整块阴影,
     * 空 outline 可阻止,否则抬行时整排会连成一块矩形阴影。
     */
    private fun setContainerNoShadow(container: View) {
        container.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // 空 outline:不画阴影,但 elevation 仍参与 Z 轴排序
            }
        }
    }

    /**
     * 抬升某一行到其他行之上,让该行聚焦卡片的放大+阴影盖过相邻行。
     * 行是 right_panel 的兄弟子节点,按绘制顺序 上->中->下,
     * 只有抬高行的 elevation 才能让它连同阴影排到其他行前。
     */
    private fun setRowRaised(row: View, raised: Boolean) {
        row.elevation = if (raised) dpToPx(20).toFloat() else 0f
    }

    /**
     * 抬升 sheet(状态栏+卡片区)盖过快捷栏,解决 IVI 卡和底排卡
     * 聚焦放大后向下溢出的阴影/边框被快捷栏遮挡。
     * 快捷栏是 sheet 的兄弟,后绘制会盖住 sheet 溢出的部分。
     */
    private fun setSheetRaised(raised: Boolean) {
        sheet.elevation = if (raised) dpToPx(24).toFloat() else 0f
    }

    // ─── App Loading & Background ─────────────────────────────────

    /**
     * 异步加载已安装应用并裁剪背景图块
     * 分两步执行：
     *   1. 后台线程：查找应用、裁剪背景图
     *   2. 主线程：将结果设置到卡片的图标/文字/背景/覆盖层
     */
    private fun loadAppsAndBackground() {
        val rightPanel = findViewById<View>(R.id.right_panel)
        val mainContent = findViewById<View>(R.id.main_content)

        // 使用 post 确保布局已完成，可以获取正确的宽高
        mainContent.post {
            val contentHeight = mainContent.height
            val rightW = rightPanel.width
            // IVI 卡片宽度 = main_content 宽度 *（IVI 权重 1 / 总权重 4）
            val iviW = mainContent.width / 4

            // 如果高度无效（布局未完成），跳过背景裁剪
            if (contentHeight <= 0 || iviW <= 0 || rightW <= 0) {
                return@post
            }

            lifecycleScope.launch {
                // 读取 data/data.json 并解析（IO 线程,readText 阻塞）
                val rawData = withContext(Dispatchers.IO) {
                    cardDataSource.loadLauncherData()
                }
                // 解析成功但无任何有效卡片(如返回 {}) → 视为无数据,走离线兜底
                val launcherData = rawData?.takeIf { hasBindableApps(it) }
                // 已联网(WiFi/以太网) 且 data.json 解析成功且有有效卡片 → 联网模式
                val networkMode = isNetworkConnected() && launcherData != null

                // 离线模式：先尝试从快照恢复上次联网内容（9 张卡缓存全部命中才用）
                val snapshotData = if (!networkMode) {
                    withContext(Dispatchers.IO) { cardDataSource.loadLauncherDataSnapshot() }
                } else null
                // 9 张卡的 URL 全部能从 Glide 磁盘缓存取出才返回 true，否则 null
                val cachedApps: List<GroupApp?>? = if (snapshotData != null) {
                    withContext(Dispatchers.IO) { loadCachedCardApps(snapshotData) }
                } else null

                // 离线且缓存不齐 → 裁剪本地背景图兜底
                val cutter = withContext(Dispatchers.IO) {
                    if (networkMode || cachedApps != null) {
                        null  // 联网或已从缓存恢复，不裁剪本地背景图
                    } else {
                        try {
                            val src = BitmapFactory.decodeResource(resources, R.drawable.bg_full)
                            BackgroundCutter(
                                src,
                                iviW,
                                rightW,
                                contentHeight,
                                dpToPx(8),  // 卡片水平间隙
                                dpToPx(8)   // 卡片垂直间隙
                            )
                        } catch (e: Exception) { null }
                    }
                }

                withContext(Dispatchers.Main) {
                    // 离线且缓存齐全：恢复上次联网的卡片内容
                    if (cachedApps != null) {
                        bindCachedCards(cachedApps)
                    } else if (cutter != null) {
                        // 离线兜底：本地背景图块
                        backgroundCutter = cutter
                        // IVI面板背景（图块0）
                        iviCard.setCardBackground(cutter.getTile(0))
                        // 右侧8张卡片背景（图块1-8 → cardViews[0-7]）
                        for (i in 1..8) {
                            val cardIdx = i - 1
                            if (cardIdx < cardViews.size) {
                                cardViews[cardIdx].setCardBackground(cutter.getTile(i))
                            }
                        }
                    }
                    // 联网模式：按 data.json 绑定卡片
                    if (networkMode && launcherData != null) {
                        bindCardsFromLauncherData(launcherData)
                        checkAppUpdates(launcherData)
                    }

                    // 设置D-pad焦点导航
                    setupFocusNavigation()
                }
            }
        }
    }

    /**
     * 从快照提取 9 个卡槽应用，同步探测 Glide 磁盘缓存。
     * 全部命中返回 9 元素列表（无图槽位为 null）；任一缺失返回 null（触发整片退回 bg_full）。
     * 必须在 IO 线程调用（Glide submit().get() 阻塞）。
     */
    private fun loadCachedCardApps(data: LauncherData): List<GroupApp?>? {
        val apps = data.modules
            .sortedBy { it.sort }
            .flatMap { m -> m.productGroups.sortedBy { it.sort }.flatMap { it.groupApps } }
            .filter { it.packageName != null || it.iconBgUrl != null || it.resolveIntent() != null }

        val slots = (0 until 9).map { apps.getOrNull(it) }
        // 无有效应用则无缓存可恢复，退回 bg_full
        if (slots.all { it == null }) return null

        for (app in slots) {
            val url = app?.iconBgUrl ?: app?.iconUrl ?: continue  // 无图槽位跳过
            val hit = try {
                Glide.with(this)
                    .asFile()
                    .load(url)
                    .onlyRetrieveFromCache(true)
                    .submit()
                    .get()
                true
            } catch (e: Exception) {
                false
            }
            if (!hit) return null  // 任一缺失 → 全有或全无，整片退回
        }
        return slots
    }

    /**
     * 离线模式：用快照应用绑定 9 张卡（仅从 Glide 缓存取图，不走网络）。
     * cachedApps 来自 loadCachedCardApps，已确认所有有图槽位缓存命中。
     */
    private fun bindCachedCards(cachedApps: List<GroupApp?>) {
        val targets = listOf(iviCard) + cardViews
        targets.forEachIndexed { idx, card ->
            val app = cachedApps.getOrNull(idx) ?: return@forEachIndexed
            // 仅从缓存加载（缓存已确认存在）
            val imageUrl = app.iconBgUrl ?: app.iconUrl
            imageUrl?.let { card.setCardImageUrlFromCache(it) }
            // 点击行为与联网一致
            val launchPkg = resolveLaunchPackage(app)
            card.onCardClicked = if (launchPkg != null) {
                {
                    val intent = packageManager.getLaunchIntentForPackage(launchPkg)
                    if (intent != null) startActivity(intent)
                    else showDarkToast("应用无法启动")
                }
            } else {
                { showDarkToast(R.string.not_configured) }
            }
        }
    }

    /** 提取 data.json 中可绑定的有效卡片（与 bindCardsFromLauncherData 同一过滤规则） */
    private fun extractBindableApps(data: LauncherData): List<GroupApp> {
        return data.modules
            .sortedBy { it.sort }
            .flatMap { m -> m.productGroups.sortedBy { it.sort }.flatMap { it.groupApps } }
            .filter { it.packageName != null || it.iconBgUrl != null || it.resolveIntent() != null }
    }

    /** 数据中是否至少有一张可绑定的卡片；空内容(如 {}) 返回 false */
    private fun hasBindableApps(data: LauncherData): Boolean {
        return extractBindableApps(data).isNotEmpty()
    }

    /** 判断当前是否有可用网络（WiFi 或以太网任一即视为联网） */
    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * 将 data.json 的应用按 sort 顺序绑定到固定卡片布局。
     * 卡片顺序：0=IVI, 1-3=上排, 4-5=中排, 6-8=下排。
     * data.json 的 groupApps 扁平化为按 sort 排列的列表，前 9 个依次绑定。
     */
    private fun bindCardsFromLauncherData(data: LauncherData) {
        val apps = extractBindableApps(data)

        val targets = listOf(iviCard) + cardViews  // 9 个卡槽
        targets.forEachIndexed { idx, card ->
            val app = apps.getOrNull(idx) ?: return@forEachIndexed
            bindCard(card, app)
        }
    }

    /**
     * 检查 data.json 中各应用版本：已安装且 versionCode 落后 → 弹窗提示更新。
     * 未安装的应用不处理。
     */
    private fun checkAppUpdates(data: LauncherData) {
        val apps = data.modules.flatMap { m ->
            m.productGroups.flatMap { it.groupApps }
        }
        val outdated = mutableListOf<GroupApp>()
        for (app in apps) {
            if (app.isCheckVer != 1) continue
            val pkg = app.packageName ?: continue
            val installedCode = try {
                packageManager.getPackageInfo(pkg, 0).versionCode
            } catch (e: Exception) {
                continue  // 未安装，跳过
            }
            if (installedCode < app.versionCode) outdated.add(app)
        }
        if (outdated.isEmpty()) return

        val names = outdated.joinToString("\n") { it.appName ?: it.packageName ?: "" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("应用有更新")
            .setMessage("以下应用有新版本：\n$names")
            .setPositiveButton("知道了", null)
            .show()
    }

    /** 将单个应用绑定到卡片：图片（iconBgUrl 优先,回退 iconUrl） + 点击行为（packageName/intents） */
    private fun bindCard(card: LauncherCardView, app: GroupApp) {
        // 图片：iconBgUrl(banner 大图)优先,缺失时回退 iconUrl(小图标)
        val imageUrl = app.iconBgUrl ?: app.iconUrl
        imageUrl?.let { card.setCardImageUrl(it) }

        // 点击行为：intents 内置行为优先，否则 packageName
        val launchPkg = resolveLaunchPackage(app)
        card.onCardClicked = if (launchPkg != null) {
            {
                val intent = packageManager.getLaunchIntentForPackage(launchPkg)
                if (intent != null) startActivity(intent)
                else showDarkToast("应用无法启动")
            }
        } else {
            { showDarkToast(R.string.not_configured) }
        }
    }

    /**
     * 解析应用点击目标：内置 intents 行为优先（FILE_MANAGER 用设备真实文件管理器包名，
     * SETTINGS 用系统设置），否则用 packageName。
     */
    private fun resolveLaunchPackage(app: GroupApp): String? {
        return when (app.intents) {
            "FILE_MANAGER" -> defaultFileManagerPackage
            "SETTINGS" -> "com.android.settings"
            else -> app.packageName
        }
    }

    // ─── Card Config Binding ─────────────────────────────────────

    /** 根据卡槽索引返回对应卡片视图（0=IVI, 1-3=上排, 4-5=中排），无效索引返回 null */
    private fun cardForSlot(slotIndex: Int): LauncherCardView? {
        return when (slotIndex) {
            0 -> iviCard
            1 -> cardViews[0]
            2 -> cardViews[1]
            3 -> cardViews[2]
            4 -> cardViews[3]
            5 -> cardViews[4]
            else -> null
        }
    }

    /** 将后端配置应用到单张卡片。null 字段跳过,卡片保持占位 */
    private fun applyCardConfig(config: CardConfig) {
        val card = cardForSlot(config.slotIndex) ?: return

        // 整卡图片:优先 imageUrl,否则保持背景图块占位
        config.imageUrl?.let { card.setCardImageUrl(it) }

        // 应用包名:决定点击启动;无包名则点击显示未配置提示
        val pkg = config.packageName
        card.onCardClicked = if (pkg != null) {
            {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) startActivity(intent)
                else showDarkToast("应用无法启动")
            }
        } else {
            { showDarkToast(R.string.not_configured) }
        }
    }

    // ─── Focus Navigation ─────────────────────────────────────────

    /** 设置 D-pad 焦点导航的关系链 */
    private fun setupFocusNavigation() {
        // 向右循环链路：ivi -> 9张卡 -> ivi
        val chain = listOf(
            iviCard,
            cardViews[0], cardViews[1], cardViews[2],
            cardViews[3], cardViews[4],
            cardViews[5], cardViews[6], cardViews[7]
        )
        for (i in chain.indices) {
            // 仅向右：当前 -> 下一个（循环）
            chain[i].nextFocusRightId = chain[(i + 1) % chain.size].id
        }
        // 左边界：IVI 是卡片区最左,LEFT 停在原地（防止系统几何导航捡到意外的下方目标）
        iviCard.nextFocusLeftId = iviCard.id
        // 右侧网格每行最左卡片,LEFT 确定性指向 IVI
        cardViews[0].nextFocusLeftId = iviCard.id  // 上排最左
        cardViews[3].nextFocusLeftId = iviCard.id  // 中排最左
        cardViews[5].nextFocusLeftId = iviCard.id  // 下排最左
    }

    // ─── System Functions ─────────────────────────────────────────

    /** 打开应用列表 Activity */
    private fun openAppList() {
        startActivity(Intent(this, AppListActivity::class.java))
    }

    /** 打开系统设置 */
    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    /** 打开系统文件管理器应用（默认软通文件管理器,后端可通过 CardConfig.packageName 覆盖） */
    private fun openFileManager() {
        val intent = packageManager.getLaunchIntentForPackage(defaultFileManagerPackage)
        if (intent != null) {
            startActivity(intent)
        } else {
            showDarkToast("未找到文件管理器应用")
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // 重新开始接收时间和WiFi广播
        statusBar.startListening()
    }

    override fun onPause() {
        super.onPause()
        // 停止接收广播以节省资源
        statusBar.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        statusBar.stopListening()
        // 释放背景图内存
        backgroundCutter?.recycle()
    }
}
