package com.example.tvlauncher

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.data.QuickAppsStore
import com.example.tvlauncher.ui.LauncherCardView
import com.example.tvlauncher.ui.QuickBarView
import com.example.tvlauncher.ui.StatusBarView
import com.example.tvlauncher.util.BackgroundCutter
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.setFocusZoom
import com.example.tvlauncher.util.setSafeOnClickListener
import com.example.tvlauncher.util.setSafeOnLongClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusBar: StatusBarView
    private lateinit var quickBar: QuickBarView
    private lateinit var appRepo: AppRepository
    private lateinit var quickStore: QuickAppsStore
    private var backgroundCutter: BackgroundCutter? = null
    private val cardViews = mutableListOf<LauncherCardView>()

    // IVI panel views
    private lateinit var iviPanel: View
    private lateinit var iviBg: ImageView
    private lateinit var iviAppIcon: ImageView
    private lateinit var iviAppLabel: TextView

    // App assignment
    private var iviAppInfo: AppRepository.AppInfo? = null
    private val boundCardPackages = mutableSetOf<String>()

    companion object {
        const val PKG_LAZYMEDIA = "com.lazymedia.deluxe"
        const val PKG_YOUTUBE = "com.google.android.youtube.tv"
        const val PKG_GOOGLE_PLAY = "com.android.vending"

        val IVI_CANDIDATE_PACKAGES = listOf(
            "ru.ivi.client",
            "ru.ivi.client.tv"
        )

        val SYSTEM_CARD_LABELS = listOf(
            R.string.app_list,
            R.string.settings,
            R.string.file_manager
        )
        val SYSTEM_CARD_ICONS = listOf(
            R.drawable.ic_app_list,
            R.drawable.ic_settings,
            R.drawable.ic_file_manager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appRepo = AppRepository(this)
        quickStore = QuickAppsStore(this)

        setupStatusBar()
        setupQuickBar()
        setupIviPanel()
        buildCards()
        loadAppsAndBackground()
    }

    // ─── Status Bar ───────────────────────────────────────────────

    private fun setupStatusBar() {
        statusBar = StatusBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(32)
            )
        }
        val container = findViewById<View>(R.id.status_bar_container)
        (container as android.widget.FrameLayout).addView(statusBar)
    }

    // ─── Quick Bar ───────────────────────────────────────────────

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
                    Toast.makeText(this@MainActivity, "应用无法启动", Toast.LENGTH_SHORT).show()
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

    private fun setupIviPanel() {
        iviPanel = findViewById(R.id.ivi_panel)
        iviBg = findViewById(R.id.ivi_bg)
        iviAppIcon = findViewById(R.id.ivi_app_icon)
        iviAppLabel = findViewById(R.id.ivi_app_label)

        iviPanel.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                iviPanel.setFocusZoom(1.05f)
            } else {
                iviPanel.setFocusZoom(1.0f)
            }
        }

        iviPanel.setSafeOnClickListener {
            if (iviAppInfo != null) {
                val intent = packageManager.getLaunchIntentForPackage(iviAppInfo!!.packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "应用无法启动", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.ivi_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Card Building ─────────────────────────────────────────────

    private fun buildCards() {
        val rowTop = findViewById<LinearLayout>(R.id.row_top)
        val rowMiddle = findViewById<LinearLayout>(R.id.row_middle)
        val rowBottom = findViewById<LinearLayout>(R.id.row_bottom)

        // Top row: 3 tall cards
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            rowTop.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    if (i < 2) rightMargin = dpToPx(8)
                })
        }

        // Middle row: 2 wide cards
        for (i in 0 until 2) {
            val card = createCard(isWide = true)
            rowMiddle.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    if (i == 0) rightMargin = dpToPx(8)
                })
        }

        // Bottom row: 3 system function cards
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            card.setIconResource(SYSTEM_CARD_ICONS[i])
            card.setLabel(getString(SYSTEM_CARD_LABELS[i]))
            rowBottom.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    if (i < 2) rightMargin = dpToPx(8)
                })

            when (i) {
                0 -> card.onCardClicked = { openAppList() }
                1 -> card.onCardClicked = { openSettings() }
                2 -> card.onCardClicked = { openFileManager() }
            }
        }

        // cardViews indices: 0-2 top row, 3-4 middle row, 5-7 bottom row
        // Replaceable cards: index 2 (top-right) and index 4 (middle-right)
        cardViews[2].onCardLongClicked = { showReplaceAppDialog(2) }
        cardViews[4].onCardLongClicked = { showReplaceAppDialog(4) }
    }

    private fun createCard(isWide: Boolean): LauncherCardView {
        val card = LauncherCardView(this).apply {
            id = View.generateViewId()
            setIconLayout(iconAbove = !isWide)
            setAppInfo(null)
        }
        cardViews.add(card)
        return card
    }

    // ─── App Loading & Background ─────────────────────────────────

    private fun loadAppsAndBackground() {
        val rightPanel = findViewById<View>(R.id.right_panel)
        val mainContent = findViewById<View>(R.id.main_content)

        mainContent.post {
            val contentHeight = mainContent.height
            val iviW = iviPanel.width
            val rightW = rightPanel.width

            if (contentHeight <= 0 || iviW <= 0 || rightW <= 0) {
                applyOverlays()
                return@post
            }

            lifecycleScope.launch {
                // Find all apps on background thread
                val iviApp = withContext(Dispatchers.IO) { findIviApp() }
                val lazyMedia = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_LAZYMEDIA) }
                val youtube = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_YOUTUBE) }
                val googlePlay = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_GOOGLE_PLAY) }
                val allApps = withContext(Dispatchers.IO) { appRepo.getInstalledLaunchableApps() }

                iviAppInfo = iviApp

                // Track bound packages for random exclusion
                boundCardPackages.clear()
                boundCardPackages.add(packageName) // self
                if (iviApp != null) boundCardPackages.add(iviApp.packageName)
                if (lazyMedia != null) boundCardPackages.add(lazyMedia.packageName)
                if (youtube != null) boundCardPackages.add(youtube.packageName)
                if (googlePlay != null) boundCardPackages.add(googlePlay.packageName)

                // Cut background tiles
                val cutter = withContext(Dispatchers.IO) {
                    try {
                        val src = BitmapFactory.decodeResource(resources, R.drawable.bg_full)
                        BackgroundCutter(
                            src,
                            iviW,
                            rightW,
                            contentHeight,
                            dpToPx(8),
                            dpToPx(8)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    if (cutter != null) {
                        backgroundCutter = cutter
                        // Set IVI background (tile 0)
                        val iviTile = cutter.getTile(0)
                        iviBg.setImageBitmap(iviTile)

                        // Set card backgrounds (tiles 1-8 map to cardViews[0-7])
                        for (i in 1..8) {
                            val cardIdx = i - 1
                            if (cardIdx < cardViews.size) {
                                val tile = cutter.getTile(i)
                                cardViews[cardIdx].setCardBackground(tile)
                            }
                        }
                    }

                    // Update IVI panel content
                    if (iviApp != null) {
                        iviAppIcon.setImageDrawable(iviApp.icon)
                        iviAppLabel.text = iviApp.label
                    }

                    // Assign apps to top/middle cards
                    assignAppsToCards(allApps, lazyMedia, youtube, googlePlay)
                    applyOverlays()
                    setupFocusNavigation()
                }
            }
        }
    }

    private fun findIviApp(): AppRepository.AppInfo? {
        for (pkg in IVI_CANDIDATE_PACKAGES) {
            val info = appRepo.getAppInfo(pkg)
            if (info != null) return info
        }
        return null
    }

    private fun assignAppsToCards(
        allApps: List<AppRepository.AppInfo>,
        lazyMedia: AppRepository.AppInfo?,
        youtube: AppRepository.AppInfo?,
        googlePlay: AppRepository.AppInfo?
    ) {
        // cardViews: [0]=top-left, [1]=top-mid, [2]=top-right, [3]=mid-left, [4]=mid-right
        bindAppToCard(0, lazyMedia, "LazyMedia")
        bindAppToCard(1, youtube, "YouTube")
        bindRandomAppToCard(2, allApps)
        bindAppToCard(3, googlePlay, "Google Play")
        bindRandomAppToCard(4, allApps)
    }

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
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindRandomAppToCard(cardIndex: Int, allApps: List<AppRepository.AppInfo>) {
        if (cardIndex >= cardViews.size) return
        val card = cardViews[cardIndex]

        // Check SharedPreferences for user-selected binding first
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
                return
            }
        }

        // Random from available apps (excluding bound packages)
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

    private fun applyOverlays() {
        val orientations = listOf(
            null,
            null,
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            null,
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            null,
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        )

        val colors = listOf(
            listOf(getColor(R.color.card_overlay_1)),
            listOf(getColor(R.color.card_overlay_2)),
            listOf(getColor(R.color.card_overlay_3_start), getColor(R.color.card_overlay_3_end)),
            listOf(getColor(R.color.card_overlay_4_start), getColor(R.color.card_overlay_4_end)),
            listOf(getColor(R.color.card_overlay_5)),
            listOf(getColor(R.color.card_overlay_6_start), getColor(R.color.card_overlay_6_end)),
            listOf(getColor(R.color.card_overlay_7)),
            listOf(getColor(R.color.card_overlay_8_start), getColor(R.color.card_overlay_8_end))
        )

        for (i in 0 until 8) {
            if (i >= cardViews.size) break
            val orientation = orientations[i]
            val colorList = colors[i]
            if (orientation != null && colorList.size >= 2) {
                cardViews[i].setOverlayGradient(colorList[0], colorList[1], orientation)
            } else {
                cardViews[i].setOverlayColor(colorList[0])
            }
        }
    }

    // ─── Focus Navigation ─────────────────────────────────────────

    private fun setupFocusNavigation() {
        // IVI ↔ top row card 1 (cardViews[0] = LazyMedia)
        iviPanel.nextFocusRightId = cardViews[0].id
        cardViews[0].nextFocusLeftId = iviPanel.id

        // IVI ↔ middle row card 1 (cardViews[3] = Google Play)
        cardViews[3].nextFocusLeftId = iviPanel.id
    }

    // ─── System Functions ─────────────────────────────────────────

    private fun openAppList() {
        startActivity(Intent(this, AppListActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun openFileManager() {
        startActivity(Intent(this, FileManagerActivity::class.java))
    }

    private fun showAddAppDialog() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            if (apps.isEmpty()) {
                Toast.makeText(this@MainActivity, "没有找到可用的应用", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val appNames = apps.map { it.label }.toTypedArray()

            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.add_app)
                .setItems(appNames) { _, which ->
                    val selected = apps[which]
                    if (quickStore.addQuickApp(selected.packageName)) {
                        quickBar.refresh()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "应用已存在或无法添加",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showReplaceAppDialog(cardIndex: Int) {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            if (apps.isEmpty()) return@launch

            val appNames = apps.map { it.label }.toTypedArray()

            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.select_app)
                .setItems(appNames) { _, which ->
                    val selected = apps[which]
                    val prefs = getSharedPreferences("card_bindings", MODE_PRIVATE)
                    prefs.edit().putString("card_${cardIndex}_pkg", selected.packageName).apply()

                    // Immediately refresh the card
                    cardViews[cardIndex].setAppInfo(selected)
                    cardViews[cardIndex].onCardClicked = {
                        val intent = packageManager.getLaunchIntentForPackage(selected.packageName)
                        if (intent != null) startActivity(intent)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        statusBar.startListening()

        // Refresh random app cards
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }

            // Rebuild bound packages exclusion set
            boundCardPackages.clear()
            boundCardPackages.add(packageName)
            if (iviAppInfo != null) boundCardPackages.add(iviAppInfo!!.packageName)

            val lmPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_LAZYMEDIA)?.packageName }
            val ytPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_YOUTUBE)?.packageName }
            val gpPkg = withContext(Dispatchers.IO) { appRepo.getAppInfo(PKG_GOOGLE_PLAY)?.packageName }
            if (lmPkg != null) boundCardPackages.add(lmPkg)
            if (ytPkg != null) boundCardPackages.add(ytPkg)
            if (gpPkg != null) boundCardPackages.add(gpPkg)

            bindRandomAppToCard(2, apps)
            bindRandomAppToCard(4, apps)
        }
    }

    override fun onPause() {
        super.onPause()
        statusBar.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        statusBar.stopListening()
        backgroundCutter?.recycle()
    }
}
