package com.applauncher.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import com.applauncher.R

/** The incoming-call and ongoing-call notifications. */
object CallNotifications {

    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"
    private const val NOTIFICATION_ID = 4201
    const val ACTION_DECLINE = "com.applauncher.phone.DECLINE"
    const val ACTION_HANG_UP = "com.applauncher.phone.HANG_UP"

    fun update(context: Context) {
        val call = CallManager.primary() ?: return cancel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)

        val state = CallManager.stateOf(call)
        val number = PhoneData.numberOf(call)
        val name = PhoneData.knownName(call) ?: number.ifBlank { "Unknown caller" }
        val openCallScreen = PendingIntent.getActivity(
            context, 0, InCallActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = if (state == Call.STATE_RINGING) {
            incoming(context, name, openCallScreen)
        } else {
            ongoing(context, call, state, name, openCallScreen)
        }
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun incoming(context: Context, name: String, open: PendingIntent): Notification {
        val answer = PendingIntent.getActivity(
            context, 1, InCallActivity.intent(context).setAction(InCallActivity.ACTION_ANSWER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val decline = broadcast(context, 2, ACTION_DECLINE)
        val builder = Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(name)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            // Shows the full call screen when the phone is locked or the screen is off.
            .setFullScreenIntent(open, true)
        if (Build.VERSION.SDK_INT >= 31) {
            val caller = Person.Builder().setName(name).setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forIncomingCall(caller, decline, answer))
        } else {
            builder.addAction(Notification.Action.Builder(null, "Decline", decline).build())
            builder.addAction(Notification.Action.Builder(null, "Answer", answer).build())
        }
        return builder.build()
    }

    private fun ongoing(context: Context, call: Call, state: Int, name: String, open: PendingIntent): Notification {
        val text = when (state) {
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose a SIM"
            else -> "Ongoing call"
        }
        val builder = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(name)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Hang up", broadcast(context, 3, ACTION_HANG_UP)).build())
        val connected = call.details.connectTimeMillis
        if (state == Call.STATE_ACTIVE && connected > 0) {
            builder.setUsesChronometer(true).setWhen(connected).setShowWhen(true)
        }
        return builder.build()
    }

    private fun broadcast(context: Context, requestCode: Int, action: String) = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_INCOMING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    // Android itself plays the ringtone and vibrates; the notification stays silent.
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}

/** Handles the Decline and Hang up buttons on call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallManager.primary() ?: return
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> CallManager.decline(call)
            CallNotifications.ACTION_HANG_UP -> CallManager.hangUp(call)
        }
    }
}
