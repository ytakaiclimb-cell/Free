package com.pocketlauncher.classic.ui

import androidx.compose.ui.graphics.Color

/**
 * Fixed palette. The launcher is a skeuomorphic object, so it keeps the same
 * silver-and-LCD look in light and dark system themes.
 */
object Palette {
    val Bezel = Color(0xFFE9EAEE)
    val BezelEdge = Color(0xFFC7C9D0)

    val ScreenFrame = Color(0xFF2B2D33)
    val ScreenBackground = Color(0xFFF6F6F1)

    val TitleBarTop = Color(0xFFEDEFF3)
    val TitleBarBottom = Color(0xFFB9BFCB)
    val TitleText = Color(0xFF2A2E36)

    val RowText = Color(0xFF16181C)
    val RowSubText = Color(0xFF6A7079)
    val RowDivider = Color(0xFFDCDCD4)

    val SelectTop = Color(0xFF6FA3EC)
    val SelectBottom = Color(0xFF2560B8)
    val SelectText = Color(0xFFFFFFFF)

    val ScrollTrack = Color(0xFFE2E2DA)
    val ScrollThumb = Color(0xFF9AA0A8)

    val WheelTop = Color(0xFFF3F4F6)
    val WheelBottom = Color(0xFFD3D6DC)
    val WheelEdge = Color(0xFFB4B8C0)
    val WheelLabel = Color(0xFF6E737C)

    val HubTop = Color(0xFFFCFCFD)
    val HubBottom = Color(0xFFDFE2E7)

    val DockIcon = Color(0xFF5B616B)
    val DockIconActive = Color(0xFF2560B8)
}
