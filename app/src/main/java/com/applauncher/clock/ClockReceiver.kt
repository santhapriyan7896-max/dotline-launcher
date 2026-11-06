package com.applauncher.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires alarms and timers, handles Stop / Snooze, and re-registers alarms after a restart or time change. */
class ClockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ClockStore.load(context)
        when (intent.action) {
            ACTION_ALARM -> {
                val alarm = ClockStore.onAlarmRang(context, intent.getIntExtra("id", -1)) ?: return
                Ringer.ring(context, alarm.label.ifBlank { "ALARM" }, snoozable = true)
            }
            ACTION_SNOOZED -> Ringer.ring(context, intent.getStringExtra("title") ?: "ALARM", snoozable = true)
            ACTION_TIMER -> {
                ClockStore.onTimerFinished(context)
                Ringer.ring(context, "TIME'S UP", snoozable = false)
            }
            ACTION_STOP -> Ringer.stop(context)
            ACTION_SNOOZE -> {
                Ringer.stop(context)
                ClockStore.snooze(context, intent.getStringExtra("title") ?: "ALARM")
            }
            // Android forgets scheduled alarms when the phone restarts or the time changes.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ClockStore.scheduleAlarms(context)
                ClockStore.scheduleTimer(context)
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.applauncher.clock.ALARM"
        const val ACTION_SNOOZED = "com.applauncher.clock.SNOOZED"
        const val ACTION_TIMER = "com.applauncher.clock.TIMER"
        const val ACTION_STOP = "com.applauncher.clock.STOP"
        const val ACTION_SNOOZE = "com.applauncher.clock.SNOOZE"
    }
}
