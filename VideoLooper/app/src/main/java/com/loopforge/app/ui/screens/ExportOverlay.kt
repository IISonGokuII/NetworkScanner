package com.loopforge.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loopforge.app.LoopViewModel.ExportState
import com.loopforge.app.ui.components.BigPercent
import com.loopforge.app.ui.components.GhostButton
import com.loopforge.app.ui.components.GlassCard
import com.loopforge.app.ui.components.GradientButton
import com.loopforge.app.ui.components.ProgressOrb
import com.loopforge.app.ui.components.formatClock
import com.loopforge.app.ui.theme.Neon

@Composable
fun ExportOverlay(
    state: ExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNewVideo: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEB07070F))
            // Swallow touches so the studio below cannot be changed mid-export.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is ExportState.Running -> {
                val view = LocalView.current
                DisposableEffect(Unit) {
                    view.keepScreenOn = true
                    onDispose { view.keepScreenOn = false }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressOrb(state.progress, size = 250.dp) { BigPercent(state.progress, "wird geschmiedet") }
                    Spacer(Modifier.height(24.dp))
                    Text(state.stage, style = MaterialTheme.typography.titleMedium, color = Neon.Text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Bitte lass die App geöffnet, bis das Video fertig ist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextFaint,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    GhostButton("Abbrechen", icon = Icons.Rounded.Close, onClick = onCancel)
                }
            }

            is ExportState.Done -> GlassCard(Modifier.fillMaxWidth(), glow = true, padding = 22.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(84.dp)
                        .background(Neon.brush, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Fertig – ${formatClock(state.output.durationUs)} Endlos-Video",
                    style = MaterialTheme.typography.titleLarge,
                    color = Neon.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gespeichert unter Filme/LoopForge\n${state.output.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.output.crossfadeSkipped) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hinweis: Dieses Gerät konnte die Überblendung nicht rendern – es wurde hart geschnitten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.Amber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(20.dp))
                GradientButton("Abspielen", icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth()) {
                    play(context, state.output.uri)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Teilen", icon = Icons.Rounded.Share, modifier = Modifier.weight(1f)) { share(context, state.output.uri) }
                    GhostButton("Bearbeiten", icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f), onClick = onDismiss)
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("Neues Video", icon = Icons.Rounded.VideoLibrary, modifier = Modifier.fillMaxWidth(), onClick = onNewVideo)
            }

            is ExportState.Failed -> GlassCard(Modifier.fillMaxWidth(), padding = 22.dp) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Neon.Coral,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Neon.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                GradientButton("Erneut versuchen", icon = Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth(), onClick = onRetry)
                Spacer(Modifier.height(10.dp))
                GhostButton("Schließen", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }

            ExportState.Idle -> Unit
        }
    }
}

private fun play(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Kein Videoplayer gefunden", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Video teilen"))
}
