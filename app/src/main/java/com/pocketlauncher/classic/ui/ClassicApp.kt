package com.pocketlauncher.classic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Screen, dock, wheel — the whole device, stacked. */
@Composable
fun ClassicApp(state: LauncherState) {
    val context = LocalContext.current

    // Reload the app list on every return to the foreground so newly installed
    // apps appear without restarting the launcher.
    LaunchedEffect(state.resumeTick) { state.loadApps() }

    // Back never leaves the launcher; it just walks up one level.
    BackHandler { state.back() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Bezel, Palette.BezelEdge))),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentWidth = minOf(maxWidth, 460.dp)
        val wheelSize = minOf(contentWidth - 64.dp, maxHeight * 0.33f, 300.dp)

        Column(
            modifier = Modifier
                .width(contentWidth)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DisplayPanel(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            DockBar(
                state = state,
                onHome = state::openRoot,
                onApps = state::openApps,
                onClock = state::toggleClock,
            )

            Spacer(Modifier.height(10.dp))

            ClickWheel(
                wheelSize = wheelSize,
                onScroll = state::scroll,
                onCenterClick = { state.select(context) },
                onCenterLongClick = { state.centerLongPress(context) },
                onMenu = { state.back() },
                onPrev = { state.scroll(-3) },
                onNext = { state.scroll(3) },
                onPlay = { state.toggleClock() },
            )

            Spacer(Modifier.height(6.dp))
        }
    }
}
