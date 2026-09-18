package com.pop.insta.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pop.insta.BuildConfig
import com.pop.insta.core.Backdrop
import com.pop.insta.core.Composer
import com.pop.insta.core.FitMode
import com.pop.insta.core.PostFormat
import com.pop.insta.core.Renderer
import kotlin.math.roundToInt

/** The picker will not hand over more than this in one go. */
private const val MAX_AT_ONCE = 30

private val popScheme = darkColorScheme(
    primary = Skin.Accent,
    background = Skin.Shell,
    surface = Skin.Panel,
    onPrimary = Skin.Shell,
    onBackground = Skin.Text,
    onSurface = Skin.Text,
)

@Composable
fun PopScreen(state: EditorState) {
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AT_ONCE)
    ) { uris -> if (uris.isNotEmpty()) state.open(uris) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) state.open(uris) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AT_ONCE)
    ) { uris -> if (uris.isNotEmpty()) state.open(uris) }

    // A device that cannot open a picker throws rather than returning, so the
    // failure is caught and shown instead of looking like a dead button.
    val openPhoto: () -> Unit = {
        try {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (t: Throwable) {
            state.report("写真を開けませんでした（${t.javaClass.simpleName}）")
        }
    }
    val openPdf: () -> Unit = {
        try {
            documentPicker.launch(arrayOf("application/pdf"))
        } catch (t: Throwable) {
            state.report("PDF を開けませんでした（${t.javaClass.simpleName}）")
        }
    }
    val openVideo: () -> Unit = {
        try {
            videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } catch (t: Throwable) {
            state.report("動画を開けませんでした（${t.javaClass.simpleName}）")
        }
    }

    MaterialTheme(colorScheme = popScheme) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Skin.Shell)
        ) {
            // The preview takes a share of the window rather than whatever is
            // left over, so a short screen can never squeeze it — and the
            // controls under it — down to nothing.
            val stageHeight = (maxHeight * 0.42f).coerceIn(200.dp, 460.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Header(state, openPhoto, openPdf, openVideo)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!state.loaded) {
                        EmptyStage(openPhoto, openPdf, openVideo)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(stageHeight)
                        ) { Stage(state) }
                    }
                    Controls(state)
                }
                Actions(state)
            }
        }
    }
}

@Composable
private fun Header(
    state: EditorState,
    openPhoto: () -> Unit,
    openPdf: () -> Unit,
    openVideo: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "POP → INSTAGRAM",
                color = Skin.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = state.notice
                    ?: state.name.ifEmpty { null }
                    ?: "A4 の POP を投稿サイズに ・ v${BuildConfig.VERSION_NAME}",
                color = if (state.notice != null) Skin.Accent else Skin.TextDim,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Always on screen: this is the only guaranteed way into the app.
        GhostButton("写真", Modifier.width(52.dp), enabled = !state.busy, onClick = openPhoto)
        Spacer(Modifier.width(6.dp))
        GhostButton("PDF", Modifier.width(52.dp), enabled = !state.busy, onClick = openPdf)
        Spacer(Modifier.width(6.dp))
        GhostButton("動画", Modifier.width(52.dp), enabled = !state.busy, onClick = openVideo)
    }
}

@Composable
private fun EmptyStage(openPhoto: () -> Unit, openPdf: () -> Unit, openVideo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("写真から", Modifier.weight(1f), primary = true, onClick = openPhoto)
            ActionButton("PDF から", Modifier.weight(1f), onClick = openPdf)
            ActionButton("動画から", Modifier.weight(1f), onClick = openVideo)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "A4 で作った POP を、そのまま\nインスタの投稿サイズに切り出します。",
            color = Skin.Text,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "写真でも、Word や Canva から書き出した PDF でも、動画でも読み込めます。",
            color = Skin.TextDim,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        SheetDiagram()
    }
}

/** The frame itself: backdrop, POP, and the pinch-and-drag on top of both. */
@Composable
private fun Stage(state: EditorState) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val room = maxWidth / maxHeight
        val frame = if (room > state.format.aspect) {
            Modifier.fillMaxHeight()
        } else {
            Modifier.fillMaxWidth()
        }
        Box(
            frame
                .aspectRatio(state.format.aspect)
                .border(1.dp, Skin.Line)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        state.transform(
                            pan.x / size.width.toFloat(),
                            pan.y / size.height.toFloat(),
                            zoom,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { state.resetPlacement() })
                }
        ) {
            val image = state.image
            if (image != null && state.loaded) {
                Canvas(Modifier.fillMaxSize()) {
                    val frameW = size.width
                    val frameH = size.height
                    val tile = state.blurTile
                    if (state.backdrop == Backdrop.BLUR && tile != null) {
                        drawImage(
                            image = tile,
                            dstSize = IntSize(frameW.roundToInt(), frameH.roundToInt()),
                        )
                        drawRect(Color.Black.copy(alpha = 0.2f))
                    } else {
                        drawRect(Color(Renderer.backdropColor(state.backdrop, state.paper)))
                    }
                    val place = Composer.place(
                        layout = state.layout,
                        srcW = state.srcW,
                        srcH = state.srcH,
                        frameW = frameW,
                        frameH = frameH,
                        margin = state.margin,
                        mode = state.mode,
                    )
                    val pad = Composer.pad(frameW, frameH, state.margin)
                    clipRect(
                        left = pad,
                        top = pad,
                        right = frameW - pad,
                        bottom = frameH - pad,
                    ) {
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(place.left.roundToInt(), place.top.roundToInt()),
                            dstSize = IntSize(
                                place.width.roundToInt().coerceAtLeast(1),
                                place.height.roundToInt().coerceAtLeast(1),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(state: EditorState) {
    val enabled = state.loaded
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Strip(state)
        SectionLabel("サイズ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PostFormat.entries.forEach { format ->
                Chip(
                    title = format.ratio,
                    subtitle = format.label,
                    selected = state.format == format,
                    modifier = Modifier.weight(1f),
                ) { state.chooseFormat(format) }
            }
        }

        SectionLabel("合わせかた")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitMode.entries.forEach { mode ->
                Chip(
                    title = mode.label,
                    subtitle = mode.hint,
                    selected = state.mode == mode,
                    modifier = Modifier.weight(1f),
                ) { state.chooseMode(mode) }
            }
        }

        if (state.isVideo) {
            Text(
                text = "動画は背景と余白が効きません。「切り抜く」を選ぶと位置とピンチが反映されます。",
                color = Skin.Marker,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }

        SectionLabel("背景")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Backdrop.entries.forEach { backdrop ->
                val fill = if (backdrop == Backdrop.BLUR) {
                    blurBrush
                } else {
                    solid(Color(Renderer.backdropColor(backdrop, state.paper)))
                }
                Swatch(
                    label = backdrop.label,
                    fill = fill,
                    selected = state.backdrop == backdrop,
                    modifier = Modifier.weight(1f),
                ) { state.chooseBackdrop(backdrop) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "余白",
                color = Skin.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Slider(
                value = state.margin,
                onValueChange = { state.changeMargin(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                enabled = enabled,
                valueRange = 0f..0.18f,
                colors = SliderDefaults.colors(
                    thumbColor = Skin.Accent,
                    activeTrackColor = Skin.Accent,
                    inactiveTrackColor = Skin.PanelHi,
                ),
            )
            Text(
                text = "${(state.margin * 100).roundToInt()}%",
                color = Skin.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.isPdf && state.pageCount > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "ページ ${state.pageIndex + 1} / ${state.pageCount}",
                    color = Skin.TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    label = "前へ",
                    modifier = Modifier.width(74.dp),
                    enabled = !state.busy && state.pageIndex > 0,
                ) { state.turnPage(-1) }
                GhostButton(
                    label = "次へ",
                    modifier = Modifier.width(74.dp),
                    enabled = !state.busy && state.pageIndex < state.pageCount - 1,
                ) { state.turnPage(1) }
            }
        }
    }
}

/** What is open, shown as soon as there is more than one. */
@Composable
private fun Strip(state: EditorState) {
    if (state.items.size < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.items.size} 件（設定は全部に効きます）",
                color = Skin.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            GhostButton("クリア", Modifier.width(78.dp), enabled = !state.busy) { state.clearAll() }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.items) { index, item ->
                val selected = index == state.active
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Skin.PanelHi else Skin.Panel)
                        .border(
                            width = 1.dp,
                            color = if (selected) Skin.Accent else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable(enabled = !state.busy) { state.select(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = item.thumb,
                        contentDescription = null,
                        modifier = Modifier.padding(5.dp).fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(state: EditorState) {
    val ready = state.loaded && !state.busy
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                label = "位置をリセット",
                modifier = Modifier.weight(1f),
                enabled = ready,
            ) { state.resetPlacement() }
            GhostButton(
                label = if (state.isVideo) "動画は 1 サイズずつ" else "3サイズまとめて保存",
                modifier = Modifier.weight(1f),
                enabled = ready && !state.isVideo,
            ) { state.export(PostFormat.entries.toList(), toInstagram = false) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = when {
                    state.progress >= 0f -> "変換中… ${(state.progress * 100).roundToInt()}%"
                    state.busy -> "処理中…"
                    state.isVideo -> "MP4 で保存"
                    else -> "保存"
                },
                modifier = Modifier.weight(1f),
                enabled = ready,
            ) { state.export(listOf(state.format), toInstagram = false) }
            ActionButton(
                label = "Instagram へ",
                modifier = Modifier.weight(1.3f),
                primary = true,
                enabled = ready,
            ) { state.export(listOf(state.format), toInstagram = true) }
        }
        if (state.items.size > 1) {
            Spacer(Modifier.height(10.dp))
            ActionButton(
                label = if (state.progress >= 0f) {
                    "保存中… ${(state.progress * 100).roundToInt()}%"
                } else {
                    "${state.items.size} 件すべて保存"
                },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                enabled = ready,
            ) { state.exportAll() }
        }
    }
}
