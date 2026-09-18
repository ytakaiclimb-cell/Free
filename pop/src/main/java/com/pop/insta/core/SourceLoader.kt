package com.pop.insta.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** A POP that has been read into memory, whatever it arrived as. */
data class Source(
    val bitmap: Bitmap,
    val name: String,
    val uri: Uri,
    val isPdf: Boolean,
    val pageCount: Int,
    val pageIndex: Int,
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

sealed interface LoadResult {
    data class Ok(val source: Source) : LoadResult
    data class Failed(val message: String) : LoadResult
}

/**
 * Reads a POP from the gallery, the files app, or a share. A4 artwork usually
 * arrives as a PDF out of Word or Canva, so page one of a PDF is rendered at
 * print-ish resolution rather than refused.
 */
object SourceLoader {
    /** Long edge for an export. Plenty for a 1080 px post. */
    const val FULL_EDGE = 2600

    /** Long edge for the copy kept on screen. A dozen of these must fit in
     *  memory at once, which a dozen full size ones would not. */
    const val PREVIEW_EDGE = 1100

    fun load(context: Context, uri: Uri, page: Int = 0, maxEdge: Int = FULL_EDGE): LoadResult = try {
        if (isPdf(context, uri)) loadPdf(context, uri, page, maxEdge)
        else loadImage(context, uri, maxEdge)
    } catch (t: Throwable) {
        LoadResult.Failed("読み込めませんでした（${t.javaClass.simpleName}）")
    }

    private fun isPdf(context: Context, uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null) return type == "application/pdf"
        return uri.toString().endsWith(".pdf", ignoreCase = true)
    }

    private fun loadImage(context: Context, uri: Uri, maxEdge: Int): LoadResult {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software pixels: the backdrop sampler and the exporter both read
            // and redraw this bitmap, which a hardware one will not allow.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height)
            if (longest > maxEdge) {
                decoder.setTargetSampleSize(ceil(longest.toFloat() / maxEdge).toInt())
            }
        }
        val safe = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        return LoadResult.Ok(
            Source(
                bitmap = safe,
                name = displayName(context, uri) ?: "写真",
                uri = uri,
                isPdf = false,
                pageCount = 1,
                pageIndex = 0,
            )
        )
    }

    private fun loadPdf(context: Context, uri: Uri, page: Int, maxEdge: Int): LoadResult {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return LoadResult.Failed("PDF を開けませんでした")
        var renderer: PdfRenderer? = null
        try {
            renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) return LoadResult.Failed("空の PDF です")
            val index = page.coerceIn(0, renderer.pageCount - 1)
            val pdfPage = renderer.openPage(index)
            try {
                // Page size is in points; scale so the long edge lands on MAX_EDGE.
                val scale = maxEdge.toFloat() / max(pdfPage.width, pdfPage.height)
                val w = max(1, (pdfPage.width * scale).roundToInt())
                val h = max(1, (pdfPage.height * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // A PDF paints nothing where the paper is blank, so lay paper first.
                Canvas(bitmap).drawColor(Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return LoadResult.Ok(
                    Source(
                        bitmap = bitmap,
                        name = displayName(context, uri) ?: "PDF",
                        uri = uri,
                        isPdf = true,
                        pageCount = renderer.pageCount,
                        pageIndex = index,
                    )
                )
            } finally {
                pdfPage.close()
            }
        } finally {
            // PdfRenderer takes ownership of the descriptor, so it only needs
            // closing here when the renderer never got built.
            if (renderer != null) renderer.close() else descriptor.close()
        }
    }

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    } catch (t: Throwable) {
        null
    }
}
