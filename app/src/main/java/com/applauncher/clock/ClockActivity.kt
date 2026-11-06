package com.applauncher.clock

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.data.SettingsStore
import com.applauncher.phone.RoundButton
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** Clock app: alarms, timer, stopwatch and world clock. */
class ClockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        ClockStore.load(this)
        val startTab = if (intent?.getStringExtra(EXTRA_TAB) == "timer") ClockTab.Timer else ClockTab.Alarms
        setContent { MonoTheme { ClockApp(startTab) } }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

private enum class ClockTab(val label: String) { Alarms("ALARMS"), Timer("TIMER"), Stopwatch("STOPWATCH"), World("WORLD") }

@Composable
private fun ClockApp(startTab: ClockTab) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // Alarms and timers need notifications to ring; ask once.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize().background(Mono.Black).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ClockTab.entries.forEach { t ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tab = t }.padding(6.dp),
                ) {
                    Text(t.label, style = Mono.label.copy(fontSize = 12.sp, color = if (tab == t) Mono.White else Mono.Grey))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (tab == t) Mono.Red else Mono.Black))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                ClockTab.Alarms -> AlarmsTab()
                ClockTab.Timer -> TimerTab()
                ClockTab.Stopwatch -> StopwatchTab()
                ClockTab.World -> WorldTab()
            }
        }
    }
}

// ---------------- Alarms ----------------

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun daysText(days: Set<Int>): String = when {
    days.isEmpty() -> "ONCE"
    days.size == 7 -> "EVERY DAY"
    days == setOf(1, 2, 3, 4, 5) -> "WEEKDAYS"
    days == setOf(6, 7) -> "WEEKENDS"
    else -> days.sorted().joinToString(" ") { listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")[it - 1] }
}

private fun timeText(hour: Int, minute: Int, h24: Boolean): String =
    if (h24) "%02d:%02d".format(hour, minute)
    else "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)

@Composable
private fun AlarmsTab() {
    val context = LocalContext.current
    val alarms by ClockStore.alarms.collectAsStateWithLifecycle()
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val h24 = settings.clock24h ?: android.text.format.DateFormat.is24HourFormat(context)
    var editing by remember { mutableStateOf<Alarm?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Text("No alarms yet", color = Mono.Grey, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(alarms, key = { it.id }) { alarm ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mono.Tile)
                        .clickable { editing = alarm }.padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                timeText(alarm.hour, alarm.minute, h24),
                                color = if (alarm.enabled) Mono.White else Mono.Grey,
                                fontSize = 40.sp, fontWeight = FontWeight.Light,
                            )
                            if (!h24) {
                                Spacer(Modifier.width(6.dp))
                                Text(if (alarm.hour < 12) "AM" else "PM", style = Mono.label, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        Text(
                            listOf(daysText(alarm.days), alarm.label.uppercase()).filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = Mono.label.copy(fontSize = 10.sp, color = if (alarm.enabled) Mono.Red else Mono.Grey),
                        )
                    }
                    MonoSwitch(alarm.enabled) { on ->
                        ClockStore.setEnabled(context, alarm, on)
                        if (on) toastNext(context, alarm)
                    }
                }
            }
        }
        RoundButton(
            size = 64.dp, color = Mono.Red, onClick = { editing = Alarm(id = 0, hour = 7, minute = 0) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "New alarm", tint = Mono.White)
        }
    }

    editing?.let { alarm ->
        AlarmEditor(
            alarm = alarm,
            h24 = h24,
            onSave = { saved ->
                ClockStore.saveAlarm(context, saved.copy(enabled = true))
                toastNext(context, saved)
                editing = null
            },
            onDelete = if (alarm.id != 0) ({ ClockStore.deleteAlarm(context, alarm); editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
}

private fun toastNext(context: android.content.Context, alarm: Alarm) {
    val minutes = (ClockStore.nextTrigger(alarm) - System.currentTimeMillis()) / 60_000 + 1
    val text = when {
        minutes < 60 -> "Alarm in $minutes min"
        else -> "Alarm in ${minutes / 60} h ${minutes % 60} min"
    }
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    alarm: Alarm,
    h24: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val time = rememberTimePickerState(alarm.hour, alarm.minute, h24)
    var days by remember { mutableStateOf(alarm.days) }
    var label by remember { mutableStateOf(alarm.label) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Mono.Sheet).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (alarm.id == 0) "NEW ALARM" else "EDIT ALARM", style = Mono.label.copy(color = Mono.White))
            Spacer(Modifier.height(16.dp))
            TimePicker(
                state = time,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Mono.Circle,
                    selectorColor = Mono.Red,
                    containerColor = Mono.Sheet,
                    clockDialSelectedContentColor = Mono.White,
                    clockDialUnselectedContentColor = Mono.White,
                    periodSelectorSelectedContainerColor = Mono.Red,
                    periodSelectorUnselectedContainerColor = Mono.Circle,
                    periodSelectorSelectedContentColor = Mono.White,
                    periodSelectorUnselectedContentColor = Mono.Grey,
                    periodSelectorBorderColor = Color.Transparent,
                    timeSelectorSelectedContainerColor = Mono.Red,
                    timeSelectorUnselectedContainerColor = Mono.Circle,
                    timeSelectorSelectedContentColor = Mono.White,
                    timeSelectorUnselectedContentColor = Mono.White,
                ),
            )
            // Repeat days
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_LETTERS.forEachIndexed { i, letter ->
                    val day = i + 1
                    val on = day in days
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (on) Mono.White else Mono.Circle)
                            .clickable { days = if (on) days - day else days + day },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(letter, color = if (on) Mono.Ink else Mono.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = label,
                onValueChange = { label = it.take(30) },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Mono.Red),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Mono.Circle)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box {
                        if (label.isEmpty()) Text("Label (optional)", color = Mono.Grey, fontSize = 15.sp)
                        field()
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) PillButton("DELETE", Mono.Circle, Mono.Red, Modifier.weight(1f), onDelete)
                PillButton("SAVE", Mono.Red, Mono.White, Modifier.weight(1f)) {
                    onSave(alarm.copy(hour = time.hour, minute = time.minute, days = days, label = label.trim()))
                }
            }
        }
    }
}

// ---------------- Timer ----------------

@Composable
private fun TimerTab() {
    val context = LocalContext.current
    val timer by ClockStore.timer.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableIntStateOf(5) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        TimerDial(
            if (timer.isSet) timer else TimerState(durationMs = 0, remainingMs = minutes * 60_000L),
            Modifier.size(260.dp),
            dot = 7.dp,
            ringWidth = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        if (!timer.isSet) {
            // Choose a length
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(size = 52.dp, color = Mono.Tile, onClick = { minutes = (minutes - 1).coerceAtLeast(1) }) {
                    Text("−", color = Mono.White, fontSize = 22.sp)
                }
                Text("$minutes MIN", style = Mono.label.copy(color = Mono.White, fontSize = 14.sp), modifier = Modifier.width(110.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                RoundButton(size = 52.dp, color = Mono.Tile, onClick = { minutes = (minutes + 1).coerceAtMost(600) }) {
                    Text("+", color = Mono.White, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 15, 30).forEach { m ->
                    Text(
                        "$m",
                        style = Mono.label.copy(color = if (m == minutes) Mono.Ink else Mono.White),
                        modifier = Modifier.clip(CircleShape).background(if (m == minutes) Mono.White else Mono.Circle)
                            .clickable { minutes = m }.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            RoundButton(size = 80.dp, color = Mono.Red, onClick = { ClockStore.startTimer(context, minutes * 60_000L) }) {
                Text("START", style = Mono.label.copy(color = Mono.White, fontSize = 12.sp))
            }
        } else {
            Text(
                if (timer.running) "RUNNING" else "PAUSED",
                style = Mono.label.copy(color = if (timer.running) Mono.Red else Mono.Grey),
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(size = 64.dp, color = Mono.Tile, onClick = { ClockStore.resetTimer(context) }) {
                    Text("RESET", style = Mono.label.copy(color = Mono.White, fontSize = 10.sp))
                }
                RoundButton(
                    size = 80.dp, color = Mono.Red,
                    onClick = { if (timer.running) ClockStore.pauseTimer(context) else ClockStore.resumeTimer(context) },
                ) {
                    Text(if (timer.running) "PAUSE" else "GO", style = Mono.label.copy(color = Mono.White, fontSize = 12.sp))
                }
                RoundButton(
                    size = 64.dp, color = Mono.Tile,
                    onClick = {
                        // +1 minute on the current timer
                        val t = ClockStore.timer.value
                        ClockStore.startTimer(context, t.remaining() + 60_000L)
                    },
                ) {
                    Text("+1 MIN", style = Mono.label.copy(color = Mono.White, fontSize = 10.sp))
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

// ---------------- Stopwatch ----------------

private fun formatStopwatch(ms: Long): Pair<String, String> {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60_000) % 60
    val h = ms / 3_600_000
    val main = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return main to ".%02d".format(cs)
}

@Composable
private fun StopwatchTab() {
    val context = LocalContext.current
    val sw by ClockStore.stopwatch.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sw.running) {
        now = SystemClock.elapsedRealtime()
        while (sw.running) {
            delay(33)
            now = SystemClock.elapsedRealtime()
        }
    }
    val (main, hundredths) = formatStopwatch(sw.elapsed(now))

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            DotMatrixText(main, dotSize = 8.dp, gap = 3.dp)
            Spacer(Modifier.width(8.dp))
            Text(hundredths, color = Mono.Grey, fontSize = 22.sp)
        }
        Spacer(Modifier.height(28.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            val laps = sw.laps
            items(laps.size) { i ->
                val index = laps.size - 1 - i
                val total = laps[index]
                val split = total - (laps.getOrNull(index - 1) ?: 0L)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text("LAP ${index + 1}", style = Mono.label, modifier = Modifier.weight(1f))
                    Text("+" + formatStopwatch(split).let { it.first + it.second }, style = Mono.label, modifier = Modifier.weight(1f))
                    Text(formatStopwatch(total).let { it.first + it.second }, style = Mono.label.copy(color = Mono.White))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                size = 64.dp, color = Mono.Tile,
                onClick = { if (sw.running) ClockStore.lap(context) else ClockStore.resetStopwatch(context) },
            ) {
                Text(if (sw.running) "LAP" else "RESET", style = Mono.label.copy(color = Mono.White, fontSize = 10.sp))
            }
            RoundButton(
                size = 80.dp, color = Mono.Red,
                onClick = { if (sw.running) ClockStore.pauseStopwatch(context) else ClockStore.startStopwatch(context) },
            ) {
                Text(if (sw.running) "PAUSE" else "START", style = Mono.label.copy(color = Mono.White, fontSize = 12.sp))
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

// ---------------- World clock ----------------

private fun cityOf(zone: String) = zone.substringAfterLast('/').replace('_', ' ')

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldTab() {
    val context = LocalContext.current
    val zones by ClockStore.zones.collectAsStateWithLifecycle()
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val h24 = settings.clock24h ?: android.text.format.DateFormat.is24HourFormat(context)
    val now = rememberNow(ticking = true, stepMs = 10_000)
    var adding by remember { mutableStateOf(false) }
    val here = ZoneId.systemDefault()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(zones, key = { it }) { zone ->
                val instant = java.time.Instant.ofEpochMilli(now)
                val zoned = runCatching { ZonedDateTime.ofInstant(instant, ZoneId.of(zone)) }.getOrNull() ?: return@items
                val diffMin = (zoned.offset.totalSeconds - ZonedDateTime.ofInstant(instant, here).offset.totalSeconds) / 60
                val diff = when {
                    diffMin == 0 -> "SAME TIME"
                    diffMin % 60 == 0 -> "%+dH".format(diffMin / 60)
                    else -> "%+d:%02d".format(diffMin / 60, kotlin.math.abs(diffMin % 60))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mono.Tile)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                ClockStore.removeZone(context, zone)
                                Toast.makeText(context, "${cityOf(zone)} removed", Toast.LENGTH_SHORT).show()
                            },
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cityOf(zone).uppercase(), style = Mono.label.copy(color = Mono.White))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$diff  ·  " + zoned.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(),
                            style = Mono.label.copy(fontSize = 10.sp),
                        )
                    }
                    Text(
                        zoned.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.getDefault())),
                        color = Mono.White, fontSize = 30.sp, fontWeight = FontWeight.Light,
                    )
                }
            }
            item { Text("Long-press a city to remove it.", color = Mono.Grey, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
        }
        RoundButton(
            size = 64.dp, color = Mono.Red, onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add city", tint = Mono.White)
        }
    }
    if (adding) {
        CityPicker(onPick = { ClockStore.addZone(context, it); adding = false }, onDismiss = { adding = false })
    }
}

@Composable
private fun CityPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        TimeZone.getAvailableIDs()
            .filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }
            .sortedBy { cityOf(it) }
    }
    val shown = remember(query) { all.filter { cityOf(it).contains(query.trim(), ignoreCase = true) }.take(60) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Mono.Sheet).padding(20.dp)) {
            Text("ADD CITY", style = Mono.label.copy(color = Mono.White))
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Mono.Red),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Mono.Circle)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) Text("Search a city", color = Mono.Grey, fontSize = 15.sp)
                        field()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(shown, key = { it }) { zone ->
                    Text(
                        cityOf(zone),
                        color = Mono.White, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(zone) }.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// ---------------- Shared bits ----------------

@Composable
private fun PillButton(label: String, background: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(background).clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Mono.label.copy(color = content, fontSize = 12.sp))
    }
}

@Composable
private fun MonoSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Mono.White,
            checkedTrackColor = Mono.Red,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Mono.Grey,
            uncheckedTrackColor = Mono.Circle,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}
