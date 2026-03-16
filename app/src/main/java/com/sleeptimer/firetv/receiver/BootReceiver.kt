package com.sleeptimer.firetv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED to potentially restore a saved timer.
 * For now, this just logs boot - active timer state is lost on reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - Sleep Timer app ready")
        }
    }
}
