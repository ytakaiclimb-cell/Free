package com.pop.insta.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.pop.insta.core.Source
import com.pop.insta.core.SourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the editor knows: the POP, the frame, and how they sit together. */
@Stable
class EditorState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var source by mutableStateOf<Source?>(null)
        private set
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurTile by mutableStateOf<ImageBitmap?>(null)
        private set
    var paper by mutableStateOf(AndroidColor.WHITE)
        private set

    var format by mutableStateOf(PostFormat.PORTRAIT)
        private set
    var mode by mutableStateOf(FitMode.CONTAIN)
        private set
    var backdrop by mutableStateOf(Backdrop.PAPER)
        private set
    var margin by mutableStateOf(0.04f)
        private set
    var layout by mutableStateOf(Layout())
        private set

    var busy by mutableStateOf(false)
        private set

    /** The last thing that went wrong, shown in the header rather than only
     *  flashed as a toast, which is easy to miss. */
    var notice by mutableStateOf<String?>(null)
        private set

    // ---- loading -----------------------------------------------------------

    /** Surfaces a problem the screen itself ran into, such as a picker that
     *  the device could not open. */
    fun report(message: String) {
        notice = message
    }

    fun open(uri: Uri, page: Int = 0) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { SourceLoader.load(context, uri, page) }
            when (result) {
                is LoadResult.Failed -> {
                    busy = false
                    notice = result.message
                    toast(result.message)
                }

                is LoadResult.Ok -> {
                    val loaded = result.source
                    val sampled = withContext(Dispatchers.Default) { Palette.paperColor(loaded.bitmap) }
                    // The old bitmap is left to the collector: a frame that is
                    // already being drawn may still be holding it.
                    source = loaded
                    image = loaded.bitmap.asImageBitmap()
                    paper = sampled
                    layout = Layout()
                    notice = null
                    busy = false
                    refreshBlurTile()
                }
            }
        }
    }

    /** Steps through a multi-page PDF without leaving the editor. */
    fun turnPage(delta: Int) {
        val current = source ?: return
        if (!current.isPdf || current.pageCount <= 1) return
        val next = (current.pageIndex + delta).coerceIn(0, current.pageCount - 1)
        if (next != current.pageIndex) open(current.uri, next)
    }

    private fun refreshBlurTile() {
        val current = source ?: return
        val aspect = format.aspect
        scope.launch {
            val tile = withContext(Dispatchers.Default) {
                Palette.blurTile(current.bitmap, aspect).asImageBitmap()
            }
            if (source === current && format.aspect == aspect) blurTile = tile
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
        if (source == null) return
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
        val current = source ?: return next
        return Composer.normalize(
            layout = next,
            srcW = current.width,
            srcH = current.height,
            frameW = format.width.toFloat(),
            frameH = format.height.toFloat(),
            margin = margin,
            mode = mode,
        )
    }

    // ---- output ------------------------------------------------------------

    fun export(formats: List<PostFormat>, toInstagram: Boolean) {
        val current = source ?: return
        if (busy || formats.isEmpty()) return
        busy = true
        val snapshot = Snapshot(mode, backdrop, margin, layout, paper)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                formats.mapNotNull { target ->
                    val bitmap = Renderer.compose(
                        src = current.bitmap,
                        format = target,
                        mode = snapshot.mode,
                        backdrop = snapshot.backdrop,
                        margin = snapshot.margin,
                        layout = snapshot.layout,
                        paper = snapshot.paper,
                    )
                    val uri = Exporter.save(context, bitmap, Exporter.fileName(target))
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

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private data class Snapshot(
        val mode: FitMode,
        val backdrop: Backdrop,
        val margin: Float,
        val layout: Layout,
        val paper: Int,
    )
}
