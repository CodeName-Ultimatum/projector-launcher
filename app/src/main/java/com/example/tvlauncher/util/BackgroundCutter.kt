package com.example.tvlauncher.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF

class BackgroundCutter(
    sourceBitmap: Bitmap,
    private val rightAreaWidth: Int,
    private val rightAreaHeight: Int,
    private val gapW: Int,
    private val gapH: Int
) {
    private val tileW3 = (rightAreaWidth - 2 * gapW) / 3
    private val tileW2 = (rightAreaWidth - gapW) / 2
    private val tileH = (rightAreaHeight - 2 * gapH) / 3

    private val scaledBitmap: Bitmap

    init {
        val srcRect = RectF(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat())
        val dstRect = RectF(0f, 0f, rightAreaWidth.toFloat(), rightAreaHeight.toFloat())
        val matrix = Matrix()
        matrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.CENTER)
        scaledBitmap = Bitmap.createBitmap(
            sourceBitmap, 0, 0,
            sourceBitmap.width, sourceBitmap.height, matrix, true
        )
    }

    private val tileDefs: List<TileDef> by lazy {
        listOf(
            // Row top: 3 cards, 1/3 width each
            TileDef(0, 0, tileW3, tileH),
            TileDef(tileW3 + gapW, 0, tileW3, tileH),
            TileDef(2 * (tileW3 + gapW), 0, tileW3, tileH),
            // Row middle: 2 cards, 1/2 width each
            TileDef(0, tileH + gapH, tileW2, tileH),
            TileDef(tileW2 + gapW, tileH + gapH, tileW2, tileH),
            // Row bottom: 3 cards, 1/3 width each
            TileDef(0, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(tileW3 + gapW, 2 * (tileH + gapH), tileW3, tileH),
            TileDef(2 * (tileW3 + gapW), 2 * (tileH + gapH), tileW3, tileH)
        )
    }

    val tileCount: Int get() = 8

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
