package com.pocketlauncher.classic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2

private const val SCROLL_STEP_DEGREES = 13f
private const val VOLUME_STEP_DEGREES = 26f
private const val TAP_SLOP_DEGREES = 7f
private const val HUB_FRACTION = 0.40f

/** How long the finger must rest on the ring before turning means "volume". */
private const val VOLUME_HOLD_MS = 320L

private enum class Sector { MENU, NEXT, PLAY, PREV }

/**
 * The click wheel.
 *
 * Turning the ring reports notches, one every [SCROLL_STEP_DEGREES], each with a
 * tick of haptics. Resting a finger on the ring for [VOLUME_HOLD_MS] before
 * turning switches that one gesture over to media volume. A touch that barely
 * turns counts as a press of whichever label it landed on. The hub is stacked on
 * top and owns its own click and long press.
 *
 * While audio is playing the whole wheel turns into a spinning record.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickWheel(
    wheelSize: Dp,
    record: Boolean,
    onScroll: (Int) -> Unit,
    onVolumeStart: () -> Unit,
    onVolumeStep: (Int) -> Unit,
    onVolumeEnd: () -> Unit,
    onCenterClick: () -> Unit,
    onCenterLongClick: () -> Unit,
    onMenu: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val haptics = LocalHapticFeedback.current

    // pointerInput captures its lambdas once, so read them through holders.
    val currentScroll by rememberUpdatedState(onScroll)
    val currentVolumeStart by rememberUpdatedState(onVolumeStart)
    val currentVolumeStep by rememberUpdatedState(onVolumeStep)
    val currentVolumeEnd by rememberUpdatedState(onVolumeEnd)
    val currentMenu by rememberUpdatedState(onMenu)
    val currentPrev by rememberUpdatedState(onPrev)
    val currentNext by rememberUpdatedState(onNext)
    val currentPlay by rememberUpdatedState(onPlay)
    val currentCenterClick by rememberUpdatedState(onCenterClick)
    val currentCenterLongClick by rememberUpdatedState(onCenterLongClick)

    val spin = rememberInfiniteTransition(label = "record")
    val spinAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "record-angle",
    )

    val labelInset = wheelSize * 0.075f
    val glyphWidth = wheelSize * 0.085f

    Box(
        modifier = modifier
            .size(wheelSize)
            .graphicsLayer { rotationZ = if (record) spinAngle else 0f }
            .clip(CircleShape)
            .background(skin.wheel)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val hubRadius = size.width * HUB_FRACTION / 2f
                    val deadZone = 14.dp.toPx()

                    val down = awaitFirstDown()
                    val start = down.position - centre
                    // The hub composable on top handles its own touches.
                    if (start.getDistance() <= hubRadius) return@awaitEachGesture

                    val pressedAt = System.currentTimeMillis()
                    var lastAngle = angleOf(start)
                    var accumulated = 0f
                    var travelled = 0f
                    var decided = false
                    var volumeMode = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val vector = change.position - centre
                        if (vector.getDistance() > deadZone) {
                            val angle = angleOf(vector)
                            var delta = angle - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            lastAngle = angle

                            if (!decided && abs(delta) > 0.5f) {
                                decided = true
                                volumeMode =
                                    System.currentTimeMillis() - pressedAt >= VOLUME_HOLD_MS
                                if (volumeMode) currentVolumeStart()
                            }

                            accumulated += delta
                            travelled += abs(delta)

                            val step = if (volumeMode) VOLUME_STEP_DEGREES else SCROLL_STEP_DEGREES
                            while (accumulated >= step) {
                                accumulated -= step
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (volumeMode) currentVolumeStep(1) else currentScroll(1)
                            }
                            while (accumulated <= -step) {
                                accumulated += step
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (volumeMode) currentVolumeStep(-1) else currentScroll(-1)
                            }
                        }
                        if (change.positionChanged()) change.consume()
                    }

                    if (volumeMode) currentVolumeEnd()

                    if (travelled < TAP_SLOP_DEGREES) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (sectorOf(angleOf(start))) {
                            Sector.MENU -> currentMenu()
                            Sector.NEXT -> currentNext()
                            Sector.PLAY -> currentPlay()
                            Sector.PREV -> currentPrev()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Grooves: a faint tone arm normally, record rings while music plays.
        Canvas(Modifier.size(wheelSize)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            if (record) {
                var radius = outer * 0.46f
                while (radius < outer * 0.95f) {
                    drawCircle(skin.groove, radius, centre, style = Stroke(width = 1.dp.toPx()))
                    radius += 6.dp.toPx()
                }
            }
            drawLine(
                color = skin.groove,
                start = Offset(
                    centre.x + outer * 0.42f * 0.78f,
                    centre.y - outer * 0.42f * 0.62f,
                ),
                end = Offset(centre.x + outer * 0.72f, centre.y - outer * 0.56f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        Text(
            text = "MENU",
            color = skin.wheelLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = labelInset),
        )
        PrevGlyph(
            skin.wheelLabel,
            glyphWidth,
            Modifier.align(Alignment.CenterStart).padding(start = labelInset),
        )
        NextGlyph(
            skin.wheelLabel,
            glyphWidth,
            Modifier.align(Alignment.CenterEnd).padding(end = labelInset),
        )
        PlayPauseGlyph(
            skin.wheelLabel,
            glyphWidth,
            Modifier.align(Alignment.BottomCenter).padding(bottom = labelInset),
        )

        Box(
            modifier = Modifier
                .size(wheelSize * HUB_FRACTION)
                .clip(CircleShape)
                .background(skin.wheelHub)
                .combinedClickable(
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentCenterLongClick()
                    },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentCenterClick()
                    },
                ),
        )
    }
}

/** 0 degrees points straight up, growing clockwise. */
private fun angleOf(vector: Offset): Float {
    val degrees = Math.toDegrees(atan2(vector.x.toDouble(), -vector.y.toDouble())).toFloat()
    return (degrees + 360f) % 360f
}

private fun sectorOf(angle: Float): Sector = when {
    angle >= 315f || angle < 45f -> Sector.MENU
    angle < 135f -> Sector.NEXT
    angle < 225f -> Sector.PLAY
    else -> Sector.PREV
}

@Composable
private fun PrevGlyph(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width * 1.5f, width * 0.62f)) {
        val w = size.width
        val h = size.height
        drawPath(leftTriangle(w * 0.46f, h), color)
        drawPath(leftTriangle(w, h, w * 0.54f), color)
    }
}

@Composable
private fun NextGlyph(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width * 1.5f, width * 0.62f)) {
        val w = size.width
        val h = size.height
        drawPath(rightTriangle(w * 0.46f, h), color)
        drawPath(rightTriangle(w, h, w * 0.54f), color)
    }
}

@Composable
private fun PlayPauseGlyph(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width * 1.9f, width * 0.62f)) {
        val w = size.width
        val h = size.height
        drawPath(rightTriangle(w * 0.34f, h), color)
        // the slash between play and pause
        drawLine(
            color = color,
            start = Offset(w * 0.52f, h),
            end = Offset(w * 0.62f, 0f),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawRect(color, Offset(w * 0.72f, 0f), Size(w * 0.10f, h))
        drawRect(color, Offset(w * 0.90f, 0f), Size(w * 0.10f, h))
    }
}

private fun leftTriangle(right: Float, height: Float, left: Float = 0f): Path = Path().apply {
    moveTo(right, 0f)
    lineTo(right, height)
    lineTo(left, height / 2f)
    close()
}

private fun rightTriangle(right: Float, height: Float, left: Float = 0f): Path = Path().apply {
    moveTo(left, 0f)
    lineTo(left, height)
    lineTo(right, height / 2f)
    close()
}
