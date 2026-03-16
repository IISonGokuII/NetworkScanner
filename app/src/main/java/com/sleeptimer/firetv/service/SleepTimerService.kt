package com.sleeptimer.firetv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.sleeptimer.firetv.DeviceController
import com.sleeptimer.firetv.MainActivity
import com.sleeptimer.firetv.R
import com.sleeptimer.firetv.TimerState
import com.sleeptimer.firetv.overlay.OverlayService
import com.sleeptimer.firetv.receiver.TimerActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SleepTimerService : Service() {

    companion object {
        const val TAG = "SleepTimerService"
        const val CHANNEL_ID = "sleep_timer_channel"
        const val NOTIFICATION_ID = 1
        const val WARNING_THRESHOLD_SECONDS = 300L // 5 minutes
        const val EXTEND_MINUTES = 15

        const val ACTION_START = "com.sleeptimer.firetv.ACTION_START"
        const val ACTION_STOP = "com.sleeptimer.firetv.ACTION_STOP"
        const val ACTION_EXTEND = "com.sleeptimer.firetv.ACTION_EXTEND"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun start(context: Context, durationSeconds: Long) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun extend(context: Context) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_EXTEND
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayShown = false

    inner class LocalBinder : Binder() {
        fun getService(): SleepTimerService = this@SleepTimerService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0)
                if (duration > 0) {
                    startTimer(duration)
                }
            }
            ACTION_STOP -> {
                stopTimer()
                stopSelf()
            }
            ACTION_EXTEND -> {
                extendTimer()
            }
        }
        return START_STICKY
    }

    private fun startTimer(durationSeconds: Long) {
        countdownJob?.cancel()
        overlayShown = false

        _timerState.value = TimerState(
            isRunning = true,
            totalSeconds = durationSeconds,
            remainingSeconds = durationSeconds
        )

        startForeground(NOTIFICATION_ID, createNotification(durationSeconds))

        countdownJob = serviceScope.launch {
            var remaining = durationSeconds
            while (remaining > 0) {
                _timerState.value = TimerState(
                    isRunning = true,
                    totalSeconds = durationSeconds,
                    remainingSeconds = remaining,
                    isInWarningPhase = remaining <= WARNING_THRESHOLD_SECONDS
                )

                // Show overlay warning at 5 minutes
                if (remaining <= WARNING_THRESHOLD_SECONDS && !overlayShown) {
                    overlayShown = true
                    showWarningOverlay()
                }

                // Update notification every 30 seconds to save battery
                if (remaining % 30 == 0L) {
                    updateNotification(remaining)
                }

                delay(1000)
                remaining--
            }

            // Timer finished - shut down
            Log.d(TAG, "Timer finished, shutting down...")
            hideWarningOverlay()
            performShutdown()
        }
    }

    private fun stopTimer() {
        countdownJob?.cancel()
        overlayShown = false
        _timerState.value = TimerState()
        hideWarningOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun extendTimer() {
        val current = _timerState.value
        if (!current.isRunning) return

        overlayShown = false
        hideWarningOverlay()

        val newRemaining = current.remainingSeconds + (EXTEND_MINUTES * 60)
        val newTotal = current.totalSeconds + (EXTEND_MINUTES * 60)

        countdownJob?.cancel()
        startTimer(newRemaining)

        _timerState.value = _timerState.value.copy(totalSeconds = newTotal)
    }

    private fun showWarningOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startService(intent)
    }

    private fun hideWarningOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        }
        startService(intent)
    }

    private suspend fun performShutdown() {
        _timerState.value = TimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)

        DeviceController.shutdownFireTV(this)

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt den laufenden Sleep Timer an"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(remainingSeconds: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, TimerActionReceiver::class.java).apply {
                action = TimerActionReceiver.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val extendIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, TimerActionReceiver::class.java).apply {
                action = TimerActionReceiver.ACTION_EXTEND
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Timer aktiv")
            .setContentText("Noch ${mins}:${String.format("%02d", secs)} verbleibend")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Stopp",
                    stopIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null, "+15 Min",
                    extendIntent
                ).build()
            )
            .build()
    }

    private fun updateNotification(remainingSeconds: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(remainingSeconds))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepTimer::CountdownWakeLock"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // Max 4 hours
        }
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _timerState.value = TimerState()
        super.onDestroy()
    }
}
