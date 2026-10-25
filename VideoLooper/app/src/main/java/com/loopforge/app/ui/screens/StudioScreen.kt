package com.loopforge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopforge.app.LoopViewModel
import com.loopforge.app.LoopViewModel.LengthMode
import com.loopforge.app.analysis.LoopEngine
import com.loopforge.app.ui.components.CandidateCarousel
import com.loopforge.app.ui.components.ChainBar
import com.loopforge.app.ui.components.FrameNudger
import com.loopforge.app.ui.components.GlassCard
import com.loopforge.app.ui.components.GradientButton
import com.loopforge.app.ui.components.InfoPill
import com.loopforge.app.ui.components.LegendDot
import com.loopforge.app.ui.components.LoopPreview
import com.loopforge.app.ui.components.LoopTimeline
import com.loopforge.app.ui.components.NeonChip
import com.loopforge.app.ui.components.ScoreRing
import com.loopforge.app.ui.components.SeamLens
import com.loopforge.app.ui.components.SectionTitle
import com.loopforge.app.ui.components.SimilarityMapView
import com.loopforge.app.ui.components.SwitchRow
import com.loopforge.app.ui.components.formatClock
import com.loopforge.app.ui.components.formatPrecise
import com.loopforge.app.ui.components.formatSeconds
import com.loopforge.app.ui.theme.Neon
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(vm: LoopViewModel, previewActive: Boolean) {
    val info = vm.info ?: return
    val detection = vm.detection ?: return
    val engine = vm.engine ?: return
    val selection = vm.selection ?: return
    val map = vm.similarityMap
    val plan = vm.plan()
    var loopOnly by rememberSaveable { mutableStateOf(true) }
    val best = detection.candidates.first()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Neon.Glass)
                    .clickable { vm.reset() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück", tint = Neon.Text)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Loop-Studio", style = MaterialTheme.typography.headlineMedium.copy(brush = Neon.brush))
                Text(info.name, style = MaterialTheme.typography.labelSmall, color = Neon.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Verdict(best.quality, detection.periodUs, detection.periodStrength)

        LoopPreview(
            uri = info.uri,
            startUs = selection.startUs,
            endUs = selection.endUs,
            loopOnly = loopOnly,
            active = previewActive,
            aspectRatio = info.aspectRatio,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            NeonChip("Loop-Vorschau", loopOnly) { loopOnly = true }
            NeonChip("Original", !loopOnly) { loopOnly = false }
        }

        // Detected loops
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Erkannte Loops") { InfoPill("${detection.candidates.size} Treffer", color = Neon.Cyan) }
            Spacer(Modifier.height(12.dp))
            CandidateCarousel(detection.candidates, vm.candidateThumbs, selection, vm::select)
            Spacer(Modifier.height(14.dp))
            val maxMin = max(0.5f, min(10f, info.durationUs / 1e6f * 0.7f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mindestlänge", style = MaterialTheme.typography.bodyMedium, color = Neon.TextDim, modifier = Modifier.weight(1f))
                Text(String.format(Locale.GERMAN, "%.1f s", vm.minLoopSec), style = MaterialTheme.typography.labelLarge, color = Neon.Text)
            }
            Slider(
                value = vm.minLoopSec.coerceIn(0.2f, maxMin),
                onValueChange = vm::updateMinLoop,
                valueRange = 0.2f..maxMin,
                colors = neonSlider(),
            )
        }

        // Similarity map
        if (map != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Wiederholungs-Karte") { InfoPill("tippen zum Wählen", icon = Icons.Rounded.AutoAwesome) }
                Spacer(Modifier.height(12.dp))
                SimilarityMapView(
                    bitmap = map,
                    candidates = detection.candidates.map { engine.mapFraction(it.endFrame) to engine.mapFraction(it.startFrame) },
                    selection = engine.mapFraction(selection.endFrame) to engine.mapFraction(selection.startFrame),
                    onPick = vm::pickOnMap,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill("→  Loop-Ende", color = Neon.Cyan)
                    InfoPill("↓  Loop-Start", color = Neon.Magenta)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Jeder Punkt vergleicht zwei Momente des Videos. Leuchtende Flecken abseits der Diagonale zeigen, " +
                        "wo sich der Ablauf wiederholt – genau dort entstehen nahtlose Loops. Gleichmäßige Streifen = periodische Bewegung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.TextFaint,
                )
            }
        }

        // Fine tuning
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Feinschliff") { InfoPill("${selection.endFrame - selection.startFrame} Frames", icon = Icons.Rounded.Movie) }
            Spacer(Modifier.height(12.dp))
            LoopTimeline(
                frames = vm.filmstrip,
                durationUs = info.durationUs.coerceAtLeast(1L),
                startUs = selection.startUs,
                endUs = selection.endUs,
                onChange = vm::setRangeUs,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameNudger("START", formatPrecise(selection.startUs), Neon.Magenta, { vm.nudge(true, -1) }, { vm.nudge(true, 1) }, Modifier.weight(1f))
                FrameNudger("ENDE", formatPrecise(selection.endUs), Neon.Cyan, { vm.nudge(false, -1) }, { vm.nudge(false, 1) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(selection.quality, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Nahtlos-Score", style = MaterialTheme.typography.labelMedium, color = Neon.quality(selection.quality))
                    Text(
                        "Loop-Länge ${formatSeconds(selection.durationUs)}" +
                            (if (selection.periodMultiple > 0) " · ${selection.periodMultiple}× Periode" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextDim,
                    )
                }
            }
        }

        // Seam lens
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Nahtstellen-Lupe") { ScoreRing(selection.quality, size = 30.dp, stroke = 3.dp, showLabel = false) }
            Spacer(Modifier.height(12.dp))
            val seam = vm.seamFrames
            SeamLens(seam?.last, seam?.first, info.aspectRatio, selection.quality)
        }

        // Length
        if (plan != null) {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                SectionTitle("Neue Länge")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("Ziel-Länge", vm.lengthMode == LengthMode.DURATION) { vm.lengthMode = LengthMode.DURATION }
                    NeonChip("Wiederholungen", vm.lengthMode == LengthMode.REPEATS) { vm.lengthMode = LengthMode.REPEATS }
                }
                Spacer(Modifier.height(14.dp))
                if (vm.lengthMode == LengthMode.DURATION) {
                    Text(
                        formatClock(vm.targetSec * 1_000_000L),
                        style = MaterialTheme.typography.displayLarge.copy(brush = Neon.brush),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, sec) in PRESETS) NeonChip(label, vm.targetSec == sec) { vm.targetSec = sec }
                    }
                    Slider(
                        value = secondsToSlider(vm.targetSec),
                        onValueChange = { vm.targetSec = sliderToSeconds(it) },
                        colors = neonSlider(),
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        StepButton("−") { vm.repeatCount = max(1, vm.repeatCount - 1) }
                        Text(
                            "${vm.repeatCount}×",
                            style = MaterialTheme.typography.displayLarge.copy(brush = Neon.brush),
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        StepButton("+") { vm.repeatCount = min(LoopViewModel.MAX_REPEATS, vm.repeatCount + 1) }
                    }
                    Slider(
                        value = repeatsToSlider(vm.repeatCount),
                        onValueChange = { vm.repeatCount = sliderToRepeats(it) },
                        colors = neonSlider(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                ChainBar(plan.introUs, plan.loopUs, plan.repeats, plan.outroUs)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (plan.introUs > 0) LegendDot(Neon.Violet, "Intro")
                    LegendDot(Neon.Magenta, "${plan.repeats}× Loop")
                    if (plan.outroUs > 0) LegendDot(Neon.Mint, "Outro")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Ergebnis: ${formatPrecise(plan.totalUs)} (Original ${formatPrecise(info.durationUs)})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neon.Text,
                )
            }
        }

        // Options
        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("Optionen")
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                "Intro behalten",
                "Startet mit dem Original bis zum Loop-Beginn",
                vm.includeIntro,
                enabled = selection.startUs > 50_000L,
            ) { vm.includeIntro = it }
            SwitchRow(
                "Outro anhängen",
                "Nach dem letzten Durchlauf läuft das Original zu Ende",
                vm.includeOutro,
                enabled = info.durationUs - selection.endUs > 50_000L,
            ) { vm.includeOutro = it }
            SwitchRow(
                "Ton behalten",
                if (info.hasAudio) "Mit Mikro-Überblendung an jeder Nahtstelle" else "Video hat keine Tonspur",
                vm.keepAudio && info.hasAudio,
                enabled = info.hasAudio,
            ) { vm.keepAudio = it }
        }

        if (plan != null) {
            GradientButton(
                "Video verlängern · ${formatClock(plan.totalUs)}",
                icon = Icons.Rounded.AllInclusive,
                onClick = vm::startExport,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                InfoPill("H.264 · MP4", icon = Icons.Rounded.Movie)
                InfoPill("Loop wird nur 1× kodiert", icon = Icons.Rounded.Speed)
                if (plan.keepAudio) InfoPill("Ton", icon = Icons.Rounded.GraphicEq)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Verdict(quality: Float, periodUs: Long?, strength: Float) {
    val (title, text) = when {
        quality >= LoopEngine.SEAMLESS -> "Perfekter Loop gefunden" to "Der Übergang ist praktisch unsichtbar."
        quality >= 0.6f -> "Guter Loop gefunden" to "Eine kleine Nahtstelle kann sichtbar sein – prüfe sie in der Lupe."
        else -> "Kein exakter Wiederholungsablauf" to "Das ist die beste Annäherung. Wähle auf der Karte einen hellen Punkt oder schneide selbst."
    }
    val color = Neon.quality(quality)
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(quality, size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Neon.TextDim)
                if (periodUs != null) {
                    Spacer(Modifier.height(6.dp))
                    InfoPill(
                        "Wiederholung alle ${formatSeconds(periodUs)} · ${(strength * 100).roundToInt()}% Rhythmus",
                        color = Neon.Cyan,
                        icon = Icons.Rounded.AllInclusive,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Neon.GlassStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = Neon.Text)
    }
}

@Composable
private fun neonSlider() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Neon.Violet,
    inactiveTrackColor = Color(0x22FFFFFF),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

private val PRESETS = listOf(
    "15 s" to 15, "30 s" to 30, "1 min" to 60, "2 min" to 120,
    "5 min" to 300, "10 min" to 600, "30 min" to 1800, "1 h" to 3600,
)

private const val MIN_TARGET = 5.0
private const val MAX_TARGET = 3600.0

/** Logarithmic slider so both 20 seconds and 40 minutes are easy to hit. */
private fun sliderToSeconds(v: Float): Int {
    val raw = MIN_TARGET * exp(v * ln(MAX_TARGET / MIN_TARGET))
    val step = when {
        raw < 60 -> 1
        raw < 600 -> 5
        else -> 30
    }
    return ((raw / step).roundToInt() * step).coerceIn(MIN_TARGET.toInt(), MAX_TARGET.toInt())
}

private fun secondsToSlider(sec: Int): Float =
    (ln(sec.coerceIn(MIN_TARGET.toInt(), MAX_TARGET.toInt()) / MIN_TARGET) / ln(MAX_TARGET / MIN_TARGET)).toFloat()

private fun sliderToRepeats(v: Float): Int =
    exp(v * ln(LoopViewModel.MAX_REPEATS.toDouble())).roundToInt().coerceIn(1, LoopViewModel.MAX_REPEATS)

private fun repeatsToSlider(n: Int): Float =
    (ln(n.coerceAtLeast(1).toDouble()) / ln(LoopViewModel.MAX_REPEATS.toDouble())).toFloat()
