package com.pocketlauncher.classic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How many neighbours either side of the selection stay on screen. */
private const val VISIBLE_SPAN = 4

/** Degrees between two neighbouring apps on the ring. */
private const val ANGLE_STEP = 13f

/** Where the selected app sits, as a fraction of the content box. */
private const val ANCHOR_X = 0.72f
private const val ANCHOR_Y = 0.33f

/** Centre of the ring, also as a fraction of the content box. */
private const val CENTRE_X = 0.10f
private const val CENTRE_Y = 0.15f

/**
 * The home screen: apps threaded onto a circle that the wheel turns. The
 * selected app sits at the anchor with its name spelled out large on the left;
 * its neighbours shrink and fade as they run away down the arc. Spinning the
 * wheel hard pushes the whole ring outwards and shakes the meter.
 */
@Composable
fun HomeRing(state: LauncherState, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val selected = state.ringIndex
    val apps = state.apps

    val density = LocalDensity.current

    val expansion by animateFloatAsState(
        targetValue = 1f + 0.14f * state.energy,
        animationSpec = spring(),
        label = "ring-expansion",
    )

    BoxWithConstraints(modifier) {
        val width = maxWidth
        val height = maxHeight
        val iconSize = width * 0.17f

        val centre = with(density) {
            Offset((width * CENTRE_X).toPx(), (height * CENTRE_Y).toPx())
        }
        val anchor = with(density) {
            Offset((width * ANCHOR_X).toPx(), (height * ANCHOR_Y).toPx())
        }
        val radius = (anchor - centre).getDistance() * expansion
        val anchorAngle = Math.toDegrees(
            atan2((anchor.y - centre.y).toDouble(), (anchor.x - centre.x).toDouble()),
        ).toFloat()

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("読み込み中…", color = skin.textDim, fontSize = 14.sp)
            }
            return@BoxWithConstraints
        }

        // The tone-arm line that points at the selected app.
        Canvas(Modifier.fillMaxSize()) {
            val end = Offset(
                centre.x + radius * cos(Math.toRadians(anchorAngle.toDouble())).toFloat(),
                centre.y + radius * sin(Math.toRadians(anchorAngle.toDouble())).toFloat(),
            )
            val start = Offset(size.width * 0.42f, end.y)
            drawLine(skin.textFaint, start, end, strokeWidth = 1.dp.toPx())
            drawCircle(skin.textFaint, 5.dp.toPx(), start, style = Stroke(1.dp.toPx()))
        }

        val first = (selected.roundToInt() - VISIBLE_SPAN).coerceAtLeast(0)
        val last = (selected.roundToInt() + VISIBLE_SPAN).coerceAtMost(apps.lastIndex)

        for (index in first..last) {
            val distance = index - selected
            val angle = anchorAngle + distance * ANGLE_STEP
            val radians = Math.toRadians(angle.toDouble())
            val x = centre.x + radius * cos(radians).toFloat()
            val y = centre.y + radius * sin(radians).toFloat()

            val falloff = abs(distance)
            val scale = 1f / (1f + 0.55f * falloff)
            val alpha = (1f / (1f + 0.95f * falloff)).coerceIn(0f, 1f)
            if (alpha < 0.04f) continue

            val entry = apps[index]
            val tileSize = iconSize

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - tileSize.toPx() / 2f).roundToInt(),
                            (y - tileSize.toPx() / 2f).roundToInt(),
                        )
                    }
                    .size(tileSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(percent = 24))
                    .background(skin.card),
                contentAlignment = Alignment.Center,
            ) {
                val icon = state.iconOf(entry)
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = entry.label, modifier = Modifier.fillMaxSize())
                }
            }

            // Neighbours carry a small right-aligned caption; the selection gets
            // the big name over on the left instead.
            if (falloff > 0.35f && falloff <= 3.2f) {
                val captionWidth = width * 0.42f
                Column(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (x - tileSize.toPx() * scale / 2f - captionWidth.toPx() - 12.dp.toPx()).roundToInt(),
                                (y - 18.dp.toPx()).roundToInt(),
                            )
                        }
                        .width(captionWidth)
                        .graphicsLayer { this.alpha = alpha },
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = entry.label,
                        color = skin.text,
                        fontSize = (16f - 2.2f * falloff).coerceAtLeast(9f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = "Installed App",
                        color = skin.textDim,
                        fontSize = (12f - 1.6f * falloff).coerceAtLeast(8f).sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        // The selected app, spelled out.
        val current = apps.getOrNull(selected.roundToInt())
        if (current != null) {
            Column(
                modifier = Modifier
                    .offset(x = width * 0.05f, y = height * 0.36f)
                    .width(width * 0.56f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = accentFirstLetter(current.label, skin.accent),
                    color = skin.text,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Installed App",
                    color = skin.textDim,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                SoundWave(
                    energy = state.energy,
                    modifier = Modifier
                        .width(width * 0.42f)
                        .height(height * 0.16f),
                )
            }
        }
    }
}

/** "Acode" with the A in the accent colour, the way the mock has it. */
private fun accentFirstLetter(label: String, accent: Color): AnnotatedString = buildAnnotatedString {
    if (label.isEmpty()) return@buildAnnotatedString
    withStyle(SpanStyle(color = accent)) { append(label.take(1)) }
    append(label.drop(1))
}
