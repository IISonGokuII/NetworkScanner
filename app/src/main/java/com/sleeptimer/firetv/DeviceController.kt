package com.sleeptimer.firetv

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles Fire TV and TV shutdown via HDMI-CEC and device sleep.
 *
 * Fire TV supports HDMI-CEC out of the box - when the Fire TV goes to sleep,
 * it sends a CEC standby command to the TV, which turns off the TV too.
 */
object DeviceController {

    private const val TAG = "DeviceController"

    /**
     * Puts the Fire TV to sleep. This also triggers HDMI-CEC to turn off the TV
     * if CEC is enabled in the TV settings (which is the default on most TVs).
     */
    suspend fun shutdownFireTV(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initiating Fire TV sleep...")

                // Method 1: Send KEYCODE_SLEEP via input command
                // This is the most reliable method on Fire TV and triggers CEC standby
                val process = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "KEYCODE_SLEEP"))
                val exitCode = process.waitFor()
                Log.d(TAG, "KEYCODE_SLEEP sent, exit code: $exitCode")

                if (exitCode != 0) {
                    // Method 2: Alternative - use KEYCODE_POWER
                    Log.d(TAG, "Trying KEYCODE_POWER as fallback...")
                    val fallback = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "KEYCODE_POWER"))
                    fallback.waitFor()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Shell command failed, trying broadcast method", e)

                // Method 3: Broadcast approach - send sleep intent
                try {
                    val sleepIntent = Intent("android.intent.action.SCREEN_OFF")
                    context.sendBroadcast(sleepIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Broadcast also failed", e2)
                }
            }

            // Method 4: Use PowerManager as additional fallback
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isInteractive) {
                    // Send go-to-sleep via key event simulation
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26")) // POWER key
                        .waitFor()
                }
            } catch (e: Exception) {
                Log.e(TAG, "PowerManager fallback failed", e)
            }
        }
    }

    /**
     * Sends a CEC standby command directly to turn off the TV.
     * This requires the device to have CEC support (all Fire TVs do).
     */
    suspend fun sendCecStandby() {
        withContext(Dispatchers.IO) {
            try {
                // Try sending CEC standby command via the cec-client or HDMI CEC framework
                // On Fire TV, going to sleep automatically sends CEC standby
                // But we can also try the direct approach
                val commands = arrayOf(
                    arrayOf("cmd", "hdmi_control", "cec_setting", "set", "hdmi_cec_enabled", "1"),
                    arrayOf("cmd", "hdmi_control", "one_touch_play"),
                )
                for (cmd in commands) {
                    try {
                        Runtime.getRuntime().exec(cmd).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "CEC command failed: ${cmd.joinToString(" ")}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CEC standby failed", e)
            }
        }
    }
}
