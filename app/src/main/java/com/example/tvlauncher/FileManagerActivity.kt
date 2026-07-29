package com.example.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.util.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var pathText: TextView
    private lateinit var recyclerView: RecyclerView
    private var currentDir: File = Environment.getExternalStorageDirectory()
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC1A1A2E.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val backBtn = TextView(this).apply {
            text = "← 返回"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { navigateUp() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        toolbar.addView(backBtn)

        pathText = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                leftMargin = dpToPx(12)
                rightMargin = dpToPx(12)
            }
        }
        toolbar.addView(pathText)
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48))
        )

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@FileManagerActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            adapter = FileAdapter()
        }
        root.addView(recyclerView)

        setContentView(root)
        navigateTo(currentDir)
    }

    private fun navigateUp() {
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

    inner class FileAdapter : RecyclerView.Adapter<FileAdapter.Holder>() {

        inner class Holder(
            view: View,
            val iconText: TextView,
            val nameText: TextView,
            val detailText: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun getItemCount(): Int = files.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val item = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }

            val iconText = TextView(parent.context).apply {
                textSize = 20f
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                    rightMargin = dpToPx(12)
                }
                gravity = Gravity.CENTER
                isFocusable = false
            }

            val textGroup = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val nameText = TextView(parent.context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isFocusable = false
            }
            textGroup.addView(nameText)

            val detailText = TextView(parent.context).apply {
                setTextColor(0x99FFFFFF.toInt())
                textSize = 11f
                isFocusable = false
            }
            textGroup.addView(detailText)

            item.addView(iconText)
            item.addView(textGroup)

            item.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    item.setBackgroundColor(0x33334455.toInt())
                } else {
                    item.setBackgroundColor(0x00000000.toInt())
                }
            }

            return Holder(item, iconText, nameText, detailText)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = files[position]
            holder.iconText.text = if (item.isDirectory) "📁" else "📄"
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
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
