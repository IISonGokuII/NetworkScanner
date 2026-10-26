package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.data.GameSummary
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.Stats
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.components.ChartPoint
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.LevelBadge
import com.mycompany.synapse.ui.components.LineChart
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.RadarChart
import com.mycompany.synapse.ui.components.RadarSeries
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.SectionTitle
import com.mycompany.synapse.ui.components.SelectChip
import com.mycompany.synapse.ui.components.Sparkline
import com.mycompany.synapse.ui.components.StatTile
import com.mycompany.synapse.ui.components.TrainingHeatmap
import com.mycompany.synapse.ui.theme.Synapse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(nav: Navigator) {
    val c = Synapse.colors
    val records = Repo.records
    val timeline = Repo.timeline
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Dein Fortschritt", "So entwickelt sich deine Denkleistung")
        if (records.isEmpty()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(Modifier.fillMaxWidth(), accent = c.domain(Domain.LOGIK)) {
                    Text("Noch keine Daten", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sobald du trainierst, siehst du hier deinen Gehirn-Index, dein Profil über alle Bereiche, " +
                            "deine Level-Kurven, Reaktionszeiten und einen Trainingskalender.",
                        style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
                    )
                }
                PrimaryButton("Erstes Training starten", Modifier.fillMaxWidth()) { nav.home(); startWorkout(nav) }
            }
            return
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroIndex(timeline.lastOrNull()?.overall ?: 0, timeline.firstOrNull()?.overall ?: 0, timeline, today)
            if (Repo.pendingDomains.isNotEmpty()) {
                Text(
                    "In Einstufung: ${Repo.pendingDomains.joinToString { it.title }} – zählt ab der zweiten Runde (oder nach dem Einstufungstest) zum Index.",
                    style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            BenchmarkSection(nav)
            IndexHistory(timeline, today)
            ProfileRadar(timeline, today)
            WeekCompare(today)
            val lastWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.WeekReviewScreen(lastWeek.toEpochDay())) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wochenrückblick", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(weekLabel(lastWeek), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    }
                    Text("Ansehen ›", style = MaterialTheme.typography.labelLarge, color = c.domain(Domain.RECHNEN))
                }
            }
            SectionTitle("Trainingskalender")
            GlassCard(Modifier.fillMaxWidth()) {
                val minutes = remember(records) { Stats.minutesPerDay(records) }
                TrainingHeatmap(minutes, today)
            }
            Highlights(nav)
            SectionTitle("Alle Spiele")
            GameProgressList(nav)
            Totals()
        }
    }
}

@Composable
private fun HeroIndex(current: Int, first: Int, timeline: List<com.mycompany.synapse.data.IndexPoint>, today: LocalDate) {
    val c = Synapse.colors
    val weekAgo = Stats.indexAt(timeline, today.minusDays(7))?.overall ?: first
    val monthAgo = Stats.indexAt(timeline, today.minusDays(30))?.overall ?: first
    GlassCard(Modifier.fillMaxWidth(), accent = c.domain(Domain.LOGIK)) {
        Text("GEHIRN-INDEX", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Text("$current", style = MaterialTheme.typography.displayLarge, color = c.textPrimary)
        Text(
            "Skala 0–1000 · steigt mit deinem Level und deiner Trefferquote in allen Bereichen",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeltaTile("7 Tage", current - weekAgo, Modifier.weight(1f))
            DeltaTile("30 Tage", current - monthAgo, Modifier.weight(1f))
            DeltaTile("Seit Start", current - first, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeltaTile(label: String, delta: Int, modifier: Modifier) {
    val c = Synapse.colors
    StatTile(
        label, (if (delta > 0) "+" else "") + delta, modifier,
        hint = when {
            delta > 0 -> "▲ besser"
            delta < 0 -> "▼ niedriger"
            else -> "± gleich"
        },
        hintColor = when {
            delta > 0 -> c.good
            delta < 0 -> Color(0xFFFB923C)
            else -> c.textMuted
        },
    )
}

@Composable
private fun IndexHistory(timeline: List<com.mycompany.synapse.data.IndexPoint>, today: LocalDate) {
    val c = Synapse.colors
    var domain by rememberSaveable { mutableStateOf(-1) }
    var range by rememberSaveable { mutableStateOf(1) } // 0 = 30 Tage, 1 = 90 Tage, 2 = alles
    val from = when (range) {
        0 -> today.minusDays(30)
        1 -> today.minusDays(90)
        else -> LocalDate.MIN
    }
    val color = if (domain < 0) c.domain(Domain.LOGIK) else c.domain(Domain.entries[domain])
    val points = timeline.filter { !it.day.isBefore(from) }.mapNotNull { p ->
        val v = if (domain < 0) p.overall else p.domains[Domain.entries[domain]] ?: return@mapNotNull null
        ChartPoint(p.day, v.toFloat())
    }
    SectionTitle("Verlauf")
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Gesamt", domain < 0, c.domain(Domain.LOGIK)) { domain = -1 }
            for (d in Domain.entries) SelectChip(d.title, domain == d.ordinal, c.domain(d)) { domain = d.ordinal }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (domain < 0) "Gehirn-Index" else "${Domain.entries[domain].title}-Index",
            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
        )
        LineChart(points, color)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("30 Tage", "90 Tage", "Alles").forEachIndexed { i, s -> SelectChip(s, range == i, color) { range = i } }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Finger auf die Kurve legen und verschieben, um einzelne Tage zu sehen.",
            style = MaterialTheme.typography.labelMedium, color = c.textMuted,
        )
    }
}

@Composable
private fun ProfileRadar(timeline: List<com.mycompany.synapse.data.IndexPoint>, today: LocalDate) {
    val c = Synapse.colors
    val now = timeline.lastOrNull()?.domains.orEmpty()
    val past = Stats.indexAt(timeline, today.minusDays(30))?.domains ?: timeline.firstOrNull()?.domains.orEmpty()
    val pastLabel = if (Stats.indexAt(timeline, today.minusDays(30)) != null) "Vor 30 Tagen" else "Zu Beginn"
    val axes = Domain.entries
    val max = ((listOf(now.values.maxOrNull() ?: 0, past.values.maxOrNull() ?: 0).max() * 1.25f).coerceAtLeast(100f))
    SectionTitle("Gehirnprofil")
    GlassCard(Modifier.fillMaxWidth()) {
        RadarChart(
            axes = axes.map { it.title },
            axisColors = axes.map { c.domain(it) },
            series = listOf(
                RadarSeries(pastLabel, axes.map { (past[it] ?: 0).toFloat() }, c.textMuted, dashed = true),
                RadarSeries("Jetzt", axes.map { (now[it] ?: 0).toFloat() }, c.domain(Domain.RECHNEN), dashed = false),
            ),
            maxValue = max,
        )
        Spacer(Modifier.height(10.dp))
        val strongest = now.maxByOrNull { it.value }?.key
        val weakest = axes.minBy { now[it] ?: -1 }
        if (strongest != null) {
            Text("Stärkster Bereich: ${strongest.title} (${now[strongest]})", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Text(
            if (now[weakest] == null) "Noch nicht trainiert: ${weakest.title} – probiere es aus!"
            else "Ausbaufähig: ${weakest.title} (${now[weakest]})",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
        )
    }
}

@Composable
private fun WeekCompare(today: LocalDate) {
    val c = Synapse.colors
    val records = Repo.records
    val timeline = Repo.timeline
    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = remember(records) { Stats.week(records, timeline, thisWeekStart) }
    val lastWeek = remember(records) { Stats.week(records, timeline, thisWeekStart.minusWeeks(1)) }
    SectionTitle("Diese Woche vs. letzte Woche")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CompareTile("Sessions", "${thisWeek.sessions}", thisWeek.sessions - lastWeek.sessions, "${lastWeek.sessions}", Modifier.weight(1f))
        CompareTile("Minuten", "${thisWeek.minutes}", thisWeek.minutes - lastWeek.minutes, "${lastWeek.minutes}", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val accNow = (thisWeek.accuracy * 100).roundToInt()
        val accLast = (lastWeek.accuracy * 100).roundToInt()
        CompareTile(
            "Trefferquote", if (thisWeek.sessions > 0) "$accNow %" else "–",
            if (thisWeek.sessions > 0 && lastWeek.sessions > 0) accNow - accLast else null, if (lastWeek.sessions > 0) "$accLast %" else "–",
            Modifier.weight(1f),
        )
        CompareTile(
            "Index-Zuwachs", thisWeek.indexChange?.let { (if (it > 0) "+" else "") + it } ?: "–",
            null, lastWeek.indexChange?.let { (if (it > 0) "+" else "") + it } ?: "–", Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompareTile(label: String, value: String, delta: Int?, previous: String, modifier: Modifier) {
    val c = Synapse.colors
    StatTile(
        label, value, modifier,
        hint = "Vorwoche: $previous" + when {
            delta == null || delta == 0 -> ""
            delta > 0 -> "  ▲"
            else -> "  ▼"
        },
        hintColor = when {
            delta == null || delta == 0 -> c.textMuted
            delta > 0 -> c.good
            else -> Color(0xFFFB923C)
        },
    )
}

@Composable
private fun Highlights(nav: Navigator) {
    val c = Synapse.colors
    val records = Repo.records
    val summaries = remember(records) { GameCatalog.all.map { Stats.gameSummary(it.id, records) }.filter { it.sessions > 0 } }
    if (summaries.isEmpty()) return
    val climber = summaries.maxByOrNull { Repo.level(it.gameId) - it.firstLevel }
    val faster = summaries.filter { it.firstAvgMs != null && it.recentAvgMs != null }
        .maxByOrNull { 1.0 - it.recentAvgMs!!.toDouble() / it.firstAvgMs!! }
    SectionTitle("Highlights")
    if (climber != null && Repo.level(climber.gameId) > climber.firstLevel) {
        val g = GameCatalog[climber.gameId]
        HighlightCard(
            g.domain, "Größter Aufstieg", "${g.title}: Level ${climber.firstLevel} → ${Repo.level(g.id)}",
        ) { nav.push(Screen.GameStats(g.id)) }
    }
    if (faster != null) {
        val pct = ((1.0 - faster.recentAvgMs!!.toDouble() / faster.firstAvgMs!!) * 100).roundToInt()
        if (pct > 0) {
            val g = GameCatalog[faster.gameId]
            HighlightCard(g.domain, "Schneller geworden", "${g.title}: $pct % schnellere Antworten als zu Beginn") {
                nav.push(Screen.GameStats(g.id))
            }
        }
    }
    val bestCombo = records.maxByOrNull { it.bestCombo }
    if (bestCombo != null && bestCombo.bestCombo >= 3) {
        val g = GameCatalog[bestCombo.gameId]
        HighlightCard(g.domain, "Längste Serie", "${bestCombo.bestCombo} richtige Antworten in Folge (${g.title})") {
            nav.push(Screen.GameStats(g.id))
        }
    }
}

@Composable
private fun HighlightCard(domain: Domain, title: String, text: String, onClick: () -> Unit) {
    val c = Synapse.colors
    val col = c.domain(domain)
    GlassCard(Modifier.fillMaxWidth(), accent = col, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DomainGlyph(domain, col, Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun GameProgressList(nav: Navigator) {
    val c = Synapse.colors
    val records = Repo.records
    val summaries = remember(records) { GameCatalog.all.associate { it.id to Stats.gameSummary(it.id, records) } }
    for (g in GameCatalog.all) {
        val s: GameSummary = summaries.getValue(g.id)
        val col = c.domain(g.domain)
        GlassCard(Modifier.fillMaxWidth(), accent = col, onClick = { nav.push(Screen.GameStats(g.id)) }, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DomainGlyph(g.domain, col, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        if (s.sessions == 0) "Noch nicht gespielt"
                        else "${s.sessions}× · Ø ${(s.avgAccuracy * 100).roundToInt()} % · Level ${s.firstLevel} → ${Repo.level(g.id)}",
                        style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                    )
                }
                if (s.skillSeries.size >= 2) {
                    Sparkline(s.skillSeries.takeLast(20), col, Modifier.width(64.dp).height(26.dp))
                    Spacer(Modifier.width(10.dp))
                }
                LevelBadge(Repo.level(g.id), col)
            }
        }
    }
}

@Composable
private fun Totals() {
    val c = Synapse.colors
    val records = Repo.records
    val streak = remember(records) { Stats.streak(Repo.trainedDays, LocalDate.now()) }
    val minutes = (records.sumOf { it.durationMs } / 60_000).toInt()
    val correct = records.sumOf { it.correct }
    SectionTitle("Gesamt")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Sessions", "${records.size}", Modifier.weight(1f))
        StatTile("Trainingszeit", if (minutes >= 60) "${minutes / 60} h ${minutes % 60} m" else "$minutes Min.", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Richtige Antworten", "$correct", Modifier.weight(1f))
        StatTile("Beste Serie (Tage)", "${streak.best}", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Trainingstage", "${Repo.trainedDays.size}", Modifier.weight(1f))
        StatTile("Sprints", "${records.count { it.mode == GameMode.SPRINT }}", Modifier.weight(1f))
    }
    Text(
        "Ehrlich gesagt: Training verbessert vor allem die geübten Aufgaben. Abwechslung, Regelmäßigkeit, " +
            "Schlaf und Bewegung machen den größten Unterschied.",
        style = MaterialTheme.typography.bodyMedium, color = c.textMuted, fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// ---------------------------------------------------------------- Spiel-Statistik

@Composable
fun GameStatsScreen(gameId: String, nav: Navigator) {
    val c = Synapse.colors
    val g = GameCatalog[gameId]
    val col = c.domain(g.domain)
    val records = Repo.records
    val list = remember(records) { records.filter { it.gameId == gameId }.sortedBy { it.startedAt } }
    val s = remember(records) { Stats.gameSummary(gameId, records) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(g.title, g.domain.title, onBack = { nav.pop() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Level", "${Repo.level(gameId)}", Modifier.weight(1f), hint = "Start: ${s.firstLevel}")
                StatTile("Bestes Level", "${s.bestLevel}", Modifier.weight(1f))
                StatTile("Sessions", "${s.sessions}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Rekord", "${s.bestScore}", Modifier.weight(1f))
                StatTile("Ø Treffer", formatPercent(s.avgAccuracy), Modifier.weight(1f))
                val speedHint = if (s.firstAvgMs != null && s.recentAvgMs != null) {
                    val pct = ((1.0 - s.recentAvgMs.toDouble() / s.firstAvgMs) * 100).roundToInt()
                    if (pct >= 0) "$pct % schneller" else "${-pct} % langsamer"
                } else null
                StatTile(
                    "Tempo", s.recentAvgMs?.let { formatSeconds(it) } ?: s.firstAvgMs?.let { formatSeconds(it) } ?: "–",
                    Modifier.weight(1f), hint = speedHint, hintColor = if (speedHint?.contains("schneller") == true) c.good else null,
                )
            }
            if (list.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Noch keine Runden in diesem Spiel.", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
                PrimaryButton("Jetzt spielen", Modifier.fillMaxWidth(), color = col) { nav.replace(Screen.Play(gameId)) }
                return@Column
            }
            SectionTitle("Level-Verlauf")
            GlassCard(Modifier.fillMaxWidth()) {
                LineChart(dailyLast(list) { it.newLevel.toFloat() }, col, fixedMin = 0f, format = { "${it.roundToInt()}" })
            }
            SectionTitle("Trefferquote")
            GlassCard(Modifier.fillMaxWidth()) {
                LineChart(dailyAvg(list) { it.accuracy.toFloat() * 100f }, col, fixedMin = 0f, fixedMax = 100f, ticks = 4, format = { "${it.roundToInt()} %" })
            }
            if (g.speedMatters && list.any { it.avgMs > 0 && it.mode != GameMode.ZEN }) {
                SectionTitle("Antwortzeit (niedriger ist besser)")
                GlassCard(Modifier.fillMaxWidth()) {
                    LineChart(
                        dailyAvg(list.filter { it.avgMs > 0 && it.mode != GameMode.ZEN }) { it.avgMs / 1000f }, col,
                        format = { String.format(java.util.Locale.GERMAN, "%.1f s", it) },
                    )
                }
            }
            SectionTitle("Letzte Runden")
            for (r in list.takeLast(20).reversed()) {
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${relativeDay(r.startedAt)} · ${r.mode.title}" + if (r.workout) " · Tagestraining" else "",
                                style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                            )
                            Text(
                                "${r.score} Punkte · ${formatPercent(r.accuracy)}" +
                                    if (r.avgMs > 0 && g.speedMatters) " · Ø ${formatSeconds(r.avgMs)}" else "",
                                style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                            )
                        }
                        Text(
                            when {
                                r.newLevel > r.level -> "${r.level} ▲ ${r.newLevel}"
                                r.newLevel < r.level -> "${r.level} ▼ ${r.newLevel}"
                                else -> "Lvl ${r.level}"
                            },
                            color = when {
                                r.newLevel > r.level -> c.good
                                r.newLevel < r.level -> Color(0xFFFB923C)
                                else -> c.textSecondary
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private fun dailyLast(list: List<com.mycompany.synapse.data.SessionRecord>, f: (com.mycompany.synapse.data.SessionRecord) -> Float) =
    list.groupBy { it.day() }.toSortedMap().map { (d, l) -> ChartPoint(d, f(l.last())) }

private fun dailyAvg(list: List<com.mycompany.synapse.data.SessionRecord>, f: (com.mycompany.synapse.data.SessionRecord) -> Float) =
    list.groupBy { it.day() }.toSortedMap().map { (d, l) -> ChartPoint(d, l.map(f).average().toFloat()) }

@Composable
private fun BenchmarkSection(nav: Navigator) {
    val c = Synapse.colors
    val runs = Repo.benchmarkRuns
    val accent = c.domain(Domain.RECHNEN)
    SectionTitle("Leistungstest")
    GlassCard(Modifier.fillMaxWidth(), accent = accent) {
        if (runs.isEmpty()) {
            Text(
                "Der Leistungstest misst dich immer mit derselben Schwierigkeit. Mach ihn einmal im Monat – " +
                    "dann siehst du schwarz auf weiß, ob du wirklich besser wirst.",
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
        } else {
            val first = runs.first()
            val last = runs.last()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${last.total}", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("von 100", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                if (runs.size > 1) {
                    val d = last.total - first.total
                    Text(
                        (if (d >= 0) "▲ +$d" else "▼ $d") + " seit dem ersten Test",
                        color = if (d >= 0) c.good else c.bad, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Text("${runs.size} Test${if (runs.size > 1) "s" else ""} · zuletzt ${relativeDay(last.run)}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            if (runs.size > 1) {
                Spacer(Modifier.height(8.dp))
                val zone = java.time.ZoneId.systemDefault()
                LineChart(
                    runs.map { ChartPoint(java.time.Instant.ofEpochMilli(it.run).atZone(zone).toLocalDate(), it.total.toFloat()) },
                    accent, height = 150, fixedMin = 0f, fixedMax = 100f, ticks = 4,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            if (Repo.benchmarkDue()) "Test starten (fällig)" else "Test starten", Modifier.fillMaxWidth(), color = accent,
        ) { nav.push(Screen.Test(Repo.KIND_BENCHMARK)) }
    }
}
