package com.mpc.launcher.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mpc.launcher.ui.Skin

/**
 * A folder, shaped like a rotary knob. Tapping turns it and opens the folder;
 * the marker dot is the knob's pointer.
 */
@Composable
fun KnobModule(
    state: LauncherState,
    module: Module,
    entry: Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val open = state.openFolder == module.id
    val badged = module.folderApps.any { key ->
        state.entry(key)?.packageName?.let { it in Badges.packages } == true
    }

    // Turns a third of the way round while open, and winds in on arrival.
    val turn by animateFloatAsState(
        targetValue = if (open) 120f else 0f,
        animationSpec = spring(),
        label = "knob-turn",
    )

    ModuleTray(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dial = minOf(maxWidth, maxHeight * 0.66f)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(dial)
                        .graphicsLayer {
                            rotationZ = turn - (1f - entry) * 90f
                            val scale = 0.86f + 0.14f * entry
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(if (badged) Skin.Signal else Skin.Ink)
                        .pointerInput(module.id) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    state.openFolder(module.id)
                                },
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.openFolder(module.id)
                                },
                            )
                        },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = dial * 0.13f)
                            .size(dial * 0.13f)
                            .clip(CircleShape)
                            .background(Skin.Marker),
                    )
                }

                Spacer(Modifier.height(5.dp))

                Text(
                    text = module.folderName,
                    color = Skin.Text,
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
