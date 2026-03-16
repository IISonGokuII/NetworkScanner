package com.sleeptimer.firetv

/**
 * Represents the current state of the sleep timer.
 */
data class TimerState(
    val isRunning: Boolean = false,
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val isInWarningPhase: Boolean = false
) {
    val progress: Float
        get() = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f

    val remainingMinutes: Int
        get() = (remainingSeconds / 60).toInt()

    val remainingDisplaySeconds: Int
        get() = (remainingSeconds % 60).toInt()

    val formattedTime: String
        get() {
            val hours = remainingSeconds / 3600
            val mins = (remainingSeconds % 3600) / 60
            val secs = remainingSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, mins, secs)
            } else {
                String.format("%02d:%02d", mins, secs)
            }
        }
}

/**
 * Preset timer durations for the selection screen.
 */
enum class TimerPreset(val minutes: Int, val label: String) {
    MIN_15(15, "15 Min"),
    MIN_30(30, "30 Min"),
    MIN_45(45, "45 Min"),
    MIN_60(60, "1 Std"),
    MIN_90(90, "1,5 Std"),
    MIN_120(120, "2 Std");

    val seconds: Long get() = minutes * 60L
}
