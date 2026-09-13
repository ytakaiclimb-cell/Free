package com.pocketlauncher.classic.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Every colour in the launcher, in one object, swapped whole when the mode flips. */
@Immutable
data class Skin(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val wheel: Color,
    val wheelHub: Color,
    val wheelLabel: Color,
    val groove: Color,
    val track: Color,
    val knob: Color,
    val knobLine: Color,
    val badge: Color,
)

val LightSkin = Skin(
    dark = false,
    background = Color(0xFFF1F0EC),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF1B1A18),
    textDim = Color(0xFF8A8983),
    textFaint = Color(0xFFC6C5BF),
    accent = Color(0xFFD9542F),
    wheel = Color(0xFFEDECE7),
    wheelHub = Color(0xFFF4F3EF),
    wheelLabel = Color(0xFF6C6B65),
    groove = Color(0xFFE3E2DC),
    track = Color(0xFFDEDDD7),
    knob = Color(0xFFFFFFFF),
    knobLine = Color(0xFFE8E7E1),
    badge = Color(0xFF2F7BD9),
)

val DarkSkin = Skin(
    dark = true,
    background = Color(0xFF0B0B0B),
    card = Color(0xFF1B1B1B),
    text = Color(0xFFF1F0EC),
    textDim = Color(0xFF7E7D78),
    textFaint = Color(0xFF34342F),
    accent = Color(0xFFE9654A),
    wheel = Color(0xFF161616),
    wheelHub = Color(0xFF1F1F1F),
    wheelLabel = Color(0xFF6C6B65),
    groove = Color(0xFF202020),
    track = Color(0xFF2B2B2B),
    knob = Color(0xFF242424),
    knobLine = Color(0xFF343434),
    badge = Color(0xFF4E94E8),
)

val LocalSkin = staticCompositionLocalOf { LightSkin }
