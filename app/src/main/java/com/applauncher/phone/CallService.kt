package com.applauncher.phone

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import java.lang.ref.WeakReference

/**
 * Android hands every phone call to this service while Dotline is the default phone app.
 * If this ever crashes during a call, Android falls back to the phone's built-in call screen.
 */
class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = WeakReference(this)
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        CallNotifications.update(this)
        // Incoming calls show through the full-screen notification (it also wakes a locked phone).
        // Outgoing calls open the call screen straight away.
        if (CallManager.stateOf(call) != Call.STATE_RINGING) {
            runCatching { startActivity(InCallActivity.intent(this)) }
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        CallNotifications.update(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.audioChanged(audioState)
    }

    override fun onDestroy() {
        CallManager.service = null
        CallNotifications.cancel(this)
        super.onDestroy()
    }
}
