package com.loopforge.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loopforge.app.analysis.VideoInfo
import com.loopforge.app.ui.components.BigPercent
import com.loopforge.app.ui.components.GhostButton
import com.loopforge.app.ui.components.GlassCard
import com.loopforge.app.ui.components.GradientButton
import com.loopforge.app.ui.components.InfinityOrb
import com.loopforge.app.ui.components.InfoPill
import com.loopforge.app.ui.components.ProgressOrb
import com.loopforge.app.ui.components.formatPrecise
import com.loopforge.app.ui.theme.Neon

@Composable
fun HomeScreen(error: String?, onPick: () -> Unit, onDismissError: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AllInclusive, contentDescription = null, tint = Neon.Cyan, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("LoopForge", style = MaterialTheme.typography.titleLarge.copy(brush = Neon.brush))
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "Mach jedes Video\nendlos lang.",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 0.82f),
            color = Neon.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "LoopForge erkennt wiederkehrende Abläufe automatisch und setzt den perfekten Schnitt – du musst nichts selbst schneiden.",
            style = MaterialTheme.typography.bodyLarge,
            color = Neon.TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        InfinityOrb(onClick = onPick)
        GradientButton(
            "Video auswählen",
            icon = Icons.Rounded.VideoLibrary,
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = error != null, enter = fadeIn()) {
            GlassCard(Modifier.padding(top = 18.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Neon.Coral)
                    Spacer(Modifier.width(10.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Neon.Text, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Schließen",
                        tint = Neon.TextDim,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDismissError)
                            .padding(4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepCard(Icons.Rounded.VideoLibrary, "1 · Wählen", "Clip aus der Galerie oder per Teilen", Neon.Magenta, Modifier.weight(1f))
            StepCard(Icons.Rounded.AutoAwesome, "2 · Erkennen", "Nahtlose Loop-Punkte automatisch", Neon.Violet, Modifier.weight(1f))
            StepCard(Icons.Rounded.Timelapse, "3 · Verlängern", "Auf jede Länge, bis 1 Stunde", Neon.Cyan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun StepCard(icon: ImageVector, title: String, text: String, accent: Color, modifier: Modifier) {
    GlassCard(modifier.fillMaxHeight(), padding = 12.dp) {
        Box(
            Modifier
                .size(34.dp)
                .background(accent.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = Neon.Text)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Neon.TextFaint)
    }
}

@Composable
fun AnalyzingScreen(
    info: VideoInfo?,
    progress: Float,
    frames: Int,
    message: String,
    filmstrip: List<ImageBitmap>,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressOrb(progress) {
            BigPercent(progress, if (frames > 0) "$frames Frames" else "Starte…")
        }
        Spacer(Modifier.height(26.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = Neon.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Jedes Bild wird mit jedem verglichen, um den Moment zu finden, an dem sich der Ablauf wiederholt.",
            style = MaterialTheme.typography.bodyMedium,
            color = Neon.TextFaint,
            textAlign = TextAlign.Center,
        )
        if (info != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(info.name, modifier = Modifier.width(160.dp))
                InfoPill(formatPrecise(info.durationUs))
                if (info.displayWidth > 0) InfoPill("${info.displayWidth}×${info.displayHeight}")
            }
        }
        Spacer(Modifier.height(24.dp))
        ScanningStrip(filmstrip, progress)
        Spacer(Modifier.height(28.dp))
        GhostButton("Abbrechen", icon = Icons.Rounded.Close, onClick = onCancel)
    }
}

@Composable
private fun ScanningStrip(frames: List<ImageBitmap>, progress: Float) {
    val transition = rememberInfiniteTransition(label = "scan")
    val glow by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "glow")
    val animated by animateFloatAsState(progress, tween(250), label = "scanProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15152A)),
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach {
                Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * animated
            drawRect(Color(0x9907070F), Offset(x, 0f), Size(size.width - x, size.height))
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Neon.Cyan.copy(alpha = 0.35f + 0.25f * glow)), startX = x - 80f, endX = x),
                Offset(x - 80f, 0f),
                Size(80f, size.height),
            )
            drawLine(Neon.Cyan, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
        }
        if (frames.isEmpty()) {
            Text(
                "Vorschau wird geladen…",
                style = MaterialTheme.typography.labelSmall,
                color = Neon.TextFaint,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
