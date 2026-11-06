package com.applauncher.ui.mono

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.LauncherViewModel
import com.applauncher.clock.ClockActivity
import com.applauncher.clock.ClockStore
import com.applauncher.clock.TimerDial
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.applauncher.system.CalendarEvent
import com.applauncher.system.NextEvent
import com.applauncher.system.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Title + hint shown on a tile that still needs setting up. */
@Composable
private fun SetupText(title: String, hint: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
        Text(title, style = Mono.label.copy(color = Mono.White))
        Spacer(Modifier.height(8.dp))
        Text(hint, color = Mono.Grey, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Temperature in dots, conditions and today's high / low. */
@Composable
fun WeatherTile(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val weather by vm.weather.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshWeather(force = true)
    }
    val allowed = Weather.hasPermission(context)
    Tile(modifier, Mono.Tile, onClick = {
        if (!allowed) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else vm.refreshWeather(force = true)
    }) {
        val w = weather
        when {
            !allowed -> SetupText("WEATHER", "Tap to allow\nrough location")
            w == null -> SetupText("WEATHER", "Loading…")
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotMatrixText("${w.temp}°", dotSize = 4.dp, gap = 2.dp, accentChars = setOf('°'))
                Spacer(Modifier.height(10.dp))
                Text(Weather.describe(w.code), style = Mono.label.copy(fontSize = 10.sp, color = Mono.White))
                Spacer(Modifier.height(4.dp))
                Text("H ${w.high}°  L ${w.low}°", style = Mono.label.copy(fontSize = 10.sp))
            }
        }
    }
}

/** The next calendar event this week. */
@Composable
fun EventTile(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val event by vm.nextEvent.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshEvent()
    }
    val allowed = NextEvent.hasPermission(context)
    Tile(modifier, Mono.Tile, onClick = {
        when {
            !allowed -> ask.launch(Manifest.permission.READ_CALENDAR)
            event != null -> vm.startIntent(event!!.openIntent())
            else -> vm.startIntent(Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
        }
    }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val e = event
                Text(if (e == null) "NEXT EVENT" else whenText(e), style = Mono.label.copy(color = if (e == null) Mono.White else Mono.Red))
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !allowed -> "Tap to allow calendar"
                        e == null -> "Nothing in the next 7 days"
                        else -> e.title
                    },
                    color = if (e == null) Mono.Grey else Mono.White,
                    fontSize = if (e == null) 13.sp else 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (event != null) Mono.Red else Mono.Dim))
        }
    }
}

private fun whenText(e: CalendarEvent): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(e.begin).atZone(if (e.allDay) ZoneId.of("UTC") else zone)
    val today = LocalDate.now(zone)
    val day = when (start.toLocalDate()) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> start.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
    }
    val time = if (e.allDay) "ALL DAY" else start.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).uppercase()
    return "$day  ·  $time"
}

/** A photo you pick; tap to change it. */
@Composable
fun PhotoTile(vm: LauncherViewModel, grayscale: Boolean, modifier: Modifier = Modifier) {
    val version by vm.photoVersion.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::setPhoto)
    }
    val bitmap by produceState<ImageBitmap?>(null, version) {
        value = if (version == 0L) null else withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(vm.photoFile.path)?.asImageBitmap()
        }
    }
    Tile(modifier, Mono.Tile, onClick = {
        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }) {
        val b = bitmap
        if (b == null) {
            SetupText("PHOTO", "Tap to choose one")
        } else {
            Image(
                b, contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Round timer tile: dot-matrix countdown with a red ring.
 * Tap: pick a length (when idle) / pause / resume. Long-press: reset, or open the Clock app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) { ClockStore.load(context) }
    val timer by ClockStore.timer.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(size)
                .squish(interaction, pressedScale = 0.95f)
                .clip(CircleShape)
                .background(Mono.Tile)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = {
                        view.toggleTick()
                        when {
                            !timer.isSet -> menuOpen = true
                            timer.running -> ClockStore.pauseTimer(context)
                            else -> ClockStore.resumeTimer(context)
                        }
                    },
                    onLongClick = {
                        view.toggleTick()
                        if (timer.isSet) ClockStore.resetTimer(context)
                        else context.startActivity(
                            Intent(context, ClockActivity::class.java)
                                .putExtra(ClockActivity.EXTRA_TAB, "timer")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            TimerDial(timer, Modifier.fillMaxSize().padding(6.dp), dot = 3.dp, ringWidth = 4.dp)
            Text(
                when {
                    !timer.isSet -> "TIMER"
                    timer.running -> "TAP TO PAUSE"
                    else -> "PAUSED"
                },
                style = Mono.label.copy(fontSize = 8.sp, letterSpacing = 1.sp, color = if (timer.running) Mono.Grey else Mono.Red),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.2f),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(1, 3, 5, 10, 15, 25).forEach { m ->
                DropdownMenuItem(
                    text = { Text("$m min") },
                    onClick = { menuOpen = false; ClockStore.startTimer(context, m * 60_000L) },
                )
            }
            DropdownMenuItem(
                text = { Text("Open Clock") },
                onClick = {
                    menuOpen = false
                    context.startActivity(
                        Intent(context, ClockActivity::class.java)
                            .putExtra(ClockActivity.EXTRA_TAB, "timer")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }
    }
}

/** Torch and Wi-Fi pills side by side. */
@Composable
fun TogglesRow(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val view = androidx.compose.ui.platform.LocalView.current
    val torchOn by vm.torch.on.collectAsStateWithLifecycle()
    val wifiOn by vm.wifiOn.collectAsStateWithLifecycle()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.torch.available) {
            ToggleTile(
                "TORCH", torchOn,
                onClick = { view.toggleTick(); vm.torch.toggle() },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        ToggleTile("WI-FI", wifiOn, onClick = vm::openWifiPanel, modifier = Modifier.weight(1f).fillMaxSize())
    }
}
