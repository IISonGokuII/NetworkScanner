package com.mycompany.synapse.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.CorsiGen
import com.mycompany.synapse.game.logic.NBackGen
import com.mycompany.synapse.game.logic.SpanGen
import com.mycompany.synapse.ui.components.AnswerDisplay
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.NumberPad
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Gemeinsames Leuchtgitter

@Composable
fun GlowGrid(
    size: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    lit: (Int) -> Boolean,
    label: (Int) -> String? = { null },
    cellColor: (Int) -> Color? = { null },
    onTap: ((Int) -> Unit)? = null,
) {
    val c = Synapse.colors
    Column(modifier.aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until size) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until size) {
                    val i = r * size + col
                    val on = lit(i)
                    val override = cellColor(i)
                    val bg by animateColorAsState(
                        override ?: if (on) accent else c.surface, tween(if (on) 90 else 220), label = "cell",
                    )
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(if (on) Modifier.shadow(16.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
                            .clip(shape)
                            .background(bg)
                            .border(1.dp, if (on) accent else c.stroke, shape)
                            .then(if (onTap != null) Modifier.clickable { onTap(i) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        label(i)?.let {
                            Text(it, color = if (on) Color(0xFF05070F) else c.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- N-Back

@Composable
fun NBackTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val cfg = remember { NBackGen.config(ctx.level) }
    val seq = remember { NBackGen.sequence(cfg, ctx.rng()) }
    val interval = if (ctx.zen) (cfg.intervalMs * 1.3).toLong() else cfg.intervalMs
    var index by remember { mutableIntStateOf(-1) }
    var visible by remember { mutableStateOf(false) }
    val pressedPos = remember { mutableStateMapOf<Int, Boolean>() }
    val pressedLetter = remember { mutableStateMapOf<Int, Boolean>() }
    var hits by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var falseAlarms by remember { mutableIntStateOf(0) }
    var posFlash by remember { mutableStateOf<Color?>(null) }
    var letterFlash by remember { mutableStateOf<Color?>(null) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors

    LaunchedEffect(Unit) {
        delay(700)
        for (i in seq.indices) {
            index = i
            visible = true
            posFlash = null
            letterFlash = null
            delay((interval * 0.55).toLong())
            visible = false
            delay((interval * 0.45).toLong())
            if (i >= cfg.n) {
                if (seq[i].pos == seq[i - cfg.n].pos && pressedPos[i] != true) { misses++; posFlash = Color(0xFFFB923C) }
                if (cfg.dual && seq[i].letter == seq[i - cfg.n].letter && pressedLetter[i] != true) { misses++; letterFlash = Color(0xFFFB923C) }
            }
        }
        delay(250)
        once.report(
            TrialResult(
                hits, (hits + misses + falseAlarms).coerceAtLeast(1), 0,
                "Treffer $hits · verpasst $misses · Fehlalarme $falseAlarms",
            )
        )
    }

    fun press(letter: Boolean) {
        val i = index
        if (i < cfg.n || once.done) return
        val map = if (letter) pressedLetter else pressedPos
        if (map[i] == true) return
        map[i] = true
        val match = if (letter) seq[i].letter == seq[i - cfg.n].letter else seq[i].pos == seq[i - cfg.n].pos
        if (match) { hits++; Feedback.correct(0) } else { falseAlarms++; Feedback.wrong() }
        val flash = if (match) c.good else c.bad
        if (letter) letterFlash = flash else posFlash = flash
    }

    TrialLayout {
        Text("${cfg.n}-BACK", style = MaterialTheme.typography.labelMedium, color = ctx.accent)
        LinearProgressIndicator(
            progress = { ((index + 1).coerceAtLeast(0)).toFloat() / seq.size },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = ctx.accent, trackColor = c.gridLine,
        )
        HintText(
            when {
                index < 0 -> "Gleich geht's los …"
                index < cfg.n -> "Einprägen … (${cfg.n} zurück)"
                else -> "Gleich wie vor ${cfg.n} Schritt${if (cfg.n > 1) "en" else ""}?"
            }
        )
        Spacer(Modifier.weight(1f))
        GlowGrid(
            3, ctx.accent, Modifier.fillMaxWidth(0.82f),
            lit = { it == seq.getOrNull(index)?.pos && visible },
            label = { if (cfg.dual && visible && it == seq.getOrNull(index)?.pos) seq[index].letter.toString() else null },
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton(
                Modifier.weight(1f), accent = posFlash ?: ctx.accent, height = 76.dp,
                state = if (posFlash == c.good) ChoiceState.CORRECT else if (posFlash == c.bad) ChoiceState.WRONG else ChoiceState.NORMAL,
                onClick = { press(false) },
            ) { ChoiceText("Position ✓") }
            if (cfg.dual) {
                ChoiceButton(
                    Modifier.weight(1f), accent = letterFlash ?: ctx.accent, height = 76.dp,
                    state = if (letterFlash == c.good) ChoiceState.CORRECT else if (letterFlash == c.bad) ChoiceState.WRONG else ChoiceState.NORMAL,
                    onClick = { press(true) },
                ) { ChoiceText("Buchstabe ✓") }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Corsi-Pfad

@Composable
fun CorsiTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { CorsiGen.generate(ctx.level, ctx.rng()) }
    val stepMs = if (ctx.zen) (task.stepMs * 1.3).toLong() else task.stepMs
    val expected = remember { if (task.backwards) task.sequence.reversed() else task.sequence }
    var lit by remember { mutableIntStateOf(-1) }
    var input by remember { mutableStateOf(false) }
    val tapped = remember { mutableStateListOf<Int>() }
    var wrongCell by remember { mutableIntStateOf(-1) }
    var reveal by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors

    LaunchedEffect(Unit) {
        delay(600)
        for (cell in task.sequence) {
            lit = cell
            Feedback.tap()
            delay((stepMs * 0.7).toLong())
            lit = -1
            delay((stepMs * 0.3).toLong())
        }
        input = true
        start = now()
    }
    LaunchedEffect(reveal) {
        if (reveal) {
            delay(1500)
            once.report(TrialResult.wrong(since(start)))
        }
    }

    TrialLayout {
        Text(
            when {
                !input -> "Merke dir die Reihenfolge"
                task.backwards -> "Jetzt RÜCKWÄRTS antippen"
                else -> "Jetzt in derselben Reihenfolge antippen"
            },
            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
        )
        HintText("${tapped.size} / ${expected.size}")
        Spacer(Modifier.weight(1f))
        GlowGrid(
            task.gridSize, ctx.accent, Modifier.fillMaxWidth(0.9f),
            lit = { it == lit },
            label = { cell -> if (reveal) expected.indexOf(cell).takeIf { it >= 0 }?.let { "${it + 1}" } else null },
            cellColor = { cell ->
                when {
                    cell == wrongCell -> c.bad.copy(alpha = 0.6f)
                    cell in tapped -> ctx.accent.copy(alpha = 0.35f)
                    else -> null
                }
            },
            onTap = { cell ->
                if (!input || once.done || reveal || cell in tapped) return@GlowGrid
                val pos = tapped.size
                if (expected[pos] == cell) {
                    tapped += cell
                    Feedback.tap()
                    if (tapped.size == expected.size) once.report(TrialResult.right(since(start)))
                } else {
                    wrongCell = cell
                    reveal = true
                    Feedback.wrong()
                }
            },
        )
        Spacer(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Zahlen merken

@Composable
fun SpanTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { SpanGen.generate(ctx.level, ctx.rng()) }
    val stepMs = if (ctx.zen) (task.stepMs * 1.3).toLong() else task.stepMs
    var shownIndex by remember { mutableIntStateOf(-1) }
    var digitVisible by remember { mutableStateOf(false) }
    var inputPhase by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var start by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors

    LaunchedEffect(Unit) {
        delay(600)
        for (i in task.digits.indices) {
            shownIndex = i
            digitVisible = true
            delay((stepMs * 0.75).toLong())
            digitVisible = false
            delay((stepMs * 0.25).toLong())
        }
        inputPhase = true
        start = now()
    }
    fun check() {
        val ok = input == task.expected
        once.report(
            if (ok) TrialResult.right(since(start))
            else TrialResult.wrong(since(start), "Richtig: " + task.expected.toList().joinToString(" "))
        )
    }
    TrialLayout {
        Text(
            if (!inputPhase) "Merke dir die Ziffern" else if (task.backwards) "Jetzt RÜCKWÄRTS eingeben" else "Jetzt eingeben",
            style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (!inputPhase) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                if (digitVisible && shownIndex >= 0) {
                    Text(task.digits[shownIndex].toString(), color = ctx.accent, fontSize = 110.sp, fontWeight = FontWeight.Black)
                }
            }
            HintText("${(shownIndex + 1).coerceAtLeast(0)} / ${task.digits.length}")
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(300.dp))
        } else {
            AnswerDisplay(input.toList().joinToString(" "), ctx.accent, placeholder = "_ ".repeat(task.digits.length).trim())
            HintText("${input.length} / ${task.digits.length}")
            Spacer(Modifier.weight(1f))
            NumberPad(
                ctx.accent,
                onDigit = { d ->
                    if (once.done || input.length >= task.digits.length) return@NumberPad
                    input += d
                    if (input.length == task.digits.length) check()
                },
                onDelete = { input = input.dropLast(1) },
                onSubmit = { if (input.isNotEmpty()) check() },
            )
        }
    }
}
