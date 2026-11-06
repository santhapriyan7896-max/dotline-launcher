package com.applauncher.messages

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Tells you if a text failed to send (no signal, no balance, ...). */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Message not sent. Check your signal and try again.", Toast.LENGTH_LONG).show()
        }
    }
}
