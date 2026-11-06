package com.applauncher.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.applauncher.data.SettingsStore
import com.applauncher.lock.ChargingActivity
import com.applauncher.lock.LockScreenActivity
import com.applauncher.phone.CallManager
import java.lang.ref.WeakReference

/**
 * Switched on once under Settings > Accessibility. It reads nothing on screen. It's used for:
 * - double-tap to lock (turns the screen off), and
 * - the Dot lock screen: when the screen turns off, it gets the Dot lock screen ready
 *   on top of the phone's own lock screen (Android only allows this for services like this one).
 */
class LockService : AccessibilityService() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> showNothingLockScreen()
                Intent.ACTION_POWER_CONNECTED -> showChargingOnLockScreen()
            }
        }
    }

    override fun onServiceConnected() {
        current = WeakReference(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        ContextCompat.registerReceiver(this, screenOff, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Charger plugged in while the phone is locked: play the charging animation on the lock screen. */
    private fun showChargingOnLockScreen() {
        if (!SettingsStore.flow(this).value.chargingAnimation || inCall()) return
        val locked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        if (locked) ChargingActivity.show(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        current = null
        runCatching { unregisterReceiver(screenOff) }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun showNothingLockScreen() {
        if (!SettingsStore.flow(this).value.lockScreen) return
        // The screen also turns off when you hold the phone to your ear: never cover a call.
        if (inCall()) return
        runCatching { startActivity(LockScreenActivity.intent(this)) }
    }

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode
        return CallManager.calls.value.isNotEmpty() ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    companion object {
        private var current: WeakReference<LockService>? = null

        /** True when the user has switched the service on (doesn't lock anything). */
        val isOn: Boolean get() = current?.get() != null

        /** Locks the screen. Returns false if the service isn't switched on. */
        fun lock(): Boolean {
            val service = current?.get() ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
