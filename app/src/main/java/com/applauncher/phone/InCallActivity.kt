package com.applauncher.phone

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.ui.mono.DotMatrixText
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Full-screen call screen: incoming, calling, in a call, on hold. Shows over the lock screen. */
class InCallActivity : ComponentActivity() {

    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle(intent)
        setContent {
            MonoTheme {
                InCallScreen(onFinished = { finishAndRemoveTask() }, onProximity = ::setProximity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Answer" tapped on the notification. */
    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            CallManager.calls.value.firstOrNull { CallManager.stateOf(it) == Call.STATE_RINGING }
                ?.let(CallManager::answer)
        }
    }

    /** Turns the screen off when the phone is held to your ear. */
    private fun setProximity(on: Boolean) {
        val power = getSystemService(PowerManager::class.java) ?: return
        if (on) {
            if (proximityLock == null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "applauncher:incall")
            }
            proximityLock?.takeIf { !it.isHeld }?.acquire(3 * 60 * 60 * 1000L)
        } else {
            proximityLock?.takeIf { it.isHeld }?.release()
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ANSWER = "com.applauncher.phone.ANSWER"

        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun InCallScreen(onFinished: () -> Unit, onProximity: (Boolean) -> Unit) {
    val calls by CallManager.calls.collectAsState()
    val changes by CallManager.changes.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val call = remember(calls, changes) { CallManager.primary(calls) }

    // Remember who we were talking to, for the "Call ended" moment.
    var lastName by remember { mutableStateOf("") }

    LaunchedEffect(call == null) {
        if (call == null) {
            onProximity(false)
            delay(900)
            onFinished()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (call == null) {
            Spacer(Modifier.weight(1f))
            Text("CALL ENDED", style = Mono.label.copy(color = Mono.Red))
            Spacer(Modifier.height(16.dp))
            Text(lastName, color = Mono.Grey, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1.4f))
            return@Column
        }

        // Read 'changes' so this part redraws when the call's state changes.
        val state = remember(call, changes) { CallManager.stateOf(call) }
        val number = remember(call, changes) { PhoneData.numberOf(call) }
        val name = callerName(call, number, changes)
        SideEffect { lastName = name }

        val earpiece = audio?.route?.let {
            it == CallAudioState.ROUTE_EARPIECE || it == CallAudioState.ROUTE_WIRED_HEADSET
        } ?: true
        val talking = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
        LaunchedEffect(talking, earpiece) { onProximity(talking && earpiece) }

        var keypadOpen by remember { mutableStateOf(false) }
        var typed by remember(call) { mutableStateOf("") }

        Spacer(Modifier.height(40.dp))
        Text(
            when (state) {
                Call.STATE_RINGING -> "INCOMING CALL"
                Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "CALLING…"
                Call.STATE_HOLDING -> "ON HOLD"
                Call.STATE_SELECT_PHONE_ACCOUNT -> "CHOOSE A SIM"
                Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "ENDING…"
                else -> "ON CALL"
            },
            style = Mono.label.copy(color = if (state == Call.STATE_RINGING) Mono.Red else Mono.Grey),
        )

        if (!keypadOpen) {
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Mono.Circle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull { it.isLetter() }?.uppercase() ?: "#",
                    color = Mono.White, fontSize = 40.sp, fontWeight = FontWeight.Light,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            name, color = Mono.White, fontSize = 32.sp, fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (name != number && number.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(number, style = Mono.label)
        }

        if (state == Call.STATE_ACTIVE) {
            Spacer(Modifier.height(22.dp))
            CallTimer(call.details.connectTimeMillis)
        }

        Spacer(Modifier.weight(1f))

        when {
            state == Call.STATE_RINGING -> IncomingButtons(call)
            state == Call.STATE_SELECT_PHONE_ACCOUNT -> SimChooser(call)
            keypadOpen -> {
                Text(typed, color = Mono.White, fontSize = 30.sp, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Keypad(onKey = { key -> typed += key; CallManager.pressKey(call, key) }, keySize = 68.dp)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    LabelButton("HIDE", on = false, onClick = { keypadOpen = false })
                    HangUpButton(call)
                    Spacer(Modifier.size(76.dp))
                }
            }
            else -> {
                ActiveControls(call, state, audio, onKeypad = { keypadOpen = true })
                Spacer(Modifier.height(36.dp))
                HangUpButton(call)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Contact name if we have one, else the number. */
@Composable
private fun callerName(call: Call, number: String, changes: Int): String {
    val context = LocalContext.current
    val known = remember(call, changes) { PhoneData.knownName(call) }
    val looked by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { PhoneData.nameFor(context, number) }
    }
    return known ?: looked ?: number.ifBlank { "Unknown caller" }
}

/** mm:ss in dots, counting up from when the call connected. */
@Composable
private fun CallTimer(connectedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val seconds = if (connectedAt > 0) ((now - connectedAt) / 1000).coerceAtLeast(0) else 0
    val text = if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }
    DotMatrixText(text, dotSize = 5.dp, gap = 2.dp, accentChars = emptySet())
}

@Composable
private fun IncomingButtons(call: Call) {
    // The answer button gently pulses while ringing.
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundButton(size = 80.dp, color = Mono.Red, onClick = { CallManager.decline(call) }) {
                CallIcon(Mono.White, hangUp = true)
            }
            Spacer(Modifier.height(10.dp))
            Text("DECLINE", style = Mono.label.copy(fontSize = 10.sp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundButton(
                size = 80.dp, color = Mono.White, onClick = { CallManager.answer(call) },
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            ) {
                CallIcon(Mono.Ink)
            }
            Spacer(Modifier.height(10.dp))
            Text("ANSWER", style = Mono.label.copy(fontSize = 10.sp))
        }
    }
}

@Composable
private fun ActiveControls(call: Call, state: Int, audio: CallAudioState?, onKeypad: () -> Unit) {
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val supported = audio?.supportedRouteMask ?: 0
    val hasBluetooth = supported and CallAudioState.ROUTE_BLUETOOTH != 0
    val connected = state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
    val canHold = call.details.can(Call.Details.CAPABILITY_HOLD) || call.details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabelButton("MUTE", on = audio?.isMuted == true, onClick = { CallManager.setMuted(audio?.isMuted != true) })
            LabelButton("KEYPAD", on = false, onClick = onKeypad)
            LabelButton(
                "SPEAKER", on = route == CallAudioState.ROUTE_SPEAKER,
                onClick = {
                    CallManager.setRoute(
                        if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE
                        else CallAudioState.ROUTE_SPEAKER
                    )
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabelButton(
                "HOLD", on = state == Call.STATE_HOLDING,
                enabled = connected && canHold,
                onClick = { CallManager.toggleHold(call) },
            )
            if (hasBluetooth) {
                LabelButton(
                    "BLUETOOTH", on = route == CallAudioState.ROUTE_BLUETOOTH,
                    onClick = {
                        CallManager.setRoute(
                            if (route == CallAudioState.ROUTE_BLUETOOTH) CallAudioState.ROUTE_WIRED_OR_EARPIECE
                            else CallAudioState.ROUTE_BLUETOOTH
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HangUpButton(call: Call) {
    RoundButton(size = 80.dp, color = Mono.Red, onClick = { CallManager.hangUp(call) }) {
        CallIcon(Mono.White, hangUp = true)
    }
}

/** Shown when you have more than one SIM and no default: pick which one to call from. */
@Composable
private fun SimChooser(call: Call) {
    val context = LocalContext.current
    val accounts = remember(call) {
        @Suppress("DEPRECATION")
        call.details.intentExtras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS).orEmpty()
    }
    val telecom = context.getSystemService(TelecomManager::class.java)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        accounts.forEachIndexed { index, handle ->
            val label = runCatching { telecom?.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
                ?: "SIM ${index + 1}"
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Mono.Tile)
                    .clickable { CallManager.chooseSim(call, handle) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = Mono.label.copy(color = Mono.White))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HangUpButton(call) }
    }
}
