package com.pocketlauncher.classic.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Status bar, screen, wheel, faders and switches — the whole device. */
@Composable
fun ClassicApp(state: LauncherState) {
    val context = LocalContext.current
    val view = LocalView.current
    val skin = if (state.dark) DarkSkin else LightSkin

    // The launcher owns its light/dark state, so the system bar icons follow it
    // rather than the system theme.
    LaunchedEffect(state.dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !state.dark
            isAppearanceLightNavigationBars = !state.dark
        }
    }

    LaunchedEffect(state.resumeTick) {
        state.loadApps()
        state.seedFadersIfEmpty()
    }
    LaunchedEffect(Unit) { state.runTicker() }

    BackHandler { state.systemBack() }

    CompositionLocalProvider(LocalSkin provides skin) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(skin.background),
        ) {
            val width = minOf(maxWidth, 560.dp)
            val wheelSize = minOf(width * 0.42f, maxHeight * 0.30f)
            val faderWidth = width * 0.21f

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(width)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp),
            ) {
                StatusBar(title = state.screen.title())

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (state.screen) {
                        Screen.HOME -> HomeRing(state, Modifier.fillMaxSize())
                        Screen.CLOCK -> ClockScreen(Modifier.fillMaxSize())
                        Screen.MENU -> MenuScreen(state, Modifier.fillMaxSize())
                        Screen.DRAWER -> DrawerScreen(state, Modifier.fillMaxSize())
                    }
                }

                if (state.screen != Screen.DRAWER) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(wheelSize * 1.16f),
                    ) {
                        ClickWheel(
                            wheelSize = wheelSize,
                            record = state.musicActive,
                            onScroll = state::scroll,
                            onVolumeStart = state::volumeGestureStart,
                            onVolumeStep = state::volumeStep,
                            onVolumeEnd = state::volumeGestureEnd,
                            onCenterClick = state::select,
                            onCenterLongClick = { state.centerLongPress(context) },
                            onMenu = { state.back() },
                            onPrev = { state.nudge(-1) },
                            onNext = { state.nudge(1) },
                            onPlay = { state.toggleClock() },
                            modifier = Modifier.align(Alignment.Center),
                        )

                        FaderBank(
                            state = state,
                            clockMode = state.screen == Screen.CLOCK,
                            width = faderWidth,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )

                        if (state.volumeVisible) {
                            VolumeReadout(
                                level = state.volumeLevel,
                                max = state.volume.max,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                }

                HomeIndicator(onSwipeUp = { state.openDrawer() })
                Spacer(Modifier.height(4.dp))
                BottomSwitches(state)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun Screen.title(): String = when (this) {
    Screen.HOME -> "Apps"
    Screen.MENU -> "iPod"
    Screen.CLOCK -> "iPod"
    Screen.DRAWER -> "All Apps"
}

/** The bar that appears while the wheel is being used as a volume dial. */
@Composable
private fun VolumeReadout(level: Int, max: Int, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val fraction by animateFloatAsState(
        targetValue = if (max == 0) 0f else level.toFloat() / max,
        animationSpec = spring(),
        label = "volume",
    )
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = "VOLUME  $level / $max",
            color = skin.textDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(132.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(skin.track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(skin.accent),
            )
        }
    }
}
