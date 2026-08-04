package com.example.tvlauncher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.CardConfig
import com.example.tvlauncher.data.CardDataSource
import com.example.tvlauncher.data.LocalCardDataSource
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.ui.LauncherCardView
import com.example.tvlauncher.ui.QuickBarView
import com.example.tvlauncher.ui.StatusBarView
import com.example.tvlauncher.util.BackgroundCutter
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.showDarkToast
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
 *
 * 阴影：Google 原生 elevation + ViewOutlineProvider，spot(40%) + ambient(12%) ≈ 3.3:1
 *
 * 核心流程：
 *   1. setupUI    — 创建状态栏、快捷栏、IVI面板、卡片网格
 *   2. loadApps   — 异步查询已安装应用，分配到卡片
 *   3. cutBg      — 从全局背景图裁剪9个图块，分别设给9张卡片
 *   4. overlays   — 给每张卡片设置彩色渐变覆盖层
 *   5. navigation — 设置D-pad焦点导航路径
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

    /** 当前弹出的应用选择对话框 */
    private var pickerDialog: android.app.Dialog? = null

    /** 8张右侧卡片（索引：[0-2]上排 [3-4]中排 [5-7]下排） */
    private val cardViews = mutableListOf<LauncherCardView>()

    /** IVI面板卡片 */
    private lateinit var iviCard: LauncherCardView

    // ─── 常量 ─────────────────────────────────────────────────────

    companion object {
        /** 下排固定功能卡片的标签资源 ID */
        val SYSTEM_CARD_LABELS = listOf(
            R.string.app_list,
            R.string.settings,
            R.string.file_manager
        )
        /** 下排固定功能卡片的图标资源 ID */
        val SYSTEM_CARD_ICONS = listOf(
            R.drawable.ic_app_list,
            R.drawable.ic_settings,
            R.drawable.ic_file_manager
        )
    }

    // ─── 生命周期 ───────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 覆盖主题的深蓝窗口背景，避免透出（深色极简风格）
        window.decorView.setBackgroundColor(Color.parseColor("#0F1419"))

        appRepo = AppRepository(this)
        quickStore = QuickAppsStore(this)
        cardDataSource = LocalCardDataSource()

        setupStatusBar()
        setupQuickBar()
        setupIviPanel()
        buildCards()
        loadAppsAndBackground()
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
                showAddAppDialog()
            }
        }
        val container = findViewById<View>(R.id.quick_bar_container)
        (container as android.widget.FrameLayout).addView(quickBar)
    }

    // ─── IVI Panel ─────────────────────────────────────────────

    /** 创建IVI面板卡片 — 使用 LauncherCardView，与右侧卡片统一外观 */
    private fun setupIviPanel() {
        iviCard = LauncherCardView(this).apply {
            id = View.generateViewId()
            // 竖卡布局：图标在上，文字在下
            setIconLayout(iconAbove = true)
            setAppInfo(null)
            // 不设置标签：与右侧小卡片一致，清空后显示"暂无"占位
            // 珊瑚橙渐变覆盖层
            setOverlayGradient(
                getColor(R.color.ivi_overlay_start),
                getColor(R.color.ivi_overlay_end),
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            )
        }

        iviCard.onCardClicked = {
            showDarkToast(R.string.not_configured)
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
        val rowTop = findViewById<LinearLayout>(R.id.row_top)
        val rowMiddle = findViewById<LinearLayout>(R.id.row_middle)
        val rowBottom = findViewById<LinearLayout>(R.id.row_bottom)

        val gapH = dpToPx(8)  // card_gap_horizontal
        val halfGapH = gapH / 2

        // 上排：3张竖卡（图标在上）
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
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
            rowMiddle.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    leftMargin = halfGapH
                    rightMargin = halfGapH
                })
        }

        // 下排：3张系统功能卡片（应用列表/设置/文件管理）
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            card.setIconResource(SYSTEM_CARD_ICONS[i])
            card.setLabel(getString(SYSTEM_CARD_LABELS[i]))
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
            }
        }
    }

    /** 创建一张卡片并加入 cardViews 列表 */
    private fun createCard(isWide: Boolean): LauncherCardView {
        val card = LauncherCardView(this).apply {
            id = View.generateViewId()
            // 宽卡=图标在左（横卡），窄卡=图标在上（竖卡）
            setIconLayout(iconAbove = !isWide)
            setAppInfo(null)
        }
        cardViews.add(card)
        return card
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

            // 如果高度无效（布局未完成），跳过背景裁剪，只设置覆盖层
            if (contentHeight <= 0 || iviW <= 0 || rightW <= 0) {
                applyOverlays()
                return@post
            }

            lifecycleScope.launch {
                // 从数据源获取卡片配置（后端未接入时返回空列表）
                val cardConfigs = withContext(Dispatchers.IO) {
                    cardDataSource.getCardConfigs()
                }

                // 在后台线程裁剪背景图（生成9个图块：0=IVI, 1-8=右侧卡片）
                val cutter = withContext(Dispatchers.IO) {
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
                    } catch (e: Exception) {
                        null
                    }
                }

                // 回到主线程设置UI
                withContext(Dispatchers.Main) {
                    if (cutter != null) {
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

                    // 应用后端卡片配置（当前为空列表，卡片保持占位）
                    cardConfigs.forEach { applyCardConfig(it) }

                    // 设置彩色渐变覆盖层
                    applyOverlays()
                    // 设置D-pad焦点导航
                    setupFocusNavigation()
                }
            }
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

    /** 将后端配置应用到单张卡片。null 字段跳过，卡片保持占位 */
    private fun applyCardConfig(config: CardConfig) {
        val card = cardForSlot(config.slotIndex) ?: return

        // 应用包名：查到则设置应用信息，查不到则保持占位
        val pkg = config.packageName
        if (pkg != null) {
            val appInfo = appRepo.getAppInfo(pkg)
            if (appInfo != null) {
                card.setAppInfo(appInfo)
                card.onCardClicked = {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) startActivity(intent)
                }
                return
            }
        }

        // 未绑定应用时，点击卡片显示未配置提示
        card.onCardClicked = {
            showDarkToast(R.string.not_configured)
        }

        // 配置了自定义名称
        config.label?.let { card.setLabel(it) }

        // 配置了图标URL
        config.iconUrl?.let { url -> card.setIconUrl(url) }

        // 配置了覆盖层颜色
        if (config.overlayStartColor != null && config.overlayEndColor != null) {
            val orientation = when (config.overlayOrientation) {
                "LEFT_RIGHT" -> android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                "TOP_BOTTOM" -> android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                else -> android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            card.setOverlayGradient(
                Color.parseColor(config.overlayStartColor),
                Color.parseColor(config.overlayEndColor),
                orientation
            )
        }
    }

    // ─── Overlays ───────────────────────────────────────────────

    /**
     * 给8张右侧卡片设置彩色渐变覆盖层
     * 每张卡片可不同：纯色或渐变，方向各异
     */
    private fun applyOverlays() {
        // 每张卡片的渐变方向
        val orientations = listOf(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,       // card 0: 对角
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,       // card 1: 对角
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,       // card 2: 对角
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,  // card 3: 左右
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,       // card 4: 对角
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,  // card 5: 上下
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,       // card 6: 对角
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT   // card 7: 左右
        )

        // 每张卡片的渐变起止色
        val colors = listOf(
            listOf(getColor(R.color.card_overlay_1_start), getColor(R.color.card_overlay_1_end)),
            listOf(getColor(R.color.card_overlay_2_start), getColor(R.color.card_overlay_2_end)),
            listOf(getColor(R.color.card_overlay_3_start), getColor(R.color.card_overlay_3_end)),
            listOf(getColor(R.color.card_overlay_4_start), getColor(R.color.card_overlay_4_end)),
            listOf(getColor(R.color.card_overlay_5_start), getColor(R.color.card_overlay_5_end)),
            listOf(getColor(R.color.card_overlay_6_start), getColor(R.color.card_overlay_6_end)),
            listOf(getColor(R.color.card_overlay_7_start), getColor(R.color.card_overlay_7_end)),
            listOf(getColor(R.color.card_overlay_8_start), getColor(R.color.card_overlay_8_end))
        )

        for (i in 0 until 8) {
            if (i >= cardViews.size) break
            cardViews[i].setOverlayGradient(colors[i][0], colors[i][1], orientations[i])
        }
    }

    // ─── Focus Navigation ─────────────────────────────────────────

    /** 设置 D-pad 焦点导航的关系链 */
    private fun setupFocusNavigation() {
        // 向右循环链路：ivi -> Lazy -> YouTube -> Netflix -> Google -> Chrome -> 应用列表 -> 设置 -> 文件管理 -> ivi
        val chain = listOf(
            iviCard,
            cardViews[0], cardViews[1], cardViews[2],
            cardViews[3], cardViews[4],
            cardViews[5], cardViews[6], cardViews[7]
        )
        for (i in chain.indices) {
            // 仅向右：当前 -> 下一个（循环），向左由系统默认布局导航处理
            chain[i].nextFocusRightId = chain[(i + 1) % chain.size].id
        }
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

    /** 打开文件管理器 */
    private fun openFileManager() {
        startActivity(Intent(this, FileManagerActivity::class.java))
    }

    /** 弹出应用选择对话框，将选中的应用添加到快捷栏 */
    private fun showAddAppDialog() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            if (apps.isEmpty()) {
                showDarkToast("没有找到可用的应用")
                return@launch
            }

            showAppPickerDialog(
                title = getString(R.string.add_app),
                apps = apps
            ) { selected ->
                if (quickStore.addQuickApp(selected.packageName)) {
                    quickBar.refresh()
                } else {
                    showDarkToast("应用已存在或无法添加")
                }
            }
        }
    }

    /**
     * 自定义应用选择对话框 — 深色圆角面板 + 自绘列表行
     *
     * 不用系统 setItems()，因为 AOSP TV 上系统列表项会渲染成深蓝底+白边。
     * 这里完全自绘，与深色主题统一。
     */
    private fun showAppPickerDialog(
        title: String,
        apps: List<AppRepository.AppInfo>,
        onSelect: (AppRepository.AppInfo) -> Unit
    ) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 面板深色背景作为对话框容器（直角矩形，无圆角，避免四角漏灰）
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3E4349"))
                setStroke(dpToPx(1), Color.parseColor("#4E5359"))
            }
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            // 固定面板宽度，行更宽敞便于聚焦
            layoutParams = ViewGroup.LayoutParams(
                dpToPx(520),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题
        panel.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, dpToPx(12))
        })

        // 应用列表放入可滚动容器，应用多时能滚动选择（限高 400dp，确保在 TV 720p 上不超屏、最后一项完整可见）
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(400)
            )
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        scrollView.addView(listContainer)

        // 应用列表行：左侧应用图标 + 右侧名称，行宽加大便于聚焦
        apps.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dpToPx(2), Color.TRANSPARENT)
                }
                setOnClickListener {
                    onSelect(app)
                    pickerDialog?.dismiss()
                }
            }
            // 应用图标 48x48dp
            row.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
                isFocusable = false
            })
            // 应用名称
            row.addView(TextView(this).apply {
                text = app.label
                setTextColor(Color.parseColor("#F2F5F9"))
                textSize = 17f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dpToPx(14) }
                isFocusable = false
            })
            row.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                val bg = view.background as GradientDrawable
                // 背景始终透明，聚焦时只显示白色描边框
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke(dpToPx(2), if (hasFocus) Color.WHITE else Color.TRANSPARENT)
                // 聚焦时若行超出 ScrollView 可视区，滚动跟随，确保聚焦行完整可见
                if (hasFocus) {
                    scrollView.post {
                        val rowTop = view.top
                        val rowBottom = view.bottom
                        val svScroll = scrollView.scrollY
                        val svHeight = scrollView.height
                        val targetScroll = when {
                            // 行顶部越出可视区顶部：滚到行顶部
                            rowTop < svScroll -> rowTop
                            // 行底部越出可视区底部：滚到让行完整可见（留一点底边距）
                            rowBottom > svScroll + svHeight ->
                                rowBottom - svHeight + dpToPx(4)
                            else -> svScroll // 完全可见，不滚动
                        }
                        if (targetScroll != svScroll) {
                            scrollView.smoothScrollTo(0, targetScroll)
                        }
                    }
                }
            }
            listContainer.addView(row)
        }
        panel.addView(scrollView)

        // 不带主题创建 Dialog，窗口包裹内容并居中，用系统 dim 蒙层覆盖窗口外区域（含四角）
        val dlg = android.app.Dialog(this)
        dlg.setContentView(panel)
        dlg.window?.apply {
            // DecorView 背景透明，仅显示 panel 深色背景
            decorView.setBackgroundColor(Color.TRANSPARENT)
            // 窗口尺寸包裹内容并居中，使四角位于窗口外被系统 dim 蒙灰
            setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(android.view.Gravity.CENTER)
            // 系统背景变暗（标准 dim 效果）
            setDimAmount(0.4f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        pickerDialog = dlg
        dlg.show()
        // 第一个应用行获得焦点（应用行在 listContainer 里）
        (listContainer.getChildAt(0) as? View)?.requestFocus()
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
