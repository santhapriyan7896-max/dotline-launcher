package com.applauncher.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** One alarm. [days] uses 1 = Monday … 7 = Sunday; empty means "once". */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
)

/** Countdown timer. While running it ends at [endsAt] (wall clock); while paused [remainingMs] is left. */
data class TimerState(
    val durationMs: Long = 0,
    val endsAt: Long = 0,
    val remainingMs: Long = 0,
    val running: Boolean = false,
) {
    val isSet: Boolean get() = durationMs > 0
    fun remaining(now: Long = System.currentTimeMillis()): Long =
        if (running) (endsAt - now).coerceAtLeast(0) else remainingMs
}

/** Stopwatch, counted on the phone's uptime clock so it survives the app being closed. */
data class StopwatchState(
    val startedAt: Long = 0,
    val accumulated: Long = 0,
    val running: Boolean = false,
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long =
        accumulated + if (running) now - startedAt else 0
}

/** Alarms, timer, stopwatch and world clocks: saved on the phone and scheduled with Android. */
object ClockStore {

    private const val PREFS = "clock"
    private var loaded = false

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    private val _zones = MutableStateFlow(listOf("Europe/London", "America/New_York"))
    val zones: StateFlow<List<String>> = _zones.asStateFlow()

    // --- Loading and saving ---

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(sp.getString("alarms", "[]"))
            _alarms.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val days = o.getJSONArray("days")
                Alarm(
                    id = o.getInt("id"),
                    hour = o.getInt("hour"),
                    minute = o.getInt("minute"),
                    days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                    label = o.optString("label"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }
        runCatching {
            val o = JSONObject(sp.getString("timer", "{}")!!)
            _timer.value = TimerState(o.optLong("duration"), o.optLong("endsAt"), o.optLong("remaining"), o.optBoolean("running"))
        }
        runCatching {
            val o = JSONObject(sp.getString("stopwatch", "{}")!!)
            val laps = o.optJSONArray("laps") ?: JSONArray()
            _stopwatch.value = StopwatchState(
                o.optLong("startedAt"), o.optLong("accumulated"), o.optBoolean("running"),
                (0 until laps.length()).map { laps.getLong(it) },
            )
        }
        sp.getString("zones", null)?.let { saved -> _zones.value = saved.split(',').filter { it.isNotBlank() } }
        loaded = true
    }

    private fun save(context: Context) {
        val alarms = JSONArray().apply {
            _alarms.value.forEach { a ->
                put(
                    JSONObject().put("id", a.id).put("hour", a.hour).put("minute", a.minute)
                        .put("days", JSONArray(a.days.sorted())).put("label", a.label).put("enabled", a.enabled)
                )
            }
        }
        val t = _timer.value
        val s = _stopwatch.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("alarms", alarms.toString())
            .putString(
                "timer",
                JSONObject().put("duration", t.durationMs).put("endsAt", t.endsAt)
                    .put("remaining", t.remainingMs).put("running", t.running).toString(),
            )
            .putString(
                "stopwatch",
                JSONObject().put("startedAt", s.startedAt).put("accumulated", s.accumulated)
                    .put("running", s.running).put("laps", JSONArray(s.laps)).toString(),
            )
            .putString("zones", _zones.value.joinToString(","))
            .apply()
    }

    // --- Alarms ---

    fun saveAlarm(context: Context, alarm: Alarm) {
        load(context)
        val list = _alarms.value
        val withId = if (alarm.id == 0) alarm.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else alarm
        _alarms.value = (list.filter { it.id != withId.id } + withId).sortedBy { it.hour * 60 + it.minute }
        save(context)
        scheduleAlarms(context)
    }

    fun deleteAlarm(context: Context, alarm: Alarm) {
        load(context)
        alarmManager(context)?.cancel(alarmIntent(context, alarm.id))
        _alarms.value = _alarms.value.filter { it.id != alarm.id }
        save(context)
        scheduleAlarms(context)
    }

    fun setEnabled(context: Context, alarm: Alarm, enabled: Boolean) = saveAlarm(context, alarm.copy(enabled = enabled))

    /** When [alarm] next rings, in milliseconds since 1970. */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var t = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        if (alarm.days.isNotEmpty()) while (t.dayOfWeek.value !in alarm.days) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    /** (Re)registers every switched-on alarm with Android. */
    fun scheduleAlarms(context: Context) {
        load(context)
        val manager = alarmManager(context) ?: return
        _alarms.value.forEach { alarm ->
            val operation = alarmIntent(context, alarm.id)
            manager.cancel(operation)
            if (alarm.enabled) setExact(context, manager, nextTrigger(alarm), operation)
        }
    }

    /** An alarm just rang: one-time alarms switch off, repeating ones are set for next time. */
    fun onAlarmRang(context: Context, id: Int): Alarm? {
        load(context)
        val alarm = _alarms.value.firstOrNull { it.id == id } ?: return null
        if (alarm.days.isEmpty()) {
            _alarms.value = _alarms.value.map { if (it.id == id) it.copy(enabled = false) else it }
            save(context)
        }
        scheduleAlarms(context)
        return alarm
    }

    fun snooze(context: Context, title: String, minutes: Int = 10) {
        val manager = alarmManager(context) ?: return
        val operation = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST,
            Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.ACTION_SNOOZED).putExtra("title", title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setExact(context, manager, System.currentTimeMillis() + minutes * 60_000L, operation)
    }

    // --- Timer ---

    fun startTimer(context: Context, durationMs: Long) {
        load(context)
        val now = System.currentTimeMillis()
        _timer.value = TimerState(durationMs, now + durationMs, durationMs, running = true)
        save(context)
        scheduleTimer(context)
    }

    fun pauseTimer(context: Context) {
        val t = _timer.value
        if (!t.running) return
        _timer.value = t.copy(running = false, remainingMs = t.remaining())
        save(context)
        scheduleTimer(context)
    }

    fun resumeTimer(context: Context) {
        val t = _timer.value
        if (t.running || !t.isSet) return
        _timer.value = t.copy(running = true, endsAt = System.currentTimeMillis() + t.remainingMs)
        save(context)
        scheduleTimer(context)
    }

    fun resetTimer(context: Context) {
        _timer.value = TimerState()
        save(context)
        scheduleTimer(context)
    }

    /** The timer finished ringing: clear it. */
    fun onTimerFinished(context: Context) {
        load(context)
        _timer.value = TimerState()
        save(context)
    }

    fun scheduleTimer(context: Context) {
        val manager = alarmManager(context) ?: return
        val operation = timerIntent(context)
        manager.cancel(operation)
        val t = _timer.value
        if (t.running) setExact(context, manager, t.endsAt, operation)
    }

    // --- Stopwatch ---

    fun startStopwatch(context: Context) {
        val s = _stopwatch.value
        if (s.running) return
        _stopwatch.value = s.copy(running = true, startedAt = SystemClock.elapsedRealtime())
        save(context)
    }

    fun pauseStopwatch(context: Context) {
        val s = _stopwatch.value
        if (!s.running) return
        _stopwatch.value = s.copy(running = false, accumulated = s.elapsed())
        save(context)
    }

    fun lap(context: Context) {
        val s = _stopwatch.value
        if (!s.running) return
        _stopwatch.value = s.copy(laps = s.laps + s.elapsed())
        save(context)
    }

    fun resetStopwatch(context: Context) {
        _stopwatch.value = StopwatchState()
        save(context)
    }

    // --- World clock ---

    fun addZone(context: Context, id: String) {
        if (id in _zones.value) return
        _zones.value = _zones.value + id
        save(context)
    }

    fun removeZone(context: Context, id: String) {
        _zones.value = _zones.value - id
        save(context)
    }

    // --- Android alarm plumbing ---

    private const val TIMER_REQUEST = 999
    private const val SNOOZE_REQUEST = 998

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 1000 + id,
        Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.ACTION_ALARM).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun timerIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, TIMER_REQUEST,
        Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.ACTION_TIMER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Uses Android's "alarm clock" slot: exact, wakes the phone, and shows the alarm icon. */
    private fun setExact(context: Context, manager: AlarmManager, at: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, ClockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exactAllowed = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }
}
