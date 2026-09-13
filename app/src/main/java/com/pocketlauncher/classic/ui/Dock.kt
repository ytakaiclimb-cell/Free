package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The shortcut row under the screen. The clock mark here is what puts the
 * launcher into clock mode.
 */
@Composable
fun DockBar(
    state: LauncherState,
    onHome: () -> Unit,
    onApps: () -> Unit,
    onClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(
            active = state.mode == Mode.MENU && state.page == Page.ROOT,
            description = "ホーム",
            onClick = onHome,
            draw = { colour -> drawHome(colour) },
        )
        DockButton(
            active = state.mode == Mode.MENU && state.page == Page.APPS,
            description = "すべてのアプリ",
            onClick = onApps,
            draw = { colour -> drawApps(colour) },
        )
        DockButton(
            active = state.mode == Mode.CLOCK,
            description = "時計",
            onClick = onClock,
            draw = { colour -> drawClock(colour) },
        )
    }
}

@Composable
private fun DockButton(
    active: Boolean,
    description: String,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colour = if (active) Palette.DockIconActive else Palette.DockIcon
    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = description }
            .clip(CircleShape)
            .background(if (active) Palette.BezelEdge else Color.Transparent)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { draw(colour) }
    }
}

private fun DrawScope.drawHome(colour: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w, h * 0.46f)
        lineTo(w * 0.84f, h * 0.46f)
        lineTo(w * 0.84f, h)
        lineTo(w * 0.16f, h)
        lineTo(w * 0.16f, h * 0.46f)
        lineTo(0f, h * 0.46f)
        close()
    }
    drawPath(path, colour)
}

private fun DrawScope.drawApps(colour: Color) {
    val w = size.width
    val cell = w * 0.42f
    val gap = w - cell * 2f
    drawRect(colour, Offset(0f, 0f), Size(cell, cell))
    drawRect(colour, Offset(cell + gap, 0f), Size(cell, cell))
    drawRect(colour, Offset(0f, cell + gap), Size(cell, cell))
    drawRect(colour, Offset(cell + gap, cell + gap), Size(cell, cell))
}

private fun DrawScope.drawClock(colour: Color) {
    val radius = size.minDimension / 2f - 1.dp.toPx()
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawCircle(colour, radius, centre, style = Stroke(width = 2.dp.toPx()))
    // hands at roughly ten past ten, the way a clock icon is always drawn
    drawLine(
        color = colour,
        start = centre,
        end = Offset(centre.x, centre.y - radius * 0.55f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = colour,
        start = centre,
        end = Offset(centre.x + radius * 0.45f, centre.y + radius * 0.28f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
