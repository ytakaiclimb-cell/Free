package com.pocketlauncher.classic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketlauncher.classic.service.Badges

/** How far the knob must travel before the end it points at fires. */
private const val TRIGGER_PX = 46f

/**
 * The bank of four faders beside the wheel.
 *
 * Each one carries two apps, one at each end: flick the knob up for the top
 * app, down for the bottom one. A fader whose app is showing a notification
 * swaps its accent for the badge colour. In clock mode the ends become
 * countdown presets instead.
 */
@Composable
fun FaderBank(
    state: LauncherState,
    clockMode: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    // Passed down so reassigning a slot recomposes every fader.
    val revision = state.faderRevision

    val columnWidth = width / 2
    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(width * 0.08f),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(width * 0.06f)) {
                for (column in 0 until 2) {
                    val slot = (row * 2 + column) * 2
                    Fader(
                        state = state,
                        clockMode = clockMode,
                        upSlot = slot,
                        downSlot = slot + 1,
                        revision = revision,
                        width = columnWidth * 0.9f,
                    )
                }
            }
        }
    }
}

@Composable
private fun Fader(
    state: LauncherState,
    clockMode: Boolean,
    upSlot: Int,
    downSlot: Int,
    revision: Int,
    width: Dp,
) {
    val skin = LocalSkin.current
    val haptics = LocalHapticFeedback.current

    val upApp = remember(upSlot, revision) { state.faderApp(upSlot) }
    val downApp = remember(downSlot, revision) { state.faderApp(downSlot) }
    val badged = Badges.packages.let { packages ->
        (upApp != null && upApp.packageName in packages) ||
            (downApp != null && downApp.packageName in packages)
    }
    val endColour = if (badged) skin.badge else skin.accent

    var drag by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(drag, spring(), label = "fader-knob")

    val trackHeight = width * 2.9f
    val capHeight = width * 0.46f
    val knobHeight = width * 0.52f

    Box(
        modifier = Modifier
            .width(width)
            .height(trackHeight)
            .pointerInput(upSlot, downSlot, clockMode) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag <= -TRIGGER_PX) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            fire(state, clockMode, upSlot)
                        } else if (drag >= TRIGGER_PX) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            fire(state, clockMode, downSlot)
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, dragAmount ->
                    drag = (drag + dragAmount).coerceIn(-TRIGGER_PX * 1.4f, TRIGGER_PX * 1.4f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // rail
        Box(
            Modifier
                .width(width * 0.2f)
                .height(trackHeight)
                .clip(CircleShape)
                .background(skin.track),
        )

        FaderEnd(
            label = if (clockMode) TIMER_PRESETS.getOrNull(upSlot)?.toString() else null,
            colour = endColour,
            width = width * 0.34f,
            height = capHeight,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        FaderEnd(
            label = if (clockMode) TIMER_PRESETS.getOrNull(downSlot)?.toString() else null,
            colour = endColour,
            width = width * 0.34f,
            height = capHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        Box(
            modifier = Modifier
                .graphicsLayer { translationY = offset }
                .width(width)
                .height(knobHeight)
                .clip(RoundedCornerShape(width * 0.16f))
                .background(skin.knob),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(width * 0.09f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(width * 0.64f).height(1.dp).background(skin.knobLine))
                Box(Modifier.width(width * 0.64f).height(3.dp).background(endColour))
                Box(Modifier.width(width * 0.64f).height(1.dp).background(skin.knobLine))
            }
        }
    }
}

@Composable
private fun FaderEnd(
    label: String?,
    colour: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(CircleShape)
            .background(colour),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 1.dp),
            )
        }
    }
}

private fun fire(state: LauncherState, clockMode: Boolean, slot: Int) {
    if (clockMode) {
        TIMER_PRESETS.getOrNull(slot)?.let { state.startTimer(it) }
    } else {
        state.faderApp(slot)?.let { state.launch(it) }
    }
}
