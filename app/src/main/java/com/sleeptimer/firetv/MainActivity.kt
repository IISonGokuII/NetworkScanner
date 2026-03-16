package com.sleeptimer.firetv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleeptimer.firetv.service.SleepTimerService
import com.sleeptimer.firetv.ui.theme.AccentBlue
import com.sleeptimer.firetv.ui.theme.AccentGreen
import com.sleeptimer.firetv.ui.theme.AccentOrange
import com.sleeptimer.firetv.ui.theme.AccentRed
import com.sleeptimer.firetv.ui.theme.DarkBackground
import com.sleeptimer.firetv.ui.theme.DarkCard
import com.sleeptimer.firetv.ui.theme.DarkSurface
import com.sleeptimer.firetv.ui.theme.SleepTimerTheme
import com.sleeptimer.firetv.ui.theme.TextMuted
import com.sleeptimer.firetv.ui.theme.TextPrimary
import com.sleeptimer.firetv.ui.theme.TextSecondary
import com.sleeptimer.firetv.ui.theme.TimerWarning

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestOverlayPermission()

        setContent {
            SleepTimerTheme {
                val timerState by SleepTimerService.timerState.collectAsState()
                SleepTimerApp(
                    timerState = timerState,
                    onStartTimer = { preset ->
                        SleepTimerService.start(this, preset.seconds)
                    },
                    onStopTimer = {
                        SleepTimerService.stop(this)
                    },
                    onExtendTimer = {
                        SleepTimerService.extend(this)
                    }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
fun SleepTimerApp(
    timerState: TimerState,
    onStartTimer: (TimerPreset) -> Unit,
    onStopTimer: () -> Unit,
    onExtendTimer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (timerState.isRunning) {
            TimerRunningScreen(
                timerState = timerState,
                onStop = onStopTimer,
                onExtend = onExtendTimer
            )
        } else {
            TimerSelectionScreen(onStartTimer = onStartTimer)
        }
    }
}

@Composable
fun TimerSelectionScreen(onStartTimer: (TimerPreset) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Title
        Text(
            text = "Sleep Timer",
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Fire TV & TV automatisch ausschalten",
            fontSize = 18.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Timer preset grid - 2 rows of 3
        val presets = TimerPreset.entries
        for (row in presets.chunked(3)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                for (preset in row) {
                    TimerPresetButton(
                        preset = preset,
                        onClick = { onStartTimer(preset) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Mit der Fernbedienung auswaehlen und OK druecken",
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TimerPresetButton(preset: TimerPreset, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = if (isFocused) AccentBlue else DarkCard
    val borderColor = if (isFocused) AccentBlue else TextMuted
    val textColor = if (isFocused) Color.White else TextPrimary

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor),
        modifier = Modifier
            .size(width = 160.dp, height = 100.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = preset.minutes.toString(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = if (preset.minutes >= 60) "Stunden" else "Minuten",
                fontSize = 12.sp,
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else TextSecondary
            )
        }
    }
}

@Composable
fun TimerRunningScreen(
    timerState: TimerState,
    onStop: () -> Unit,
    onExtend: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = timerState.progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    val progressColor = when {
        timerState.remainingSeconds <= 60 -> AccentRed
        timerState.isInWarningPhase -> TimerWarning
        else -> AccentGreen
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Circular timer display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            // Background circle
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = DarkCard,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )

            // Progress circle
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = progressColor,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )

            // Time display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timerState.formattedTime,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "verbleibend",
                    fontSize = 16.sp,
                    color = TextSecondary
                )
                if (timerState.isInWarningPhase) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TV wird gleich ausgeschaltet",
                        fontSize = 14.sp,
                        color = TimerWarning,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Extend button
            var extendFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = onExtend,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (extendFocused) AccentBlue else Color.Transparent,
                    contentColor = if (extendFocused) Color.White else AccentBlue
                ),
                border = BorderStroke(2.dp, if (extendFocused) AccentBlue else AccentBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(56.dp)
                    .width(180.dp)
                    .onFocusChanged { extendFocused = it.isFocused }
                    .focusable()
            ) {
                Text(
                    text = "+15 Minuten",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Stop button
            var stopFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (stopFocused) AccentRed else AccentRed.copy(alpha = 0.2f),
                    contentColor = if (stopFocused) Color.White else AccentRed
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(56.dp)
                    .width(180.dp)
                    .onFocusChanged { stopFocused = it.isFocused }
                    .focusable()
            ) {
                Text(
                    text = "Timer Stoppen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
