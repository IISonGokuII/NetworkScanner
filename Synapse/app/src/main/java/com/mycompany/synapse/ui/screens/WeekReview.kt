package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Achievements
import com.mycompany.synapse.data.Insights
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.WeekSummary
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.StatTile
import com.mycompany.synapse.ui.theme.Synapse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.roundToInt

fun weekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    val f = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    return "KW ${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${start.format(f)} – ${end.format(f)}"
}

/** Wochenziel: sieben Punkte Mo–So, gefüllt = trainiert. */
@Composable
fun WeekDots(weekStart: LocalDate, trained: Set<LocalDate>, goal: Int, modifier: Modifier = Modifier) {
    val c = Synapse.colors
    val today = LocalDate.now()
    val days = (0 until 7).map { weekStart.plusDays(it.toLong()) }
    val count = days.count { it in trained }
    Row(
        modifier.semantics { contentDescription = "Wochenziel: $count von $goal Tagen trainiert" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("M", "D", "M", "D", "F", "S", "S").forEachIndexed { i, label ->
            val d = days[i]
            val done = d in trained
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Canvas(Modifier.size(22.dp)) {
                    val col = if (done) c.good else if (d == today) c.textSecondary else c.gridLine
                    if (done) drawCircle(col, size.minDimension / 2)
                    else drawCircle(col, size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (d == today) c.textPrimary else c.textMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$count/$goal", style = MaterialTheme.typography.titleMedium,
            color = if (count >= goal) c.good else c.textPrimary, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun WeekReviewScreen(weekStartEpochDay: Long, nav: Navigator) {
    val c = Synapse.colors
    val weekStart = LocalDate.ofEpochDay(weekStartEpochDay)
    val records = Repo.records
    val review = remember(records) {
        Insights.weekReview(records, Repo.timeline, Repo.unlockedMap(), weekStart, Repo.weeklyGoal)
    }
    LaunchedEffect(Unit) { Repo.markReviewSeen(weekStart) }
    val goalReached = review.trainedDays >= review.goal
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Wochenrückblick", weekLabel(weekStart), onBack = { nav.pop() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 12.dp.toPx()
                    val frac = (review.trainedDays.toFloat() / review.goal).coerceIn(0f, 1f)
                    drawArc(c.gridLine, -90f, 360f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w))
                    drawArc(
                        if (goalReached) c.good else c.domain(Domain.RECHNEN), -90f, 360f * frac, false,
                        Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${review.trainedDays}/${review.goal}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = c.textPrimary)
                    Text("Tage", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                }
            }
            Text(
                when {
                    review.summary.sessions == 0 -> "Diese Woche war Pause – auch das gehört dazu. Neue Woche, neues Glück!"
                    goalReached -> "Wochenziel geschafft! Genau diese Regelmäßigkeit bringt am meisten."
                    review.trainedDays >= review.goal - 1 -> "Knapp am Ziel vorbei – nächste Woche klappt's."
                    else -> "Jeder Trainingstag zählt. Vielleicht hilft eine Erinnerung zu deiner Lieblingszeit?"
                },
                style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareStat("Sessions", review.summary.sessions, review.previous.sessions, Modifier.weight(1f))
                CompareStat("Minuten", review.summary.minutes, review.previous.minutes, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "Trefferquote", if (review.summary.sessions > 0) "${(review.summary.accuracy * 100).roundToInt()} %" else "–",
                    Modifier.weight(1f), hint = previousAcc(review.previous),
                )
                StatTile(
                    "Level-Aufstiege", "${review.levelUps}", Modifier.weight(1f),
                    hint = review.indexEnd?.let { "Index am Ende: $it" },
                )
            }
            review.bestGame?.let { id ->
                val g = GameCatalog[id]
                GlassCard(Modifier.fillMaxWidth(), accent = c.domain(g.domain)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DomainGlyph(g.domain, c.domain(g.domain), Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Spiel der Woche", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                            Text("${g.title}: +${review.bestGain} Level", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        }
                    }
                }
            }
            if (review.achievementIds.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), accent = Color(0xFFFBBF24)) {
                    Text("NEUE ERFOLGE", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    for (id in review.achievementIds) {
                        Achievements.all.firstOrNull { it.id == id }?.let {
                            Text("★ ${it.title} – ${it.description}", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton("Weiter trainieren", Modifier.fillMaxWidth().padding(18.dp)) { nav.home() }
    }
}

private fun previousAcc(prev: WeekSummary): String =
    if (prev.sessions == 0) "Vorwoche: –" else "Vorwoche: ${(prev.accuracy * 100).roundToInt()} %"

@Composable
private fun CompareStat(label: String, now: Int, before: Int, modifier: Modifier) {
    val c = Synapse.colors
    val d = now - before
    StatTile(
        label, "$now", modifier,
        hint = "Vorwoche: $before" + when {
            d > 0 -> "  ▲"
            d < 0 -> "  ▼"
            else -> ""
        },
        hintColor = when {
            d > 0 -> c.good
            d < 0 -> Color(0xFFFB923C)
            else -> c.textMuted
        },
    )
}
