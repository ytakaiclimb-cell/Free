package com.mpc.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Screen brightness, as a 0..1 value.
 *
 * Writing it needs WRITE_SETTINGS, which the user grants on a system screen.
 * Until then [canWrite] is false and the fader stays read-only rather than
 * failing silently.
 */
class Brightness(private val context: Context) {

    val canWrite: Boolean get() = Settings.System.canWrite(context)

    val auto: Boolean
        get() = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)

    val level: Float
        get() = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)

    fun set(fraction: Float): Boolean {
        if (!canWrite) return false
        return runCatching {
            // Sliding by hand means manual mode; auto would fight the change.
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (fraction.coerceIn(0.01f, 1f) * 255).toInt(),
            )
            true
        }.getOrDefault(false)
    }

    fun toggleAuto(): Boolean {
        if (!canWrite) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (auto) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                } else {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                },
            )
            true
        }.getOrDefault(false)
    }

    /** Opens the system screen that grants WRITE_SETTINGS. */
    fun requestAccess() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
