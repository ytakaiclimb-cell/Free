package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The wall clock, re-read once a second and aligned to the second boundary. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
            now = LocalDateTime.now()
        }
    }
    return now
}

private val SecondHand = Color(0xFFC2352E)

/**
 * Clock mode: an analogue dial over the digital time. Long-pressing the wheel's
 * centre button from here opens the device clock app — see [LauncherState.centerLongPress].
 */
@Composable
fun ClockFace(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val weekday = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.JAPAN)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dial = minOf(maxWidth * 0.62f, maxHeight * 0.46f, 168.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(dial)) {
                val radius = size.minDimension / 2f
                val centre = Offset(size.width / 2f, size.height / 2f)

                drawCircle(Color.White, radius, centre)
                drawCircle(
                    color = Palette.ScreenFrame,
                    radius = radius - 1.dp.toPx(),
                    center = centre,
                    style = Stroke(width = 2.dp.toPx()),
                )

                for (tick in 0 until 60) {
                    val major = tick % 5 == 0
                    val angle = Math.toRadians(tick * 6.0)
                    val outer = radius - 5.dp.toPx()
                    val inner = outer - (if (major) 8.dp.toPx() else 3.dp.toPx())
                    drawLine(
                        color = if (major) Palette.RowText else Palette.RowSubText,
                        start = Offset(
                            centre.x + outer * sin(angle).toFloat(),
                            centre.y - outer * cos(angle).toFloat(),
                        ),
                        end = Offset(
                            centre.x + inner * sin(angle).toFloat(),
                            centre.y - inner * cos(angle).toFloat(),
                        ),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }

                fun hand(turns: Float, length: Float, width: Float, color: Color) {
                    val angle = Math.toRadians(turns * 360.0)
                    drawLine(
                        color = color,
                        start = centre,
                        end = Offset(
                            centre.x + length * sin(angle).toFloat(),
                            centre.y - length * cos(angle).toFloat(),
                        ),
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                    )
                }

                val seconds = now.second + now.nano / 1_000_000_000f
                hand(((now.hour % 12) + now.minute / 60f) / 12f, radius * 0.50f, 4.dp.toPx(), Palette.RowText)
                hand((now.minute + seconds / 60f) / 60f, radius * 0.74f, 3.dp.toPx(), Palette.RowText)
                hand(seconds / 60f, radius * 0.80f, 1.5.dp.toPx(), SecondHand)
                drawCircle(Palette.RowText, 3.dp.toPx(), centre)
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = String.format(Locale.JAPAN, "%02d:%02d:%02d", now.hour, now.minute, now.second),
                color = Palette.RowText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )
            Text(
                text = String.format(
                    Locale.JAPAN,
                    "%d年%d月%d日 (%s)",
                    now.year,
                    now.monthValue,
                    now.dayOfMonth,
                    weekday,
                ),
                color = Palette.RowSubText,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "中央ボタンを長押しで時計アプリを開きます",
                color = Palette.RowSubText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
