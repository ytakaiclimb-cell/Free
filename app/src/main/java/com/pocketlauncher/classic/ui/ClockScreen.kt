package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val WEEKDAYS = listOf("日", "月", "火", "水", "木", "金", "土")

/**
 * Clock mode: the month at a glance, the time in full, and an analogue face.
 * Long-pressing the wheel's hub from here is the shortcut to the clock app.
 */
@Composable
fun ClockScreen(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val now = rememberNow()
    val today = now.toLocalDate()

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = String.format(Locale.JAPAN, "%d月 %d", today.monthValue, today.year),
            color = skin.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )

        MonthGrid(today)

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.JAPAN, "%d:%02d", now.hour, now.minute),
                color = skin.text,
                fontSize = 62.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = String.format(Locale.JAPAN, "%02d", now.second),
                color = skin.accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        Text(
            text = "中央ボタン長押しで時計アプリ / フェーダーでタイマー",
            color = skin.textDim,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dial = minOf(maxWidth * 0.52f, maxHeight, 190.dp)
            AnalogFace(
                hour = now.hour,
                minute = now.minute,
                second = now.second,
                dial = dial,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun MonthGrid(today: LocalDate, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val month = YearMonth.from(today)
    val daysInMonth = month.lengthOfMonth()
    // DayOfWeek is Monday=1..Sunday=7; the grid starts on Sunday.
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { label ->
                Text(
                    text = label,
                    color = skin.textDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (column in 0 until 7) {
                    val dayNumber = row * 7 + column - leading + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            DayCell(dayNumber, dayNumber == today.dayOfMonth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean) {
    val skin = LocalSkin.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isToday) skin.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                color = if (isToday) Color.White else skin.text,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(3.dp))
        // Only today is marked. Per-day dots would need a shift calendar to read from.
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isToday) skin.textDim else Color.Transparent),
        )
    }
}

@Composable
private fun AnalogFace(hour: Int, minute: Int, second: Int, dial: Dp, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Canvas(modifier.size(dial)) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(skin.wheel, radius, centre)
        drawCircle(skin.groove, radius * 0.34f, centre)

        for (tick in 0 until 60) {
            val major = tick % 5 == 0
            val angle = Math.toRadians(tick * 6.0)
            val outer = radius - 6.dp.toPx()
            val inner = outer - (if (major) 12.dp.toPx() else 5.dp.toPx())
            drawLine(
                color = if (major) skin.textDim else skin.textFaint,
                start = Offset(
                    centre.x + outer * sin(angle).toFloat(),
                    centre.y - outer * cos(angle).toFloat(),
                ),
                end = Offset(
                    centre.x + inner * sin(angle).toFloat(),
                    centre.y - inner * cos(angle).toFloat(),
                ),
                strokeWidth = if (major) 3.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        fun hand(turns: Float, length: Float, width: Float, colour: Color, tail: Float = 0f) {
            val angle = Math.toRadians(turns * 360.0)
            val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
            drawLine(
                color = colour,
                start = centre - direction * tail,
                end = centre + direction * length,
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        hand(((hour % 12) + minute / 60f) / 12f, radius * 0.48f, 7.dp.toPx(), skin.text)
        hand((minute + second / 60f) / 60f, radius * 0.72f, 6.dp.toPx(), skin.text)
        hand(second / 60f, radius * 0.80f, 2.dp.toPx(), skin.accent, tail = radius * 0.20f)
        drawCircle(skin.accent, 5.dp.toPx(), centre)
        drawCircle(skin.wheel, 3.dp.toPx(), centre, style = Stroke(1.dp.toPx()))
    }
}
