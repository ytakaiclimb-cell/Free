package com.mpc.launcher.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.ui.LauncherState
import com.mpc.launcher.ui.Skin
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.sin

/** The wall clock, re-read on the second boundary. */
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

/**
 * The month as a speaker grille: one dot per day, Sunday to Saturday across,
 * with today lit. Days outside the month stay pale.
 */
@Composable
fun CalendarModule(entry: Float, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val today = now.toLocalDate()
    val month = YearMonth.from(today)
    val days = month.lengthOfMonth()
    // DayOfWeek runs Monday=1..Sunday=7; the grid starts on Sunday.
    val leading = month.atDay(1).dayOfWeek.value % 7

    val bloom by animateFloatAsState(entry, tween(600), label = "calendar-bloom")

    Box(modifier.padding(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val columns = 7
            val rows = 6
            val stepX = size.width / columns
            val stepY = size.height / rows
            val radius = minOf(stepX, stepY) * 0.19f

            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    val day = index - leading + 1
                    val inMonth = day in 1..days
                    val isToday = day == today.dayOfMonth

                    // Dots wake up in reading order when the page arrives.
                    val wake = ((bloom * (rows * columns + 8)) - index).coerceIn(0f, 1f)
                    if (wake <= 0f) continue

                    val colour = when {
                        isToday -> Skin.Signal
                        inMonth -> Skin.Ink
                        else -> Skin.TextFaint
                    }
                    drawCircle(
                        color = colour,
                        radius = radius * (0.6f + 0.4f * wake) * if (isToday) 1.05f else 1f,
                        center = Offset(stepX * (column + 0.5f), stepY * (row + 0.5f)),
                        alpha = wake,
                    )
                }
            }
        }
    }
}

/** Big time, running seconds, and the date on the right. */
@Composable
fun ClockModule(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val weekday = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
        .uppercase(Locale.ENGLISH)

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = String.format(Locale.JAPAN, "%02d:%02d", now.hour, now.minute),
            color = Skin.Text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        Text(
            text = String.format(Locale.JAPAN, ":%02d", now.second),
            color = Skin.TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = String.format(
                Locale.ENGLISH,
                "%02d.%02d %s",
                now.monthValue,
                now.dayOfMonth,
                weekday,
            ),
            color = Skin.TextDim,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/**
 * What is playing, wherever it is playing from. The meter runs while audio is
 * going and the transport button hands play/pause back to that app.
 */
@Composable
fun MediaModule(state: LauncherState, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val playing = state.nowPlaying

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = playing?.line ?: "再生中の音楽なし",
            color = Skin.TextDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelMeter(active = playing?.playing == true)
            Spacer(Modifier.width(10.dp))
            TransportButton(
                playing = playing?.playing == true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    state.toggleMedia()
                },
            )
        }
    }
}

/** A dot-matrix VU meter, still when nothing is playing. */
@Composable
private fun LevelMeter(active: Boolean, modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(active) {
        while (active) {
            delay(90)
            phase += 0.55f
        }
    }

    Canvas(modifier.size(34.dp, 16.dp)) {
        val columns = 7
        val rows = 4
        val stepX = size.width / columns
        val stepY = size.height / rows
        val radius = minOf(stepX, stepY) * 0.26f

        for (column in 0 until columns) {
            val wobble = (sin(phase + column * 1.1f) + 1f) / 2f
            val lit = if (active) (1 + wobble * (rows - 1)).toInt() else 1

            for (row in 0 until rows) {
                // Row 0 is the top; the meter fills from the bottom up.
                val fromBottom = rows - 1 - row
                drawCircle(
                    color = if (fromBottom < lit) Skin.Text else Skin.TextFaint,
                    radius = radius,
                    center = Offset(stepX * (column + 0.5f), stepY * (row + 0.5f)),
                )
            }
        }
    }
}

@Composable
private fun TransportButton(playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing) {
            Box(Modifier.size(4.dp, 18.dp).background(Skin.Text))
            Box(Modifier.size(4.dp, 18.dp).background(Skin.Text))
        } else {
            Canvas(Modifier.size(14.dp, 18.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Skin.Text)
            }
        }
    }
}

/** The page name, set on its side down the left edge. */
@Composable
fun PageTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    // The band is the rotated text's height; the run is its length. Centring a
    // run x band box inside a band x run one lands it square after the turn.
    val band = 48.dp
    val run = 280.dp

    Box(modifier.width(band).height(run)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredWidth(run)
                .requiredHeight(band)
                .graphicsLayer { rotationZ = 90f },
            verticalArrangement = Arrangement.Center,
        ) {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Skin.TextDim,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                color = Skin.Text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
