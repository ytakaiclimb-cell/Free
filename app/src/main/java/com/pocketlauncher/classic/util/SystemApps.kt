package com.pocketlauncher.classic.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

/** Opening the stock apps that the launcher itself links to. */
object SystemApps {

    /**
     * Clock packages in preference order. Samsung first, because that is what a
     * Galaxy Z Fold ships with; then Google's and AOSP's.
     */
    private val CLOCK_PACKAGES = listOf(
        "com.sec.android.app.clockpackage",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
    )

    /**
     * Opens the device clock app. Falls back to the generic "show alarms" intent
     * when none of the known packages is installed.
     *
     * @return true when something was actually opened.
     */
    fun openClock(context: Context): Boolean {
        val pm = context.packageManager
        val direct = CLOCK_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
        val intent = direct ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    fun openSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /** Opens the system screen where the default home app is chosen. */
    fun openHomeSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
