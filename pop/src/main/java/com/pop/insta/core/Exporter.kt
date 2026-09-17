package com.pop.insta.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saving to the camera roll, and handing the result to Instagram. */
object Exporter {
    private const val FOLDER = "POP"
    private const val INSTAGRAM = "com.instagram.android"

    fun fileName(format: PostFormat, stamp: Date = Date()): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(stamp)
        return "POP_${time}_${format.width}x${format.height}.jpg"
    }

    /** Writes a JPEG into Pictures/POP and returns its MediaStore entry. */
    fun save(context: Context, bitmap: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + FOLDER,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("cannot write $uri")
            stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Hands the saved posts over. Instagram directly when it is installed,
     * otherwise the ordinary share sheet.
     */
    fun share(context: Context, uris: List<Uri>, preferInstagram: Boolean) {
        if (uris.isEmpty()) return
        val base = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        base.type = "image/jpeg"
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (preferInstagram) {
            val direct = Intent(base).setPackage(INSTAGRAM)
            if (direct.resolveActivity(context.packageManager) != null) {
                context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        val chooser = Intent.createChooser(base, "投稿する")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
