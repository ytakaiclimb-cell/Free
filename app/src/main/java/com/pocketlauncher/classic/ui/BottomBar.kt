package com.pocketlauncher.classic.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The grab bar above the switches. Dragging up from here opens the app list. */
@Composable
fun HomeIndicator(onSwipeUp: () -> Unit, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -10f) onSwipeUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(128.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(skin.track),
        )
    }
}

/** Y, clock and dark-mode, as three labelled switches. */
@Composable
fun BottomSwitches(state: LauncherState, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Y",
            color = skin.textDim,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, end = 2.dp),
        )
        Switch(checked = false, onToggle = { state.openAssistant() })

        ClockGlyph(skin.textDim, Modifier.padding(start = 8.dp, end = 2.dp))
        Switch(checked = state.screen == Screen.CLOCK, onToggle = { state.toggleClock() })

        Box(
            Modifier
                .padding(start = 8.dp, end = 2.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(skin.textDim),
        )
        Switch(checked = state.dark, onToggle = { state.toggleDark() })
    }
}

@Composable
private fun Switch(checked: Boolean, onToggle: () -> Unit) {
    val skin = LocalSkin.current
    val haptics = LocalHapticFeedback.current
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 34.dp else 4.dp,
        animationSpec = spring(),
        label = "switch-knob",
    )
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(if (checked) skin.accent else skin.track)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(30.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else skin.knob),
        )
    }
}

@Composable
private fun ClockGlyph(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(19.dp)) {
        val radius = size.minDimension / 2f - 1.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(colour, radius, centre, style = Stroke(width = 1.6.dp.toPx()))
        drawLine(
            colour,
            centre,
            Offset(centre.x, centre.y - radius * 0.56f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            colour,
            centre,
            Offset(centre.x + radius * 0.44f, centre.y + radius * 0.26f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
