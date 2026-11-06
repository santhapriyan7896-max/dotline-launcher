package com.applauncher.phone

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/** All calls Android has handed to us, and the controls for them. */
object CallManager {

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    /** Goes up whenever anything about any call changes, so screens know to redraw. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    internal var service: WeakReference<CallService>? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = changed()
    }

    internal fun add(call: Call) {
        call.registerCallback(callback)
        _calls.value = _calls.value + call
        changed()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        _calls.value = _calls.value - call
        changed()
    }

    internal fun audioChanged(state: CallAudioState?) {
        _audio.value = state
    }

    private fun changed() {
        _changes.value++
        // Keep the call notification in step (ringing -> ongoing, on hold, ended).
        service?.get()?.let { CallNotifications.update(it) }
    }

    /** The call the screen should be about: a ringing call first, then the active one. */
    fun primary(calls: List<Call> = _calls.value): Call? =
        calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }
            ?: calls.firstOrNull { stateOf(it) == Call.STATE_SELECT_PHONE_ACCOUNT }
            ?: calls.firstOrNull { stateOf(it) in setOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING) }
            ?: calls.firstOrNull { stateOf(it) == Call.STATE_HOLDING }
            ?: calls.firstOrNull()

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun decline(call: Call) = call.reject(false, null)

    fun hangUp(call: Call) {
        if (stateOf(call) == Call.STATE_RINGING) decline(call) else call.disconnect()
    }

    fun toggleHold(call: Call) {
        if (stateOf(call) == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.get()?.setMuted(muted)
    }

    fun setRoute(route: Int) {
        service?.get()?.setAudioRoute(route)
    }

    fun pressKey(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    fun chooseSim(call: Call, account: PhoneAccountHandle) = call.phoneAccountSelected(account, false)

    @Suppress("DEPRECATION") // Call.getState() is replaced by Details.getState() only from Android 12
    fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= 31) call.details.state else call.state
}
