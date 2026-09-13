package com.mpc.launcher.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.data.AppEntry
import com.mpc.launcher.data.Module

/** The full app list, reached by swiping up from the bottom of the screen. */
@Composable
fun Drawer(state: LauncherState, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = "ALL APPS",
        onDismiss = { state.closeDrawer() },
        modifier = modifier,
    ) {
        SearchField(state)
        Spacer(Modifier.height(12.dp))
        AppGrid(
            state = state,
            apps = state.filteredApps,
            onPick = { state.launch(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** What a knob holds, with a way to add more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderSheet(state: LauncherState, module: Module, modifier: Modifier = Modifier) {
    val apps = module.folderApps.mapNotNull { state.entry(it) }

    SheetScaffold(
        title = module.folderName.uppercase(),
        onDismiss = { state.closeFolder() },
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.key }) { entry ->
                AppCell(
                    state = state,
                    entry = entry,
                    modifier = Modifier.combinedClickable(
                        onLongClick = { state.removeFromFolder(module.id, entry.key) },
                        onClick = { state.launch(entry) },
                    ),
                )
            }
            item {
                Column(
                    modifier = Modifier.clickable {
                        state.openPicker(PickerTarget.FolderAdd(module.id))
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(percent = 26))
                            .background(Skin.Tray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = Skin.TextDim, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("追加", color = Skin.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}

/** Choosing an app for a pad, a fader end, or a folder. */
@Composable
fun AppPicker(state: LauncherState, target: PickerTarget, modifier: Modifier = Modifier) {
    val title = when (target) {
        is PickerTarget.PadPrimary -> "パッドに割り当て"
        is PickerTarget.PadSecondary -> "スライド先に割り当て"
        is PickerTarget.FolderAdd -> "フォルダに追加"
        is PickerTarget.FlickUp -> "上に弾いたとき"
        is PickerTarget.FlickDown -> "下に弾いたとき"
    }

    SheetScaffold(title = title, onDismiss = { state.closePicker() }, modifier = modifier) {
        SearchField(state)
        Spacer(Modifier.height(12.dp))
        AppGrid(
            state = state,
            apps = state.filteredApps,
            onPick = { state.pick(it) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Skin.Paper)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 14f) onDismiss()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(104.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Skin.TextFaint),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Skin.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Skin.Tray)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Skin.Text, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SearchField(state: LauncherState) {
    BasicTextField(
        value = state.query,
        onValueChange = state::updateQuery,
        singleLine = true,
        textStyle = TextStyle(color = Skin.Text, fontSize = 18.sp),
        cursorBrush = SolidColor(Skin.Signal),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Skin.Tray)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.query.isEmpty()) {
                    Text("Search", color = Skin.TextDim, fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun AppGrid(
    state: LauncherState,
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        items(apps, key = { it.key }) { entry ->
            AppCell(
                state = state,
                entry = entry,
                modifier = Modifier.clickable { onPick(entry) },
            )
        }
    }
}

@Composable
private fun AppCell(state: LauncherState, entry: AppEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = state.icon(entry)
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = entry.label, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(percent = 26))
                        .background(Skin.Ink),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            color = Skin.Text,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
