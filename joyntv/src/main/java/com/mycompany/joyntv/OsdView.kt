package com.mycompany.joyntv

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Playback bar shown at the bottom while seeking or pausing with the remote. */
class OsdView(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val state = TextView(context)
    private val time = TextView(context)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val hide = Runnable { visibility = View.GONE }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (16 * density).toInt()
        setPadding(pad * 2, pad, pad * 2, pad)
        background = GradientDrawable().apply {
            setColor(0xCC000000.toInt())
            cornerRadius = 12 * density
        }
        state.textSize = 22f
        state.setTextColor(0xFFFFFFFF.toInt())
        time.textSize = 18f
        time.setTextColor(0xFFFFFFFF.toInt())
        progress.max = 1000
        addView(state, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(progress, LayoutParams(0, (8 * density).toInt(), 1f).apply {
            marginStart = pad
            marginEnd = pad
        })
        addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        visibility = View.GONE
    }

    fun show(position: Double, duration: Double, paused: Boolean) {
        state.text = if (paused) "II" else "▶"
        if (duration > 0) {
            progress.visibility = View.VISIBLE
            progress.progress = (position / duration * 1000).toInt()
            time.text = "${format(position)} / ${format(duration)}"
        } else {
            // Live stream: no timeline
            progress.visibility = View.INVISIBLE
            time.text = "LIVE"
        }
        visibility = View.VISIBLE
        removeCallbacks(hide)
        postDelayed(hide, 3000)
    }

    private fun format(seconds: Double): String {
        val s = seconds.toLong().coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
