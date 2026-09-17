package com.pop.insta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small caps heading over a row of controls. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        color = Skin.TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
fun Chip(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Skin.Accent else Skin.Panel)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) Skin.Shell else Skin.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = if (selected) Skin.Shell.copy(alpha = 0.7f) else Skin.TextDim,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A backdrop choice: the colour itself, with its name underneath. */
@Composable
fun Swatch(
    label: String,
    fill: Brush,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Skin.PanelHi else Skin.Panel)
            .border(
                width = 1.dp,
                color = if (selected) Skin.Accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(fill, CircleShape)
                .border(1.dp, Skin.Line, CircleShape)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (selected) Skin.Text else Skin.TextDim,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

fun solid(color: Color): Brush = SolidColor(color)

val blurBrush: Brush = Brush.linearGradient(
    listOf(Color(0xFF5E5E6B), Color(0xFFD8D5CC), Color(0xFF6E6A62))
)

@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> Skin.Panel
        primary -> Skin.Accent
        else -> Skin.PanelHi
    }
    val foreground = when {
        !enabled -> Skin.TextDim
        primary -> Skin.Shell
        else -> Skin.Text
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** A quieter button for the secondary row. */
@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Skin.Line, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Skin.TextDim else Skin.Line,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The empty-state drawing: an A4 sheet landing inside a square post. */
@Composable
fun SheetDiagram(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val sheetH = size.height * 0.82f
        val sheetW = sheetH / 1.414f
        val gap = sheetW * 0.55f
        val squareSide = sheetH * 0.86f
        val totalW = sheetW + gap + squareSide
        val left = (size.width - totalW) / 2f
        val top = (size.height - sheetH) / 2f

        // The A4 original.
        drawRect(Skin.PanelHi, Offset(left, top), Size(sheetW, sheetH))
        drawRect(Skin.Line, Offset(left, top), Size(sheetW, sheetH), style = Stroke(width = 2f))
        val inset = sheetW * 0.16f
        drawRect(Skin.Accent, Offset(left + inset, top + sheetH * 0.16f), Size(sheetW - inset * 2, sheetH * 0.1f))
        drawRect(Skin.Line, Offset(left + inset, top + sheetH * 0.38f), Size(sheetW - inset * 2, sheetH * 0.05f))
        drawRect(Skin.Line, Offset(left + inset, top + sheetH * 0.5f), Size((sheetW - inset * 2) * 0.6f, sheetH * 0.05f))

        // The arrow between them.
        val midY = size.height / 2f
        val arrowStart = left + sheetW + gap * 0.22f
        val arrowEnd = left + sheetW + gap * 0.78f
        drawLine(Skin.TextDim, Offset(arrowStart, midY), Offset(arrowEnd, midY), strokeWidth = 3f)
        drawLine(Skin.TextDim, Offset(arrowEnd - 7f, midY - 7f), Offset(arrowEnd, midY), strokeWidth = 3f)
        drawLine(Skin.TextDim, Offset(arrowEnd - 7f, midY + 7f), Offset(arrowEnd, midY), strokeWidth = 3f)

        // The square post, with the same sheet sitting inside it.
        val squareLeft = left + sheetW + gap
        val squareTop = midY - squareSide / 2f
        drawRect(Skin.Panel, Offset(squareLeft, squareTop), Size(squareSide, squareSide))
        val innerH = squareSide * 0.86f
        val innerW = innerH / 1.414f
        val innerLeft = squareLeft + (squareSide - innerW) / 2f
        val innerTop = squareTop + (squareSide - innerH) / 2f
        drawRect(Skin.PanelHi, Offset(innerLeft, innerTop), Size(innerW, innerH))
        val innerInset = innerW * 0.16f
        drawRect(Skin.Accent, Offset(innerLeft + innerInset, innerTop + innerH * 0.16f), Size(innerW - innerInset * 2, innerH * 0.1f))
        drawRect(Skin.Accent, Offset(squareLeft, squareTop), Size(squareSide, squareSide), style = Stroke(width = 3f))
    }
}
