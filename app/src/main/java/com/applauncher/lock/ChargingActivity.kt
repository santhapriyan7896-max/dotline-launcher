package com.applauncher.lock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Short "now charging" animation: a dot-matrix battery fills up to the current level. */
class ChargingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val level = getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
        setContent { MonoTheme { ChargingScreen(level, onDone = ::close) } }
    }

    private fun close() {
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
        private var lastShown = 0L

        /** Shows the animation, at most once every few seconds (some chargers reconnect a few times). */
        fun show(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastShown < 5000) return
            lastShown = now
            runCatching {
                context.startActivity(
                    Intent(context, ChargingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            }
        }
    }
}

private const val BODY_COLS = 16
private const val ROWS = 8

@Composable
private fun ChargingScreen(level: Int, onDone: () -> Unit) {
    val fill = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "glow",
    )

    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(250))
        fill.animateTo(level / 100f, tween(1300, easing = FastOutSlowInEasing))
        delay(1600)
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone)
            .graphicsLayer { alpha = shown.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        // Battery made of dots: dim outline, white fill, red leading edge, small nib on the right.
        Canvas(Modifier.size(width = 300.dp, height = 140.dp)) {
            val pitch = size.width / (BODY_COLS + 2)
            val radius = pitch * 0.32f
            val top = (size.height - pitch * ROWS) / 2f
            val interiorCols = BODY_COLS - 2
            val filled = fill.value * interiorCols
            for (y in 0 until ROWS) for (x in 0 until BODY_COLS + 1) {
                val center = Offset(pitch * (x + 0.5f), top + pitch * (y + 0.5f))
                val isNib = x == BODY_COLS && y in 3..4
                val isBody = x < BODY_COLS
                if (!isBody && !isNib) continue
                val border = isNib || x == 0 || x == BODY_COLS - 1 || y == 0 || y == ROWS - 1
                if (border) {
                    drawCircle(Mono.Grey.copy(alpha = 0.55f), radius, center)
                } else {
                    val col = x - 1 // 0 until interiorCols
                    val amount = (filled - col).coerceIn(0f, 1f)
                    val leading = col == filled.toInt().coerceAtMost(interiorCols - 1) && amount > 0f && fill.value < 1f
                    when {
                        leading -> drawCircle(Mono.Red.copy(alpha = glow), radius, center)
                        amount > 0f -> drawCircle(Mono.White.copy(alpha = 0.3f + 0.7f * amount), radius, center)
                        else -> drawCircle(Mono.White.copy(alpha = 0.06f), radius, center)
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            DotMatrixText((fill.value * 100).roundToInt().toString(), dotSize = 7.dp, gap = 2.dp, accentChars = emptySet())
            Spacer(Modifier.width(6.dp))
            Text("%", color = Mono.Grey, fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (level >= 100) "FULLY CHARGED" else "CHARGING", style = Mono.label.copy(color = Mono.Red))
    }
}
