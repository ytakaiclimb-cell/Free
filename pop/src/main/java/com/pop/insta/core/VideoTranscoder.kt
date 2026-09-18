package com.pop.insta.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Lays a clip into a post-shaped frame and writes a new MP4.
 *
 * The part of the clip that stays visible is worked out with exactly the same
 * geometry the preview uses, and handed to the video pipeline as a crop; the
 * frame itself is then scaled to the post size. Cutting a clip down is lossy
 * work no matter what, so it is done once, by the device's own encoder.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object VideoTranscoder {

    sealed interface Outcome {
        data class Ok(val file: File) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /**
     * The region of the source that lands inside the frame, in the normalised
     * device coordinates the crop effect wants: -1..1, y pointing up.
     */
    fun visibleRegion(
        srcW: Int,
        srcH: Int,
        format: PostFormat,
        mode: FitMode,
        margin: Float,
        layout: Layout,
    ): FloatArray {
        val frameW = format.width.toFloat()
        val frameH = format.height.toFloat()
        val place = Composer.place(layout, srcW, srcH, frameW, frameH, margin, mode)
        // Where the frame's edges fall on the drawn picture, 0..1 across it.
        val u0 = ((0f - place.left) / place.width).coerceIn(0f, 1f)
        val u1 = ((frameW - place.left) / place.width).coerceIn(0f, 1f)
        val v0 = ((0f - place.top) / place.height).coerceIn(0f, 1f)
        val v1 = ((frameH - place.top) / place.height).coerceIn(0f, 1f)
        return floatArrayOf(
            2f * min(u0, u1) - 1f,   // left
            2f * max(u0, u1) - 1f,   // right
            1f - 2f * max(v0, v1),   // bottom
            1f - 2f * min(v0, v1),   // top
        )
    }

    private fun needsCrop(region: FloatArray): Boolean =
        region[0] > -0.999f || region[1] < 0.999f || region[2] > -0.999f || region[3] < 0.999f

    /**
     * Runs one clip through. Must be called from the main thread: the video
     * pipeline keeps to the looper it was built on.
     */
    suspend fun run(
        context: Context,
        scope: CoroutineScope,
        uri: Uri,
        srcW: Int,
        srcH: Int,
        format: PostFormat,
        mode: FitMode,
        margin: Float,
        layout: Layout,
        onProgress: (Float) -> Unit,
    ): Outcome {
        val output = File(context.cacheDir, "pop-video-${System.currentTimeMillis()}.mp4")
        val effects = mutableListOf<androidx.media3.common.Effect>()
        // Cropping in COVER is what carries the drag and the pinch across.
        val region = visibleRegion(srcW, srcH, format, mode, margin, layout)
        if (mode == FitMode.COVER && needsCrop(region)) {
            effects += Crop(region[0], region[1], region[2], region[3])
        }
        effects += Presentation.createForWidthAndHeight(
            format.width,
            format.height,
            if (mode == FitMode.COVER) {
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            } else {
                Presentation.LAYOUT_SCALE_TO_FIT
            },
        )

        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(ImmutableList.of(), ImmutableList.copyOf(effects)))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        settle(continuation, Outcome.Ok(output))
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        output.delete()
                        settle(
                            continuation,
                            Outcome.Failed("変換できませんでした（${exception.errorCode}）"),
                        )
                    }
                })
                .build()

            // Progress is polled: the pipeline reports it on demand, not by push.
            val watcher = scope.launch {
                val holder = ProgressHolder()
                while (continuation.isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(200)
                }
            }
            continuation.invokeOnCancellation {
                watcher.cancel()
                transformer.cancel()
                output.delete()
            }

            try {
                transformer.start(item, output.absolutePath)
            } catch (t: Throwable) {
                watcher.cancel()
                settle(continuation, Outcome.Failed("変換を始められませんでした（${t.javaClass.simpleName}）"))
            }
        }
    }

    private fun settle(continuation: CancellableContinuation<Outcome>, outcome: Outcome) {
        if (continuation.isActive) continuation.resume(outcome)
    }
}
