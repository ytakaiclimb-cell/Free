package com.pocketlauncher.classic.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Collator
import java.util.Locale

/** One launchable activity on the device. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
) {
    val key: String get() = "$packageName/$className"
}

/**
 * Reads the list of launchable apps and starts them.
 * Labels and icons are cached because the click wheel re-reads them on every frame.
 */
class AppRepository(private val context: Context) {

    private var cached: List<AppEntry>? = null
    private val iconCache = HashMap<String, ImageBitmap?>()

    fun apps(): List<AppEntry> = cached ?: load().also { cached = it }

    /** Drops the cache so a newly installed app shows up. */
    fun refresh() {
        cached = null
        iconCache.clear()
    }

    private fun load(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.JAPAN)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                if (activity.packageName == context.packageName) return@mapNotNull null
                AppEntry(
                    label = resolved.loadLabel(pm).toString(),
                    packageName = activity.packageName,
                    className = activity.name,
                )
            }
            .sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })
    }

    fun icon(entry: AppEntry): ImageBitmap? = iconCache.getOrPut(entry.key) {
        runCatching {
            context.packageManager
                .getActivityIcon(ComponentName(entry.packageName, entry.className))
                .toImageBitmap(ICON_PX)
        }.getOrNull()
    }

    /** Finds a loaded app by its [AppEntry.key]. */
    fun byKey(key: String?): AppEntry? =
        if (key == null) null else apps().firstOrNull { it.key == key }

    /** Best-effort lookup by visible name, used for the "Y" shortcut. */
    fun byLabel(vararg candidates: String): AppEntry? {
        val loaded = apps()
        for (candidate in candidates) {
            loaded.firstOrNull { it.label.equals(candidate, ignoreCase = true) }?.let { return it }
        }
        for (candidate in candidates) {
            loaded.firstOrNull { it.label.contains(candidate, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val ICON_PX = 144
    }
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
