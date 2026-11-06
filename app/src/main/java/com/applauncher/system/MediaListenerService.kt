package com.applauncher.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Turned on by the user under "Notification access". It lets the launcher:
 * - see what music is playing (music tile), and
 * - show and dismiss notifications in its own notification panel.
 *
 * Keep this class name: renaming it would switch off the access the user already granted.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() = NotificationStore.attach(this)
    override fun onListenerDisconnected() = NotificationStore.detach()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = NotificationStore.refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NotificationStore.refresh()

    // Badge settings or importance changed.
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = NotificationStore.refresh()

    override fun onInterruptionFilterChanged(interruptionFilter: Int) =
        NotificationStore.updateDnd(interruptionFilter)
}
