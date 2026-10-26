package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.Stats
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameInfo
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.WorkoutRun
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.LevelBadge
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.SectionTitle
import com.mycompany.synapse.ui.components.Sparkline
import com.mycompany.synapse.ui.theme.Synapse
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun startWorkout(nav: Navigator) {
    Repo.ensureWorkout()
    val remaining = Repo.workoutRemaining
    if (remaining.isEmpty()) {
        // Bonusrunde: ein zufälliges Spiel aus dem schwächsten Bereich
        val weakest = Domain.entries.minBy { Repo.domainIndices[it] ?: 0 }
        nav.push(Screen.Play(GameCatalog.ofDomain(weakest).random().id))
        return
    }
    nav.push(Screen.Play(remaining.first(), WorkoutRun(remaining, 0, emptyList())))
}

@Composable
fun HomeScreen(nav: Navigator) {
    val c = Synapse.colors
    LaunchedEffect(Unit) { Repo.ensureWorkout() }
    val records = Repo.records
    val vitality = remember(records) { Stats.vitality(records, System.currentTimeMillis()) }
    val streak = remember(records) { Stats.streak(Repo.trainedDays, LocalDate.now()) }
    val plan = Repo.workoutPlan
    val done = Repo.workoutDone
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 110.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val hour = LocalTime.now().hour
                Text(
                    when {
                        hour < 11 -> "Guten Morgen"
                        hour < 18 -> "Hallo"
                        else -> "Guten Abend"
                    },
                    style = MaterialTheme.typography.headlineLarge, color = c.textPrimary,
                )
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            StreakChip(streak.current, streak.trainedToday, streak.jokerUsedThisWeek)
        }

        NeuralNetwork(
            vitality = vitality,
            indices = Repo.domainIndices,
            workoutColors = plan.map { c.domain(GameCatalog[it].domain) },
            workoutDone = plan.map { it in done },
            modifier = Modifier.fillMaxWidth().aspectRatio(0.98f).padding(horizontal = 6.dp),
            onDomain = { nav.push(Screen.DomainDetail(it)) },
            onCenter = { startWorkout(nav) },
        )

        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutCard(plan, done, onStart = { startWorkout(nav) })
            IndexCard(onClick = { nav.tab(Screen.Progress) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Sprint", "60 Sek.", c.domain(Domain.FOKUS), Modifier.weight(1f)) {
                    val g = GameCatalog.all.filter { it.supportsSprint }.random()
                    nav.push(Screen.Play(g.id, presetMode = GameMode.SPRINT))
                }
                QuickAction("Duell", "zu zweit", c.domain(Domain.SPRACHE), Modifier.weight(1f)) { nav.push(Screen.Duel) }
                QuickAction("Alle", "${GameCatalog.all.size} Spiele", c.domain(Domain.LOGIK), Modifier.weight(1f)) { nav.push(Screen.AllGames) }
            }
            SuggestionCard(vitality, nav)
        }
    }
}

@Composable
private fun StreakChip(days: Int, trainedToday: Boolean, joker: Boolean) {
    val c = Synapse.colors
    val flame = if (days > 0) Color(0xFFFB923C) else c.textMuted
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, flame.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 0.95f, h * 0.35f, w, h * 0.7f, w * 0.5f, h)
                cubicTo(0f, h * 0.7f, w * 0.05f, h * 0.4f, w * 0.32f, h * 0.22f)
                cubicTo(w * 0.32f, h * 0.45f, w * 0.45f, h * 0.5f, w * 0.5f, 0f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFACC15), flame)))
        }
        Spacer(Modifier.width(6.dp))
        Text("$days", color = c.textPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                days == 0 -> "Serie"
                !trainedToday -> "Tage · heute offen"
                joker -> "Tage · Joker genutzt"
                else -> "Tage"
            },
            color = c.textSecondary, style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun WorkoutCard(plan: List<String>, done: Set<String>, onStart: () -> Unit) {
    val c = Synapse.colors
    val complete = plan.isNotEmpty() && plan.all { it in done }
    GlassCard(Modifier.fillMaxWidth(), accent = if (complete) c.good else c.domain(Domain.RECHNEN), onClick = onStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (complete) "Tagestraining geschafft" else "Dein Tagestraining",
                    style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                )
                Text(
                    if (complete) "Tippe für eine Bonusrunde im schwächsten Bereich"
                    else "${plan.size} Spiele · ca. ${plan.size * 2 + 1} Minuten · passt sich dir an",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            Text(if (complete) "✓" else "▶", color = if (complete) c.good else c.domain(Domain.RECHNEN), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in plan) {
                val g = GameCatalog[id]
                val col = c.domain(g.domain)
                val isDone = id in done
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(col.copy(alpha = if (isDone) 0.22f else 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DomainGlyph(g.domain, col, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(g.title, color = c.textPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (isDone) "✓ erledigt" else "Level ${Repo.level(id)}",
                        color = if (isDone) c.good else c.textMuted, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexCard(onClick: () -> Unit) {
    val c = Synapse.colors
    val timeline = Repo.timeline
    val index = Repo.overallIndex
    val weekAgo = Stats.indexAt(timeline, LocalDate.now().minusDays(7))?.overall
    val delta = if (timeline.isNotEmpty()) index - (weekAgo ?: timeline.first().overall) else null
    GlassCard(Modifier.fillMaxWidth(), accent = c.domain(Domain.LOGIK), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GEHIRN-INDEX", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$index", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    if (delta != null) {
                        Text(
                            (if (delta >= 0) "▲ +$delta" else "▼ $delta") + " in 7 Tagen",
                            color = if (delta >= 0) c.good else c.bad, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                Text(
                    if (timeline.isEmpty()) "Starte ein Training – hier siehst du, wie du besser wirst."
                    else "Mittel deiner Bereiche · tippe für alle Details",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            if (timeline.size >= 2) {
                Sparkline(timeline.takeLast(30).map { it.overall.toDouble() }, c.domain(Domain.LOGIK), Modifier.width(96.dp).height(48.dp))
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Synapse.colors
    GlassCard(modifier, accent = color, onClick = onClick, padding = 14.dp) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
private fun SuggestionCard(vitality: Map<Domain, Float>, nav: Navigator) {
    val c = Synapse.colors
    val sleepy = Domain.entries.minBy { vitality[it] ?: 0f }
    val col = c.domain(sleepy)
    GlassCard(Modifier.fillMaxWidth(), accent = col, onClick = { nav.push(Screen.DomainDetail(sleepy)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DomainGlyph(sleepy, col, Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Weckruf für ${sleepy.title}", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    "Dieser Knoten leuchtet gerade am schwächsten. Abwechslung hält das Gehirn flexibel.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Bereich & Spieleliste

@Composable
fun GameCard(game: GameInfo, nav: Navigator) {
    val c = Synapse.colors
    val col = c.domain(game.domain)
    val level = Repo.level(game.id)
    val records = Repo.records
    val summary = remember(records, game.id) { Stats.gameSummary(game.id, records) }
    GlassCard(Modifier.fillMaxWidth(), accent = col, onClick = { nav.push(Screen.Play(game.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DomainGlyph(game.domain, col, Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(game.tagline, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                LevelBadge(level, col)
                if (summary.skillSeries.size >= 2) {
                    Spacer(Modifier.height(6.dp))
                    Sparkline(summary.skillSeries.takeLast(20), col, Modifier.width(64.dp).height(22.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(game.levelLabel(level), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (summary.sessions == 0) "Noch nicht gespielt"
                else "${summary.sessions}× gespielt · Rekord ${summary.bestScore} · Level ${summary.firstLevel} → $level",
                style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f),
            )
            if (summary.sessions > 0) {
                Text(
                    "Statistik ›", color = col, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { nav.push(Screen.GameStats(game.id)) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun DomainScreen(domain: Domain, nav: Navigator) {
    val c = Synapse.colors
    val col = c.domain(domain)
    val idx = Repo.domainIndices[domain]
    val records = Repo.records
    val last = remember(records) { records.filter { it.domain == domain }.maxOfOrNull { it.startedAt } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(domain.title, domain.subtitle, onBack = { nav.pop() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth(), accent = col) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DomainGlyph(domain, col, Modifier.size(54.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("BEREICHS-INDEX", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        Text(idx?.toString() ?: "–", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
                        Text(
                            last?.let { "Zuletzt trainiert: ${relativeDay(it)}" } ?: "Noch nicht trainiert",
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        )
                    }
                }
            }
            SectionTitle("Spiele")
            for (g in GameCatalog.ofDomain(domain)) GameCard(g, nav)
        }
    }
}

@Composable
fun AllGamesScreen(nav: Navigator) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Alle Spiele", "${GameCatalog.all.size} Spiele in 6 Bereichen", onBack = { nav.pop() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (d in Domain.entries) {
                SectionTitle(d.title)
                for (g in GameCatalog.ofDomain(d)) GameCard(g, nav)
            }
        }
    }
}

fun relativeDay(millis: Long): String {
    val day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return when (val d = Stats.daysBetween(day, LocalDate.now())) {
        0L -> "heute"
        1L -> "gestern"
        else -> "vor $d Tagen"
    }
}
