package com.applauncher.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.data.Prefs
import com.applauncher.data.SettingsStore
import com.applauncher.system.LockWallpaper
import com.applauncher.system.MusicWatcher
import com.applauncher.system.NotificationItem
import com.applauncher.system.NotificationStore
import com.applauncher.system.Weather
import com.applauncher.system.WeatherNow
import com.applauncher.ui.IconDispatcher
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import com.applauncher.ui.mono.snapTick
import com.applauncher.ui.rememberStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nothing-style lock screen, shown on top of the phone's own lock screen.
 * The phone stays locked underneath: swiping up asks for your PIN / fingerprint as usual.
 * Notifications show only as app icons, never their content.
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var music: MusicWatcher

    /** Goes up every time the screen wakes, to replay the "lights on" animation. */
    private val wakes = MutableStateFlow(0)

    /** 0 = dark, 1 = fully shown. Reset to dark when the screen turns off, so waking never flashes the old frame. */
    private val appear = Animatable(0f)

    /** The phone was unlocked some other way (e.g. fingerprint): fade out of the way. */
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finishSmoothly()
    }

    private val displays by lazy { getSystemService(DisplayManager::class.java) }

    private fun displayOn(): Boolean = displays?.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON

    /**
     * Follows the display itself (more precise than "screen on" broadcasts, which arrive ~0.7 s late on vivo):
     * goes dark the moment it turns off, and plays the "lights on" animation the moment it turns on.
     */
    private val displayWatcher = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            if (displayOn()) replayWakeAnimation() else goDark()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    /** Backup: vivo doesn't always report the display turning off, but it always sends "screen off". */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = goDark()
    }

    /** True after the display went off; the next "on" plays the animation once and clears it. */
    private var isDark = true

    private fun goDark() {
        isDark = true
        lifecycleScope.launch { appear.snapTo(0f) }
    }

    /**
     * Plays the "lights on" animation once per wake. Several signals report a wake, and "display changed"
     * also fires for brightness changes, so it only plays if the screen really went dark first.
     */
    private fun replayWakeAnimation() {
        if (!displayOn() || !isDark) return
        isDark = false
        wakes.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        music = MusicWatcher(this)
        ContextCompat.registerReceiver(
            this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        displays?.registerDisplayListener(displayWatcher, android.os.Handler(mainLooper))
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            val wake by wakes.collectAsState()
            MonoTheme { LockScreen(music, wake, appear, onUnlock = ::unlock) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Already unlocked (e.g. the lock timeout hadn't passed yet): nothing to cover.
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true) {
            finishSmoothly()
            return
        }
        replayWakeAnimation()
        music.start()
    }

    override fun onPause() {
        music.stop()
        if (!displayOn()) goDark()
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        displays?.unregisterDisplayListener(displayWatcher)
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    /** Shows the phone's PIN / fingerprint screen. [onCancelled] runs if you back out of it. */
    private fun unlock(onCancelled: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            finishSmoothly()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishSmoothly()
            override fun onDismissCancelled() = onCancelled()
            override fun onDismissError() = onCancelled()
        })
    }

    /** Close with a short fade instead of a hard cut. */
    private fun finishSmoothly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

/** 0..1 progress of one item inside the wake animation, starting at [start] and lasting [length]. */
private fun stagger(t: Float, start: Float, length: Float = 0.45f): Float =
    FastOutSlowInEasing.transform(((t - start) / length).coerceIn(0f, 1f))

@Composable
private fun LockScreen(
    music: MusicWatcher,
    wake: Int,
    appear: Animatable<Float, AnimationVector1D>,
    onUnlock: (onCancelled: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val status = rememberStatus(settings.clock24h)
    val notifications by NotificationStore.items.collectAsStateWithLifecycle()
    val playing by music.nowPlaying.collectAsStateWithLifecycle()
    val weather = remember(wake) { WeatherNow.fromJson(Prefs(context).weatherJson) }

    val style = LockWallpaper.Style.entries.firstOrNull { it.name == settings.lockStyle } ?: LockWallpaper.Style.DOTS
    val background by produceState<ImageBitmap?>(null, style, settings.lockText) {
        value = withContext(Dispatchers.Default) {
            LockWallpaper.background(context, style, settings.lockText).asImageBitmap()
        }
    }

    // Wake animation: 0 -> 1 every time the screen comes on. (Before the first wake it stays dark.)
    LaunchedEffect(wake) {
        if (wake == 0) return@LaunchedEffect
        appear.snapTo(0f)
        appear.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Mono.Black)) {
        val screenHeight = with(density) { maxHeight.toPx() }
        val threshold = with(density) { 150.dp.toPx() }

        // How far the content has been pushed up (0 or negative). Changed directly by your finger.
        var offset by remember { mutableFloatStateOf(0f) }
        val pastLine by remember { derivedStateOf { -offset > threshold } }
        LaunchedEffect(pastLine) { if (pastLine) view.snapTick() }

        val drag = rememberDraggableState { delta ->
            // A little resistance once you're past the line, so it feels anchored.
            val step = if (-offset > threshold && delta < 0) delta * 0.5f else delta
            offset = (offset + step).coerceIn(-screenHeight, 0f)
        }

        // If you back out of the PIN / fingerprint screen, slide back down.
        val slideBack: () -> Unit = {
            scope.launch {
                animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) { v, _ -> offset = v }
            }
        }

        suspend fun settle(velocity: Float) {
            if (-offset > threshold || velocity < -1800f) {
                // Ask for PIN / fingerprint right away; the float-away plays while it opens, not before.
                onUnlock(slideBack)
                animate(offset, -screenHeight * 0.3f, velocity, tween(160, easing = LinearOutSlowInEasing)) { v, _ -> offset = v }
            } else {
                animate(offset, 0f, velocity, spring(dampingRatio = 0.7f, stiffness = 420f)) { v, _ -> offset = v }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .draggable(drag, Orientation.Vertical, onDragStopped = { v -> settle(v) }),
        ) {
            // Dot wallpaper: fades in on wake and moves slower than the content (depth).
            background?.let {
                Image(
                    it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationY = offset * 0.25f
                        alpha = 0.3f + 0.7f * stagger(appear.value, 0f, 0.5f)
                    },
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        val p = (-offset / (threshold * 1.6f)).coerceIn(0f, 1f)
                        translationY = offset
                        alpha = 1f - 0.85f * p
                        scaleX = 1f - 0.06f * p
                        scaleY = 1f - 0.06f * p
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(72.dp))
                DotMatrixText(status.time, dotSize = 9.dp, gap = 3.dp, reveal = { stagger(appear.value, 0f, 0.65f) })
                Spacer(Modifier.height(20.dp))
                Text(
                    buildString {
                        append(status.date)
                        append("  ·  ")
                        append(status.battery)
                        append('%')
                        if (status.charging) append(" +")
                    },
                    style = Mono.label.copy(color = Mono.White),
                    modifier = Modifier.slideIn { stagger(appear.value, 0.3f) },
                )
                if (weather != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${weather.temp}°  ${Weather.describe(weather.code)}",
                        style = Mono.label,
                        modifier = Modifier.slideIn { stagger(appear.value, 0.38f) },
                    )
                }

                Spacer(Modifier.weight(1f))

                playing?.let { np ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .slideIn { stagger(appear.value, 0.45f) }
                            .fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Mono.Tile)
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(np.title, color = Mono.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(np.artist.ifBlank { np.appLabel }, color = Mono.Grey, fontSize = 12.sp, maxLines = 1)
                        }
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Mono.Red).clickable { music.playPause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (np.playing) "II" else "▶", color = Mono.White, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                if (notifications.isNotEmpty()) {
                    NotificationIcons(notifications) { appear.value }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (notifications.size == 1) "1 NOTIFICATION" else "${notifications.size} NOTIFICATIONS",
                        style = Mono.label.copy(fontSize = 10.sp),
                        modifier = Modifier.slideIn { stagger(appear.value, 0.55f) },
                    )
                    Spacer(Modifier.height(28.dp))
                }

                UnlockHint(
                    pastLine = pastLine,
                    modifier = Modifier.slideIn { stagger(appear.value, 0.6f) },
                    onTap = {
                        onUnlock(slideBack)
                        scope.launch {
                            animate(offset, -threshold * 1.2f, animationSpec = tween(160, easing = LinearOutSlowInEasing)) { v, _ -> offset = v }
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Fades in and rises a little as [progress] goes 0 -> 1. Read while drawing, so it's cheap to animate. */
private fun Modifier.slideIn(progress: () -> Float): Modifier = graphicsLayer {
    val a = progress()
    alpha = a
    translationY = (1f - a) * 24.dp.toPx()
}

/** Breathing red dot, a floating arrow and the hint text. */
@Composable
private fun UnlockHint(pastLine: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val loop = rememberInfiniteTransition(label = "hint")
    val breath by loop.animateFloat(1f, 1.6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "breath")
    val float by loop.animateFloat(0f, -6f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onTap).padding(16.dp),
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Mono.Grey,
            modifier = Modifier.size(22.dp).graphicsLayer { translationY = float * density },
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(8.dp).graphicsLayer {
                scaleX = breath
                scaleY = breath
                alpha = 1.4f - breath * 0.5f
            }.clip(CircleShape).background(Mono.Red)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (pastLine) "RELEASE TO UNLOCK" else "SWIPE UP TO UNLOCK",
            style = Mono.label.copy(fontSize = 10.sp, color = if (pastLine) Mono.White else Mono.Grey),
        )
    }
}

/** One small white icon per app with notifications (no content shown on the lock screen). They pop in one by one. */
@Composable
private fun NotificationIcons(items: List<NotificationItem>, appear: () -> Float) {
    val apps = remember(items) { items.distinctBy { it.packageName }.take(7) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEachIndexed { i, item ->
            Box(
                Modifier
                    .graphicsLayer {
                        val a = stagger(appear(), 0.45f + i * 0.04f, 0.35f)
                        alpha = a
                        scaleX = 0.6f + 0.4f * a
                        scaleY = 0.6f + 0.4f * a
                    }
                    .size(34.dp).clip(CircleShape).background(Mono.Circle),
                contentAlignment = Alignment.Center,
            ) {
                SmallIcon(item)
            }
        }
    }
}

@Composable
private fun SmallIcon(item: NotificationItem) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 16.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, item.packageName) {
        value = withContext(IconDispatcher) {
            runCatching {
                val drawable = item.smallIcon?.loadDrawable(context) ?: return@runCatching null
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, px, px)
                drawable.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                drawable.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, contentDescription = item.appLabel, modifier = Modifier.size(16.dp)) }
        ?: Spacer(Modifier.width(16.dp))
}
