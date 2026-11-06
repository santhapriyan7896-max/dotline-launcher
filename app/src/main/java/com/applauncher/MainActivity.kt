package com.applauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.applauncher.data.SettingsStore
import com.applauncher.lock.ChargingActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applauncher.ui.mono.MonoLauncher
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Goes up by one every time the Home button is pressed while we're the home screen. */
    private val homePresses = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LauncherViewModel = viewModel()
            val presses by homePresses.collectAsState()
            MonoTheme {
                MonoLauncher(vm = vm, homePresses = presses)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    /** Charger plugged in while you're on the home screen: play the charging animation. */
    private val powerConnected = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SettingsStore.flow(context).value.chargingAnimation) ChargingActivity.show(context)
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, powerConnected, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        runCatching { unregisterReceiver(powerConnected) }
        super.onPause()
    }
}
