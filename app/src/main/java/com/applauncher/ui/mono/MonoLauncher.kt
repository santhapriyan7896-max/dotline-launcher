package com.applauncher.ui.mono

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.applauncher.LauncherViewModel
import com.applauncher.ui.rememberStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** The whole launcher: home screen, with the app drawer sliding over it. */
@Composable
fun MonoLauncher(vm: LauncherViewModel, homePresses: Int) {
    val sheet = rememberDrawerSheet()
    val panel = rememberDrawerSheet() // notification panel, comes down from the top
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status = rememberStatus(settings.clock24h)
    val scope = rememberCoroutineScope()
    // Goes up each time the drawer fully closes, so the drawer can clear its search.
    var drawerResets by remember { mutableIntStateOf(0) }

    // 0 = home hidden, 1 = fully shown. Animates when you come back from an app.
    val settle = remember { Animatable(1f) }

    // Build the drawer just after the first frame, so the home screen appears as fast as possible.
    var drawerReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        drawerReady = true
    }

    // Home button: close the drawer and the notification panel.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            sheet.close()
            panel.close()
        }
    }
    // Leaving the launcher: reset to a clean home screen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        sheet.closeNow()
        panel.closeNow()
        drawerResets++
        scope.launch { settle.snapTo(0f) }
    }
    // Coming back: the home screen settles into place quickly, in step with Android's own close animation.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch {
            settle.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
        }
    }
    // Clear the drawer's search once it has finished closing.
    LaunchedEffect(sheet) {
        var wasOpen = false
        androidx.compose.runtime.snapshotFlow { sheet.progress.value == 0f }.collect { closed ->
            if (closed && wasOpen) drawerResets++
            wasOpen = !closed
        }
    }

    // Back closes the drawer or panel; on the home screen it does nothing (a launcher can't "go back").
    BackHandler {
        sheet.close()
        panel.close()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Mono.Black)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        sheet.heightPx = heightPx
        panel.heightPx = heightPx

        // Home: grows in when you return, and shrinks back and darkens as the drawer or panel comes in.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = settle.value
                val p = maxOf(sheet.progress.value, panel.progress.value).coerceIn(0f, 1f)
                // Small zoom only; never fade from black (that made returning feel like a blank flash).
                val scale = (0.96f + 0.04f * s) * (1f - 0.07f * p)
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.55f * p
            }
        ) {
            MonoHome(
                vm = vm,
                onDrawerDrag = sheet::dragBy,
                onDrawerRelease = sheet::settle,
                onOpenDrawer = sheet::open,
                // Panel comes from the top: finger down = more open.
                onPanelDrag = { dy -> panel.dragBy(-dy) },
                onPanelRelease = { v -> panel.settle(-v) },
            )
        }

        if (drawerReady) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = sheet.progress.value.coerceIn(0f, 1f)
                    translationY = (1f - p) * heightPx
                    alpha = if (p == 0f) 0f else 1f
                }
            ) {
                MonoDrawer(vm = vm, sheet = sheet, resetKey = drawerResets)
            }

            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = panel.progress.value.coerceIn(0f, 1f)
                    translationY = -(1f - p) * heightPx
                    alpha = if (p == 0f) 0f else 1f
                }
            ) {
                NotificationPanel(vm = vm, sheet = panel, status = status)
            }
        }
    }
}
