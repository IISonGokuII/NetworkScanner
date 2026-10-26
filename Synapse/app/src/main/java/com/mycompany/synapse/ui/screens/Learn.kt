package com.mycompany.synapse.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Achievement
import com.mycompany.synapse.data.Insights
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.data.TagStat
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.logic.DrillTask
import com.mycompany.synapse.game.logic.Lesson
import com.mycompany.synapse.game.logic.Tricks
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.components.AnswerDisplay
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.DomainGlyph
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.NumberPad
import com.mycompany.synapse.ui.components.Pill
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.components.SectionTitle
import com.mycompany.synapse.ui.components.StatTile
import com.mycompany.synapse.ui.games.BigTaskText
import com.mycompany.synapse.ui.games.HintText
import com.mycompany.synapse.ui.games.now
import com.mycompany.synapse.ui.games.since
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private val gold = Color(0xFFFBBF24)

/** Aufgabentypen, die gezielt geübt werden können (Blitzrechnen) – sonst Verweis aufs Spiel. */
private fun drillIdFor(stat: TagStat): String? = if (stat.gameId == "blitz") "weak:${stat.tag}" else null

@Composable
fun LearnScreen(nav: Navigator) {
    val c = Synapse.colors
    val tagRows = Repo.tagRows
    val stats = remember(tagRows) { Insights.tagStats(tagRows, System.currentTimeMillis()) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Lernen", "Tricks verstehen, Schwächen gezielt üben")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Deine Schwächen")
            if (stats.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Sobald du ein paar Runden Blitzrechnen, Zahlenfolgen, Schlussfolgern, Farbwort, Wochentag oder Schätzen " +
                            "gespielt hast, zeigt die App hier, welche Aufgabentypen dir am schwersten fallen.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                    )
                }
            } else {
                for (s in stats.take(4)) WeaknessCard(s, nav)
                val strong = stats.takeLast(2).reversed().filter { it.accuracy >= 0.85 && it !in stats.take(4) }
                if (strong.isNotEmpty()) {
                    Text(
                        "Stark: " + strong.joinToString(" · ") { "${it.tag} (${(it.accuracy * 100).roundToInt()} %)" },
                        style = MaterialTheme.typography.labelMedium, color = c.good, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            for ((category, lessons) in Tricks.lessons.groupBy { it.category }) {
                SectionTitle(category)
                for (l in lessons) LessonCard(l, nav)
            }
        }
    }
}

@Composable
private fun WeaknessCard(s: TagStat, nav: Navigator) {
    val c = Synapse.colors
    val g = GameCatalog[s.gameId]
    val col = c.domain(g.domain)
    val drill = drillIdFor(s)
    GlassCard(Modifier.fillMaxWidth(), accent = col, onClick = {
        if (drill != null) nav.push(Screen.Drill(drill)) else nav.push(Screen.Play(g.id))
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DomainGlyph(g.domain, col, Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.tag, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    "${g.title} · ${(s.accuracy * 100).roundToInt()} % richtig" +
                        (if (s.avgMs > 0 && g.speedMatters) " · Ø ${formatSeconds(s.avgMs)}" else ""),
                    style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                )
            }
            Text(if (drill != null) "Üben ›" else "Spielen ›", color = col, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LessonCard(l: Lesson, nav: Navigator) {
    val c = Synapse.colors
    val p = Repo.lesson(l.id)
    val accent = when (l.category) {
        "Kalender" -> c.domain(Domain.LOGIK)
        "Merktechniken" -> c.domain(Domain.GEDAECHTNIS)
        else -> c.domain(Domain.RECHNEN)
    }
    GlassCard(Modifier.fillMaxWidth(), accent = if (p?.mastered == true) gold else accent, onClick = { nav.push(Screen.LessonDetail(l.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(l.summary, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.width(10.dp))
            when {
                p?.mastered == true -> Pill("★ gemeistert", gold)
                p != null -> Pill("${p.bestCorrect}/${p.total}", accent)
                l.drill == null -> Pill("Technik", accent)
                else -> Pill("neu", accent)
            }
        }
    }
}

@Composable
fun LessonScreen(id: String, nav: Navigator) {
    val c = Synapse.colors
    val l = Tricks.find(id) ?: return
    val p = Repo.lesson(id)
    val accent = c.domain(if (l.category == "Merktechniken") Domain.GEDAECHTNIS else if (l.category == "Kalender") Domain.LOGIK else Domain.RECHNEN)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(l.title, l.category, onBack = { nav.pop() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(l.summary, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            GlassCard(Modifier.fillMaxWidth()) {
                Text("SO GEHT'S", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                l.steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", color = accent, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp))
                        Text(step, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Text("BEISPIEL", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                Text(l.example, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            if (p != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Bestes Ergebnis", "${p.bestCorrect}/${p.total}", Modifier.weight(1f))
                    StatTile("Bestzeit Ø", formatSeconds(p.bestAvgMs), Modifier.weight(1f))
                    StatTile("Übungen", "${p.attempts}", Modifier.weight(1f))
                }
            }
            if (l.drill != null) {
                Text(
                    "Gemeistert: ${Tricks.DRILL_SIZE} von ${Tricks.DRILL_SIZE} richtig und im Schnitt unter ${formatSeconds(l.targetMs)}.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (l.drill != null) {
            PrimaryButton("Üben (${Tricks.DRILL_SIZE} Aufgaben)", Modifier.fillMaxWidth().padding(18.dp), color = accent) {
                nav.push(Screen.Drill(l.id))
            }
        } else if (l.gameId != null) {
            PrimaryButton("Im Spiel „${GameCatalog[l.gameId].title}“ üben", Modifier.fillMaxWidth().padding(18.dp), color = accent) {
                nav.push(Screen.Play(l.gameId))
            }
        }
    }
}

/** Übungsrunde für einen Trick oder eine Schwäche: 10 Aufgaben mit Erklärung nach jeder Antwort. */
@Composable
fun DrillScreen(id: String, nav: Navigator) {
    val c = Synapse.colors
    val lesson = Tricks.find(id)
    val weakKind = id.removePrefix("weak:").takeIf { id.startsWith("weak:") }
    val generator = remember(id) {
        lesson?.drill ?: weakKind?.let { Tricks.weaknessDrill(it, Repo.level("blitz").coerceAtLeast(20)) }
    } ?: return
    val title = lesson?.title ?: "Gezielt üben: $weakKind"
    val targetMs = lesson?.targetMs ?: 5000L
    val accent = c.domain(if (lesson?.category == "Merktechniken") Domain.GEDAECHTNIS else if (lesson?.category == "Kalender") Domain.LOGIK else Domain.RECHNEN)
    val rng = remember(id) { Random(System.nanoTime()) }
    val tasks = remember(id) { List(Tricks.DRILL_SIZE) { generator(rng) } }
    var index by remember { mutableIntStateOf(0) }
    val times = remember { mutableStateListOf<Long>() }
    var correct by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var newlyMastered by remember { mutableStateOf(false) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(title, if (done) "Ergebnis" else "Aufgabe ${index + 1} von ${tasks.size}", onBack = { nav.pop() })
        if (!done) {
            key(index) {
                DrillTaskView(tasks[index], accent) { ok, ms ->
                    if (ok) correct++
                    times += ms
                    if (index < tasks.size - 1) index++ else {
                        val avg = times.average().toLong()
                        if (lesson != null) {
                            newlyMastered = Repo.saveLesson(lesson.id, correct, tasks.size, avg, targetMs)
                            achievements = Repo.syncAchievements(silent = false)
                        }
                        done = true
                        Feedback.complete()
                    }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("$correct / ${tasks.size}", color = accent, fontSize = 64.sp, fontWeight = FontWeight.Black)
                Text("richtig · Ø ${formatSeconds(times.average().toLong())} pro Aufgabe", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                if (newlyMastered) Pill("★ Trick gemeistert!", gold)
                else if (lesson != null) {
                    Text(
                        if (correct < tasks.size) "Für „gemeistert“: alle richtig." else "Für „gemeistert“: im Schnitt unter ${formatSeconds(targetMs)}.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center,
                    )
                }
                for (a in achievements) AchievementUnlockedCard(a)
            }
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Fertig", Modifier.weight(1f)) { nav.pop() }
                PrimaryButton("Nochmal", Modifier.weight(1f), color = accent) { nav.replace(Screen.Drill(id)) }
            }
        }
    }
}

@Composable
private fun DrillTaskView(task: DrillTask, accent: Color, onDone: (Boolean, Long) -> Unit) {
    val c = Synapse.colors
    var hidden by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var start by remember { mutableStateOf(now()) }
    var time by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        if (task.flashMs != null) {
            delay(task.flashMs)
            hidden = true
            start = now()
        }
    }
    LaunchedEffect(result) {
        if (result == true) {
            delay(1100)
            onDone(true, time)
        }
    }
    fun answer(a: String) {
        if (result != null) return
        time = since(start)
        val ok = a == task.answer
        result = ok
        if (ok) Feedback.correct(1) else Feedback.wrong()
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            if (!hidden) BigTaskText(task.prompt) else HintText("Jetzt aus dem Gedächtnis eingeben")
        }
        result?.let { ok ->
            GlassCard(Modifier.fillMaxWidth(), accent = if (ok) c.good else c.bad, padding = 14.dp) {
                Text(if (ok) "✓ Richtig" else "✕ Richtig wäre: ${task.answer}", color = if (ok) c.good else c.bad, fontWeight = FontWeight.Bold)
                Text(task.explanation, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            }
        }
        Spacer(Modifier.weight(1f))
        if (result == false) {
            PrimaryButton("Weiter", Modifier.fillMaxWidth(), color = accent) { onDone(false, time) }
        } else if (task.choices != null) {
            for (row in task.choices.chunked(if (task.choices.size > 4) 4 else 2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (ch in row) {
                        ChoiceButton(
                            Modifier.weight(1f), accent = accent, height = 60.dp,
                            state = if (result == true && ch == task.answer) ChoiceState.CORRECT else ChoiceState.NORMAL,
                            onClick = { answer(ch) },
                        ) { ChoiceText(if (task.choices.size > 4) ch.take(2) else ch, size = 18) }
                    }
                    if (task.choices.size > 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else if (task.flashMs == null || hidden) {
            AnswerDisplay(input, accent)
            NumberPad(
                accent,
                onDigit = { d ->
                    if (result == null && input.length < 12) {
                        input += d
                        if (input == task.answer) answer(input)
                    }
                },
                onDelete = { input = input.dropLast(1) },
                onSubmit = { if (input.isNotEmpty()) answer(input) },
            )
        }
    }
}
