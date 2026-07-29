package com.example.tvlauncher.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF

class BackgroundCutter(
    sourceBitmap: Bitmap,
    private val iviWidth: Int,
    private val rightAreaWidth: Int,
    private val contentHeight: Int,
    private val gapW: Int,
    private val gapH: Int
) {
    // 3 列卡片宽度和 2 列卡片宽度（基于右侧面板）
    private val tileW3 = (rightAreaWidth - 2 * gapW) / 3
    private val tileW2 = (rightAreaWidth - gapW) / 2
    private val tileH = (contentHeight - 2 * gapH) / 3

    // 将原图缩放以匹配整个内容区域（IVI + 右侧面板）
    private val scaledBitmap: Bitmap

    init {
        val totalWidth = iviWidth + rightAreaWidth
        val srcRect = RectF(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat())
        val dstRect = RectF(0f, 0f, totalWidth.toFloat(), contentHeight.toFloat())
        val matrix = Matrix()
        matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
        scaledBitmap = Bitmap.createBitmap(
            sourceBitmap, 0, 0,
            sourceBitmap.width, sourceBitmap.height, matrix, true
        )
    }

    /** 9 个图块定义：(x, y, w, h)，索引 0 为 IVI 面板，1-8 为右侧卡片 */
    private val tileDefs: List<TileDef> by lazy {
        val rightOffset = iviWidth
        listOf(
            // 索引 0：IVI 面板（左侧 iviWidth × 满高）
            TileDef(0, 0, iviWidth, contentHeight),
            // 索引 1-3：上排 3 张卡片（右侧）
            TileDef(rightOffset, 0, tileW3, tileH),
            TileDef(rightOffset + tileW3 + gapW, 0, tileW3, tileH),
            TileDef(rightOffset + 2 * (tileW3 + gapW), 0, tileW3, tileH),
            // 索引 4-5：中排 2 张卡片（右侧）
            TileDef(rightOffset, tileH + gapH, tileW2, tileH),
            TileDef(rightOffset + tileW2 + gapW, tileH + gapH, tileW2, tileH),
            // 索引 6-8：下排 3 张卡片（右侧）
            TileDef(rightOffset, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(rightOffset + tileW3 + gapW, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(rightOffset + 2 * (tileW3 + gapW), 2 * (tileH + gapH), tileW3, tileH)
        )
    }

    val tileCount: Int get() = 9

    fun getTile(index: Int): Bitmap {
        val def = tileDefs[index]
        return scaledBitmap.cropRegion(def.x, def.y, def.w, def.h)
    }

    fun getTileRect(index: Int): Rect {
        val def = tileDefs[index]
        return Rect(def.x, def.y, def.x + def.w, def.y + def.h)
    }

    fun recycle() {
        if (!scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
    }

    private data class TileDef(val x: Int, val y: Int, val w: Int, val h: Int)
}
