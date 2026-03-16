package com.sleeptimer.firetv.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.sleeptimer.firetv.R
import com.sleeptimer.firetv.service.SleepTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_SHOW = "com.sleeptimer.firetv.overlay.SHOW"
        const val ACTION_HIDE = "com.sleeptimer.firetv.overlay.HIDE"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 60
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_warning, null)

        overlayView?.let { view ->
            val timerText = view.findViewById<TextView>(R.id.overlay_timer_text)
            val btnExtend5 = view.findViewById<Button>(R.id.btn_extend_5)
            val btnExtend15 = view.findViewById<Button>(R.id.btn_extend_15)
            val btnExtend30 = view.findViewById<Button>(R.id.btn_extend_30)
            val btnExtend60 = view.findViewById<Button>(R.id.btn_extend_60)
            val btnDismiss = view.findViewById<Button>(R.id.btn_dismiss)

            // Wire up extend buttons with different durations
            btnExtend5.setOnClickListener {
                SleepTimerService.extend(this, 5)
                hideOverlay()
            }
            btnExtend15.setOnClickListener {
                SleepTimerService.extend(this, 15)
                hideOverlay()
            }
            btnExtend30.setOnClickListener {
                SleepTimerService.extend(this, 30)
                hideOverlay()
            }
            btnExtend60.setOnClickListener {
                SleepTimerService.extend(this, 60)
                hideOverlay()
            }
            btnDismiss.setOnClickListener {
                hideOverlay()
            }

            // Focus on +15 Min by default (most common choice)
            btnExtend15.requestFocus()

            try {
                windowManager?.addView(view, layoutParams)
                Log.d(TAG, "Overlay shown with extend options: 5/15/30/60 min")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay", e)
                overlayView = null
            }
        }

        // Start updating the timer display
        updateJob = scope.launch {
            while (true) {
                val state = SleepTimerService.timerState.value
                overlayView?.let { view ->
                    val timerText = view.findViewById<TextView>(R.id.overlay_timer_text)
                    timerText?.text = "Noch ${state.formattedTime} verbleibend"
                }
                delay(1000)
            }
        }
    }

    private fun hideOverlay() {
        updateJob?.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide overlay", e)
            }
        }
        overlayView = null
        stopSelf()
    }

    override fun onDestroy() {
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }
}
