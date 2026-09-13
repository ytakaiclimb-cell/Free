package com.pocketlauncher.classic.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

/** Opening the stock apps and system screens the launcher links to. */
object SystemApps {

    /** Clock packages in preference order: Samsung first, then Google, then AOSP. */
    private val CLOCK_PACKAGES = listOf(
        "com.sec.android.app.clockpackage",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
    )

    /**
     * Opens the device clock app, falling back to the generic "show alarms"
     * intent when none of the known packages is installed.
     */
    fun openClock(context: Context): Boolean {
        val direct = CLOCK_PACKAGES.firstNotNullOfOrNull {
            context.packageManager.getLaunchIntentForPackage(it)
        }
        val intent = direct ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)
        return start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Starts a countdown of [minutes] in the device clock app. */
    fun startTimer(context: Context, minutes: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "${minutes}分")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    fun openSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The system screen where this launcher is made the default home app. */
    fun openHomeSettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            openSettings(context)

    /** The system screen that grants notification access, for the fader badges. */
    fun openNotificationAccess(context: Context): Boolean {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent) || openSettings(context)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
