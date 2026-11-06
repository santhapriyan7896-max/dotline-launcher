package com.applauncher.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import kotlinx.coroutines.delay

/** "05:00", or "1:05:00" from an hour up. Rounds up, so a 5-minute timer starts at 05:00. */
fun formatCountdown(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Current wall-clock time, refreshed every [stepMs] while [ticking]. */
@Composable
fun rememberNow(ticking: Boolean, stepMs: Long = 100): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(stepMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Round timer face: remaining time in dots, with a red ring that empties as time runs out.
 * [dot] sets the size of the digits.
 */
@Composable
fun TimerDial(timer: TimerState, modifier: Modifier = Modifier, dot: Dp = 6.dp, ringWidth: Dp = 6.dp) {
    val now = rememberNow(timer.running)
    val remaining = timer.remaining(now)
    val progress = if (timer.isSet) (remaining.toFloat() / timer.durationMs).coerceIn(0f, 1f) else 0f

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = ringWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Mono.Circle, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (timer.isSet) {
                drawArc(
                    Mono.Red, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        DotMatrixText(
            text = formatCountdown(remaining),
            dotSize = dot,
            gap = dot / 3,
            color = if (timer.isSet && !timer.running) Mono.Grey else Mono.White,
        )
    }
}
