package com.mpc.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.data.GRID_COLUMNS
import com.mpc.launcher.data.GRID_ROWS
import com.mpc.launcher.data.Module
import com.mpc.launcher.data.ModuleType
import com.mpc.launcher.data.Page
import com.mpc.launcher.ui.modules.CalendarModule
import com.mpc.launcher.ui.modules.ClockModule
import com.mpc.launcher.ui.modules.FaderModule
import com.mpc.launcher.ui.modules.KnobModule
import com.mpc.launcher.ui.modules.MediaModule
import com.mpc.launcher.ui.modules.PadModule
import kotlin.math.roundToInt

/**
 * One page of the grid. Modules sit at whole cells; in edit mode each one can
 * be picked up and dropped on any free cell, and the move is saved on release.
 */
@Composable
fun PageCanvas(
    state: LauncherState,
    pageIndex: Int,
    page: Page,
    entry: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.pointerInput(pageIndex, state.editing) {
            detectTapGestures(
                onLongPress = {
                    if (!state.editing) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.startEditing()
                    }
                },
            )
        },
    ) {
        val cellWidth = maxWidth / GRID_COLUMNS
        val cellHeight = maxHeight / GRID_ROWS
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }

        page.modules.forEach { module ->
            key(module.id) {
                ModuleSlot(
                    state = state,
                    pageIndex = pageIndex,
                    module = module,
                    entry = entry,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    width = cellWidth * module.spanX,
                    height = cellHeight * module.spanY,
                )
            }
        }
    }
}

@Composable
private fun ModuleSlot(
    state: LauncherState,
    pageIndex: Int,
    module: Module,
    entry: Float,
    cellWidthPx: Float,
    cellHeightPx: Float,
    width: Dp,
    height: Dp,
) {
    val haptics = LocalHapticFeedback.current
    var drag by remember(module.id) { mutableStateOf(Offset.Zero) }
    val lift by animateFloatAsState(
        targetValue = if (drag == Offset.Zero) 1f else 1.06f,
        animationSpec = spring(),
        label = "module-lift",
    )

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (module.col * cellWidthPx + drag.x).roundToInt(),
                    (module.row * cellHeightPx + drag.y).roundToInt(),
                )
            }
            .size(width, height)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            },
    ) {
        when (module.type) {
            ModuleType.PAD -> PadModule(state, module, entry, Modifier.fillMaxSize())
            ModuleType.KNOB -> KnobModule(state, module, entry, Modifier.fillMaxSize())
            ModuleType.FADER -> FaderModule(state, module, entry, Modifier.fillMaxSize())
            ModuleType.CALENDAR -> CalendarModule(entry, Modifier.fillMaxSize())
            ModuleType.CLOCK -> ClockModule(Modifier.fillMaxSize())
            ModuleType.MEDIA -> MediaModule(state, Modifier.fillMaxSize())
        }

        if (state.editing) {
            // A lid over the module: in edit mode it is a thing to move, not to use.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(module.id, cellWidthPx, cellHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                val col = ((module.col * cellWidthPx + drag.x) / cellWidthPx).roundToInt()
                                val row = ((module.row * cellHeightPx + drag.y) / cellHeightPx).roundToInt()
                                state.moveModule(pageIndex, module.id, col, row)
                                drag = Offset.Zero
                            },
                            onDragCancel = { drag = Offset.Zero },
                        ) { change, amount ->
                            drag += amount
                            change.consume()
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(1.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Skin.Ink)
                    .pointerInput(module.id) {
                        detectTapGestures { state.removeModule(module.id) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Skin.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
