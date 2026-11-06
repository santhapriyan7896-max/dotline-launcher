package com.applauncher.ui.mono

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import com.applauncher.LauncherViewModel
import com.applauncher.SettingsActivity
import com.applauncher.clock.ClockActivity
import com.applauncher.data.AppEntry
import com.applauncher.data.LauncherSettings
import com.applauncher.data.TileId
import com.applauncher.ui.Status
import com.applauncher.ui.rememberHomeRequester
import com.applauncher.ui.rememberStatus

/** Home screen: widget tiles at the top, favourite apps as round icons at the bottom. */
@Composable
fun MonoHome(
    vm: LauncherViewModel,
    onDrawerDrag: (Float) -> Unit,
    onDrawerRelease: (Float) -> Unit,
    onOpenDrawer: () -> Unit,
    onPanelDrag: (Float) -> Unit,
    onPanelRelease: (Float) -> Unit,
) {
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val isDefaultHome by vm.isDefaultHome.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status = rememberStatus(settings.clock24h)
    val requestHome = rememberHomeRequester(vm)
    var menuOpen by remember { mutableStateOf(false) }
    val noRipple = remember { MutableInteractionSource() }
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onHomeVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onHomeHidden() }

    Column(
        Modifier
            .fillMaxSize()
            // Swipe up: the drawer follows your finger. Swipe down: notification panel.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var direction = 0 // 0 = not decided yet, -1 = up (drawer), 1 = down (notifications)
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        direction = 0
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().y
                        when (direction) {
                            -1 -> onDrawerRelease(velocity)
                            1 -> onPanelRelease(velocity)
                        }
                    },
                    onDragCancel = {
                        when (direction) {
                            -1 -> onDrawerRelease(0f)
                            1 -> onPanelRelease(0f)
                        }
                    },
                ) { change, dy ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (direction == 0) direction = if (dy < 0) -1 else 1
                    if (direction == -1) onDrawerDrag(dy) else onPanelDrag(dy)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { vm.lockScreen() },
                    onLongPress = { menuOpen = true },
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        if (!isDefaultHome) {
            Text(
                text = "SET AS HOME SCREEN  →",
                style = Mono.label.copy(color = Mono.Red),
                modifier = Modifier
                    .padding(start = 8.dp, bottom = 12.dp)
                    .clickable(noRipple, indication = null) { requestHome() },
            )
        }

        // Tiles, in swipeable pages.
        TilePages(
            tiles = settings.tiles,
            render = { id, modifier -> HomeTile(id, vm, status, settings, modifier) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Box {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Launcher settings") },
                    onClick = { menuOpen = false; vm.startIntent(Intent(context, SettingsActivity::class.java)) },
                )
                DropdownMenuItem(text = { Text("All apps") }, onClick = { menuOpen = false; onOpenDrawer() })
                DropdownMenuItem(text = { Text("Choose home app") }, onClick = { menuOpen = false; requestHome() })
            }
        }
        Spacer(Modifier.height(12.dp))

        if (favorites.isEmpty() && loaded) {
            Text(
                text = "Swipe up for all apps.\nLong-press any app to pin it here.",
                color = Mono.Grey,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Dock(favorites, vm, settings.dockColumns)
        }

        // Red dot: tap for all apps.
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Mono.Red))
            }
        }
    }
}

/** Favourite apps in rows of round icons (4 or 5 per row). */
@Composable
private fun Dock(favorites: List<AppEntry>, vm: LauncherViewModel, columns: Int) {
    favorites.chunked(columns).forEachIndexed { index, row ->
        if (index > 0) Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            for (column in 0 until columns) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    row.getOrNull(column)?.let { app ->
                        AppCircle(app, vm, isFavorite = true, circleSize = if (columns == 5) 52.dp else 58.dp, showLabel = false)
                    }
                }
            }
        }
    }
}

/** One home tile by id. */
@Composable
private fun HomeTile(
    id: TileId,
    vm: LauncherViewModel,
    status: Status,
    settings: LauncherSettings,
    modifier: Modifier,
) {
    val context = LocalContext.current
    when (id) {
        TileId.CLOCK -> ClockTile(status, onClick = { vm.startIntent(Intent(context, ClockActivity::class.java)) }, modifier = modifier)
        TileId.TIMER -> TimerTile(modifier)
        TileId.DATE -> DateTile(status, onClick = { vm.startIntent(calendarIntent()) }, modifier = modifier)
        TileId.BATTERY -> Box(modifier, contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BatteryTile(status, Modifier.size(minOf(maxWidth, maxHeight)))
            }
        }
        TileId.TOGGLES -> TogglesRow(vm, modifier)
        TileId.MUSIC -> MusicTile(vm, modifier)
        TileId.WEATHER -> WeatherTile(vm, modifier)
        TileId.EVENT -> EventTile(vm, modifier)
        TileId.PHOTO -> PhotoTile(vm, settings.photoGrayscale, modifier)
    }
}

private fun calendarIntent(): Intent {
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(System.currentTimeMillis().toString())
        .build()
    return Intent(Intent.ACTION_VIEW, uri)
}
