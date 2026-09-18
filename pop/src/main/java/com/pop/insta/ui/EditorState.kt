package com.pop.insta.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pop.insta.core.Backdrop
import com.pop.insta.core.Composer
import com.pop.insta.core.Exporter
import com.pop.insta.core.FitMode
import com.pop.insta.core.Layout
import com.pop.insta.core.LoadResult
import com.pop.insta.core.Palette
import com.pop.insta.core.PostFormat
import com.pop.insta.core.Renderer
import com.pop.insta.core.Scaler
import com.pop.insta.core.Source
import com.pop.insta.core.SourceLoader
import com.pop.insta.core.VideoTranscoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One opened POP. Only a screen sized copy is kept; the full size pixels are
 * read again at the moment it is exported, so a dozen of these can be open at
 * once without filling memory.
 */
class Item(
    val uri: Uri,
    val name: String,
    val isPdf: Boolean,
    val isVideo: Boolean,
    val durationMs: Long,
    val pageCount: Int,
    var pageIndex: Int,
    var preview: Bitmap,
    var image: ImageBitmap,
    var thumb: ImageBitmap,
    var paper: Int,
    var layout: Layout,
)

/** Everything the editor knows: the POPs, the frame, and how they sit in it. */
@Stable
class EditorState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** Everything opened, in the order it was opened. */
    val items = mutableStateListOf<Item>()

    var active by mutableStateOf(0)
        private set

    // The working copy of whichever item is on screen.
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurTile by mutableStateOf<ImageBitmap?>(null)
        private set
    var paper by mutableStateOf(AndroidColor.WHITE)
        private set
    var srcW by mutableStateOf(0)
        private set
    var srcH by mutableStateOf(0)
        private set
    var name by mutableStateOf("")
        private set
    var isPdf by mutableStateOf(false)
        private set
    var isVideo by mutableStateOf(false)
        private set
    var pageCount by mutableStateOf(1)
        private set
    var pageIndex by mutableStateOf(0)
        private set
    var layout by mutableStateOf(Layout())
        private set

    // Settings, shared by every open POP.
    var format by mutableStateOf(PostFormat.PORTRAIT)
        private set
    var mode by mutableStateOf(FitMode.CONTAIN)
        private set
    var backdrop by mutableStateOf(Backdrop.PAPER)
        private set
    var margin by mutableStateOf(0.04f)
        private set

    var busy by mutableStateOf(false)
        private set

    /** -1 when nothing is running, otherwise how far a batch has got. */
    var progress by mutableStateOf(-1f)
        private set

    /** The last thing that went wrong, shown in the header rather than only
     *  flashed as a toast, which is easy to miss. */
    var notice by mutableStateOf<String?>(null)
        private set

    val loaded: Boolean get() = srcW > 0

    // ---- loading -----------------------------------------------------------

    /** Surfaces a problem the screen itself ran into, such as a picker that
     *  the device could not open. */
    fun report(message: String) {
        notice = message
    }

    fun open(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        busy = true
        notice = null
        scope.launch {
            val fresh = mutableListOf<Item>()
            var failure: String? = null
            for (uri in uris) {
                when (val result = withContext(Dispatchers.IO) { read(uri, 0) }) {
                    is LoadResult.Failed -> if (failure == null) failure = result.message
                    is LoadResult.Ok -> fresh += result.source.toItem()
                }
            }
            if (fresh.isNotEmpty()) {
                stash()
                val first = items.size
                items.addAll(fresh)
                busy = false
                select(first)
                if (fresh.size > 1) toast("${fresh.size} 件を読み込みました")
            } else {
                busy = false
            }
            if (failure != null) notice = failure
        }
    }

    private fun read(uri: Uri, page: Int) =
        SourceLoader.load(context, uri, page, SourceLoader.PREVIEW_EDGE)

    private fun Source.toItem(): Item {
        val thumbBitmap = Scaler.reduce(bitmap, 220)
        return Item(
            uri = uri,
            name = name,
            isPdf = isPdf,
            isVideo = isVideo,
            durationMs = durationMs,
            pageCount = pageCount,
            pageIndex = pageIndex,
            preview = bitmap,
            image = bitmap.asImageBitmap(),
            thumb = thumbBitmap.asImageBitmap(),
            paper = Palette.paperColor(bitmap),
            layout = Layout(),
        )
    }

    /** Copies the working values back into the entry they came from. */
    private fun stash() {
        items.getOrNull(active)?.let { item ->
            item.layout = layout
            item.pageIndex = pageIndex
        }
    }

    fun select(index: Int) {
        if (index < 0 || index >= items.size) return
        if (index != active) stash()
        active = index
        val item = items[index]
        image = item.image
        paper = item.paper
        srcW = item.preview.width
        srcH = item.preview.height
        name = item.name
        isPdf = item.isPdf
        isVideo = item.isVideo
        pageCount = item.pageCount
        pageIndex = item.pageIndex
        layout = item.layout
        refreshBlurTile()
    }

    fun clearAll() {
        items.clear()
        active = 0
        image = null
        blurTile = null
        srcW = 0
        srcH = 0
        name = ""
        isPdf = false
        isVideo = false
        pageCount = 1
        pageIndex = 0
        layout = Layout()
        notice = null
    }

    /** Steps through a multi-page PDF without leaving the editor. */
    fun turnPage(delta: Int) {
        val item = items.getOrNull(active) ?: return
        if (!item.isPdf || item.pageCount <= 1 || busy) return
        val next = (item.pageIndex + delta).coerceIn(0, item.pageCount - 1)
        if (next == item.pageIndex) return
        busy = true
        scope.launch {
            when (val result = withContext(Dispatchers.IO) { read(item.uri, next) }) {
                is LoadResult.Failed -> {
                    busy = false
                    notice = result.message
                }

                is LoadResult.Ok -> {
                    val page = result.source
                    item.preview = page.bitmap
                    item.image = page.bitmap.asImageBitmap()
                    item.thumb = withContext(Dispatchers.Default) {
                        Scaler.reduce(page.bitmap, 220).asImageBitmap()
                    }
                    item.paper = withContext(Dispatchers.Default) { Palette.paperColor(page.bitmap) }
                    item.pageIndex = next
                    item.layout = Layout()
                    busy = false
                    select(active)
                }
            }
        }
    }

    private fun refreshBlurTile() {
        val item = items.getOrNull(active) ?: return
        val aspect = format.aspect
        scope.launch {
            val tile = withContext(Dispatchers.Default) {
                Palette.blurTile(item.preview, aspect).asImageBitmap()
            }
            if (items.getOrNull(active) === item && format.aspect == aspect) blurTile = tile
        }
    }

    // ---- composition -------------------------------------------------------

    fun chooseFormat(next: PostFormat) {
        if (next == format) return
        format = next
        layout = Layout()
        refreshBlurTile()
    }

    fun chooseMode(next: FitMode) {
        if (next == mode) return
        mode = next
        layout = Layout()
    }

    fun chooseBackdrop(next: Backdrop) {
        backdrop = next
    }

    fun changeMargin(next: Float) {
        margin = next.coerceIn(0f, 0.2f)
        layout = normalized(layout)
    }

    /** One pinch-and-drag step, in fractions of the frame. */
    fun transform(panX: Float, panY: Float, zoomBy: Float) {
        if (!loaded) return
        layout = normalized(
            layout.copy(
                zoom = layout.zoom * zoomBy,
                offsetX = layout.offsetX + panX,
                offsetY = layout.offsetY + panY,
            )
        )
    }

    fun resetPlacement() {
        layout = Layout()
    }

    private fun normalized(next: Layout): Layout {
        if (!loaded) return next
        return Composer.normalize(
            layout = next,
            srcW = srcW,
            srcH = srcH,
            frameW = format.width.toFloat(),
            frameH = format.height.toFloat(),
            margin = margin,
            mode = mode,
        )
    }

    // ---- output ------------------------------------------------------------

    /** Reads one POP back at full size and lays it into the frame. */
    private fun render(item: Item, target: PostFormat, place: Layout): Bitmap? {
        val result = SourceLoader.load(context, item.uri, item.pageIndex, SourceLoader.FULL_EDGE)
        val full = (result as? LoadResult.Ok)?.source?.bitmap ?: item.preview
        return Renderer.compose(
            src = full,
            format = target,
            mode = mode,
            backdrop = backdrop,
            margin = margin,
            layout = place,
            paper = item.paper,
        )
    }

    fun export(formats: List<PostFormat>, toInstagram: Boolean) {
        val item = items.getOrNull(active) ?: return
        if (busy || formats.isEmpty()) return
        stash()
        if (item.isVideo) {
            exportVideo(item, toInstagram)
            return
        }
        busy = true
        val place = layout
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                formats.mapNotNull { target ->
                    val bitmap = render(item, target, place) ?: return@mapNotNull null
                    val uri = Exporter.save(
                        context,
                        bitmap,
                        Exporter.fileName(target, item.name, if (item.pageCount > 1) item.pageIndex else 0),
                    )
                    bitmap.recycle()
                    uri
                }
            }
            busy = false
            if (saved.isEmpty()) {
                toast("保存できませんでした")
                return@launch
            }
            toast(
                if (saved.size == 1) "保存しました（ギャラリー / POP）"
                else "${saved.size} 枚保存しました（ギャラリー / POP）"
            )
            if (toInstagram) Exporter.share(context, saved, preferInstagram = true)
        }
    }

    /** A clip: converted by the device's own encoder, then filed in the gallery. */
    private fun exportVideo(item: Item, toInstagram: Boolean) {
        busy = true
        notice = null
        progress = 0f
        val target = format
        scope.launch {
            val outcome = VideoTranscoder.run(
                context = context,
                scope = scope,
                uri = item.uri,
                srcW = item.preview.width,
                srcH = item.preview.height,
                format = target,
                mode = mode,
                margin = margin,
                layout = item.layout,
                onProgress = { progress = it },
            )
            progress = -1f
            busy = false
            when (outcome) {
                is VideoTranscoder.Outcome.Failed -> notice = outcome.message
                is VideoTranscoder.Outcome.Ok -> {
                    val uri = withContext(Dispatchers.IO) {
                        val saved = Exporter.saveVideo(
                            context,
                            outcome.file,
                            Exporter.videoName(target, item.name),
                        )
                        outcome.file.delete()
                        saved
                    }
                    if (uri == null) {
                        notice = "保存できませんでした"
                    } else {
                        toast("保存しました（ギャラリー / POP）")
                        if (toInstagram) {
                            Exporter.share(context, listOf(uri), preferInstagram = true, mime = "video/mp4")
                        }
                    }
                }
            }
        }
    }

    /** Every open POP through the settings on screen, saved one after another. */
    fun exportAll() {
        if (busy || items.size < 2) return
        stash()
        busy = true
        notice = null
        progress = 0f
        val target = format
        val queue = items.toList()
        scope.launch {
            var saved = 0
            var failed = 0
            for ((index, item) in queue.withIndex()) {
                val done = index.toFloat() / queue.size
                val slice = 1f / queue.size
                val ok = if (item.isVideo) {
                    val outcome = VideoTranscoder.run(
                        context = context,
                        scope = scope,
                        uri = item.uri,
                        srcW = item.preview.width,
                        srcH = item.preview.height,
                        format = target,
                        mode = mode,
                        margin = margin,
                        layout = item.layout,
                        onProgress = { progress = done + it * slice },
                    )
                    when (outcome) {
                        is VideoTranscoder.Outcome.Failed -> false
                        is VideoTranscoder.Outcome.Ok -> withContext(Dispatchers.IO) {
                            val uri = Exporter.saveVideo(
                                context,
                                outcome.file,
                                Exporter.videoName(target, item.name),
                            )
                            outcome.file.delete()
                            uri != null
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        val bitmap = render(item, target, item.layout)
                        if (bitmap == null) {
                            false
                        } else {
                            val uri = Exporter.save(
                                context,
                                bitmap,
                                Exporter.fileName(target, item.name, if (item.pageCount > 1) item.pageIndex else 0),
                            )
                            bitmap.recycle()
                            uri != null
                        }
                    }
                }
                if (ok) saved++ else failed++
                progress = (index + 1f) / queue.size
            }
            progress = -1f
            busy = false
            if (saved > 0) toast("$saved 件保存しました（ギャラリー / POP）")
            if (failed > 0) notice = "$failed 件は保存できませんでした"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
