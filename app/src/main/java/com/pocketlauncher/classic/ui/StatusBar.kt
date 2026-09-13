package com.pocketlauncher.classic.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale

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

@Composable
private fun rememberBattery(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            level = runCatching {
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(-1)
            delay(30_000)
        }
    }
    return level
}

/** "A P P S" on the left, time and battery on the right. */
@Composable
fun StatusBar(title: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val now = rememberNow()
    val battery = rememberBattery()

    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title.uppercase(Locale.ROOT),
            color = skin.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
        )
        Text(
            text = buildString {
                append(String.format(Locale.JAPAN, "%d:%02d", now.hour, now.minute))
                if (battery >= 0) append("    ${battery}%")
            },
            color = skin.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
