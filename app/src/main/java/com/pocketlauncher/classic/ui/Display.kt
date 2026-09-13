package com.pocketlauncher.classic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val RowHeight = 44.dp
private val PanelShape = RoundedCornerShape(6.dp)

/** The iPod "screen": a title bar plus whichever page the wheel is driving. */
@Composable
fun DisplayPanel(state: LauncherState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(Palette.ScreenBackground)
            .border(3.dp, Palette.ScreenFrame, PanelShape),
    ) {
        TitleBar(title = state.title())

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.mode == Mode.CLOCK -> ClockFace(Modifier.fillMaxSize())
                state.page == Page.ROOT -> RootList(state, Modifier.fillMaxSize())
                state.page == Page.APPS -> AppList(state, Modifier.fillMaxSize())
                else -> AboutPage(Modifier.fillMaxSize())
            }
        }
    }
}

private fun LauncherState.title(): String = when {
    mode == Mode.CLOCK -> "時計"
    page == Page.APPS -> "すべてのアプリ"
    page == Page.ABOUT -> "情報"
    else -> "Pocket Classic"
}

@Composable
private fun TitleBar(title: String) {
    val now = rememberNow()
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Brush.verticalGradient(listOf(Palette.TitleBarTop, Palette.TitleBarBottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Palette.TitleText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        Text(
            text = String.format(Locale.JAPAN, "%02d:%02d", now.hour, now.minute),
            color = Palette.TitleText,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
        )
    }
}

@Composable
private fun RootList(state: LauncherState, modifier: Modifier = Modifier) {
    MenuList(count = state.rootItems.size, selected = state.rootIndex, modifier = modifier) { index, selected ->
        MenuRow(label = state.rootItems[index].label, selected = selected, chevron = true)
    }
}

@Composable
private fun AppList(state: LauncherState, modifier: Modifier = Modifier) {
    if (state.apps.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("読み込み中…", color = Palette.RowSubText, fontSize = 13.sp)
        }
        return
    }
    MenuList(count = state.apps.size, selected = state.appsIndex, modifier = modifier) { index, selected ->
        val entry = state.apps[index]
        MenuRow(
            label = entry.label,
            selected = selected,
            chevron = false,
            icon = state.iconOf(entry),
        )
    }
}

/**
 * Draws a window of [count] rows with [selected] kept in the middle, plus the
 * iPod scroll bar down the right edge. There is no touch scrolling on purpose:
 * the click wheel is the only way to move.
 */
@Composable
private fun MenuList(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val visible = max(1, (maxHeight / RowHeight).toInt())
        val first = (selected - visible / 2).coerceIn(0, max(0, count - visible))
        val last = min(count, first + visible)

        Column(Modifier.fillMaxSize()) {
            for (index in first until last) {
                row(index, index == selected)
            }
        }

        if (count > visible) {
            ScrollBar(
                first = first,
                visible = visible,
                count = count,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ScrollBar(first: Int, visible: Int, count: Int, modifier: Modifier = Modifier) {
    val below = count - first - visible
    Column(
        modifier
            .fillMaxHeight()
            .width(5.dp)
            .background(Palette.ScrollTrack),
    ) {
        if (first > 0) Spacer(Modifier.weight(first.toFloat()))
        Box(
            Modifier
                .weight(visible.toFloat())
                .fillMaxWidth()
                .background(Palette.ScrollThumb),
        )
        if (below > 0) Spacer(Modifier.weight(below.toFloat()))
    }
}

@Composable
private fun MenuRow(
    label: String,
    selected: Boolean,
    chevron: Boolean,
    icon: ImageBitmap? = null,
) {
    val background = if (selected) {
        Modifier.background(Brush.verticalGradient(listOf(Palette.SelectTop, Palette.SelectBottom)))
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight - 1.dp)
            .then(background)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (selected) Palette.SelectText else Palette.RowText,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chevron) {
            Chevron(
                color = if (selected) Palette.SelectText else Palette.RowSubText,
                glyphSize = 10.dp,
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.RowDivider),
    )
}

@Composable
private fun Chevron(color: Color, glyphSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(glyphSize, glyphSize * 1.4f)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun AboutPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Pocket Classic", color = Palette.RowText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "・ホイールを指でなぞると選択が動きます\n" +
                "・中央ボタンで決定\n" +
                "・MENU で 1 つ戻ります\n" +
                "・下の時計マークで時計モード\n" +
                "・時計モード中に中央ボタンを長押しすると時計アプリが開き、戻ると通常モードに戻ります",
            color = Palette.RowText,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Text(
            "ホームアプリに設定するには、メインメニューの「既定のホームアプリ」を開いてください。",
            color = Palette.RowSubText,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Start,
        )
    }
}
