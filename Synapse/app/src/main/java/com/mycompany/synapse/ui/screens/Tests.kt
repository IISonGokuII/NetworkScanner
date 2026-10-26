package com.mycompany.synapse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Achievement
import com.mycompany.synapse.data.BenchmarkRun
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.TestPartResult
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.Staircase
import com.mycompany.synapse.game.TestPlans
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.Pill
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.RadarChart
import com.mycompany.synapse.ui.components.RadarSeries
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.games.GameTrial
import com.mycompany.synapse.ui.games.HintText
import com.mycompany.synapse.ui.games.TrialContext
import com.mycompany.synapse.ui.games.now
import com.mycompany.synapse.ui.games.since
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class TestPhase { INTRO, PART, TRIAL, RESULT }

/** Einstufungstest („placement“) oder monatlicher Leistungstest („benchmark“). */
@Composable
fun TestScreen(kind: String, nav: Navigator) {
    val c = Synapse.colors
    val placement = kind == Repo.KIND_PLACEMENT
    val plan = if (placement) TestPlans.placement else TestPlans.benchmark
    var phase by remember { mutableStateOf(TestPhase.INTRO) }
    var partIndex by remember { mutableIntStateOf(0) }
    var trialIndex by remember { mutableIntStateOf(0) }
    var staircase by remember { mutableStateOf(Staircase(plan[0].startLevel)) }
    val partTrials = remember { mutableStateListOf<TrialResult>() }
    val results = remember { mutableStateListOf<TestPartResult>() }
    var partStart by remember { mutableStateOf(now()) }
    var lastOk by remember { mutableStateOf<Boolean?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var savedRun by remember { mutableStateOf<BenchmarkRun?>(null) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    val seed = remember { Random.nextLong() }
    val part = plan[partIndex]
    val accent = c.domain(part.domain)

    BackHandler(enabled = phase == TestPhase.PART || phase == TestPhase.TRIAL) { confirmExit = true }

    fun finishPart() {
        val level = if (part.adaptive) staircase.result() else part.startLevel
        val timed = partTrials.filter { it.timeMs > 0 }
        results += TestPartResult(
            part.domain, part.gameId, level, partTrials.sumOf { it.correct }, partTrials.sumOf { it.total },
            if (timed.isEmpty()) 0 else timed.map { it.timeMs }.average().toLong(), since(partStart),
        )
        partTrials.clear()
        if (partIndex < plan.size - 1) {
            partIndex++
            trialIndex = 0
            staircase = Staircase(plan[partIndex].startLevel)
            phase = TestPhase.PART
        } else {
            if (placement) achievements = Repo.savePlacement(results.toList()) else {
                savedRun = Repo.saveBenchmark(results.toList())
                achievements = Repo.syncAchievements(silent = false)
            }
            Feedback.complete()
            phase = TestPhase.RESULT
        }
    }

    LaunchedEffect(lastOk) {
        if (lastOk == null) return@LaunchedEffect
        delay(450)
        lastOk = null
        if (trialIndex < part.trials - 1) trialIndex++ else finishPart()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        when (phase) {
            TestPhase.INTRO -> TestIntro(placement, plan.map { it.domain to it.gameId }, onBack = { nav.pop() }) {
                partStart = now()
                phase = TestPhase.PART
            }
            TestPhase.PART -> {
                LaunchedEffect(partIndex) {
                    delay(2200)
                    if (phase == TestPhase.PART) { partStart = now(); phase = TestPhase.TRIAL }
                }
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("TEIL ${partIndex + 1} VON ${plan.size}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    Spacer(Modifier.height(16.dp))
                    DomainGlyph(part.domain, accent, Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(part.domain.title, style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                    Text(GameCatalog[part.gameId].title, style = MaterialTheme.typography.titleMedium, color = accent)
                    Spacer(Modifier.height(12.dp))
                    Text(GameCatalog[part.gameId].howTo, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Los", Modifier.fillMaxWidth(), color = accent) { partStart = now(); phase = TestPhase.TRIAL }
                }
            }
            TestPhase.TRIAL -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { confirmExit = true }) { Icon(Icons.Default.Close, "Abbrechen", tint = c.textSecondary) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${if (placement) "Einstufung" else "Leistungstest"} · ${part.domain.title}",
                            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                        )
                        Text("Aufgabe ${trialIndex + 1} / ${part.trials} · Teil ${partIndex + 1} / ${plan.size}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    }
                    lastOk?.let { Pill(if (it) "✓" else "✕", if (it) c.good else c.bad, Modifier.padding(end = 14.dp)) }
                }
                val totalTrials = plan.sumOf { it.trials }
                val doneTrials = plan.take(partIndex).sumOf { it.trials } + trialIndex
                LinearProgressIndicator(
                    progress = { doneTrials.toFloat() / totalTrials },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent, trackColor = c.gridLine,
                )
                Box(Modifier.weight(1f)) {
                    val level = if (part.adaptive) staircase.level else part.startLevel
                    key(partIndex, trialIndex) {
                        GameTrial(part.gameId, TrialContext(level, seed + partIndex * 1000L + trialIndex, zen = false, accent = accent)) { r ->
                            if (lastOk != null) return@GameTrial
                            partTrials += r
                            if (part.adaptive) staircase.record(r.success)
                            if (r.success) Feedback.correct(0) else Feedback.wrong()
                            lastOk = r.success
                        }
                    }
                }
            }
            TestPhase.RESULT -> TestResult(placement, results, savedRun, achievements) { nav.pop() }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Test abbrechen?") },
            text = { Text("Der Test wird nicht gespeichert. Du kannst ihn jederzeit neu starten.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; nav.pop() }) { Text("Abbrechen") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Weitermachen") } },
            containerColor = c.surfaceStrong, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
        )
    }
}

@Composable
private fun TestIntro(placement: Boolean, parts: List<Pair<Domain, String>>, onBack: () -> Unit, onStart: () -> Unit) {
    val c = Synapse.colors
    val accent = c.domain(Domain.LOGIK)
    val lastRun = Repo.benchmarkRuns.lastOrNull()
    Column(Modifier.fillMaxSize()) {
        com.mycompany.synapse.ui.components.ScreenHeader(
            if (placement) "Einstufungstest" else "Leistungstest", "ca. 5 Minuten · 6 Bereiche", onBack = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Text(
                    if (placement) "Die Aufgaben werden je nach Antwort schwerer oder leichter. So findet die App heraus, " +
                        "wo du stehst – und jedes Spiel startet danach im passenden Level statt bei Level 1."
                    else "Immer dieselbe Schwierigkeit, damit die Ergebnisse vergleichbar sind. Mach ihn etwa einmal im Monat – " +
                        "so siehst du schwarz auf weiß, ob du besser wirst.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                )
                if (!placement && lastRun != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Letzter Test: ${relativeDay(lastRun.run)} · ${lastRun.total} Punkte",
                        style = MaterialTheme.typography.labelLarge, color = c.textSecondary,
                    )
                }
            }
            for ((d, g) in parts) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    DomainGlyph(d, c.domain(d), Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(d.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(GameCatalog[g].title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            HintText("Tipp: in Ruhe und ausgeschlafen machen – dann sind die Werte am aussagekräftigsten.")
        }
        PrimaryButton("Test starten", Modifier.fillMaxWidth().padding(18.dp), color = accent, onClick = onStart)
    }
}

@Composable
private fun TestResult(
    placement: Boolean,
    results: List<TestPartResult>,
    run: BenchmarkRun?,
    achievements: List<Achievement>,
    onDone: () -> Unit,
) {
    val c = Synapse.colors
    val runs = Repo.benchmarkRuns
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (placement) "Eingestuft!" else "Test abgeschlossen", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
            if (placement) {
                Text(
                    "Alle Spiele eines Bereichs starten jetzt in der Nähe dieses Levels.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center,
                )
                for (r in results) {
                    GlassCard(Modifier.fillMaxWidth(), accent = c.domain(r.domain), padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DomainGlyph(r.domain, c.domain(r.domain), Modifier.size(26.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.domain.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                                Text(GameCatalog[r.gameId].levelLabel(r.level), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            }
                            Text("Level ${r.level}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        }
                    }
                }
            } else if (run != null) {
                val first = runs.firstOrNull()
                val prev = runs.dropLast(1).lastOrNull()
                Text("${run.total}", color = c.domain(Domain.LOGIK), fontSize = 72.sp, fontWeight = FontWeight.Black)
                Text("von 100 Punkten", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                if (prev != null) {
                    val d = run.total - prev.total
                    Pill(
                        (if (d >= 0) "▲ +$d" else "▼ $d") + " gegenüber dem letzten Test" + if (first != null && first != prev) " · ${run.total - first.total} seit dem ersten" else "",
                        if (d >= 0) c.good else c.bad,
                    )
                } else {
                    Text("Dein erster Leistungstest – die Basis für alle Vergleiche.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                if (first != null && first != run) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        RadarChart(
                            axes = Domain.entries.map { it.title },
                            axisColors = Domain.entries.map { c.domain(it) },
                            series = listOf(
                                RadarSeries("Erster Test", Domain.entries.map { (first.score(it) ?: 0).toFloat() }, c.textMuted, dashed = true),
                                RadarSeries("Heute", Domain.entries.map { (run.score(it) ?: 0).toFloat() }, c.domain(Domain.RECHNEN), dashed = false),
                            ),
                            maxValue = 100f,
                        )
                    }
                }
                for (p in run.parts) {
                    val before = prev?.score(p.domain)
                    GlassCard(Modifier.fillMaxWidth(), accent = c.domain(p.domain), padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DomainGlyph(p.domain, c.domain(p.domain), Modifier.size(26.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.domain.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                                Text(
                                    "${formatPercent(p.accuracy)} richtig" + if (p.avgMs > 0 && GameCatalog[p.gameId].speedMatters) " · Ø ${formatSeconds(p.avgMs)}" else "",
                                    style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${p.score}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                                if (before != null) {
                                    val d = p.score - before
                                    Text(if (d >= 0) "+$d" else "$d", color = if (d >= 0) c.good else c.bad, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Text("Nächster Test in etwa 4 Wochen – die App erinnert dich auf dem Startbildschirm.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
            }
            for (a in achievements) AchievementUnlockedCard(a)
        }
        Row(Modifier.fillMaxWidth().padding(18.dp)) {
            SecondaryButton("Fertig", Modifier.weight(1f), onClick = onDone)
        }
    }
}
