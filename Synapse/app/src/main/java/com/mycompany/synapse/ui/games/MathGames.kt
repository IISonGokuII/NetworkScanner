package com.mycompany.synapse.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.ArithmeticGen
import com.mycompany.synapse.game.logic.ChainGen
import com.mycompany.synapse.game.logic.TargetGen
import com.mycompany.synapse.ui.components.AnswerDisplay
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.Dot
import com.mycompany.synapse.ui.components.NumberPad
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Blitzrechnen

@Composable
fun BlitzTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { ArithmeticGen.generate(ctx.level, ctx.rng()) }
    var input by remember { mutableStateOf("") }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    fun finish(ok: Boolean) = once.report(
        if (ok) TrialResult.right(since(start)) else TrialResult.wrong(since(start), "${task.text} = ${task.answer}")
    )
    TrialLayout {
        Spacer(Modifier.weight(1f))
        BigTaskText("${task.text} =")
        AnswerDisplay(input, ctx.accent)
        Spacer(Modifier.weight(1f))
        NumberPad(
            ctx.accent,
            onDigit = { d ->
                if (input.length < 7 && !once.done) {
                    input = (input + d).trimStart('0').ifEmpty { "0" }
                    if (input.toIntOrNull() == task.answer) finish(true)
                }
            },
            onDelete = { input = input.dropLast(1) },
            onSubmit = { if (input.isNotEmpty()) finish(input.toIntOrNull() == task.answer) },
        )
    }
}

// ---------------------------------------------------------------- Kettenrechnen

@Composable
fun ChainTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { ChainGen.generate(ctx.level, ctx.rng()) }
    val stepMs = if (ctx.zen) (task.stepMs * 1.4).toLong() else task.stepMs
    var phase by remember { mutableIntStateOf(0) } // 0 = Start, 1..n = Schritte, n+1 = Eingabe
    var input by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val n = task.steps.size
    LaunchedEffect(Unit) {
        delay(stepMs + 400)
        for (i in 1..n) {
            phase = i
            Feedback.tap()
            delay(stepMs)
        }
        phase = n + 1
        start = now()
    }
    fun finish(ok: Boolean) = once.report(
        if (ok) TrialResult.right(since(start))
        else TrialResult.wrong(since(start), "${task.start} ${task.steps.joinToString(" ")} = ${task.answer}")
    )
    TrialLayout {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..n) Dot(if (i <= phase) ctx.accent else Synapse.colors.gridLine, 8.dp)
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "chain",
        ) { p ->
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                when {
                    p == 0 -> BigTaskText("${task.start}")
                    p <= n -> BigTaskText(task.steps[p - 1], color = ctx.accent)
                    else -> BigTaskText("= ?")
                }
            }
        }
        HintText(if (phase <= n) "Rechne im Kopf mit …" else "Wie lautet das Ergebnis?")
        Spacer(Modifier.weight(1f))
        if (phase > n) {
            AnswerDisplay(input, ctx.accent)
            NumberPad(
                ctx.accent,
                onDigit = { d ->
                    if (input.length < 6 && !once.done) {
                        input = (input + d).trimStart('0').ifEmpty { "0" }
                        if (input.toIntOrNull() == task.answer) finish(true)
                    }
                },
                onDelete = { input = input.dropLast(1) },
                onSubmit = { if (input.isNotEmpty()) finish(input.toIntOrNull() == task.answer) },
            )
        } else {
            Spacer(Modifier.height(320.dp))
        }
    }
}

// ---------------------------------------------------------------- Zielzahl

private data class Tile(val id: Int, val value: Int)

@Composable
fun TargetTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { TargetGen.generate(ctx.level, ctx.rng()) }
    val tiles = remember { mutableStateListOf<Tile>().apply { task.numbers.forEachIndexed { i, v -> add(Tile(i, v)) } } }
    val history = remember { mutableStateListOf<List<Tile>>() }
    var selected by remember { mutableStateOf<Int?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }
    var nextId by remember { mutableIntStateOf(100) }
    var message by remember { mutableStateOf("Tippe Zahl → Rechenart → Zahl") }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors

    fun tapTile(t: Tile) {
        if (once.done) return
        val sel = selected
        val o = op
        when {
            sel == null -> selected = t.id
            sel == t.id -> { selected = null; op = null }
            o == null -> selected = t.id
            else -> {
                val a = tiles.first { it.id == sel }
                val v = TargetGen.apply(a.value, o, t.value)
                if (v == null) {
                    message = when (o) {
                        '-' -> "Ergebnis muss positiv sein"
                        '/' -> "Nur glatte Teilungen erlaubt"
                        else -> "Nicht erlaubt"
                    }
                    Feedback.wrong()
                    op = null
                    return
                }
                history.add(tiles.toList())
                val idx = tiles.indexOfFirst { it.id == t.id }
                val newTile = Tile(nextId++, v)
                tiles[idx] = newTile
                tiles.removeAll { it.id == a.id }
                selected = newTile.id
                op = null
                message = "${a.value} ${TargetGen.symbol(o)} ${t.value} = $v"
                Feedback.tap()
                if (v == task.target) once.report(TrialResult.right(since(start)))
            }
        }
    }

    TrialLayout {
        Text("ZIEL", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        BigTaskText("${task.target}", color = ctx.accent)
        HintText(message)
        Spacer(Modifier.weight(1f))
        for (row in tiles.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                for (t in row) {
                    ChoiceButton(
                        modifier = Modifier.weight(1f),
                        accent = ctx.accent,
                        state = if (selected == t.id) ChoiceState.CORRECT else ChoiceState.NORMAL,
                        height = 68.dp,
                        onClick = { tapTile(t) },
                    ) { ChoiceText("${t.value}", size = 26) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (o in listOf('+', '-', '*', '/')) {
                val allowed = o in task.ops
                ChoiceButton(
                    modifier = Modifier.weight(1f),
                    accent = ctx.accent,
                    state = when {
                        !allowed -> ChoiceState.DIMMED
                        op == o -> ChoiceState.CORRECT
                        else -> ChoiceState.NORMAL
                    },
                    height = 60.dp,
                    onClick = { if (allowed && selected != null) op = o },
                ) { ChoiceText(TargetGen.symbol(o), size = 26) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Zurück", Modifier.weight(1f)) {
                history.removeLastOrNull()?.let { prev ->
                    tiles.clear(); tiles.addAll(prev); selected = null; op = null
                }
            }
            SecondaryButton("Lösung", Modifier.weight(1f), color = c.bad) {
                once.report(TrialResult.wrong(since(start), "${task.solution} = ${task.target}"))
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

