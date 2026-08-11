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
import com.example.tvlauncher.data.AppUpdater
import com.example.tvlauncher.data.CardConfig
import com.example.tvlauncher.data.ApiCardDataSource
import com.example.tvlauncher.data.CardDataSource
import com.example.tvlauncher.data.FileCardDataSource
import com.example.tvlauncher.data.GroupApp
import com.example.tvlauncher.data.LauncherConfig
import com.example.tvlauncher.data.LauncherData
import com.example.tvlauncher.data.PrefsLongStorage
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.ui.AppPanelView
import com.example.tvlauncher.ui.LauncherCardView
import com.example.tvlauncher.ui.QuickBarView
import com.example.tvlauncher.ui.StatusBarView
import com.example.tvlauncher.ui.ThemeManager
import com.example.tvlauncher.util.BackgroundCutter
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.showDarkToast
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    /** 应用更新下载安装器 */
    private lateinit var appUpdater: AppUpdater

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

    /** 启动骨架屏遮罩层:覆盖整个页面,数据加载完成后淡出移除 */
    private var skeletonOverlay: View? = null

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

    /**
     * 兜底主页视频卡的应用包名 → 卡槽映射。
     * 深色背景的原应用图标放背景较亮的卡槽(iviCard 大卡、上排左2、下排3),与深色太空背景错开;
     * 白色功能图标放背景较深的卡槽(上排右1、中排2),保持对比度。
     * 仅在无法拉取后端数据(cutter 兜底)时显示,叠加原应用图标并绑定点击。
     */
    private val fallbackVideoApps = mapOf(
        0 to "com.google.android.youtube.tv",  // iviCard 大卡:YouTube
        // 上排(1,2,3) 留给功能卡(白剪影,深色背景区)
        4 to "com.android.chrome",             // 中排1:Chrome
        5 to "ru.ivi.client",                  // 中排2:IVI
        6 to "ru.kinopoisk.tv",                // 下排1:Kinopoisk(深色图标→亮背景区)
        7 to "ru.vk.store.tv",                 // 下排2:VK Video(白色播放键图标,亮背景区清晰)
        8 to "com.ottplay.ottplay"             // 下排3:OKKO
    )

    /** 兜底主页功能卡(白色剪影,放深色背景卡槽) → (卡槽索引, 图标资源, 点击行为) */
    private data class FallbackFuncCard(val iconRes: Int, val onClick: MainActivity.() -> Unit)

    private val fallbackFuncCards: Map<Int, FallbackFuncCard>
        get() = mapOf(
            1 to FallbackFuncCard(R.drawable.ic_settings_card) { openSettings() },    // 上排1:设置
            2 to FallbackFuncCard(R.drawable.ic_file_manager_card) { openFileManager() }, // 上排2:文件管理
            3 to FallbackFuncCard(R.drawable.ic_app_list) { openAppList() }           // 上排3:应用列表
        )

    // ─── 常量 ─────────────────────────────────────────────────────

    companion object {
        /** 后端 data.json 接口地址（GET）。返回结构须与 data.json 一致 */
        private const val CARD_API_URL = "http://192.168.2.156:4523/m1/8695853-8480549-default/data.json"
    }

    // ─── 生命周期 ───────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 用上次快照的 config 初始化主题,避免启动瞬间闪默认深蓝再跳浅色
        ThemeManager.apply(loadSnapshotConfigForTheme())
        applyThemeBackgrounds()
        showSkeletonOverlay()
        appRepo = AppRepository(this)
        quickStore = QuickAppsStore(this)
        cardDataSource = ApiCardDataSource(
            context = this,
            apiUrl = CARD_API_URL,
            storage = PrefsLongStorage(this)
        )
        appUpdater = AppUpdater(this)

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

    /**
     * 同步读取上次联网快照的 config,用于启动时初始化主题(避免先闪默认深蓝)。
     * 快照文件由 ApiCardDataSource 写入(getExternalFilesDir/data/last_launcher_data.json)。
     * 文件不存在/解析失败 → 返回默认 LauncherConfig()。
     */
    private fun loadSnapshotConfigForTheme(): LauncherConfig {
        return runCatching {
            val f = File(File(getExternalFilesDir(null), "data"), "last_launcher_data.json")
            if (!f.exists()) return@runCatching LauncherConfig()
            com.example.tvlauncher.data.LauncherDataParser.parse(f.readText()).config
        }.getOrNull() ?: LauncherConfig()
    }

    /**
     * 在 root_container 最上层添加全屏遮罩(首帧绘制前调用)。
     * 颜色策略:
     * - 有快照配置 → 用快照主题色(与已设置的背景色一致,淡出无颜色跳变)
     * - 无快照(首次访问) → 中性深灰 #2A2A30,避免默认深蓝 #373778 闪屏
     */
    private fun showSkeletonOverlay() {
        val isFallback = ThemeManager.current == null
            || ThemeManager.current?.screenColor.isNullOrBlank()
        val color = if (isFallback) Color.parseColor("#2A2A30") else ThemeManager.screenColor()
        val overlay = View(this).apply {
            setBackgroundColor(color)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        skeletonOverlay = overlay
        findViewById<FrameLayout>(R.id.root_container).addView(overlay)
    }

    /**
     * 隐藏并移除骨架屏遮罩。
     * @param animate true=淡出(250ms,正常加载完成路径); false=立即移除(布局未就绪早退路径)
     */
    private fun hideSkeletonOverlay(animate: Boolean = true) {
        val overlay = skeletonOverlay ?: return
        skeletonOverlay = null  // 防止重复调用
        if (animate) {
            overlay.animate().alpha(0f).setDuration(250).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }.start()
        } else {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
    }

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
        statusBar.setLogoUrl(null)  // 占位;真实 logoUrl 由数据加载后设置
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
        val container = panelContainer
        // 面板容器抬升 Z 轴时不画自己的矩形阴影(背景不透明,否则会投出被快捷栏裁剪的原生阴影)
        setContainerNoShadow(container)
        // 面板底色:上亮下暗垂直渐变,色值跟随主题
        setupPanelBackground()
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
                val lp = container.layoutParams
                val totalHeight = heightPx + container.paddingTop + container.paddingBottom
                if (lp.height != totalHeight) {
                    lp.height = totalHeight
                    container.layoutParams = lp
                }
                // 面板高度变化后同步阴影位置
                container.post { updateShadowPosition() }
            }
        }
        (container as android.widget.FrameLayout).addView(panelView)

        // 面板上下边缘阴影带:加到根容器,贴面板外边缘,不受 panel_container 内边距影响
        val rootContainer = findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout
        shadowTop = createPanelShadowBar(rootContainer, isTop = true)
        shadowBottom = createPanelShadowBar(rootContainer, isTop = false)
        // 初始布局完成后同步阴影位置
        container.post { updateShadowPosition() }

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

        // 应用列表:先用磁盘缓存立即显示,后台全量查询刷新(缓存先行,避免全量查询阻塞)
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { appRepo.readCachedAppList() }
            if (cached != null) panelView.setApps(cached)
            val fresh = withContext(Dispatchers.IO) { appRepo.getInstalledLaunchableApps() }
            panelView.setApps(fresh)
        }
    }

    /**
     * 统一设置主背景为 ThemeManager.screenColor:
     * - decorView / root_container:最底背景(装饰层)
     * - sheet 及其子容器(status_bar_container / main_content):背景色。
     *   这些是"卡片区背景",随 sheet 上移移动(展开面板时背景跟随卡片),
     *   且平时盖住底层面板。
     * - main_container 保持透明(它只是容器,不是背景层)。
     */
    private fun applyThemeBackgrounds() {
        val color = ThemeManager.screenColor()
        window.decorView.setBackgroundColor(color)
        findViewById<View>(R.id.root_container)?.setBackgroundColor(color)
        // sheet 本身需全宽背景(它 match_parent 1280 宽),否则展开面板时两侧 36dp
        // 安全边距区域(子容器有 margin)会漏出底层背景。
        findViewById<View>(R.id.sheet)?.setBackgroundColor(color)
        findViewById<View>(R.id.status_bar_container)?.setBackgroundColor(color)
        findViewById<View>(R.id.main_content)?.setBackgroundColor(color)
        findViewById<View>(R.id.quick_bar_container)?.setBackgroundColor(color)
    }

    /** 根据当前主题色板重建面板竖向渐变背景，供初始化和主题变化时调用 */
    private fun setupPanelBackground() {
        panelContainer.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ThemeManager.palette().panelGradientTop,
                ThemeManager.palette().panelGradientBottom
            )
        )
    }

    /** 主题变化后统一刷新受影响的 UI：面板渐变 + 快捷栏 + 卡片占位背景 */
    private fun refreshThemeColors() {
        // 重建面板渐变
        setupPanelBackground()
        // 快捷栏重建以刷新取色
        quickBar.refresh()
        // 刷新卡片占位背景(仅未加载图片的卡片,避免深色占位残留)
        iviCard.refreshPlaceholderIfEmpty()
        cardViews.forEach { it.refreshPlaceholderIfEmpty() }
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

        // 下排：3张卡片——整图模式,无图标无名称;点击行为由兜底/联网绑定统一设置
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
        }
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
                hideSkeletonOverlay(animate = false)  // 立即移除,避免骨架屏卡死
                return@post
            }

            lifecycleScope.launch {
                // ── 1. 先读快照/缓存/兜底(秒开,不依赖网络) ──
                val snapshotData = withContext(Dispatchers.IO) {
                    cardDataSource.loadLauncherDataSnapshot()
                }
                // 9 张卡的 URL 全部能从 Glide 磁盘缓存取出才返回 true，否则 null
                val cachedApps: List<GroupApp?>? = if (snapshotData != null) {
                    withContext(Dispatchers.IO) { loadCachedCardApps(snapshotData) }
                } else null

                // 无快照 → 裁剪本地背景图兜底
                val cutter = withContext(Dispatchers.IO) {
                    if (cachedApps != null) {
                        null  // 已从缓存恢复
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

                // ── 2. 渲染快照/兜底,马上可见 ──
                withContext(Dispatchers.Main) {
                    if (cachedApps != null) {
                        // 恢复上次联网的卡片内容
                        snapshotData?.config?.let { ThemeManager.apply(it) }
                        applyThemeBackgrounds()
                        refreshThemeColors()
                        snapshotData?.logoUrl?.let { statusBar.setLogoUrl(it) }
                        bindCachedCards(cachedApps)
                    } else if (cutter != null) {
                        // 兜底:本地背景图块
                        backgroundCutter = cutter
                        iviCard.setCardBackground(cutter.getTile(0))
                        for (i in 1..8) {
                            val cardIdx = i - 1
                            if (cardIdx < cardViews.size) {
                                cardViews[cardIdx].setCardBackground(cutter.getTile(i))
                            }
                        }
                        // 6 张视频卡 + 3 张功能卡:叠加图标 + 绑定点击(深色/白色图标按背景明暗分区)
                        bindFallbackVideoCards()
                    }
                    setupFocusNavigation()
                    hideSkeletonOverlay()  // 秒开:首屏不等网络
                }

                // ── 3. 后台并发拉后端,utc 变化才刷新(不阻塞首屏) ──
                val rawData = withContext(Dispatchers.IO) {
                    cardDataSource.loadLauncherData()
                }
                val launcherData = rawData?.takeIf { hasBindableApps(it) }
                if (launcherData != null) {
                    withContext(Dispatchers.Main) {
                        // 应用后端主题 + 刷新卡片(utc 相同则 loadLauncherData 返回 null,不重绑)
                        launcherData.config?.let { ThemeManager.apply(it) }
                        applyThemeBackgrounds()
                        refreshThemeColors()
                        launcherData.logoUrl?.let { statusBar.setLogoUrl(it) }
                        bindCardsFromLauncherData(launcherData)
                        checkAppUpdates(launcherData)
                    }
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

    /**
     * 兜底主页：为视频卡叠加原应用图标并绑定点击,为功能卡叠加白色剪影图标并绑定点击。
     * 图标运行时从 packageManager 取(无需打包资源);未安装的应用不叠图标,点击 toast 未配置。
     * 视频卡原图标是自适应图标(自带背景),scale 取 0.38 避免过大;功能卡白剪影 0.5。
     */
    private fun bindFallbackVideoCards() {
        val targets = listOf(iviCard) + cardViews  // [0]=iviCard, [1..8]=cardViews[0..7]
        // 视频卡:原应用彩色图标。iviCard 大卡(YouTube)稍大 88dp,其余统一 56dp
        fallbackVideoApps.forEach { (idx, pkg) ->
            val card = targets.getOrNull(idx) ?: return@forEach
            val icon = try {
                packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null  // 未安装
            }
            if (icon != null) {
                card.setCardOverlayIcon(icon, sizeDp = if (idx == 0) 88 else 56)
                card.onCardClicked = {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) startActivity(intent)
                    else showDarkToast("应用无法启动")
                }
            } else {
                card.onCardClicked = { showDarkToast(R.string.not_configured) }
            }
        }
        // 功能卡:白色剪影图标,统一 56dp
        fallbackFuncCards.forEach { (idx, func) ->
            val card = targets.getOrNull(idx) ?: return@forEach
            card.setCardOverlayIcon(func.iconRes, sizeDp = 56)
            card.onCardClicked = { func.onClick(this) }
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
        android.app.Dialog(this, R.style.DialogAppUpdateTheme).apply {
            setContentView(R.layout.dialog_app_update)
            window?.apply {
                // 窗口默认不透明,透明背景无法穿透,会显示继承的主题深蓝。需设半透明格式。
                setBackgroundDrawableResource(android.R.color.transparent)
                setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            }
            findViewById<android.widget.TextView>(R.id.dialog_message)?.text = "以下应用有新版本：\n$names"
            findViewById<android.widget.Button>(R.id.btn_update)?.setOnClickListener {
                // 逐个下载安装全部 outdated 且有 apkUrl 的应用
                outdated.forEach { app -> appUpdater.downloadAndInstall(app) }
                dismiss()
            }
            findViewById<android.widget.Button>(R.id.btn_ignore)?.setOnClickListener { dismiss() }
            // 清除 PhoneWindow 装饰层内部容器背景(从主题 android:background 继承了 main_bg),
            // 只保留内容布局 dialog_bg 的圆角灰盒
            window?.decorView?.let { clearDecorContainerBackgrounds(it) }
            show()
        }
    }

    /** 清除 Dialog 装饰层内部容器背景,防止主题 main_bg 透出。内容背景(dialog_bg)不受影响。 */
    private fun clearDecorContainerBackgrounds(decor: android.view.View) {
        if (decor !is android.view.ViewGroup) return
        // DecorView > LinearLayout(screen) > [ViewStub, FrameLayout(content) > 我们的内容]
        val screen = decor.getChildAt(0) as? android.view.ViewGroup ?: return
        screen.background = null
        val content = screen.getChildAt(1) as? android.view.View ?: return
        content.background = null
    }

    /** 将单个应用绑定到卡片：图片（iconBgUrl 优先,回退 iconUrl） + 点击行为（packageName/intents） */
    private fun bindCard(card: LauncherCardView, app: GroupApp) {
        // 联网数据到达,卡片显示真实内容,移除兜底时的叠加图标
        card.clearCardOverlayIcon()
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
        if (::appUpdater.isInitialized) appUpdater.cleanup()
        // 安全网:协程取消时骨架屏可能未移除,兜底清理
        skeletonOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        skeletonOverlay = null
    }
}
