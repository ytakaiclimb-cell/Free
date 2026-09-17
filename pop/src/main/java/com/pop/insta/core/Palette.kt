package com.pop.insta.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap work the backdrops need: the paper colour, and the blur tile. */
object Palette {

    /**
     * The average colour of the outermost ring of the POP. On A4 artwork that
     * is the paper itself, so the fill reads as one continuous sheet.
     */
    fun paperColor(src: Bitmap): Int {
        val n = 24
        val thumb = Bitmap.createScaledBitmap(src, n, n, true)
        val pixels = IntArray(n * n)
        thumb.getPixels(pixels, 0, n, 0, 0, n, n)
        if (thumb !== src) thumb.recycle()
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (x != 0 && y != 0 && x != n - 1 && y != n - 1) continue
                val c = pixels[y * n + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
        }
        if (count == 0) return Color.WHITE
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    /**
     * A tiny cover-cropped copy of the POP. Stretched back up to the frame it
     * turns into the soft blur behind the artwork — no RenderScript needed.
     */
    fun blurTile(src: Bitmap, frameAspect: Float, width: Int = 48): Bitmap {
        val height = max(1, (width / frameAspect).roundToInt())
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val reduced = Scaler.reduce(src, width * 6)
        val outAspect = width.toFloat() / height
        val srcAspect = reduced.width.toFloat() / reduced.height
        val crop = if (srcAspect > outAspect) {
            val cw = (reduced.height * outAspect).roundToInt().coerceIn(1, reduced.width)
            val x = (reduced.width - cw) / 2
            Rect(x, 0, x + cw, reduced.height)
        } else {
            val ch = (reduced.width / outAspect).roundToInt().coerceIn(1, reduced.height)
            val y = (reduced.height - ch) / 2
            Rect(0, y, reduced.width, y + ch)
        }
        canvas.drawBitmap(reduced, crop, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        if (reduced !== src) reduced.recycle()
        return out
    }
}

/** Halving a bitmap before the final draw keeps small print readable. */
object Scaler {
    fun reduce(src: Bitmap, targetWidth: Int): Bitmap {
        if (targetWidth < 1) return src
        var current = src
        while (current.width / 2 >= targetWidth && current.width / 2 >= 1 && current.height / 2 >= 1) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== src) current.recycle()
            current = next
        }
        return current
    }
}
