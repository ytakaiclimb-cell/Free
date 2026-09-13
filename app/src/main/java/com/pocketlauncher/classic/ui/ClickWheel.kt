package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private const val STEP_DEGREES = 13f
private const val TAP_SLOP_DEGREES = 7f
private const val HUB_FRACTION = 0.40f

private enum class Sector { MENU, NEXT, PLAY, PREV }

/**
 * The iPod click wheel.
 *
 * The ring reports rotation as discrete notches, one every [STEP_DEGREES], each
 * with a short haptic tick. A touch on the ring that barely rotates counts as a
 * press of whichever of the four labels it landed on. The centre hub is a
 * separate composable stacked on top, so it gets its own click and long-press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickWheel(
    wheelSize: Dp,
    onScroll: (Int) -> Unit,
    onCenterClick: () -> Unit,
    onCenterLongClick: () -> Unit,
    onMenu: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    // Keep the callbacks fresh: pointerInput(Unit) captures them only once.
    val currentScroll by rememberUpdatedState(onScroll)
    val currentMenu by rememberUpdatedState(onMenu)
    val currentPrev by rememberUpdatedState(onPrev)
    val currentNext by rememberUpdatedState(onNext)
    val currentPlay by rememberUpdatedState(onPlay)
    val currentCenterClick by rememberUpdatedState(onCenterClick)
    val currentCenterLongClick by rememberUpdatedState(onCenterLongClick)

    val labelInset = wheelSize * 0.07f
    val glyphWidth = wheelSize * 0.09f

    Box(
        modifier = modifier
            .size(wheelSize)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Palette.WheelTop, Palette.WheelBottom)))
            .border(1.dp, Palette.WheelEdge, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val hubRadius = size.width * HUB_FRACTION / 2f
                    val deadZone = 14.dp.toPx()

                    val down = awaitFirstDown()
                    val start = down.position - centre
                    // The hub composable on top handles its own touches.
                    if (start.getDistance() <= hubRadius) return@awaitEachGesture

                    var lastAngle = angleOf(start)
                    var accumulated = 0f
                    var travelled = 0f

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
                            accumulated += delta
                            travelled += abs(delta)

                            while (accumulated >= STEP_DEGREES) {
                                accumulated -= STEP_DEGREES
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentScroll(1)
                            }
                            while (accumulated <= -STEP_DEGREES) {
                                accumulated += STEP_DEGREES
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentScroll(-1)
                            }
                        }
                        if (change.positionChanged()) change.consume()
                    }

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
        Text(
            text = "MENU",
            color = Palette.WheelLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = labelInset),
        )
        PrevGlyph(
            color = Palette.WheelLabel,
            width = glyphWidth,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = labelInset),
        )
        NextGlyph(
            color = Palette.WheelLabel,
            width = glyphWidth,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = labelInset),
        )
        PlayPauseGlyph(
            color = Palette.WheelLabel,
            width = glyphWidth,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = labelInset),
        )

        Box(
            modifier = Modifier
                .size(wheelSize * HUB_FRACTION)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Palette.HubTop, Palette.HubBottom)))
                .border(1.dp, Palette.WheelEdge, CircleShape)
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
    Canvas(modifier.size(width, width * 0.55f)) {
        val w = size.width
        val h = size.height
        drawRect(color, topLeft = Offset(0f, 0f), size = Size(w * 0.16f, h))
        val triangle = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(w * 0.26f, h / 2f)
            close()
        }
        drawPath(triangle, color)
    }
}

@Composable
private fun NextGlyph(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 0.55f)) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, h)
            lineTo(w * 0.74f, h / 2f)
            close()
        }
        drawPath(triangle, color)
        drawRect(color, topLeft = Offset(w * 0.84f, 0f), size = Size(w * 0.16f, h))
    }
}

@Composable
private fun PlayPauseGlyph(color: Color, width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width * 1.3f, width * 0.55f)) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, h)
            lineTo(w * 0.42f, h / 2f)
            close()
        }
        drawPath(triangle, color)
        drawRect(color, topLeft = Offset(w * 0.58f, 0f), size = Size(w * 0.14f, h))
        drawRect(color, topLeft = Offset(w * 0.82f, 0f), size = Size(w * 0.14f, h))
    }
}
