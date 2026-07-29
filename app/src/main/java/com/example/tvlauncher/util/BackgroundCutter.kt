package com.example.tvlauncher.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF

/**
 * 背景图片裁剪工具 — 将全局背景图裁剪为9个图块，分别分配给 IVI 面板和8张卡片
 *
 * 裁剪策略：
 *   1. 将背景原图通过 Matrix 缩放（FILL模式）覆盖整个内容区（IVI + 右侧面板）
 *   2. 将缩放后的图片按内容区的9个格子坐标裁剪
 *   3. 跳过卡片间隙（水平8dp、垂直8dp），使得背景图中不出现缝隙
 *
 * 图块索引映射：
 *   0: IVI面板（左侧1/4区域，全高）
 *   1-3: 右侧上排3张竖卡
 *   4-5: 右侧中排2张横卡
 *   6-8: 右侧下排3张竖卡
 */
class BackgroundCutter(
    sourceBitmap: Bitmap,
    private val iviWidth: Int,
    private val rightAreaWidth: Int,
    private val contentHeight: Int,
    private val gapW: Int,
    private val gapH: Int
) {
    // 右侧3列卡片的单卡宽度（扣除2个间隙后均分）
    private val tileW3 = (rightAreaWidth - 2 * gapW) / 3
    // 右侧2列卡片的单卡宽度（扣除1个间隙后均分）
    private val tileW2 = (rightAreaWidth - gapW) / 2
    // 每排卡片的高度（扣除2个垂直间隙后均分3排）
    private val tileH = (contentHeight - 2 * gapH) / 3

    // 缩放后的背景图，覆盖完整内容区
    private val scaledBitmap: Bitmap

    init {
        // 内容区总宽度 = IVI宽度 + 右侧卡片网格宽度
        val totalWidth = iviWidth + rightAreaWidth
        val srcRect = RectF(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat())
        val dstRect = RectF(0f, 0f, totalWidth.toFloat(), contentHeight.toFloat())
        val matrix = Matrix()
        // FILL 模式：拉伸填充，不保持原图比例，确保目标区域被完全覆盖
        matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
        scaledBitmap = Bitmap.createBitmap(
            sourceBitmap, 0, 0,
            sourceBitmap.width, sourceBitmap.height, matrix, true
        )
    }

    /** 9 个图块定义：(x偏移, y偏移, 宽度, 高度) */
    private val tileDefs: List<TileDef> by lazy {
        val rightOffset = iviWidth
        listOf(
            // 索引 0：IVI 面板（左侧 iviWidth × 全高度）
            TileDef(0, 0, iviWidth, contentHeight),
            // 索引 1-3：上排 3 张卡片
            TileDef(rightOffset, 0, tileW3, tileH),
            TileDef(rightOffset + tileW3 + gapW, 0, tileW3, tileH),
            TileDef(rightOffset + 2 * (tileW3 + gapW), 0, tileW3, tileH),
            // 索引 4-5：中排 2 张卡片（y偏移 = 上排高度 + 垂直间隙）
            TileDef(rightOffset, tileH + gapH, tileW2, tileH),
            TileDef(rightOffset + tileW2 + gapW, tileH + gapH, tileW2, tileH),
            // 索引 6-8：下排 3 张卡片（y偏移 = 上排 + 中排 + 2个垂直间隙）
            TileDef(rightOffset, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(rightOffset + tileW3 + gapW, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(rightOffset + 2 * (tileW3 + gapW), 2 * (tileH + gapH), tileW3, tileH)
        )
    }

    /** 图块总数 */
    val tileCount: Int get() = 9

    /** 获取指定索引的裁剪图块 */
    fun getTile(index: Int): Bitmap {
        val def = tileDefs[index]
        return scaledBitmap.cropRegion(def.x, def.y, def.w, def.h)
    }

    /** 获取指定图块在缩放后背景图中的坐标矩形 */
    fun getTileRect(index: Int): Rect {
        val def = tileDefs[index]
        return Rect(def.x, def.y, def.x + def.w, def.y + def.h)
    }

    /** 释放缩放背景图的资源 */
    fun recycle() {
        if (!scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
    }

    /** 图块坐标数据类 */
    private data class TileDef(val x: Int, val y: Int, val w: Int, val h: Int)
}
