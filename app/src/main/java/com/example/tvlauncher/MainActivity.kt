package com.example.tvlauncher

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
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

    // Fixed function card indices in cardViews list
    // Indices: 0-2 top row, 3-4 middle row, 5-7 bottom row
    private val bottomCardIndices = listOf(5, 6, 7)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appRepo = AppRepository(this)
        quickStore = QuickAppsStore(this)

        setupStatusBar()
        setupQuickBar()
        buildCards()
        loadBackground()
    }

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

    private fun buildCards() {
        val rowTop = findViewById<LinearLayout>(R.id.row_top)
        val rowMiddle = findViewById<LinearLayout>(R.id.row_middle)
        val rowBottom = findViewById<LinearLayout>(R.id.row_bottom)

        // Top row: 3 tall cards (1/3 width each)
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            rowTop.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    if (i < 2) rightMargin = dpToPx(8)
                })
        }

        // Middle row: 2 wide cards (1/2 width each)
        for (i in 0 until 2) {
            val card = createCard(isWide = true)
            rowMiddle.addView(
                card, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    if (i == 0) rightMargin = dpToPx(8)
                })
        }

        // Bottom row: 3 fixed-function cards
        val bottomLabels = listOf(
            getString(R.string.app_list),
            getString(R.string.settings),
            getString(R.string.file_manager)
        )
        for (i in 0 until 3) {
            val card = createCard(isWide = false)
            card.setAppInfo(null)
            card.setLabel(bottomLabels[i])
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
    }

    private fun createCard(isWide: Boolean): LauncherCardView {
        val card = LauncherCardView(this).apply {
            setIconLayout(iconAbove = !isWide)
            setAppInfo(null)
        }
        cardViews.add(card)
        return card
    }

    private fun loadBackground() {
        val rightPanel = findViewById<View>(R.id.right_panel)
        rightPanel.post {
            val panelWidth = rightPanel.width
            val panelHeight = rightPanel.height

            if (panelWidth <= 0 || panelHeight <= 0) {
                applyOverlays()
                return@post
            }

            lifecycleScope.launch {
                val cutter = withContext(Dispatchers.IO) {
                    try {
                        val src = BitmapFactory.decodeResource(resources, R.drawable.bg_full)
                        BackgroundCutter(
                            src,
                            panelWidth,
                            panelHeight,
                            dpToPx(8),
                            dpToPx(8)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                if (cutter != null) {
                    backgroundCutter = cutter
                    for (i in 0 until 8) {
                        if (i < cardViews.size) {
                            val tile = cutter.getTile(i)
                            cardViews[i].setCardBackground(tile)
                        }
                    }
                }
                applyOverlays()
            }
        }
    }

    private fun applyOverlays() {
        val orientations = listOf(
            null, // card 0: solid
            null, // card 1: solid
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, // card 2: diag gradient
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, // card 3: H gradient
            null, // card 4: solid
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, // card 5: V gradient
            null, // card 6: solid
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT // card 7: H gradient
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

    override fun onResume() {
        super.onResume()
        statusBar.startListening()
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
