package com.applauncher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.data.AppEntry
import com.applauncher.data.AppRepository
import com.applauncher.data.LauncherSettings
import com.applauncher.data.SettingsStore
import com.applauncher.data.TileId
import com.applauncher.system.LockService
import com.applauncher.system.LockWallpaper
import com.applauncher.ui.mono.DotFont
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch
import com.applauncher.system.MediaListenerService
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launcher settings: tiles, dock, clock, icons, hidden apps and default apps. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { MonoTheme { SettingsScreen() } }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    fun update(change: (LauncherSettings) -> LauncherSettings) = SettingsStore.update(context, change)
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val hiddenApps by produceState(emptyList<AppEntry>(), settings.hiddenApps) {
        value = withContext(Dispatchers.IO) {
            AppRepository(context).loadApps().filter { it.key in settings.hiddenApps }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Mono.Black).windowInsetsPadding(WindowInsets.systemBars),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("SETTINGS", style = Mono.label.copy(color = Mono.White, fontSize = 16.sp)) }

        section("HOME TILES", "Turn tiles on or off and change their order. Extra tiles go on more pages.")
        tileRows(settings, ::update)

        section("DOCK")
        item {
            Choice("Icons per row", listOf("4", "5"), selected = if (settings.dockColumns == 5) 1 else 0) { i ->
                update { it.copy(dockColumns = if (i == 1) 5 else 4) }
            }
        }

        section("CLOCK")
        item {
            Choice(
                "Time format", listOf("PHONE", "12 H", "24 H"),
                selected = when (settings.clock24h) { null -> 0; false -> 1; true -> 2 },
            ) { i -> update { it.copy(clock24h = when (i) { 1 -> false; 2 -> true; else -> null }) } }
        }

        section("LOCK SCREEN")
        item {
            Toggle(
                "Dot lock screen",
                if (LockService.isOn) {
                    "Covers vivo's lock screen with the clock, music and notification icons. Swipe up, then unlock as usual."
                } else {
                    "Needs Dotline switched on under Accessibility (the same switch as double-tap to lock)."
                },
                settings.lockScreen,
            ) { on ->
                update { it.copy(lockScreen = on) }
                if (on && !LockService.isOn) {
                    Toast.makeText(context, "Switch on “Dotline” here", Toast.LENGTH_LONG).show()
                    open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
        item {
            Text(
                "Dot design: used by the Dot lock screen, or set it as a plain lock wallpaper.",
                color = Mono.Grey, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        item { LockWallpaperCard(settings, ::update) }

        section("ICONS AND APPS")
        item {
            Toggle("White symbols", "Off = the apps' normal colour icons", settings.monoIcons) { on ->
                update { it.copy(monoIcons = on) }
            }
        }
        item { Toggle("App names in the drawer", null, settings.drawerLabels) { on -> update { it.copy(drawerLabels = on) } } }
        item {
            Toggle(
                "Charging animation",
                "Dot battery when you plug in, on home and the lock screen (lock screen needs the Accessibility switch)",
                settings.chargingAnimation,
            ) { on -> update { it.copy(chargingAnimation = on) } }
        }
        item {
            Toggle("Notification dots", "Red dot on apps that have notifications", settings.notificationDots) { on ->
                update { it.copy(notificationDots = on) }
            }
        }
        item { Toggle("Black and white photo tile", null, settings.photoGrayscale) { on -> update { it.copy(photoGrayscale = on) } } }
        if (settings.iconOverrides.isNotEmpty()) {
            item {
                Action("Reset changed icons", "${settings.iconOverrides.size} app(s) use a custom icon") {
                    update { it.copy(iconOverrides = emptyMap()) }
                }
            }
        }

        section("HIDDEN APPS", "Hide an app: long-press it in the app drawer, then Hide app.")
        if (hiddenApps.isEmpty()) {
            item { Text("No hidden apps", color = Mono.Grey, fontSize = 14.sp, modifier = Modifier.padding(4.dp)) }
        } else {
            hiddenApps.forEach { app ->
                item(key = "hidden_${app.key}") {
                    Action(app.label, "Tap to show it again") {
                        update { it.copy(hiddenApps = it.hiddenApps - app.key) }
                    }
                }
            }
        }

        section("DEFAULT APPS AND ACCESS")
        item { Action("Home app", "Choose which app is your home screen") { open(context, Intent(Settings.ACTION_HOME_SETTINGS)) } }
        item { Action("Phone app", "Make Dotline's Phone your default") { openDialerRole(context, roleRequest::launch) } }
        item { Action("Notification access", "Needed for the notification panel and music tile") { openNotificationAccess(context) } }
        item {
            Action(
                "Double-tap to lock",
                if (LockService.isOn) "On" else "Switch on Dotline under Accessibility",
            ) { open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Text("MY LAUNCHER  ·  ${BuildConfigVersion.name(context)}", style = Mono.label.copy(fontSize = 10.sp))
        }
    }
}

/** Pick a design, preview it, and set it as the lock screen wallpaper. */
@Composable
private fun LockWallpaperCard(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val style = LockWallpaper.Style.entries.firstOrNull { it.name == settings.lockStyle } ?: LockWallpaper.Style.DOTS
    var applying by remember { mutableStateOf(false) }

    val preview by produceState<ImageBitmap?>(null, style, settings.lockText) {
        value = withContext(Dispatchers.Default) {
            val (w, h) = LockWallpaper.screenSize(context)
            val pw = 360
            LockWallpaper.render(pw, pw * h / w, style, settings.lockText).asImageBitmap()
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mono.Tile).padding(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LockWallpaper.Style.entries.forEach { s ->
                Text(
                    s.label,
                    style = Mono.label.copy(fontSize = 11.sp, color = if (s == style) Mono.Ink else Mono.White),
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (s == style) Mono.White else Mono.Circle)
                        .clickable { update { it.copy(lockStyle = s.name) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        if (style == LockWallpaper.Style.TEXT) {
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = settings.lockText,
                onValueChange = { typed ->
                    val clean = typed.uppercase().filter { DotFont.has(it) }.take(12)
                    update { it.copy(lockText = clean) }
                },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 16.sp, letterSpacing = 2.sp),
                cursorBrush = SolidColor(Mono.Red),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Mono.Circle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box {
                        if (settings.lockText.isEmpty()) Text("Your word (letters and numbers)", color = Mono.Grey, fontSize = 15.sp)
                        field()
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            preview?.let {
                Image(
                    it, contentDescription = "Lock screen preview",
                    modifier = Modifier.height(320.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (applying) "SETTING…" else "SET AS LOCK SCREEN",
            style = Mono.label.copy(color = Mono.White, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Mono.Red)
                .clickable(enabled = !applying) {
                    applying = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { LockWallpaper.apply(context, style, settings.lockText) }
                        applying = false
                        Toast.makeText(
                            context,
                            when (result) {
                                LockWallpaper.Result.SET -> "Lock screen wallpaper set"
                                LockWallpaper.Result.BLOCKED ->
                                    "Your phone's theme kept its own lock wallpaper. Turn on Dot lock screen below instead."
                                LockWallpaper.Result.FAILED -> "Couldn't set the wallpaper"
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                .padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This replaces your current lock screen wallpaper.",
            color = Mono.Grey, fontSize = 12.sp,
        )
    }
}

/** Every tile: enabled ones first in their order, then the switched-off ones. */
private fun LazyListScope.tileRows(settings: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val enabled = settings.tiles
    val disabled = TileId.entries.filter { it !in enabled }
    (enabled + disabled).forEach { tile ->
        item(key = "tile_${tile.name}") {
            val on = tile in enabled
            val index = enabled.indexOf(tile)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mono.Tile).padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tile.title, color = if (on) Mono.White else Mono.Grey, fontSize = 16.sp)
                    Text(if (tile.wide) "FULL WIDTH" else "HALF WIDTH", style = Mono.label.copy(fontSize = 9.sp))
                }
                if (on) {
                    ArrowButton(up = true, enabled = index > 0) {
                        update { s -> s.copy(tiles = s.tiles.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                    }
                    ArrowButton(up = false, enabled = index < enabled.lastIndex) {
                        update { s -> s.copy(tiles = s.tiles.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                    }
                }
                MonoSwitch(on) { checked ->
                    update { s -> s.copy(tiles = if (checked) s.tiles + tile else s.tiles - tile) }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, hint: String? = null) {
    item(key = "section_$title") {
        Column(Modifier.padding(top = 18.dp, bottom = 2.dp, start = 4.dp)) {
            Text(title, style = Mono.label.copy(color = Mono.Red, fontSize = 11.sp))
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, color = Mono.Grey, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mono.Tile)
            .clickable { onChange(!on) }.padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mono.White, fontSize = 16.sp)
            if (hint != null) Text(hint, color = Mono.Grey, fontSize = 12.sp)
        }
        MonoSwitch(on, onChange)
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mono.Tile)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(title, color = Mono.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                Text(
                    label,
                    style = Mono.label.copy(fontSize = 11.sp, color = if (i == selected) Mono.Ink else Mono.White),
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (i == selected) Mono.White else Mono.Circle)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Action(title: String, hint: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Mono.Tile)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mono.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) Text(hint, color = Mono.Grey, fontSize = 12.sp)
        }
        Text("→", color = Mono.Grey, fontSize = 16.sp)
    }
}

@Composable
private fun ArrowButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (up) "Move up" else "Move down",
            tint = if (enabled) Mono.White else Mono.Dim,
        )
    }
    Spacer(Modifier.width(2.dp))
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

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

/** The role dialog needs to know who asked, so it's opened "for a result". */
private fun openDialerRole(context: Context, launchForResult: (Intent) -> Unit) {
    val intent = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else null
    if (intent != null) runCatching { launchForResult(intent) }
    else open(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

private fun openNotificationAccess(context: Context) {
    val component = ComponentName(context, MediaListenerService::class.java).flattenToString()
    val intent = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
    } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    open(context, intent)
}

private object BuildConfigVersion {
    fun name(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""
}
