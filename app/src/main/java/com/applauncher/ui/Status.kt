package com.applauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Status(
    val time: String,
    val date: String,
    val dayName: String,   // FRI
    val dayOfMonth: String, // 06
    val month: String,     // NOV
    val battery: Int,
    val charging: Boolean,
)

/** Current time, date and battery, kept up to date. */
@Composable
fun rememberStatus(clock24h: Boolean? = null): Status {
    val context = LocalContext.current
    var status by remember(clock24h) { mutableStateOf(readStatus(context, clock24h)) }

    DisposableEffect(context, clock24h) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                status = readStatus(context, clock24h)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // Minute ticks pause while the screen is off, so refresh when we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = readStatus(context, clock24h) }

    return status
}

/** [clock24h]: null = follow the phone's own 12/24-hour setting. */
private fun readStatus(context: Context, clock24h: Boolean?): Status {
    val now = LocalDateTime.now()
    val timePattern = if (clock24h ?: DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val battery = context.getSystemService(BatteryManager::class.java)
    val locale = Locale.getDefault()
    fun fmt(pattern: String) = now.format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
    return Status(
        time = now.format(DateTimeFormatter.ofPattern(timePattern)),
        date = fmt("EEE dd MMM"),
        dayName = fmt("EEE"),
        dayOfMonth = fmt("dd"),
        month = fmt("MMM"),
        battery = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0,
        charging = battery?.isCharging == true,
    )
}
