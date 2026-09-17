package com.pop.insta.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

/** Turns the on-screen composition into the actual 1080 px post. */
object Renderer {
    /** Laid over the blur so the POP itself stays the brightest thing. */
    private const val BLUR_SCRIM = 0x33000000

    val cream: Int = 0xFFF2F0E6.toInt()
    val ink: Int = 0xFF141416.toInt()

    fun backdropColor(backdrop: Backdrop, paper: Int): Int = when (backdrop) {
        Backdrop.PAPER -> paper
        Backdrop.WHITE -> Color.WHITE
        Backdrop.CREAM -> cream
        Backdrop.INK -> ink
        Backdrop.BLUR -> Color.BLACK
    }

    fun compose(
        src: Bitmap,
        format: PostFormat,
        mode: FitMode,
        backdrop: Backdrop,
        margin: Float,
        layout: Layout,
        paper: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val frameW = format.width.toFloat()
        val frameH = format.height.toFloat()

        if (backdrop == Backdrop.BLUR) {
            val tile = Palette.blurTile(src, format.aspect)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(
                tile,
                null,
                Rect(0, 0, format.width, format.height),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            canvas.drawColor(BLUR_SCRIM)
            tile.recycle()
        } else {
            canvas.drawColor(backdropColor(backdrop, paper))
        }

        val place = Composer.place(layout, src.width, src.height, frameW, frameH, margin, mode)
        val pad = Composer.pad(frameW, frameH, margin)
        val ready = Scaler.reduce(src, place.width.roundToInt())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        canvas.save()
        canvas.clipRect(pad, pad, frameW - pad, frameH - pad)
        canvas.drawBitmap(
            ready,
            null,
            RectF(place.left, place.top, place.right, place.bottom),
            paint,
        )
        canvas.restore()
        if (ready !== src) ready.recycle()
        return out
    }
}
