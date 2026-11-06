package com.applauncher.clock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.data.SettingsStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applauncher.phone.RoundButton
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import com.applauncher.ui.rememberStatus

/** Full-screen "alarm is ringing" screen, shown over the lock screen. */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "ALARM"
        val snoozable = intent.getBooleanExtra(EXTRA_SNOOZABLE, true)
        setContent {
            MonoTheme {
                RingScreen(
                    title = title,
                    snoozable = snoozable,
                    onStop = {
                        Ringer.stop(this)
                        finishAndRemoveTask()
                    },
                    onSnooze = {
                        Ringer.stop(this)
                        ClockStore.snooze(this, title)
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SNOOZABLE = "snoozable"

        fun intent(context: Context, title: String, snoozable: Boolean): Intent =
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SNOOZABLE, snoozable)
    }
}

@Composable
private fun RingScreen(title: String, snoozable: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by SettingsStore.flow(context).collectAsStateWithLifecycle()
    val status = rememberStatus(settings.clock24h)
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
    )

    Column(
        Modifier.fillMaxSize().background(Mono.Black).windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(title.uppercase(), style = Mono.label.copy(color = Mono.Red, fontSize = 14.sp))
        Spacer(Modifier.height(28.dp))
        DotMatrixText(status.time, dotSize = 10.dp, gap = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(status.date, style = Mono.label)
        Spacer(Modifier.weight(1.3f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (snoozable) {
                RoundButton(size = 96.dp, color = Mono.Tile, onClick = onSnooze) {
                    Text("SNOOZE", style = Mono.label.copy(color = Mono.White, fontSize = 11.sp))
                }
            }
            RoundButton(
                size = 110.dp, color = Mono.Red, onClick = onStop,
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            ) {
                Text("STOP", style = Mono.label.copy(color = Mono.White, fontSize = 13.sp))
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}
