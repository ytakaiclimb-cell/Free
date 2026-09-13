package com.mpc.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** The system screens the launcher links out to. */
object SystemScreens {

    fun notificationAccess(context: Context) =
        start(context, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))

    fun homeApp(context: Context) =
        start(context, Intent(Settings.ACTION_HOME_SETTINGS)) || start(context, Intent(Settings.ACTION_SETTINGS))

    fun appInfo(context: Context, packageName: String) = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:$packageName")),
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
