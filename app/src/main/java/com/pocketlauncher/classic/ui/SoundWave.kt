package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val COLUMNS = 6
private const val SEGMENTS = 9

/**
 * The stack-of-segments meter under the selected app's name. It idles low and
 * jumps around while the wheel is being spun, so [energy] is the whole input.
 */
@Composable
fun SoundWave(energy: Float, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    var phase by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(70)
            phase += 0.42f
        }
    }

    Canvas(modifier) {
        val columnWidth = size.width / (COLUMNS * 1.75f)
        val gap = (size.width - columnWidth * COLUMNS) / (COLUMNS - 1).coerceAtLeast(1)
        val segmentGap = 2.dp.toPx()
        val segmentHeight = (size.height - segmentGap * (SEGMENTS - 1)) / SEGMENTS

        // The thin rule down the left edge.
        drawRect(skin.textFaint, Offset(0f, 0f), Size(1.dp.toPx(), size.height))

        for (column in 0 until COLUMNS) {
            val wobble = (sin(phase + column * 1.31f) + 1f) / 2f
            val level = (0.18f + wobble * 0.72f * energy) * SEGMENTS
            val lit = level.toInt().coerceIn(1, SEGMENTS)
            val x = 8.dp.toPx() + column * (columnWidth + gap)

            for (segment in 0 until SEGMENTS) {
                // Segment 0 is the bottom one.
                val y = size.height - (segment + 1) * segmentHeight - segment * segmentGap
                val colour = when {
                    segment == 0 -> skin.accent
                    segment < lit -> skin.text
                    else -> skin.textFaint
                }
                drawRect(colour, Offset(x, y), Size(columnWidth, segmentHeight))
            }
        }
    }
}
