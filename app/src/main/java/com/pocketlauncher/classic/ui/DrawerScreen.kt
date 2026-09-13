package com.pocketlauncher.classic.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketlauncher.classic.data.AppEntry

private const val COLUMNS = 6
private const val ROWS = 5
private const val PER_PAGE = COLUMNS * ROWS

/** The full app list, reached by swiping up from the bottom of the screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerScreen(state: LauncherState, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val apps = state.filteredApps
    val pageCount = ((apps.size + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var pendingAssign by remember { mutableStateOf<AppEntry?>(null) }

    // Keep the page in step with whatever the wheel has selected.
    LaunchedEffect(state.drawerIndex, pageCount) {
        val target = (state.drawerIndex / PER_PAGE).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(skin.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 12f) state.closeDrawer()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(skin.track),
            )
        }

        Spacer(Modifier.height(10.dp))

        BasicTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            singleLine = true,
            textStyle = TextStyle(color = skin.text, fontSize = 21.sp),
            cursorBrush = SolidColor(skin.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(31.dp))
                        .background(skin.card)
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.query.isEmpty()) {
                        Text("Search", color = skin.textDim, fontSize = 21.sp)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val start = page * PER_PAGE
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until ROWS) {
                    Row(Modifier.fillMaxWidth()) {
                        for (column in 0 until COLUMNS) {
                            val index = start + row * COLUMNS + column
                            val entry = apps.getOrNull(index)
                            if (entry == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                AppTile(
                                    state = state,
                                    entry = entry,
                                    selected = index == state.drawerIndex,
                                    onAssign = { pendingAssign = entry },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        ) {
            repeat(pageCount) { page ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (page == pagerState.currentPage) skin.accent else skin.track),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }

    pendingAssign?.let { entry ->
        FaderPicker(
            entry = entry,
            onPick = { slot ->
                state.assignFader(slot, entry)
                pendingAssign = null
            },
            onDismiss = { pendingAssign = null },
        )
    }
}

/**
 * Shown after long-pressing an app: pick which end of which fader it lands on.
 * Slots run 0..7, two per fader, top end first.
 */
@Composable
private fun FaderPicker(entry: AppEntry, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxSize()
            .background(skin.background.copy(alpha = 0.94f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = entry.label,
                color = skin.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "どのフェーダーに割り当てますか",
                color = skin.textDim,
                fontSize = 13.sp,
            )
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (column in 0 until 4) {
                        val slot = row * 4 + column
                        val fader = slot / 2 + 1
                        val end = if (slot % 2 == 0) "上" else "下"
                        Box(
                            Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(skin.card)
                                .clickable { onPick(slot) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$fader$end",
                                color = skin.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    state: LauncherState,
    entry: AppEntry,
    selected: Boolean,
    onAssign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) skin.card else Color.Transparent)
            .combinedClickable(
                onLongClick = onAssign,
                onClick = { state.launch(entry) },
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = remember(entry.key) { state.iconOf(entry) }
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = entry.label, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            color = skin.text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .background(if (selected) skin.accent else Color.Transparent),
        )
    }
}
