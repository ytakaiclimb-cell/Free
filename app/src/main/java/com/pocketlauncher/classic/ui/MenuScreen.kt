package com.pocketlauncher.classic.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val RowHeight = 70.dp

/** Where the selected row sits, as a fraction of the screen height. */
private const val ANCHOR = 0.42f

/**
 * The iPod menu: one tall list that slides under a fixed selection point, rows
 * shrinking and fading the further they are from it.
 */
@Composable
fun MenuScreen(state: LauncherState, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val entries = state.menuEntries
    val selected = state.menuIndex

    BoxWithConstraints(modifier.fillMaxSize()) {
        val anchorY = maxHeight * ANCHOR
        val shift by animateDpAsState(
            targetValue = anchorY - RowHeight * selected,
            animationSpec = spring(),
            label = "menu-shift",
        )

        Column(Modifier.offset(y = shift)) {
            entries.forEachIndexed { index, entry ->
                MenuRow(
                    label = entry.label,
                    selected = index == selected,
                    enabled = entry.enabled,
                    distance = abs(index - selected),
                )
            }
        }

        Text(
            text = "${selected + 1} / ${entries.size}",
            color = skin.textDim,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = maxHeight * 0.22f)
                .padding(end = 4.dp),
        )
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, enabled: Boolean, distance: Int) {
    val skin = LocalSkin.current
    val alpha = (1f / (1f + 0.85f * distance)).coerceIn(0f, 1f)
    val fontSize = (40f - 9f * distance).coerceAtLeast(19f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .width(16.dp)
                        .height(3.dp)
                        .background(skin.accent),
                )
            }
        }
        Text(
            text = label,
            color = if (enabled) skin.text else skin.textDim,
            fontSize = fontSize.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Chevron(skin.textDim, 12.dp, Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
private fun Chevron(colour: Color, glyphSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(glyphSize, glyphSize * 1.6f)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
        }
        drawPath(path, colour, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
