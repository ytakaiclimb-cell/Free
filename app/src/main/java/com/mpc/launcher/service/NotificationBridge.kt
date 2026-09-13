package com.mpc.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Feeds [Badges] and gives the media module a component to authenticate with
 * when it asks for active sessions. Android binds it only once the user turns
 * on notification access, so everything downstream degrades quietly.
 */
class NotificationBridge : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishCurrent()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Badges.publish(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publishCurrent()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publishCurrent()

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
