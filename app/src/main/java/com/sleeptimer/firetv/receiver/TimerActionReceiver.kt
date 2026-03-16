package com.sleeptimer.firetv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleeptimer.firetv.service.SleepTimerService

/**
 * Handles notification actions (Stop / Extend) for the sleep timer.
 */
class TimerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.sleeptimer.firetv.action.STOP"
        const val ACTION_EXTEND = "com.sleeptimer.firetv.action.EXTEND"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> SleepTimerService.stop(context)
            ACTION_EXTEND -> SleepTimerService.extend(context)
        }
    }
}
