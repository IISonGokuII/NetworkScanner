package com.mycompany.synapse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Achievement
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.SessionInput
import com.mycompany.synapse.data.SessionOutcome
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameInfo
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.WorkoutEntry
import com.mycompany.synapse.ui.WorkoutRun
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.ParticleBurst
import com.mycompany.synapse.ui.components.Pill
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.components.SelectChip
import com.mycompany.synapse.ui.components.Sparkline
import com.mycompany.synapse.ui.components.StatTile
import com.mycompany.synapse.ui.games.GameTrial
import com.mycompany.synapse.ui.games.TrialContext
import com.mycompany.synapse.ui.games.now
import com.mycompany.synapse.ui.games.since
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private enum class Phase { INTRO, COUNTDOWN, PLAYING, RESULT }

private const val SPRINT_MS = 60_000L

fun formatSeconds(ms: Long): String = String.format(Locale.GERMAN, "%.1f s", ms / 1000.0)
fun formatPercent(v: Double): String = "${(v * 100).roundToInt()} %"

@Composable
fun PlayScreen(screen: Screen.Play, nav: Navigator) {
    val game = GameCatalog[screen.gameId]
    val accent = Synapse.colors.domain(game.domain)
    val workout = screen.workout
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var mode by remember { mutableStateOf(if (workout != null) GameMode.STANDARD else screen.presetMode ?: GameMode.STANDARD) }
    var outcome by remember { mutableStateOf<SessionOutcome?>(null) }
    var runId by remember { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = phase == Phase.PLAYING || phase == Phase.COUNTDOWN) { confirmExit = true }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
            label = "phase",
        ) { p ->
            when (p) {
                Phase.INTRO -> IntroView(game, mode, workout, onMode = { mode = it }, onBack = { nav.pop() }, onStart = { phase = Phase.COUNTDOWN })
                Phase.COUNTDOWN -> CountdownView(accent) { phase = Phase.PLAYING }
                Phase.PLAYING -> key(runId) {
                    SessionRunner(
                        game = game, level = Repo.level(game.id), mode = mode, accent = accent,
                        workout = workout != null, onExit = { confirmExit = true },
                        onFinish = { input ->
                            outcome = Repo.saveSession(input)
                            phase = Phase.RESULT
                        },
                    )
                }
                Phase.RESULT -> outcome?.let { o ->
                    ResultView(
                        game, o, workout,
                        onAgain = { runId++; phase = Phase.COUNTDOWN },
                        onDone = { nav.pop() },
                        onNext = {
                            val run = workout ?: return@ResultView
                            val entry = WorkoutEntry(game.id, o.record.score, o.levelBefore, o.levelAfter, o.record.accuracy)
                            val entries = run.entries + entry
                            if (run.isLast) {
                                nav.replace(Screen.WorkoutDone(entries))
                            } else {
                                nav.replace(Screen.Play(run.games[run.index + 1], run.copy(index = run.index + 1, entries = entries)))
                            }
                        },
                    )
                }
            }
        }
        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("Runde abbrechen?") },
                text = { Text("Diese Runde wird nicht gewertet. Bereits abgeschlossene Spiele bleiben gespeichert.") },
                confirmButton = { TextButton(onClick = { confirmExit = false; nav.pop() }) { Text("Abbrechen") } },
                dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Weiterspielen") } },
                containerColor = Synapse.colors.surfaceStrong,
                titleContentColor = Synapse.colors.textPrimary,
                textContentColor = Synapse.colors.textSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------- Intro

@Composable
private fun IntroView(
    game: GameInfo,
    mode: GameMode,
    workout: WorkoutRun?,
    onMode: (GameMode) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val c = Synapse.colors
    val accent = c.domain(game.domain)
    val level = Repo.level(game.id)
    val records = Repo.records
    val history = remember(records) { records.filter { it.gameId == game.id } }
    val best = history.filter { it.mode == mode }.maxOfOrNull { it.score }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = c.textPrimary) }
            if (workout != null) {
                Text(
                    "TAGESTRAINING · SPIEL ${workout.index + 1} VON ${workout.games.size}",
                    style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(104.dp)
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)), size.minDimension * 0.75f)
                    }
                    .clip(CircleShape)
                    .background(c.surfaceStrong)
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { DomainGlyph(game.domain, accent, Modifier.size(52.dp)) }
            Text(game.domain.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
            Text(game.title, style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(game.tagline, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            GlassCard(Modifier.fillMaxWidth()) {
                Text("SO GEHT'S", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                Text(game.howTo, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            }
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DEIN LEVEL", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        Text("$level", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
                        Text(game.levelLabel(level), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (history.size >= 2) Sparkline(history.takeLast(15).map { it.skill }, accent, Modifier.width(90.dp).height(40.dp))
                        Text(
                            if (best != null) "Rekord (${mode.title}): $best" else "Noch kein Rekord",
                            style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                        )
                    }
                }
            }
            if (workout == null) {
                val modes = GameMode.entries.filter { it != GameMode.SPRINT || game.supportsSprint }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in modes) SelectChip(m.title, m == mode, accent) { onMode(m) }
                }
                Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(4.dp))
        }
        PrimaryButton(
            "Los geht's", Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), color = accent, onClick = onStart,
        )
    }
}

// ---------------------------------------------------------------- Countdown

@Composable
private fun CountdownView(accent: Color, onDone: () -> Unit) {
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            n = i
            Feedback.tick()
            delay(650)
        }
        n = 0
        Feedback.go()
        delay(380)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = n,
            transitionSpec = { (scaleIn(initialScale = 1.8f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
            label = "count",
        ) { v ->
            Text(
                if (v == 0) "Los!" else "$v", color = accent, fontSize = 120.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.drawBehind {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.3f), Color.Transparent)), size.maxDimension)
                },
            )
        }
    }
}

// ---------------------------------------------------------------- Laufende Session

@Composable
private fun SessionRunner(
    game: GameInfo,
    level: Int,
    mode: GameMode,
    accent: Color,
    workout: Boolean,
    onExit: () -> Unit,
    onFinish: (SessionInput) -> Unit,
) {
    val c = Synapse.colors
    val zen = mode == GameMode.ZEN
    val sprint = mode == GameMode.SPRINT
    val startedAt = remember { System.currentTimeMillis() }
    val clock = remember { now() }
    val seedBase = remember { Random.nextLong() }
    var trial by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<TrialResult>() }
    var combo by remember { mutableIntStateOf(0) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var lastPoints by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<TrialResult?>(null) }
    var particles by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var remaining by remember { mutableLongStateOf(SPRINT_MS) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun finish() {
        if (finished) return
        finished = true
        val timed = results.filter { it.timeMs > 0 }
        onFinish(
            SessionInput(
                gameId = game.id, mode = mode, startedAt = startedAt, durationMs = since(clock), level = level,
                correct = results.sumOf { it.correct }, total = results.sumOf { it.total },
                avgMs = if (timed.isEmpty()) 0L else timed.map { it.timeMs }.average().toLong(),
                score = score, bestCombo = bestCombo, trials = results.size, workout = workout,
            )
        )
    }

    if (sprint) {
        LaunchedEffect(Unit) {
            while (!finished) {
                remaining = (SPRINT_MS - since(clock)).coerceAtLeast(0)
                if (remaining <= 0) {
                    if (results.isEmpty()) results += TrialResult(0, 1, 0)
                    finish()
                }
                delay(100)
            }
        }
    }

    fun onTrial(r: TrialResult) {
        if (feedback != null || finished) return
        results += r
        if (r.success) {
            combo++
            bestCombo = maxOf(bestCombo, combo)
            particles++
            Feedback.correct((combo - 1) / 3)
        } else {
            combo = 0
            Feedback.wrong()
            scope.launch {
                for (x in listOf(22f, -18f, 14f, -9f, 5f, 0f)) shake.animateTo(x, tween(45))
            }
        }
        val base = 10 + level * 2
        val speed = if (zen || !game.speedMatters || r.timeMs <= 0) 1.0
        else (game.targetMs(level).toDouble() / r.timeMs).coerceIn(0.6, 1.8)
        val mult = 1.0 + min(combo, 20) * 0.05
        lastPoints = (base * r.fraction * speed * mult).roundToInt()
        score += lastPoints
        feedback = r
    }

    LaunchedEffect(feedback) {
        val fb = feedback ?: return@LaunchedEffect
        delay(if (fb.success) 450L else if (fb.solution != null) 1900L else 850L)
        if (finished) return@LaunchedEffect
        if (!sprint && results.size >= game.trials) finish()
        else {
            trial++
            feedback = null
        }
    }

    val glow by animateFloatAsState(0.03f + min(combo, 15) * 0.02f, tween(500), label = "glow")
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = glow), Color.Transparent), Offset(size.width / 2, size.height * 0.4f), size.maxDimension * 0.7f),
                    size.maxDimension * 0.7f, Offset(size.width / 2, size.height * 0.4f),
                )
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            // Kopfzeile
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Abbrechen", tint = c.textSecondary) }
                Column(Modifier.weight(1f)) {
                    Text(game.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                    Text(
                        when {
                            sprint -> "Sprint · Level $level"
                            else -> "${(results.size + if (feedback == null) 1 else 0).coerceAtMost(game.trials)} / ${game.trials} · Level $level"
                        },
                        style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                    )
                }
                if (sprint) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 4.dp.toPx()
                            drawArc(c.gridLine, -90f, 360f, false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
                            drawArc(
                                if (remaining < 10_000) c.bad else accent, -90f, 360f * remaining / SPRINT_MS, false,
                                Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                                style = Stroke(w, cap = StrokeCap.Round),
                            )
                        }
                        Text("${(remaining + 999) / 1000}", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
                    Text("PUNKTE", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    AnimatedContent(targetState = score, transitionSpec = {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }, label = "score") { s ->
                        Text("$s", color = c.textPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }
            }
            if (!sprint && !zen) {
                LinearProgressIndicator(
                    progress = { results.size.toFloat() / game.trials },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent, trackColor = c.gridLine,
                )
            }
            FlowMeter(combo, accent)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shake.value }
            ) {
                key(trial) {
                    GameTrial(game.id, TrialContext(level, seedBase + trial * 7919L, zen, accent), ::onTrial)
                }
            }
        }

        ParticleBurst(particles, accent, Modifier.fillMaxSize())

        // Rückmeldung
        val fb = feedback
        if (fb != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
            )
        }
        AnimatedVisibility(
            visible = fb != null && fb.success,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = fadeOut(),
        ) {
            Pill("✓  +$lastPoints", c.good)
        }
        AnimatedVisibility(
            visible = fb != null && !fb.success,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            GlassCard(Modifier.fillMaxWidth(), accent = c.bad) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✕", color = c.bad, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if ((fb?.fraction ?: 0.0) > 0.0) "Teilweise richtig" else "Nicht ganz",
                            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                        )
                        fb?.solution?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary) }
                    }
                }
            }
        }
    }
}

/** Serienanzeige: mit jeder richtigen Antwort in Folge steigt der Multiplikator. */
@Composable
private fun FlowMeter(combo: Int, accent: Color) {
    val c = Synapse.colors
    val fill by animateFloatAsState(min(combo, 20) / 20f, tween(350), label = "flow")
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (combo >= 5) "FLOW" else "SERIE", style = MaterialTheme.typography.labelSmall,
            color = if (combo >= 5) accent else c.textMuted, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).height(6.dp)) {
            drawRoundRect(c.gridLine, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            if (fill > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent)),
                    size = androidx.compose.ui.geometry.Size(size.width * fill, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$combo · ×${String.format(Locale.GERMAN, "%.2f", 1.0 + min(combo, 20) * 0.05)}",
            style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
        )
    }
}

// ---------------------------------------------------------------- Ergebnis

@Composable
private fun ResultView(
    game: GameInfo,
    o: SessionOutcome,
    workout: WorkoutRun?,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    onNext: () -> Unit,
) {
    val c = Synapse.colors
    val accent = c.domain(game.domain)
    val r = o.record
    val shownScore = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (o.levelAfter > o.levelBefore) Feedback.levelUp() else Feedback.complete()
        shownScore.animateTo(r.score.toFloat(), tween(1100))
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    r.accuracy >= 0.9 -> "Stark!"
                    r.accuracy >= 0.7 -> "Gut gemacht!"
                    r.accuracy >= 0.5 -> "Weiter so!"
                    else -> "Dranbleiben!"
                },
                style = MaterialTheme.typography.headlineLarge, color = c.textPrimary,
            )
            Text("${game.title} · ${r.mode.title}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(
                "${shownScore.value.roundToInt()}", color = accent, fontSize = 72.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.drawBehind {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)), size.maxDimension * 0.8f)
                },
            )
            when {
                o.newBestScore -> Pill("★  Neuer Rekord!  (vorher ${o.previousBestScore})", Color(0xFFFBBF24))
                o.previousBestScore > 0 -> Text("Rekord: ${o.previousBestScore}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                else -> Text("Erster Rekord gesetzt", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Trefferquote", formatPercent(r.accuracy), Modifier.weight(1f))
                StatTile(
                    if (game.id == "schulte") "Ø pro Tafel" else "Ø Zeit",
                    if (r.avgMs > 0 && game.speedMatters) formatSeconds(r.avgMs) else "–", Modifier.weight(1f),
                )
                StatTile("Beste Serie", "${r.bestCombo}", Modifier.weight(1f))
            }
            LevelCard(game, o, accent)
            ComparisonCard(o)
            if (o.history.size >= 2) {
                GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                    Text("DEIN VERLAUF IN DIESEM SPIEL", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    Spacer(Modifier.height(10.dp))
                    Sparkline(o.history, accent, Modifier.fillMaxWidth().height(56.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Leistungswert der letzten ${o.history.size} Runden (Level + Trefferquote)",
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                    )
                }
            }
            if (o.newAchievements.isNotEmpty()) {
                for (a in o.newAchievements) AchievementUnlockedCard(a)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (workout != null) {
                PrimaryButton(
                    if (workout.isLast) "Training abschließen" else "Weiter · Spiel ${workout.index + 2}/${workout.games.size}",
                    Modifier.weight(1f), color = accent, onClick = onNext,
                )
            } else {
                SecondaryButton("Fertig", Modifier.weight(1f), onClick = onDone)
                PrimaryButton("Nochmal", Modifier.weight(1f), color = accent, onClick = onAgain)
            }
        }
    }
}

@Composable
private fun LevelCard(game: GameInfo, o: SessionOutcome, accent: Color) {
    val c = Synapse.colors
    val up = o.levelAfter > o.levelBefore
    val down = o.levelAfter < o.levelBefore
    val arrowColor = when {
        up -> c.good
        down -> Color(0xFFFB923C)
        else -> c.textSecondary
    }
    GlassCard(Modifier.fillMaxWidth(), accent = if (up) c.good else accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LEVEL", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${o.levelBefore}", style = MaterialTheme.typography.headlineLarge, color = c.textSecondary)
                    Text(
                        when {
                            up -> "  ▲  "
                            down -> "  ▼  "
                            else -> "  →  "
                        },
                        color = arrowColor, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    )
                    Text("${o.levelAfter}", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                }
                Text(
                    when {
                        up && o.levelAfter - o.levelBefore >= 2 -> "Doppelter Aufstieg – schnell und fehlerfrei!"
                        up -> "Aufgestiegen! Nächstes Mal wird's etwas kniffliger."
                        down -> "Etwas leichter, damit du im Lernbereich bleibst – das ist gewollt."
                        else -> "Level gehalten. Ab 85 % Trefferquote geht's hoch."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(game.levelLabel(o.levelAfter), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    }
}

@Composable
private fun ComparisonCard(o: SessionOutcome) {
    val c = Synapse.colors
    val lines = mutableListOf<Pair<String, Color>>()
    o.speedVsAverage?.let { ratio ->
        val pct = ((1 - ratio) * 100).roundToInt()
        if (pct >= 2) lines += "⚡ $pct % schneller als dein Schnitt" to c.good
        else if (pct <= -2) lines += "${abs(pct)} % langsamer als dein Schnitt" to c.textSecondary
        else lines += "Tempo wie dein Schnitt" to c.textSecondary
    }
    o.accuracyVsAverage?.let { d ->
        val pts = (d * 100).roundToInt()
        if (pts >= 2) lines += "🎯 +$pts Prozentpunkte Trefferquote gegenüber deinem Schnitt" to c.good
        else if (pts <= -2) lines += "$pts Prozentpunkte Trefferquote gegenüber deinem Schnitt" to c.textSecondary
    }
    val delta = o.indexAfter - o.indexBefore
    lines += when {
        delta > 0 -> "Gehirn-Index ${o.indexBefore} → ${o.indexAfter} (+$delta)" to c.good
        delta < 0 -> "Gehirn-Index ${o.indexBefore} → ${o.indexAfter} ($delta)" to c.textSecondary
        else -> "Gehirn-Index bleibt bei ${o.indexAfter} – er glättet über mehrere Runden" to c.textSecondary
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("IM VERGLEICH", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        for ((text, col) in lines) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = col, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
fun AchievementUnlockedCard(a: Achievement) {
    val c = Synapse.colors
    val gold = Color(0xFFFBBF24)
    GlassCard(Modifier.fillMaxWidth(), accent = gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AchievementBadge(a, unlocked = true, size = 48)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("ERFOLG FREIGESCHALTET", style = MaterialTheme.typography.labelSmall, color = gold)
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(a.description, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------- Tagestraining fertig

@Composable
fun WorkoutDoneScreen(entries: List<WorkoutEntry>, nav: Navigator) {
    val c = Synapse.colors
    val streak = remember { com.mycompany.synapse.data.Stats.streak(Repo.trainedDays, java.time.LocalDate.now()) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        Feedback.complete()
        ring.animateTo(1f, tween(1200))
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 12.dp.toPx()
                    val n = entries.size.coerceAtLeast(1)
                    val gap = 10f
                    val sweep = 360f / n - gap
                    drawCircle(Brush.radialGradient(listOf(c.good.copy(alpha = 0.35f * ring.value), Color.Transparent)), size.minDimension * 0.6f)
                    entries.forEachIndexed { i, e ->
                        val col = c.domain(GameCatalog[e.gameId].domain)
                        drawArc(
                            col, -90f + gap / 2 + i * (sweep + gap), sweep * ring.value, false,
                            Offset(w, w), androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                            style = Stroke(w, cap = StrokeCap.Round),
                        )
                    }
                }
                Text("✓", color = c.good, fontSize = 64.sp, fontWeight = FontWeight.Black)
            }
            Text("Tagestraining geschafft!", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, textAlign = TextAlign.Center)
            Text(
                if (streak.current > 1) "🔥 ${streak.current} Tage in Folge – stark!" else "Der erste Tag deiner Serie 🔥",
                style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
            )
            for (e in entries) {
                val g = GameCatalog[e.gameId]
                val col = c.domain(g.domain)
                GlassCard(Modifier.fillMaxWidth(), accent = col) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DomainGlyph(g.domain, col, Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Text("${e.score} Punkte · ${formatPercent(e.accuracy)}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                        Text(
                            if (e.levelAfter > e.levelBefore) "Lvl ${e.levelBefore} ▲ ${e.levelAfter}" else "Lvl ${e.levelAfter}",
                            color = if (e.levelAfter > e.levelBefore) c.good else c.textSecondary, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Text(
                "Tipp: Schlaf, Bewegung und Abwechslung verstärken den Trainingseffekt.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Fortschritt", Modifier.weight(1f)) { nav.tab(Screen.Progress) }
            PrimaryButton("Zum Netz", Modifier.weight(1f), color = c.good) { nav.home() }
        }
    }
}
