package com.example.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.util.dpToPx
import com.example.tvlauncher.util.showDarkToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器 — 深色极简风格
 *
 * 视觉规范：
 *   - 页面背景 #0F1419（近黑蓝灰）
 *   - 顶部栏 56dp，底部 1px 微光分隔线
 *   - 列表行：8dp 圆角卡片、16dp 左右边距、8dp 行间距
 *   - 聚焦：背景 #1E2530 + 2dp 白描边 + 图标变白（无放大动画）
 *   - 文件/文件夹用 Material 风格矢量图标，按类型区分
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var pathText: TextView
    private lateinit var countText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout

    private val rootDir: File = Environment.getExternalStorageDirectory()
    private var currentDir: File = rootDir
    private val files = mutableListOf<FileItem>()

    data class FileItem(
        val file: File,
        val name: String,
        val isDirectory: Boolean,
        val size: String,
        val modified: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 确保窗口背景为深色渐变，与页面背景一致
        window.decorView.setBackgroundResource(R.drawable.fm_background)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.fm_background)
        }

        // ─── 顶部栏（56dp，底部微光分隔线） ───
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
        }

        // 返回按钮：矢量箭头 + 文字，聚焦白描边圆角框
        val backBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setPadding(dpToPx(10), dpToPx(6), dpToPx(14), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(2), Color.TRANSPARENT)
            }
            setOnClickListener { navigateUp() }
        }

        val backIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Color.parseColor("#F2F5F9"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22))
            isFocusable = false
        }
        backBtn.addView(backIcon)

        val backLabel = TextView(this).apply {
            text = "返回"
            setTextColor(Color.parseColor("#F2F5F9"))
            textSize = 15f
            isFocusable = false
            // 显式透明背景，避免继承窗口背景的渐变形成色块
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(6)
            }
        }
        backBtn.addView(backLabel)

        backBtn.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            val bg = view.background as GradientDrawable
            bg.setStroke(dpToPx(2), if (hasFocus) Color.WHITE else Color.TRANSPARENT)
        }
        toolbar.addView(backBtn)

        // 当前路径（超长向左滚动省略）
        pathText = TextView(this).apply {
            setTextColor(Color.parseColor("#F2F5F9"))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            // 显式透明背景，避免继承窗口背景的渐变形成色块
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                leftMargin = dpToPx(12)
                rightMargin = dpToPx(12)
            }
        }
        toolbar.addView(pathText)

        // 文件/文件夹计数
        countText = TextView(this).apply {
            setTextColor(Color.parseColor("#8A94A6"))
            textSize = 13f
            // 显式透明背景，避免继承窗口背景的渐变形成色块
            setBackgroundColor(Color.TRANSPARENT)
        }
        toolbar.addView(countText)

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56))
        )

        // 底部微光分隔线（1px）
        root.addView(
            View(this).apply {
                setBackgroundColor(Color.parseColor("#1AFFFFFF"))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1))
        )

        // ─── 列表 ───
        recyclerView = RecyclerView(this).apply {
            layoutManager = SyncScrollLinearLayoutManager(this@FileManagerActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            adapter = FileAdapter()
            // item 尺寸固定，快速滚动/聚焦时不重测，避免聚焦项移出可视区后跳动
            setHasFixedSize(true)
            // 聚焦项移出可视区时自动滚动回，防止快速移动焦点时列表项从屏幕消失
            isFocusable = true
        }
        root.addView(recyclerView)

        // ─── 空目录提示 ───
        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val emptyIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_folder_empty)
            setColorFilter(Color.parseColor("#6E7684"))
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(56))
        }
        emptyView.addView(emptyIcon)
        val emptyLabel = TextView(this).apply {
            text = "此文件夹为空"
            setTextColor(Color.parseColor("#8A94A6"))
            textSize = 14f
            // 显式透明背景，避免继承窗口背景的渐变形成色块
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        emptyView.addView(emptyLabel)
        root.addView(emptyView)

        setContentView(root)
        navigateTo(currentDir)
    }

    private fun navigateUp() {
        // 已位于最外层（内部存储根目录），返回即退出文件管理器
        if (currentDir == rootDir) {
            finish()
            return
        }
        val parent = currentDir.parentFile
        if (parent != null && parent.exists()) {
            navigateTo(parent)
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        navigateUp()
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        pathText.text = dir.absolutePath

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val listFiles = dir.listFiles()
                if (listFiles == null) {
                    emptyList<FileItem>()
                } else {
                    listFiles
                        .sortedWith(
                            compareBy<File> { !it.isDirectory }
                                .thenBy { it.name.lowercase() })
                        .map { f ->
                            FileItem(
                                file = f,
                                name = f.name,
                                isDirectory = f.isDirectory,
                                size = if (f.isFile) formatSize(f.length()) else "",
                                modified = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                ).format(Date(f.lastModified()))
                            )
                        }
                }
            }

            files.clear()
            files.addAll(items)
            recyclerView.adapter?.notifyDataSetChanged()

            // 空目录提示
            emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            countText.text = "${files.size} 项"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /** 按文件类型返回对应的矢量图标资源 */
    private fun iconResFor(file: File): Int {
        if (file.isDirectory) return R.drawable.ic_folder
        val name = file.name.lowercase()
        return when {
            name.endsWith(".png") || name.endsWith(".jpg") ||
                name.endsWith(".jpeg") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp") -> R.drawable.ic_file_image
            name.endsWith(".mp4") || name.endsWith(".mkv") ||
                name.endsWith(".avi") || name.endsWith(".ts") ||
                name.endsWith(".mov") || name.endsWith(".wmv") -> R.drawable.ic_file_video
            name.endsWith(".mp3") || name.endsWith(".wav") ||
                name.endsWith(".flac") || name.endsWith(".aac") ||
                name.endsWith(".m4a") || name.endsWith(".ogg") -> R.drawable.ic_file_audio
            name.endsWith(".pdf") || name.endsWith(".doc") ||
                name.endsWith(".docx") || name.endsWith(".txt") ||
                name.endsWith(".html") -> R.drawable.ic_file_doc
            name.endsWith(".apk") -> R.drawable.ic_file_apk
            else -> R.drawable.ic_file_generic
        }
    }

    inner class FileAdapter : RecyclerView.Adapter<FileAdapter.Holder>() {

        inner class Holder(
            view: View,
            val iconView: ImageView,
            val nameText: TextView,
            val detailText: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun getItemCount(): Int = files.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            // 直角矩形背景（默认透明，聚焦时不透明+白描边）——画在内层 item 上，紧贴内容
            val normalBg = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            val focusedBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E2530"))
            }
            // 外层容器：获得焦点，宽度撑满右侧，左边距让列表不贴左屏
            // 外层容器：获得焦点，无上下 padding，让内层 item 完全填充，背景占满整列
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 0)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = dpToPx(24)
                    rightMargin = dpToPx(24)
                }
            }

            // 内层 item：承载内容，背景画在这里（占满 container 整个区域）
            val item = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                background = normalBg
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val iconView = ImageView(parent.context).apply {
                setColorFilter(Color.parseColor("#6E7684"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    rightMargin = dpToPx(14)
                }
                isFocusable = false
            }
            item.addView(iconView)

            val nameText = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#F2F5F9"))
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isFocusable = false
                // 显式透明背景，避免继承窗口背景的渐变形成色块
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            item.addView(nameText)

            val detailText = TextView(parent.context).apply {
                setTextColor(Color.parseColor("#8A94A6"))
                textSize = 12f
                maxLines = 1
                isFocusable = false
                // 显式透明背景，避免继承窗口背景的渐变形成色块
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dpToPx(12) }
            }
            item.addView(detailText)

            container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    item.background = focusedBg
                    iconView.setColorFilter(Color.WHITE)
                } else {
                    item.background = normalBg
                    iconView.setColorFilter(Color.parseColor("#6E7684"))
                }
            }

            container.addView(item)
            return Holder(container, iconView, nameText, detailText)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = files[position]
            holder.iconView.setImageResource(iconResFor(item.file))
            holder.nameText.text = item.name
            holder.detailText.text = if (item.isDirectory) item.modified
            else "${item.size}  •  ${item.modified}"

            holder.itemView.setOnClickListener {
                if (item.isDirectory) {
                    navigateTo(item.file)
                } else {
                    openFile(item.file)
                }
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showDarkToast("无法打开文件: ${e.message}")
        }
    }

    private fun getMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".txt") -> "text/plain"
            name.endsWith(".html") -> "text/html"
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    /**
     * 垂直列表 LayoutManager — 焦点移动与屏幕滚动同步。
     * 聚焦项获得焦点的同一帧，重写 onRequestChildFocus 立即位移滚动，
     * 目标项越出可视区边界时直接 scrollToPosition，屏幕与焦点同步移动。
     */
    inner class SyncScrollLinearLayoutManager(
        context: Context
    ) : LinearLayoutManager(context) {

        override fun onRequestChildFocus(
            parent: RecyclerView,
            state: RecyclerView.State,
            child: View,
            focused: View?
        ): Boolean {
            // 聚焦项越过可视区边界时，只滚动刚好让它进入可视区的偏移量，避免跳 2 格
            val scrollOffset = computeVisibleScrollOffset(parent, child)
            if (scrollOffset != 0) {
                parent.scrollBy(0, scrollOffset)
            }
            return super.onRequestChildFocus(parent, state, child, focused)
        }

        /** 计算让 child 完全进入可视区所需的垂直偏移量（正值向下，负值向上） */
        private fun computeVisibleScrollOffset(parent: RecyclerView, child: View): Int {
            val childRect = Rect()
            child.getDrawingRect(childRect)
            parent.offsetDescendantRectToMyCoords(child, childRect)
            val parentHeight = parent.height
            val top = childRect.top
            val bottom = childRect.bottom
            return when {
                top < 0 -> top                        // 越出顶部：向上滚动 top 距离
                bottom > parentHeight -> bottom - parentHeight  // 越出底部：向下滚动超出距离
                else -> 0                              // 完全可见：不滚动
            }
        }
    }
}
