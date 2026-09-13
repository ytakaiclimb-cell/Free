package com.mpc.launcher.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.data.Module
import com.mpc.launcher.service.Badges
import com.mpc.launcher.ui.LauncherState
import com.mpc.launcher.ui.PickerTarget
import com.mpc.launcher.ui.Skin

/** How far the tile must travel before the slide fires the second app. */
private const val SLIDE_TRIGGER = 54f

/**
 * An app pad. Tapping opens the app it holds; sliding it down opens the one
 * behind it, which a marker dot advertises. When the app has a notification
 * the whole pad lights up instead of wearing a badge.
 */
@Composable
fun PadModule(
    state: LauncherState,
    module: Module,
    entry: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val primary = state.entry(module.primary)
    val secondary = state.entry(module.secondary)
    val badged = primary != null && primary.packageName in Badges.packages

    var slide by remember(module.id) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(slide, spring(), label = "pad-slide")
    val reveal = (offset / SLIDE_TRIGGER).coerceIn(0f, 1f)

    ModuleTray(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tile = minOf(maxWidth, maxHeight * 0.66f)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The second app's name surfaces as the tile is pulled down.
                Text(
                    text = secondary?.label.orEmpty(),
                    color = Skin.TextDim,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .height(12.dp)
                        .graphicsLayer { alpha = reveal },
                )

                Box(
                    modifier = Modifier
                        .size(tile)
                        .graphicsLayer {
                            translationY = offset
                            val scale = 0.86f + 0.14f * entry
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(percent = 26))
                        .background(if (badged) Skin.Signal else Skin.Ink)
                        .pointerInput(module.id, secondary?.key) {
                            if (secondary == null) return@pointerInput
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (slide >= SLIDE_TRIGGER) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        state.launch(secondary)
                                    }
                                    slide = 0f
                                },
                                onDragCancel = { slide = 0f },
                            ) { change, dragAmount ->
                                slide = (slide + dragAmount).coerceIn(0f, SLIDE_TRIGGER * 1.3f)
                                change.consume()
                            }
                        }
                        .pointerInput(module.id, module.primary) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    state.openPicker(PickerTarget.PadPrimary(module.id))
                                },
                                onTap = {
                                    if (primary == null) {
                                        state.openPicker(PickerTarget.PadPrimary(module.id))
                                    } else {
                                        state.launch(primary)
                                    }
                                },
                            )
                        },
                ) {
                    if (secondary != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(tile * 0.11f)
                                .size(tile * 0.11f)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(if (badged) Color.White else Skin.Marker),
                        )
                    }
                }

                Spacer(Modifier.height(5.dp))

                Text(
                    text = primary?.label ?: "未設定",
                    color = if (primary == null) Skin.TextDim else Skin.Text,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** The recessed tray every module sits in. */
@Composable
fun ModuleTray(
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (filled) Skin.Tray else Color.Transparent)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
