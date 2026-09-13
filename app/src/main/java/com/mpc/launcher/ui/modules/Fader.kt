package com.mpc.launcher.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.data.FaderRole
import com.mpc.launcher.data.Module
import com.mpc.launcher.ui.LauncherState
import com.mpc.launcher.ui.PickerTarget
import com.mpc.launcher.ui.Skin

/** Travel, as a fraction of the rail, before a launch fader fires. */
private const val FLICK_TRIGGER = 0.28f

/**
 * A channel fader. On the system roles it rides a real value — media volume or
 * screen brightness — and a tap toggles mute or auto. On the launch role it is
 * a sprung lever: flick it to either end and the app assigned there opens.
 */
@Composable
fun FaderModule(
    state: LauncherState,
    module: Module,
    entry: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val launcherRole = module.faderRole == FaderRole.LAUNCH

    val upApp = state.entry(module.flickUp)
    val downApp = state.entry(module.flickDown)

    // Where the cap sits: 0 at the bottom of the rail, 1 at the top.
    var value by remember(module.id) { mutableFloatStateOf(0.5f) }
    var dragging by remember(module.id) { mutableFloatStateOf(0f) }

    // Read the live system value whenever we are not the one moving it.
    LaunchedEffect(module.id, state.resumeTick) {
        if (launcherRole) return@LaunchedEffect
        value = when (module.faderRole) {
            FaderRole.VOLUME -> state.volume.level
            FaderRole.BRIGHTNESS -> state.brightness.level
            FaderRole.LAUNCH -> 0.5f
        }
    }

    val auto = module.faderRole == FaderRole.BRIGHTNESS && state.brightness.auto
    val muted = module.faderRole == FaderRole.VOLUME && state.volume.muted

    // The cap winds up to its place when the page arrives.
    val shown by animateFloatAsState(
        targetValue = if (launcherRole) 0.5f else value * entry,
        animationSpec = spring(),
        label = "fader-value",
    )

    val topLabel = when (module.faderRole) {
        FaderRole.LAUNCH -> upApp?.label ?: "上に設定"
        else -> ""
    }
    val bottomLabel = when (module.faderRole) {
        FaderRole.LAUNCH -> downApp?.label ?: "下に設定"
        FaderRole.VOLUME -> if (muted) "MUTE" else "VOL"
        FaderRole.BRIGHTNESS -> if (auto) "AUTO" else "BRT"
    }
    val bottomColour = when {
        module.faderRole == FaderRole.BRIGHTNESS && auto -> Skin.Signal
        module.faderRole == FaderRole.VOLUME && muted -> Skin.Signal
        else -> Skin.TextDim
    }

    ModuleTray(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val capWidth = maxWidth * 0.62f
            val capHeight = 22.dp
            // The rail is what is left once the two 12dp labels are taken out.
            val travel = (maxHeight - 24.dp - capHeight).coerceAtLeast(1.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = topLabel,
                    color = Skin.TextDim,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(12.dp),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(module.id, module.faderRole, travel) {
                            val travelPx = travel.toPx().coerceAtLeast(1f)
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (launcherRole) {
                                        val moved = dragging / travelPx
                                        if (moved <= -FLICK_TRIGGER && upApp != null) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.launch(upApp)
                                        } else if (moved >= FLICK_TRIGGER && downApp != null) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            state.launch(downApp)
                                        }
                                    }
                                    dragging = 0f
                                },
                                onDragCancel = { dragging = 0f },
                            ) { change, dragAmount ->
                                change.consume()
                                if (launcherRole) {
                                    dragging = (dragging + dragAmount)
                                        .coerceIn(-travelPx * 0.45f, travelPx * 0.45f)
                                } else {
                                    dragging = 1f
                                    val next = (value - dragAmount / travelPx).coerceIn(0f, 1f)
                                    if (next != value) {
                                        value = next
                                        when (module.faderRole) {
                                            FaderRole.VOLUME -> state.volume.set(next)
                                            FaderRole.BRIGHTNESS -> state.brightness.set(next)
                                            FaderRole.LAUNCH -> Unit
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(module.id, module.faderRole) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (launcherRole) state.openPicker(PickerTarget.FlickUp(module.id))
                                },
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (module.faderRole) {
                                        FaderRole.VOLUME -> state.volume.toggleMute()
                                        FaderRole.BRIGHTNESS ->
                                            if (!state.brightness.toggleAuto()) state.brightness.requestAccess()
                                        FaderRole.LAUNCH ->
                                            state.openPicker(PickerTarget.FlickDown(module.id))
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(Skin.Text),
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val base = (1f - shown) * travel.toPx()
                                translationY = base + if (launcherRole) dragging else 0f
                            }
                            .size(capWidth, capHeight)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Skin.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(capWidth * 0.66f)
                                .height(3.dp)
                                .background(Skin.Marker),
                        )
                    }
                }

                Text(
                    text = bottomLabel,
                    color = bottomColour,
                    fontSize = 9.sp,
                    fontWeight = if (auto || muted) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(12.dp),
                )
            }
        }
    }
}
