package com.applauncher.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.applauncher.R

/**
 * Rings an alarm or timer: a notification that keeps playing your alarm sound until you stop it,
 * and opens the full-screen ringing screen (also over the lock screen).
 */
object Ringer {

    private const val CHANNEL = "clock_ringing"
    private const val NOTIFICATION_ID = 5100

    fun ring(context: Context, title: String, snoozable: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val screen = PendingIntent.getActivity(
            context, 0, RingActivity.intent(context, title, snoozable),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = action(context, 1, ClockReceiver.ACTION_STOP, title)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_clock_small)
            .setContentTitle(title)
            .setContentText(if (snoozable) "Alarm" else "Timer")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setTimeoutAfter(10 * 60 * 1000L) // stops by itself after 10 minutes
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
        if (snoozable) {
            builder.addAction(Notification.Action.Builder(null, "Snooze", action(context, 2, ClockReceiver.ACTION_SNOOZE, title)).build())
        }
        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT } // keep repeating the sound
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun stop(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun action(context: Context, code: Int, action: String, title: String) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ClockReceiver::class.java).setAction(action).putExtra("title", title),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
