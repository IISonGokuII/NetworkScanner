package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.logic.DuelGen
import com.mycompany.synapse.game.logic.DuelTask
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.components.SelectChip
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class DuelConfig(val stroop: Boolean, val level: Int, val target: Int)

@Composable
fun DuelScreen(nav: Navigator) {
    var config by remember { mutableStateOf<DuelConfig?>(null) }
    var game by remember { mutableIntStateOf(0) }
    val cfg = config
    if (cfg == null) {
        DuelSetup(onBack = { nav.pop() }, onStart = { config = it })
    } else {
        androidx.compose.runtime.key(game) {
            DuelMatch(cfg, onRematch = { game++ }, onQuit = { config = null })
        }
    }
}

@Composable
private fun DuelSetup(onBack: () -> Unit, onStart: (DuelConfig) -> Unit) {
    val c = Synapse.colors
    val accent = c.domain(Domain.SPRACHE)
    var stroop by remember { mutableStateOf(false) }
    var level by remember { mutableIntStateOf(10) }
    var target by remember { mutableIntStateOf(7) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Duell", "Zwei Personen, ein Handy", onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth(), accent = accent) {
                Text(
                    "Legt das Handy flach zwischen euch. Jede Person tippt auf ihrer Hälfte. " +
                        "Wer zuerst richtig antwortet, holt den Punkt – ein Fehlgriff sperrt dich für die Runde.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                )
            }
            Text("Disziplin", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Rechnen", !stroop, accent) { stroop = false }
                SelectChip("Farbwort", stroop, accent) { stroop = true }
            }
            if (!stroop) {
                Text("Schwierigkeit", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Leicht", level == 4, accent) { level = 4 }
                    SelectChip("Mittel", level == 10, accent) { level = 10 }
                    SelectChip("Schwer", level == 22, accent) { level = 22 }
                    SelectChip("Profi", level == 34, accent) { level = 34 }
                }
            }
            Text("Gewonnen bei", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf(5, 7, 10)) SelectChip("$t Punkten", target == t, accent) { target = t }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Duell starten", Modifier.fillMaxWidth(), color = accent) { onStart(DuelConfig(stroop, level, target)) }
        }
    }
}

@Composable
private fun DuelMatch(cfg: DuelConfig, onRematch: () -> Unit, onQuit: () -> Unit) {
    val c = Synapse.colors
    val rng = remember { Random(System.nanoTime()) }
    val names = c.stroopNames
    fun newTask(): DuelTask = if (cfg.stroop) DuelGen.stroop(rng, names) else DuelGen.math(cfg.level, rng)
    var task by remember { mutableStateOf(newTask()) }
    var round by remember { mutableIntStateOf(0) }
    val score = remember { mutableStateOf(intArrayOf(0, 0)) }
    var locked by remember { mutableStateOf(setOf<Int>()) }
    var roundWinner by remember { mutableStateOf<Int?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Int?>(null) }
    val p1 = c.domain(Domain.RECHNEN)
    val p2 = c.domain(Domain.SPRACHE)

    LaunchedEffect(round, resolving) {
        if (!resolving) return@LaunchedEffect
        delay(900)
        val s = score.value
        if (s[0] >= cfg.target || s[1] >= cfg.target) {
            winner = if (s[0] > s[1]) 0 else 1
            Feedback.complete()
            Repo.recordDuel()
        } else {
            task = newTask()
            locked = emptySet()
            roundWinner = null
            resolving = false
            round++
        }
    }

    fun answer(player: Int, option: Int) {
        if (resolving || winner != null || player in locked) return
        if (option == task.correct) {
            val s = score.value.copyOf()
            s[player]++
            score.value = s
            roundWinner = player
            resolving = true
            Feedback.correct(2)
        } else {
            locked = locked + player
            Feedback.wrong()
            if (locked.size == 2) {
                roundWinner = -1
                resolving = true
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PlayerHalf(
            player = 1, color = p2, task = task, score = score.value[1], opponent = score.value[0],
            locked = 1 in locked, roundWinner = roundWinner, winner = winner,
            modifier = Modifier.weight(1f).graphicsLayer { rotationZ = 180f },
            onAnswer = { answer(1, it) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surfaceStrong)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${score.value[0]}", color = p1, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(" : ", color = c.textMuted, fontSize = 22.sp)
            Text("${score.value[1]}", color = p2, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if (winner != null) {
                SecondaryButton("Revanche", onClick = onRematch)
                Spacer(Modifier.padding(4.dp))
            }
            SecondaryButton("Beenden", onClick = onQuit)
        }
        PlayerHalf(
            player = 0, color = p1, task = task, score = score.value[0], opponent = score.value[1],
            locked = 0 in locked, roundWinner = roundWinner, winner = winner,
            modifier = Modifier.weight(1f),
            onAnswer = { answer(0, it) },
        )
    }
}

@Composable
private fun PlayerHalf(
    player: Int,
    color: Color,
    task: DuelTask,
    score: Int,
    opponent: Int,
    locked: Boolean,
    roundWinner: Int?,
    winner: Int?,
    modifier: Modifier,
    onAnswer: (Int) -> Unit,
) {
    val c = Synapse.colors
    Box(modifier.fillMaxWidth().background(color.copy(alpha = 0.05f)).padding(16.dp)) {
        if (winner != null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    if (winner == player) "Gewonnen! 🏆" else "Knapp verloren",
                    color = if (winner == player) color else c.textSecondary, fontSize = 34.sp, fontWeight = FontWeight.Black,
                )
                Text("$score : $opponent", color = c.textPrimary, style = MaterialTheme.typography.headlineMedium)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SPIELER ${player + 1}", style = MaterialTheme.typography.labelMedium, color = color)
            Spacer(Modifier.weight(1f))
            Text(
                task.question,
                color = task.inkColor?.let { c.stroopColors[it] } ?: c.textPrimary,
                fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            )
            Text(
                when {
                    roundWinner == player -> "Punkt für dich!"
                    roundWinner == -1 -> "Niemand – nächste Runde"
                    roundWinner != null -> "Zu langsam …"
                    locked -> "Gesperrt für diese Runde"
                    task.inkColor != null -> "Tippe die SCHRIFTFARBE"
                    else -> " "
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (roundWinner == player) c.good else c.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            for (row in task.options.indices.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in row) {
                        ChoiceButton(
                            Modifier.weight(1f), accent = color, height = 58.dp,
                            state = when {
                                roundWinner != null && i == task.correct -> ChoiceState.CORRECT
                                locked -> ChoiceState.DIMMED
                                else -> ChoiceState.NORMAL
                            },
                            onClick = { onAnswer(i) },
                        ) { ChoiceText(task.options[i]) }
                    }
                }
            }
        }
    }
}
