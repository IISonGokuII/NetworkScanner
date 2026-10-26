package com.mycompany.synapse.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.EstimateGen
import com.mycompany.synapse.game.logic.Face
import com.mycompany.synapse.game.logic.NamesGen
import com.mycompany.synapse.game.logic.PathGen
import com.mycompany.synapse.game.logic.ShoppingGen
import com.mycompany.synapse.game.logic.SudokuGen
import com.mycompany.synapse.game.logic.WEEKDAYS
import com.mycompany.synapse.game.logic.WeekdayGen
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.PadKey
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Wochentag

@Composable
fun WeekdayTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { WeekdayGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    var picked by remember { mutableIntStateOf(-1) }
    val c = Synapse.colors
    val answer = task.answer.value - 1
    TrialLayout {
        Spacer(Modifier.weight(1f))
        Text(
            task.question, color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (task.anchor != null) {
            GlassCard(Modifier.fillMaxWidth(), accent = ctx.accent, padding = 12.dp) {
                Text("HINWEIS", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                Text(task.anchor, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.weight(1f))
        for (row in WEEKDAYS.indices.chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in row) {
                    ChoiceButton(
                        Modifier.weight(1f), accent = ctx.accent, height = 60.dp,
                        state = when {
                            picked < 0 -> ChoiceState.NORMAL
                            i == answer -> ChoiceState.CORRECT
                            i == picked -> ChoiceState.WRONG
                            else -> ChoiceState.DIMMED
                        },
                        onClick = {
                            if (picked >= 0) return@ChoiceButton
                            picked = i
                            once.report(
                                if (i == answer) TrialResult.right(since(start), task.kind)
                                else TrialResult.wrong(since(start), task.explanation, task.kind)
                            )
                        },
                    ) { ChoiceText(WEEKDAYS[i].take(2), size = 18) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Schätzen

@Composable
fun EstimateTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { EstimateGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    var picked by remember { mutableIntStateOf(-1) }
    TrialLayout {
        Spacer(Modifier.weight(1f))
        BigTaskText(task.question)
        HintText("Nicht rechnen – überschlagen! Welcher Wert liegt am nächsten?")
        Spacer(Modifier.weight(1f))
        for (row in task.options.indices.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in row) {
                    ChoiceButton(
                        Modifier.weight(1f), accent = ctx.accent, height = 72.dp,
                        state = when {
                            picked < 0 -> ChoiceState.NORMAL
                            i == task.correctIndex -> ChoiceState.CORRECT
                            i == picked -> ChoiceState.WRONG
                            else -> ChoiceState.DIMMED
                        },
                        onClick = {
                            if (picked >= 0) return@ChoiceButton
                            picked = i
                            once.report(
                                if (i == task.correctIndex) TrialResult.right(since(start), task.kind)
                                else TrialResult.wrong(since(start), task.exact, task.kind)
                            )
                        },
                    ) { ChoiceText("≈ ${task.options[i]}", size = 22) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Mini-Sudoku

@Composable
fun SudokuTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { SudokuGen.generate(ctx.level, ctx.rng()) }
    val n = task.size
    val grid = remember { mutableStateListOf<Int>().apply { addAll(task.puzzle.toList()) } }
    var selected by remember { mutableIntStateOf(task.puzzle.indexOfFirst { it == 0 }) }
    var wrongCells by remember { mutableStateOf(emptySet<Int>()) }
    var firstTry by remember { mutableStateOf(true) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors

    fun place(v: Int) {
        if (selected < 0 || task.puzzle[selected] != 0 || once.done) return
        grid[selected] = v
        wrongCells = wrongCells - selected
        Feedback.tap()
        if (grid.none { it == 0 }) {
            val wrong = grid.indices.filter { grid[it] != task.solution[it] }.toSet()
            if (wrong.isEmpty()) {
                once.report(if (firstTry) TrialResult.right(since(start)) else TrialResult.wrong(since(start), "Gelöst – aber erst im zweiten Anlauf"))
            } else {
                firstTry = false
                wrongCells = wrong
                Feedback.wrong()
            }
        } else {
            val next = (1..grid.size).map { (selected + it) % grid.size }.firstOrNull { grid[it] == 0 }
            if (next != null) selected = next
        }
    }

    TrialLayout {
        HintText(if (wrongCells.isEmpty()) "Jede Zahl einmal pro Zeile, Spalte und Kasten" else "Rot markierte Felder stimmen noch nicht")
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth(if (n == 4) 0.78f else 0.95f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, ctx.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        ) {
            for (r in 0 until n) {
                Row(Modifier.weight(1f)) {
                    for (col in 0 until n) {
                        val i = r * n + col
                        val given = task.puzzle[i] != 0
                        val boxAlt = ((r / task.boxRows) + (col / task.boxCols)) % 2 == 1
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(
                                    when {
                                        i in wrongCells -> c.bad.copy(alpha = 0.35f)
                                        i == selected -> ctx.accent.copy(alpha = 0.32f)
                                        boxAlt -> c.surfaceStrong
                                        else -> c.surface
                                    }
                                )
                                .border(0.5.dp, c.stroke)
                                .clickable(enabled = !given) { selected = i }
                                .semantics { contentDescription = "Zeile ${r + 1}, Spalte ${col + 1}: " + if (grid[i] == 0) "leer" else "${grid[i]}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (grid[i] != 0) {
                                Text(
                                    "${grid[i]}", fontSize = if (n == 4) 30.sp else 24.sp,
                                    fontWeight = if (given) FontWeight.Black else FontWeight.Medium,
                                    color = if (given) c.textPrimary else ctx.accent,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in 1..n) PadKey("$v", ctx.accent, highlight = false, modifier = Modifier.weight(1f), height = 56.dp) { place(v) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Löschen", Modifier.weight(1f)) {
                if (selected >= 0 && task.puzzle[selected] == 0) grid[selected] = 0
            }
            SecondaryButton("Aufgeben", Modifier.weight(1f), color = c.bad) {
                once.report(TrialResult.wrong(since(start), "Nicht gelöst – nächstes Rätsel"))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Namen merken

private val skinTones = listOf(Color(0xFFF1C27D), Color(0xFFE0AC69), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFFFDBAC))
private val hairColors = listOf(Color(0xFF1F1B18), Color(0xFF6B3E26), Color(0xFFE6C068), Color(0xFFB4441F), Color(0xFF9CA3AF), Color(0xFFF3F4F6))
private val shirtColors = listOf(Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFFBBF24), Color(0xFF34D399), Color(0xFFF87171), Color(0xFF60A5FA))

/** Zeichnet ein einfaches, gut unterscheidbares Gesicht. */
fun DrawScope.drawFace(f: Face) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val headR = w * 0.26f
    val headC = Offset(cx, h * 0.42f)
    val skin = skinTones[f.skin % skinTones.size]
    val hair = hairColors[f.hairColor % hairColors.size]
    val line = Color(0xFF1F2937)
    // Oberkörper
    drawRoundRect(shirtColors[f.shirt % shirtColors.size], Offset(cx - w * 0.36f, h * 0.74f), Size(w * 0.72f, h * 0.4f), androidx.compose.ui.geometry.CornerRadius(w * 0.2f))
    // lange Haare hinter dem Kopf
    if (f.hairStyle == 1) drawRoundRect(hair, Offset(cx - headR * 1.15f, headC.y - headR * 0.9f), Size(headR * 2.3f, headR * 2.2f), androidx.compose.ui.geometry.CornerRadius(headR))
    drawCircle(skin, headR * 0.28f, Offset(cx - headR * 0.98f, headC.y + headR * 0.1f))
    drawCircle(skin, headR * 0.28f, Offset(cx + headR * 0.98f, headC.y + headR * 0.1f))
    drawCircle(skin, headR, headC)
    // Frisur
    when (f.hairStyle) {
        0, 1 -> drawArc(hair, 180f, 180f, true, Offset(headC.x - headR, headC.y - headR * 1.05f), Size(headR * 2, headR * 1.3f))
        3 -> for (k in 0 until 7) {
            val a = Math.toRadians(200.0 + k * 23.0)
            drawCircle(hair, headR * 0.32f, headC + Offset((headR * 0.92f * kotlin.math.cos(a)).toFloat(), (headR * 0.92f * kotlin.math.sin(a)).toFloat()))
        }
        4 -> {
            drawArc(hair, 180f, 180f, true, Offset(headC.x - headR, headC.y - headR * 1.05f), Size(headR * 2, headR * 1.1f))
            drawCircle(hair, headR * 0.38f, Offset(cx, headC.y - headR * 1.15f))
        }
        5 -> {
            val p = Path().apply {
                moveTo(headC.x - headR, headC.y - headR * 0.1f)
                quadraticTo(headC.x - headR * 0.6f, headC.y - headR * 1.5f, headC.x + headR, headC.y - headR * 0.45f)
                lineTo(headC.x + headR, headC.y - headR * 0.75f)
                quadraticTo(headC.x, headC.y - headR * 1.4f, headC.x - headR, headC.y - headR * 0.6f)
                close()
            }
            drawPath(p, hair)
        }
        else -> Unit // Glatze
    }
    // Augen
    val eyeY = headC.y + headR * 0.05f
    for (sx in listOf(-1f, 1f)) {
        val e = Offset(cx + sx * headR * 0.38f, eyeY)
        when (f.eyes) {
            0 -> drawCircle(line, headR * 0.09f, e)
            1 -> drawLine(line, e - Offset(headR * 0.12f, 0f), e + Offset(headR * 0.12f, 0f), headR * 0.07f, StrokeCap.Round)
            else -> {
                drawCircle(Color.White, headR * 0.14f, e)
                drawCircle(line, headR * 0.07f, e)
            }
        }
        if (f.glasses) drawCircle(line, headR * 0.24f, e, style = Stroke(headR * 0.06f))
    }
    if (f.glasses) drawLine(line, Offset(cx - headR * 0.14f, eyeY), Offset(cx + headR * 0.14f, eyeY), headR * 0.05f)
    // Mund & Bart
    when (f.beard) {
        1 -> drawArc(hair, 0f, 180f, true, Offset(headC.x - headR * 0.8f, headC.y + headR * 0.05f), Size(headR * 1.6f, headR * 1.0f))
        2 -> drawRoundRect(hair, Offset(cx - headR * 0.35f, headC.y + headR * 0.38f), Size(headR * 0.7f, headR * 0.16f), androidx.compose.ui.geometry.CornerRadius(headR * 0.08f))
        else -> Unit
    }
    drawArc(line, 20f, 140f, false, Offset(cx - headR * 0.3f, headC.y + headR * 0.32f), Size(headR * 0.6f, headR * 0.35f), style = Stroke(headR * 0.06f, cap = StrokeCap.Round))
    // Accessoire
    when (f.accessory) {
        1 -> for (sx in listOf(-1f, 1f)) drawCircle(Color(0xFFFBBF24), headR * 0.1f, Offset(cx + sx * headR * 0.98f, headC.y + headR * 0.42f))
        2 -> {
            drawRect(Color(0xFFDC2626), Offset(cx - headR * 1.0f, headC.y - headR * 1.1f), Size(headR * 2.0f, headR * 0.35f))
            drawRect(Color(0xFFDC2626), Offset(cx - headR * 0.1f, headC.y - headR * 0.85f), Size(headR * 1.3f, headR * 0.12f))
        }
        3 -> {
            val bt = Offset(cx, h * 0.78f)
            val bow = Path().apply {
                moveTo(bt.x, bt.y); lineTo(bt.x - headR * 0.4f, bt.y - headR * 0.2f); lineTo(bt.x - headR * 0.4f, bt.y + headR * 0.2f); close()
                moveTo(bt.x, bt.y); lineTo(bt.x + headR * 0.4f, bt.y - headR * 0.2f); lineTo(bt.x + headR * 0.4f, bt.y + headR * 0.2f); close()
            }
            drawPath(bow, Color(0xFF111827))
        }
        else -> Unit
    }
}

@Composable
fun NamesTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { NamesGen.generate(ctx.level, ctx.rng()) }
    val studyMs = if (ctx.zen) (task.studyMs * 1.5).toLong() else task.studyMs
    var studyIndex by remember { mutableIntStateOf(0) }
    var quizIndex by remember { mutableIntStateOf(-1) }
    var picked by remember { mutableIntStateOf(-1) }
    var correct by remember { mutableIntStateOf(0) }
    var start by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    val n = task.people.size

    LaunchedEffect(studyIndex) {
        if (quizIndex >= 0) return@LaunchedEffect
        delay(studyMs)
        if (studyIndex < n - 1) studyIndex++ else { quizIndex = 0; start = now() }
    }
    LaunchedEffect(picked) {
        if (picked < 0) return@LaunchedEffect
        delay(650)
        if (quizIndex < n - 1) {
            quizIndex++
            picked = -1
        } else {
            once.report(TrialResult(correct, n, since(start), "$correct von $n Namen richtig"))
        }
    }

    TrialLayout {
        if (quizIndex < 0) {
            Text("LERNEN · ${studyIndex + 1} / $n", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            val (face, name) = task.people[studyIndex]
            Spacer(Modifier.weight(1f))
            Canvas(
                Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)).background(c.surface)
                    .semantics { contentDescription = "Gesicht von $name" }
            ) { drawFace(face) }
            Text(name, color = ctx.accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            HintText("Tipp: Verknüpfe den Namen mit einem Merkmal – „Brillen-Ben“.")
            SecondaryButton("Weiter", Modifier.fillMaxWidth()) {
                if (studyIndex < n - 1) studyIndex++ else { quizIndex = 0; start = now() }
            }
        } else {
            val idx = task.quizOrder[quizIndex]
            val (face, name) = task.people[idx]
            val options = task.options[quizIndex]
            Text("WIE HEISST DIESE PERSON? · ${quizIndex + 1} / $n", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            Spacer(Modifier.weight(1f))
            Canvas(
                Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)).background(c.surface)
                    .semantics { contentDescription = "Gesicht ${quizIndex + 1}" }
            ) { drawFace(face) }
            Spacer(Modifier.weight(1f))
            for (row in options.indices.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in row) {
                        ChoiceButton(
                            Modifier.weight(1f), accent = ctx.accent, height = 62.dp,
                            state = when {
                                picked < 0 -> ChoiceState.NORMAL
                                options[i] == name -> ChoiceState.CORRECT
                                i == picked -> ChoiceState.WRONG
                                else -> ChoiceState.DIMMED
                            },
                            onClick = {
                                if (picked >= 0) return@ChoiceButton
                                picked = i
                                if (options[i] == name) { correct++; Feedback.correct(0) } else Feedback.wrong()
                            },
                        ) { ChoiceText(options[i]) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Einkaufsliste

@Composable
fun ShoppingTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { ShoppingGen.generate(ctx.level, ctx.rng()) }
    val studyMs = if (ctx.zen) (task.studyMs * 1.5).toLong() else task.studyMs
    var recall by remember { mutableStateOf(false) }
    val picks = remember { mutableStateListOf<String>() }
    var start by remember { mutableLongStateOf(0L) }
    val timer = remember { Animatable(1f) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    val k = task.items.size

    LaunchedEffect(recall) {
        if (recall) return@LaunchedEffect
        timer.animateTo(0f, tween(studyMs.toInt(), easing = LinearEasing))
        recall = true
        start = now()
    }
    fun finish() {
        val correct = if (task.ordered) picks.indices.count { picks[it] == task.items[it] } else picks.count { it in task.items }
        once.report(TrialResult(correct, k, since(start), "Liste: " + task.items.joinToString(", ") { it.substringAfter(" ") }))
    }

    TrialLayout {
        if (!recall) {
            Text("EINKAUFSLISTE MERKEN", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            LinearProgressIndicator(
                progress = { timer.value },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = ctx.accent, trackColor = c.gridLine,
            )
            GlassCard(Modifier.fillMaxWidth().weight(1f), accent = ctx.accent) {
                task.items.forEachIndexed { i, item ->
                    Text(
                        (if (task.ordered) "${i + 1}. " else "• ") + item,
                        color = c.textPrimary, fontSize = if (k > 9) 18.sp else 21.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            PrimaryButton("Gemerkt", Modifier.fillMaxWidth(), color = ctx.accent) { recall = true; start = now() }
        } else {
            Text(
                if (task.ordered) "Tippe die Dinge in der RICHTIGEN REIHENFOLGE" else "Was stand auf der Liste? (${picks.size} / $k)",
                style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            for (row in task.grid.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in row) {
                        val pos = picks.indexOf(item)
                        ChoiceButton(
                            Modifier.weight(1f), accent = ctx.accent, height = 54.dp,
                            state = if (pos >= 0) ChoiceState.CORRECT else ChoiceState.NORMAL,
                            onClick = {
                                if (once.done) return@ChoiceButton
                                if (pos >= 0) picks.remove(item) else if (picks.size < k) {
                                    picks += item
                                    Feedback.tap()
                                    if (picks.size == k) finish()
                                }
                            },
                        ) {
                            Text(
                                (if (task.ordered && pos >= 0) "${pos + 1}. " else "") + item,
                                color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- Weg im Kopf

@Composable
fun PathTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { PathGen.generate(ctx.level, ctx.rng()) }
    val stepMs = if (ctx.zen) (task.stepMs * 1.4).toLong() else task.stepMs
    var phase by remember { mutableIntStateOf(0) } // 0 = Start zeigen, 1..n = Schritte, n+1 = Antwort
    var picked by remember { mutableIntStateOf(-1) }
    var start by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    val n = task.steps.size
    val dirArrow = listOf("↑", "→", "↓", "←")

    LaunchedEffect(Unit) {
        delay(stepMs + 600)
        for (i in 1..n) {
            phase = i
            Feedback.tap()
            delay(stepMs)
        }
        phase = n + 1
        start = now()
    }
    LaunchedEffect(picked) {
        if (picked < 0) return@LaunchedEffect
        if (picked == task.end) once.report(TrialResult.right(since(start)))
        else {
            delay(1300)
            once.report(TrialResult.wrong(since(start), "Das Ziel ist grün markiert"))
        }
    }

    TrialLayout {
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(240))) togetherWith
                        (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(180)))
                },
                label = "path",
            ) { p ->
                Text(
                    when {
                        p == 0 -> if (task.relative) "Start – Blick nach ${dirArrow[task.startDir]}" else "Start merken"
                        p <= n -> task.steps[p - 1]
                        else -> "Wo bist du jetzt?"
                    },
                    color = if (p in 1..n) ctx.accent else c.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Black,
                )
            }
        }
        HintText(
            when {
                phase == 0 -> "Präge dir das markierte Startfeld ein"
                phase <= n -> "Schritt $phase von $n – im Kopf mitgehen"
                else -> "Tippe auf das Zielfeld"
            }
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(0.95f).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (r in 0 until task.size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (col in 0 until task.size) {
                        val i = r * task.size + col
                        val isStart = i == task.start
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        picked >= 0 && i == task.end -> c.good.copy(alpha = 0.5f)
                                        i == picked -> c.bad.copy(alpha = 0.5f)
                                        isStart -> ctx.accent.copy(alpha = 0.35f)
                                        else -> c.surface
                                    }
                                )
                                .border(1.dp, if (isStart) ctx.accent else c.stroke, RoundedCornerShape(10.dp))
                                .clickable(enabled = phase > n && picked < 0) { picked = i }
                                .semantics { contentDescription = "Feld Zeile ${r + 1}, Spalte ${col + 1}" + if (isStart) ", Start" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isStart) {
                                Text(
                                    if (task.relative) dirArrow[task.startDir] else "●",
                                    color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
