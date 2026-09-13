package com.mpc.launcher.ui

import androidx.compose.ui.graphics.Color

/** The whole palette. Paper, ink, and two signal colours. */
object Skin {
    /** Warm paper the whole launcher sits on. */
    val Paper = Color(0xFFF2F0E6)

    /** The slightly recessed tray a module sits in. */
    val Tray = Color(0xFFEBE8DC)
    val TrayActive = Color(0xFFE2DFD1)

    /** Pads, knobs and fader caps. */
    val Ink = Color(0xFF1A1A1C)
    val InkSoft = Color(0xFF2A2A2C)

    val Text = Color(0xFF1A1A1C)
    val TextDim = Color(0xFF9E9C91)
    val TextFaint = Color(0xFFCFCCBE)

    /** A pad glows this when its app has a notification. */
    val Signal = Color(0xFFE9653C)

    /** The slide indicator, and the line across a fader cap. */
    val Marker = Color(0xFFF2C50E)
}
