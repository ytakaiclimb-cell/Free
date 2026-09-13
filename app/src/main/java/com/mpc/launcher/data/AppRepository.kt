package com.mpc.launcher.data

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

/** One launchable activity on the device. [key] is what layouts store. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
) {
    val key: String get() = "$packageName/$className"
}

/** Reads the installed apps, caches their icons, and starts them. */
class AppRepository(private val context: Context) {

    private var cached: List<AppEntry>? = null
    private val byKey = HashMap<String, AppEntry>()
    private val iconCache = HashMap<String, ImageBitmap?>()

    fun apps(): List<AppEntry> = cached ?: load().also { loaded ->
        cached = loaded
        byKey.clear()
        loaded.forEach { byKey[it.key] = it }
    }

    fun refresh() {
        cached = null
        iconCache.clear()
    }

    private fun load(): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.JAPAN)
        return pm.queryIntentActivities(main, 0)
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

    fun byKey(key: String?): AppEntry? {
        if (key == null) return null
        apps()
        return byKey[key]
    }

    /** First installed app among [packages], in the order given. */
    fun byPackage(vararg packages: String): AppEntry? {
        val loaded = apps()
        for (name in packages) {
            loaded.firstOrNull { it.packageName == name }?.let { return it }
        }
        return null
    }

    /** Resolves whatever handles the still-image camera intent. */
    fun cameraApp(): AppEntry? {
        val intent = Intent("android.media.action.STILL_IMAGE_CAMERA")
        val resolved = context.packageManager.queryIntentActivities(intent, 0).firstOrNull()
            ?: return null
        val activity = resolved.activityInfo ?: return null
        return byPackage(activity.packageName)
            ?: AppEntry(resolved.loadLabel(context.packageManager).toString(), activity.packageName, activity.name)
    }

    fun icon(entry: AppEntry): ImageBitmap? = iconCache.getOrPut(entry.key) {
        runCatching {
            context.packageManager
                .getActivityIcon(ComponentName(entry.packageName, entry.className))
                .toImageBitmap(ICON_PX)
        }.getOrNull()
    }

    fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    fun launch(key: String?) {
        byKey(key)?.let { launch(it) }
    }

    private companion object {
        const val ICON_PX = 168
    }
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
