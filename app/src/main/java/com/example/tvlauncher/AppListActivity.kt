package com.example.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.data.AppRepository
import com.example.tvlauncher.util.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var appRepo: AppRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var countText: TextView
    private val apps = mutableListOf<AppRepository.AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appRepo = AppRepository(this)

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
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        toolbar.addView(backBtn)

        val titleView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val title = TextView(this).apply {
            text = "已安装应用"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
        }
        titleView.addView(title)

        countText = TextView(this).apply {
            text = "共 0 个"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
        }
        titleView.addView(countText)
        toolbar.addView(titleView)

        // Spacer for symmetry
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(60), ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        toolbar.addView(spacer)

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48))
        )

        // RecyclerView grid
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AppListActivity, 4)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            clipToPadding = false
            adapter = AppGridAdapter()
        }
        root.addView(recyclerView)

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
            countText.text = "共 ${apps.size} 个"
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    inner class AppGridAdapter : RecyclerView.Adapter<AppGridAdapter.Holder>() {

        inner class Holder(
            view: View,
            val icon: ImageView,
            val label: TextView
        ) : RecyclerView.ViewHolder(view)

        override fun getItemCount(): Int = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val item = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }

            val icon = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
                scaleType = ImageView.ScaleType.FIT_CENTER
                isFocusable = false
            }

            val label = TextView(parent.context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
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

            // Focus effect
            item.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    item.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                } else {
                    item.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }

            return Holder(item, icon, label)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label

            holder.itemView.setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@AppListActivity,
                        "应用无法启动",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
