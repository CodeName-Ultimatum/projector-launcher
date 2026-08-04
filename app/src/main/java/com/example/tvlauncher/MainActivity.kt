package com.example.tvlauncher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.CardConfig
import com.example.tvlauncher.data.CardDataSource
import com.example.tvlauncher.data.LocalCardDataSource
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.ui.AppPanelView
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
 *   - 应用面板：平时被卡片区(sheet)覆盖,点击"+"后卡片区上移露出
 *
 * 阴影：Google 原生 elevation + ViewOutlineProvider，spot(40%) + ambient(12%) ≈ 3.3:1
 *
 * 核心流程：
 *   1. setupUI    — 创建状态栏、快捷栏、应用面板、IVI面板、卡片网格
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

    /** 8张右侧卡片（索引：[0-2]上排 [3-4]中排 [5-7]下排） */
    private val cardViews = mutableListOf<LauncherCardView>()

    /** IVI面板卡片 */
    private lateinit var iviCard: LauncherCardView

    /** 应用面板（常用应用添加/删除器）,平时被卡片区覆盖 */
    private lateinit var panelView: AppPanelView

    /** 承载状态栏+卡片区的sheet,点击"+"后上移露出面板 */
    private lateinit var sheet: View

    /** 面板是否已展开 */
    private var panelExpanded = false

    /** 面板内按OK变更过常用应用,退出面板后才刷新快捷栏 */
    private var quickBarDirty = false

    /** 面板展开后需要恢复焦点的卡片（返回时） */
    private var focusRestoreView: View? = null

    /** 默认文件管理器包名（设备软通文件管理器）,可被后端 CardConfig 覆盖 */
    private val defaultFileManagerPkg = "com.softwinner.TvdFileManager"

    // ─── 常量 ─────────────────────────────────────────────────────

    companion object {
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

        sheet = findViewById<View>(R.id.sheet)
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
        val panelContainer = findViewById<View>(R.id.panel_container)
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
        }
        (panelContainer as android.widget.FrameLayout).addView(panelView)

        // 异步加载应用列表
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }
            panelView.setApps(apps)
        }
    }

    /** 点击"+"后:卡片区和状态栏上移露出应用面板 */
    private fun expandPanel() {
        if (panelExpanded) return
        panelExpanded = true
        panelView.setExpanded(true)
        // 屏蔽卡片区/状态栏/快捷栏的焦点,焦点只能停留在面板内,仅返回键能跳出
        setSheetFocusable(false)
        setQuickBarFocusable(false)

        // 记录当前焦点,返回时恢复
        focusRestoreView = currentFocus
        // 上移 sheet(状态栏+卡片区) 216dp = 面板高度,露出底部面板;快捷栏不动
        val shift = dpToPx(216)
        sheet.animate().translationY(-shift.toFloat())
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 动画被中断(快速连按返回)时跳过:不把焦点硬塞给已被禁用的面板
                if (!panelExpanded) return@withEndAction
                panelView.post { panelView.requestFocusOnFirst() }
            }
            .start()
    }

    /** 按返回键:卡片区和状态栏下移复位,盖回面板 */
    private fun collapsePanel() {
        if (!panelExpanded) return
        panelExpanded = false
        panelView.setExpanded(false)
        sheet.animate().translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 动画被中断(快速连按展开)时跳过:面板可能已被再次展开
                if (panelExpanded) return@withEndAction
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

        // 下排：3张功能卡片（应用列表/设置/文件管理）——整图模式,无图标无名称
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
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
        val intent = packageManager.getLaunchIntentForPackage(defaultFileManagerPkg)
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
