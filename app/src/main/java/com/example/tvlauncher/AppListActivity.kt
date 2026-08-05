package com.example.tvlauncher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.showDarkToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 已安装应用列表 — 靠左网格
 *
 * 结构：ScrollView > 垂直LinearLayout（靠左排列）
 *   - 每行6个 110dp 正方形应用框
 *   - 横向/纵向间隙统一 8dp
 *   - 网格靠左，行内从左排起，未满行也靠左
 *   - 聚焦：1.1x缩放 + 4dp白色描边
 */
class AppListActivity : AppCompatActivity() {

    private lateinit var appRepo: AppRepository
    private lateinit var gridContent: LinearLayout
    private lateinit var panel: LinearLayout
    private val apps = mutableListOf<AppRepository.AppInfo>()

    private val squareSize by lazy { dpToPx(110) }
    private val gap by lazy { dpToPx(8) }
    private val perRow = 6
    private val panelColor = 0xFF3E6BC8.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appRepo = AppRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF00009B.toInt())
        }

        // Toolbar（透明背景，融入根布局）
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val title = TextView(this).apply {
            text = "已安装应用"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        toolbar.addView(title)

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48))
        )

        // 垂直滚动容器。关闭裁剪让聚焦放大不被截断；不撑满视口，内容从顶部开始
        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            clipChildren = false
            clipToPadding = false
        }

        // 外层垂直容器：撑满宽度 + 靠左排列（不居中，网格从左侧排起）
        gridContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dpToPx(12), 0, dpToPx(12))
        }

        scrollView.addView(
            gridContent,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 面板容器：透明（面板不再渲染），网格按钮自身是浅蓝色块
        // 间隙 margin 透明，露出深蓝底，形成「浅蓝按钮 + 深蓝缝隙」
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            // 左边距 36dp 对齐主界面安全边距(防过扫描),右侧保留小边距
            setPadding(dpToPx(36), dpToPx(12), dpToPx(12), dpToPx(12))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        gridContent.addView(panel)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                appRepo.getInstalledLaunchableApps()
            }
            apps.clear()
            apps.addAll(loaded)
            buildGrid()
        }
    }

    /** 按 perRow 分组构建网格行 */
    private fun buildGrid() {
        panel.removeAllViews()
        var row: LinearLayout? = null
        var indexInRow = 0
        var firstItem: View? = null
        apps.forEach { app ->
            if (indexInRow == 0) {
                row = createRow()
                panel.addView(row)
            }
            val item = createAppItem(app)
            if (indexInRow > 0) {
                val lp = item.layoutParams as LinearLayout.LayoutParams
                lp.leftMargin = gap
                item.layoutParams = lp
            }
            row?.addView(item)
            if (firstItem == null) firstItem = item
            indexInRow = (indexInRow + 1) % perRow
        }
        // 视图附加完成后，把焦点落在第一个应用上
        firstItem?.post { firstItem.requestFocus() }
    }

    /** 创建一个水平行，非首行加顶部间隙；行背景透明，横向间隙露出深蓝底 */
    private fun createRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        // 靠左排列：行内按钮从左排起，未满行也靠左
        gravity = Gravity.LEFT
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (panel.childCount > 0) {
            lp.topMargin = gap
        }
        layoutParams = lp
    }

    /** 创建单个 110dp 正方形应用框 */
    private fun createAppItem(app: AppRepository.AppInfo): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(squareSize, squareSize)
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            layoutParams = LinearLayout.LayoutParams(dpToPx(58), dpToPx(58))
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = false
        }

        val label = TextView(this).apply {
            text = app.label
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(6) }
            isFocusable = false
        }

        item.addView(icon)
        item.addView(label)

        // 按钮背景：纯色面板浅蓝 #2D4AB9，浮在深蓝底上；聚焦：4dp白边+1.1x缩放
        val normalBg = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = 0f
        }
        val focusedBg = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = 0f
            setStroke(dpToPx(4), Color.WHITE)
        }
        item.background = normalBg

        item.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                item.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                item.background = focusedBg
            } else {
                item.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                item.background = normalBg
            }
        }

        item.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                startActivity(intent)
                finish()
            } else {
                showDarkToast("应用无法启动")
            }
        }

        return item
    }
}
