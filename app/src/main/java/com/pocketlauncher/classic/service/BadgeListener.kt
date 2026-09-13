package com.pocketlauncher.classic.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Feeds [Badges]. Android only binds this once the user turns on notification
 * access, so the launcher must work fine without it.
 */
class BadgeListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishCurrent()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Badges.publish(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishCurrent()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishCurrent()
    }

    private fun publishCurrent() {
        val packages = runCatching {
            (activeNotifications ?: emptyArray())
                .filter { it.isClearable }
                .map { it.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        Badges.publish(packages)
    }
}
