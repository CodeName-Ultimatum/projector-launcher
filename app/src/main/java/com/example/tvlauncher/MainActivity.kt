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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
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

    /** 当前弹出的应用选择对话框 */
    private var pickerDialog: android.app.Dialog? = null

    /** 8张右侧卡片（索引：[0-2]上排 [3-4]中排 [5-7]下排） */
    private val cardViews = mutableListOf<LauncherCardView>()

    /** IVI面板卡片 */
    private lateinit var iviCard: LauncherCardView

    // ─── 应用分配状态 ────────────────────────────────────────────

    /** IVI应用信息，null表示未安装 */
    private var iviAppInfo: AppRepository.AppInfo? = null

    /** 已被分配到卡片的包名集合，用于排除随机分配时的重复 */
    private val boundCardPackages = mutableSetOf<String>()

    // ─── 常量 ─────────────────────────────────────────────────────

    companion object {
        // 固定分配的应用包名
        const val PKG_LAZYMEDIA = "com.lazymedia.deluxe"
        const val PKG_YOUTUBE = "com.google.android.youtube.tv"
        const val PKG_GOOGLE_PLAY = "com.android.vending"
        const val PKG_NETFLIX = "com.netflix.ninja"
        const val PKG_CHROME = "com.android.chrome"

        /** IVI 应用的候选包名列表（按顺序尝试查找） */
        val IVI_CANDIDATE_PACKAGES = listOf(
            "ru.ivi.client",
            "ru.ivi.client.tv"
        )

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
            // 默认显示 "IVI" 文字
            setLabel(getString(R.string.ivi_label))
            // 珊瑚橙渐变覆盖层
            setOverlayGradient(
                getColor(R.color.ivi_overlay_start),
                getColor(R.color.ivi_overlay_end),
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            )
        }

        iviCard.onCardClicked = {
            if (iviAppInfo != null) {
                val intent = packageManager.getLaunchIntentForPackage(iviAppInfo!!.packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    showDarkToast("应用无法启动")
                }
            } else {
                showDarkToast(R.string.ivi_not_installed)
            }
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

        // 可替换卡片（长按弹出应用选择）：上排右 = cardViews[2]，中排右 = cardViews[4]
        cardViews[2].onCardLongClicked = { showReplaceAppDialog(2) }
        cardViews[4].onCardLongClicked = { showReplaceAppDialog(4) }
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
                // 在后台线程查询应用信息
                val iviApp = withContext(Dispatchers.IO) { findIviApp() }
                val lazyMedia = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_LAZYMEDIA) }
                val youtube = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_YOUTUBE) }
                val googlePlay = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_GOOGLE_PLAY) }
                val netflix = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_NETFLIX) }
                val chrome = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_CHROME) }
                val allApps = withContext(Dispatchers.IO) { appRepo.getInstalledLaunchableApps() }

                iviAppInfo = iviApp

                // 构建已绑定的包名集合（用于随机分配时排除）
                boundCardPackages.clear()
                boundCardPackages.add(packageName) // 排除自身
                if (iviApp != null) boundCardPackages.add(iviApp.packageName)
                if (lazyMedia != null) boundCardPackages.add(lazyMedia.packageName)
                if (youtube != null) boundCardPackages.add(youtube.packageName)
                if (googlePlay != null) boundCardPackages.add(googlePlay.packageName)
                if (netflix != null) boundCardPackages.add(netflix.packageName)
                if (chrome != null) boundCardPackages.add(chrome.packageName)

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

                    // 更新IVI卡片的图标和名称
                    if (iviApp != null) {
                        iviCard.setAppInfo(iviApp)
                    }

                    // 分配应用到卡片
                    assignAppsToCards(allApps, lazyMedia, youtube, googlePlay, netflix, chrome)
                    // 设置彩色渐变覆盖层
                    applyOverlays()
                    // 设置D-pad焦点导航
                    setupFocusNavigation()
                }
            }
        }
    }

    /** 在IVI候选包名列表中查找第一个已安装的应用 */
    private fun findIviApp(): AppRepository.AppInfo? {
        for (pkg in IVI_CANDIDATE_PACKAGES) {
            val info = appRepo.getAppInfo(pkg)
            if (info != null) return info
        }
        return null
    }

    // ─── App Assignment ────────────────────────────────────────────

    /**
     * 将应用分配到上排和中排卡片
     * 索引0=上左(LazyMedia), 1=上中(YouTube), 2=上右(Netflix),
     * 3=中左(Google Play), 4=中右(Chrome)
     * 当包名匹配不到时，用应用名称关键字模糊匹配
     */
    private fun assignAppsToCards(
        allApps: List<AppRepository.AppInfo>,
        lazyMedia: AppRepository.AppInfo?,
        youtube: AppRepository.AppInfo?,
        googlePlay: AppRepository.AppInfo?,
        netflix: AppRepository.AppInfo?,
        chrome: AppRepository.AppInfo?
    ) {
        bindAppToCard(0, lazyMedia ?: findAppByLabel(allApps, listOf("lazymedia", "deluxe")), "LazyMedia Deluxe")
        bindAppToCard(1, youtube ?: findAppByLabel(allApps, listOf("youtube")), "YouTube")
        bindAppToCard(2, netflix ?: findAppByLabel(allApps, listOf("netflix")), "Netflix")
        bindAppToCard(3, googlePlay ?: findAppByLabel(allApps, listOf("google play", "play store")), "Google Play")
        bindAppToCard(4, chrome ?: findAppByLabel(allApps, listOf("chrome")), "Chrome")
    }

    /**
     * 通过应用名称关键字模糊匹配已安装应用
     * @param allApps 所有已安装应用列表
     * @param keywords 匹配关键字列表（全部转小写比较，命中任一即匹配）
     * @return 第一个匹配到的应用，找不到返回 null
     */
    private fun findAppByLabel(allApps: List<AppRepository.AppInfo>, keywords: List<String>): AppRepository.AppInfo? {
        val lowerKeywords = keywords.map { it.lowercase() }
        return allApps.firstOrNull { app ->
            val lowerLabel = app.label.lowercase()
            lowerKeywords.any { keyword -> keyword in lowerLabel }
        }
    }

    /**
     * 将指定应用绑定到卡片
     * @param cardIndex 卡片索引
     * @param app 应用信息，null表示未安装
     * @param fallbackLabel 未安装时的占位文字
     */
    private fun bindAppToCard(cardIndex: Int, app: AppRepository.AppInfo?, fallbackLabel: String) {
        if (cardIndex >= cardViews.size) return
        val card = cardViews[cardIndex]

        if (app != null) {
            card.setAppInfo(app)
            card.onCardClicked = {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) startActivity(intent)
            }
        } else {
            card.setAppInfo(null)
            card.setLabel(fallbackLabel)
            card.onCardClicked = {
                showDarkToast(R.string.app_not_installed)
            }
        }
    }

    /**
     * 为卡片随机分配一个未绑定的应用
     * 优先使用 SharedPreferences 中用户手动选择的绑定
     * 没有手动绑定则从可用应用中随机选择
     */
    private fun bindRandomAppToCard(cardIndex: Int, allApps: List<AppRepository.AppInfo>) {
        if (cardIndex >= cardViews.size) return
        val card = cardViews[cardIndex]

        // 检查是否有用户手动选择的绑定（通过长按设置）
        val prefs = getSharedPreferences("card_bindings", MODE_PRIVATE)
        val savedPkg = prefs.getString("card_${cardIndex}_pkg", null)

        if (savedPkg != null) {
            val savedApp = appRepo.getAppInfo(savedPkg)
            if (savedApp != null) {
                card.setAppInfo(savedApp)
                card.onCardClicked = {
                    val intent = packageManager.getLaunchIntentForPackage(savedApp.packageName)
                    if (intent != null) startActivity(intent)
                }
                boundCardPackages.add(savedApp.packageName)
                return
            }
        }

        // 从排除已绑定包名后的可用应用中随机选择
        val available = allApps.filter { it.packageName !in boundCardPackages }
        if (available.isNotEmpty()) {
            val randomApp = available.random()
            card.setAppInfo(randomApp)
            boundCardPackages.add(randomApp.packageName)
            card.onCardClicked = {
                val intent = packageManager.getLaunchIntentForPackage(randomApp.packageName)
                if (intent != null) startActivity(intent)
            }
        } else {
            card.setAppInfo(null)
            card.onCardClicked = null
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

    /** 弹出应用选择对话框，替换指定卡片上的应用（长按触发） */
    private fun showReplaceAppDialog(cardIndex: Int) {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            if (apps.isEmpty()) return@launch

            showAppPickerDialog(
                title = getString(R.string.select_app),
                apps = apps
            ) { selected ->
                // 持久化用户选择
                val prefs = getSharedPreferences("card_bindings", MODE_PRIVATE)
                prefs.edit().putString("card_${cardIndex}_pkg", selected.packageName).apply()

                // 立即刷新卡片
                cardViews[cardIndex].setAppInfo(selected)
                cardViews[cardIndex].onCardClicked = {
                    val intent = packageManager.getLaunchIntentForPackage(selected.packageName)
                    if (intent != null) startActivity(intent)
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
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141A21"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#2A3442"))
            }
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
        }

        // 标题
        panel.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, dpToPx(12))
        })

        // 应用列表行
        apps.forEach { app ->
            val row = TextView(this).apply {
                text = app.label
                setTextColor(Color.parseColor("#F2F5F9"))
                textSize = 15f
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
            row.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                val bg = view.background as GradientDrawable
                if (hasFocus) {
                    bg.setColor(Color.parseColor("#1E2530"))
                    bg.setStroke(dpToPx(2), Color.WHITE)
                } else {
                    bg.setColor(Color.TRANSPARENT)
                    bg.setStroke(dpToPx(2), Color.TRANSPARENT)
                }
            }
            panel.addView(row)
        }

        // 取消按钮
        panel.addView(TextView(this).apply {
            text = getString(android.R.string.cancel)
            setTextColor(Color.parseColor("#8A94A6"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(10), 0, 0)
            setOnClickListener { pickerDialog?.dismiss() }
        })

        val dlg = android.app.Dialog(this, R.style.Theme_AppPickerDialog)
        dlg.setContentView(panel)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pickerDialog = dlg
        dlg.show()
        // 第一个应用行获得焦点
        (panel.getChildAt(1) as? View)?.requestFocus()
    }

    // ─── Lifecycle ─────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // 重新开始接收时间和WiFi广播
        statusBar.startListening()

        // 刷新随机卡片（应用可能被安装/卸载）
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            // 刷新 IVI 应用状态
            val iviApp = withContext(Dispatchers.IO) { findIviApp() }
            iviAppInfo = iviApp
            if (iviApp != null) {
                iviCard.setAppInfo(iviApp)
            }

            // 重建排除列表
            boundCardPackages.clear()
            boundCardPackages.add(packageName)
            if (iviAppInfo != null) boundCardPackages.add(iviAppInfo!!.packageName)

            val lmPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_LAZYMEDIA)?.packageName }
            val ytPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_YOUTUBE)?.packageName }
            val gpPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_GOOGLE_PLAY)?.packageName }
            val nfPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_NETFLIX)?.packageName }
            val chPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_CHROME)?.packageName }
            if (lmPkg != null) boundCardPackages.add(lmPkg)
            if (ytPkg != null) boundCardPackages.add(ytPkg)
            if (gpPkg != null) boundCardPackages.add(gpPkg)
            if (nfPkg != null) boundCardPackages.add(nfPkg)
            if (chPkg != null) boundCardPackages.add(chPkg)

            // 刷新 Netflix 和 Chrome 卡片（不再随机）
            val netflix = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_NETFLIX) }
                ?: findAppByLabel(apps, listOf("netflix"))
            bindAppToCard(2, netflix, "Netflix")
            val chrome = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_CHROME) }
                ?: findAppByLabel(apps, listOf("chrome"))
            bindAppToCard(4, chrome, "Chrome")
        }
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
