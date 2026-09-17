package com.pop.insta.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Where the POP lands inside the frame, in frame pixels. */
data class Placement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * The user's pan and zoom. Offsets are a fraction of the frame, never pixels,
 * so the preview on screen and the 1080 px export agree exactly.
 */
data class Layout(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/**
 * The one piece of geometry both the preview and the exporter use. Everything
 * is expressed as a ratio of the frame, so it does not care whether the frame
 * is 900 px of screen or 1350 px of JPEG.
 */
object Composer {
    const val MIN_ZOOM = 0.3f
    const val MAX_ZOOM = 5f

    /** The equal border kept clear on every side. */
    fun pad(frameW: Float, frameH: Float, margin: Float): Float = margin * min(frameW, frameH)

    /** Clamps zoom, then keeps the POP from drifting off its own frame. */
    fun normalize(
        layout: Layout,
        srcW: Int,
        srcH: Int,
        frameW: Float,
        frameH: Float,
        margin: Float,
        mode: FitMode,
    ): Layout {
        val zoom = layout.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val pad = pad(frameW, frameH, margin)
        val innerW = max(1f, frameW - pad * 2f)
        val innerH = max(1f, frameH - pad * 2f)
        val base = baseScale(srcW, srcH, innerW, innerH, mode)
        val drawW = srcW * base * zoom
        val drawH = srcH * base * zoom
        // Either the POP is bigger than the frame and may be panned until an
        // edge lines up, or it is smaller and may be slid until it touches one.
        val limitX = abs(drawW - innerW) / 2f / frameW
        val limitY = abs(drawH - innerH) / 2f / frameH
        return Layout(
            zoom = zoom,
            offsetX = layout.offsetX.coerceIn(-limitX, limitX),
            offsetY = layout.offsetY.coerceIn(-limitY, limitY),
        )
    }

    fun place(
        layout: Layout,
        srcW: Int,
        srcH: Int,
        frameW: Float,
        frameH: Float,
        margin: Float,
        mode: FitMode,
    ): Placement {
        val safe = normalize(layout, srcW, srcH, frameW, frameH, margin, mode)
        val pad = pad(frameW, frameH, margin)
        val innerW = max(1f, frameW - pad * 2f)
        val innerH = max(1f, frameH - pad * 2f)
        val base = baseScale(srcW, srcH, innerW, innerH, mode)
        val drawW = srcW * base * safe.zoom
        val drawH = srcH * base * safe.zoom
        return Placement(
            left = pad + (innerW - drawW) / 2f + safe.offsetX * frameW,
            top = pad + (innerH - drawH) / 2f + safe.offsetY * frameH,
            width = drawW,
            height = drawH,
        )
    }

    private fun baseScale(srcW: Int, srcH: Int, innerW: Float, innerH: Float, mode: FitMode): Float {
        val w = innerW / max(1, srcW)
        val h = innerH / max(1, srcH)
        return if (mode == FitMode.CONTAIN) min(w, h) else max(w, h)
    }
}
