package com.applauncher.ui.mono

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Position of the app drawer: 0 = closed, 1 = fully open.
 * Follows the finger while dragging, then springs open or closed.
 */
@Stable
class DrawerSheet(private val scope: CoroutineScope, private val view: View) {
    val progress = Animatable(0f)

    /** Screen height in pixels; dragging the full height moves the drawer from closed to open. */
    var heightPx = 1f

    /** Where the drawer is heading (true = open). */
    var isOpen = false
        private set

    /** Move with the finger. Positive [dy] = finger moving down. */
    fun dragBy(dy: Float) {
        scope.launch { progress.snapTo((progress.value - dy / heightPx).coerceIn(0f, 1f)) }
    }

    /** Finger lifted: decide from speed (px/s, negative = upward) and position. */
    fun settle(velocityY: Float) {
        val open = when {
            velocityY < -900f -> true
            velocityY > 900f -> false
            else -> progress.value > 0.4f
        }
        animate(open, initialVelocity = -velocityY / heightPx)
    }

    fun open() = animate(true)
    fun close() = animate(false)

    /** Close instantly, e.g. when the launcher is hidden. */
    fun closeNow() {
        isOpen = false
        scope.launch { progress.snapTo(0f) }
    }

    private fun animate(open: Boolean, initialVelocity: Float = 0f) {
        if (open != isOpen) view.snapTick()
        isOpen = open
        scope.launch {
            progress.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                initialVelocity = initialVelocity,
            )
        }
    }
}

@Composable
fun rememberDrawerSheet(): DrawerSheet {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    return remember { DrawerSheet(scope, view) }
}

/** Shrinks slightly while pressed and springs back, like Nothing's widgets. */
fun Modifier.squish(interaction: InteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "squish",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Short tick for snapping into place (drawer open / closed). */
fun View.snapTick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
        else HapticFeedbackConstants.VIRTUAL_KEY
    )
}

/** Firmer tick for switching something on or off. */
fun View.toggleTick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )
}
