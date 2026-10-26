package com.mycompany.synapse.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.AnagramGen
import com.mycompany.synapse.game.logic.Cube
import com.mycompany.synapse.game.logic.OddOneGen
import com.mycompany.synapse.game.logic.RotationGen
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.components.lerpColor
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- Würfel-Rotation

/** Isometrische Darstellung: x nach rechts unten, z nach links unten, y nach oben. */
fun DrawScope.drawCubes(cubes: List<Cube>, color: Color, edge: Color) {
    val cos30 = cos(Math.PI / 6).toFloat()
    val sin30 = sin(Math.PI / 6).toFloat()
    fun proj(x: Float, y: Float, z: Float) = Offset((x - z) * cos30, (x + z) * sin30 - y)
    // Begrenzungsrahmen für die Skalierung
    val corners = cubes.flatMap { c ->
        listOf(
            proj(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()), proj(c.x + 1f, c.y + 1f, c.z + 1f),
            proj(c.x + 1f, c.y.toFloat(), c.z.toFloat()), proj(c.x.toFloat(), c.y.toFloat(), c.z + 1f),
            proj(c.x.toFloat(), c.y + 1f, c.z.toFloat()), proj(c.x + 1f, c.y + 1f, c.z.toFloat()),
            proj(c.x.toFloat(), c.y + 1f, c.z + 1f), proj(c.x + 1f, c.y.toFloat(), c.z + 1f),
        )
    }
    val minX = corners.minOf { it.x }
    val maxX = corners.maxOf { it.x }
    val minY = corners.minOf { it.y }
    val maxY = corners.maxOf { it.y }
    val scale = minOf(size.width * 0.88f / (maxX - minX), size.height * 0.88f / (maxY - minY))
    val off = Offset(
        size.width / 2 - (minX + maxX) / 2 * scale,
        size.height / 2 - (minY + maxY) / 2 * scale,
    )
    fun p(x: Float, y: Float, z: Float) = proj(x, y, z) * scale + off
    val top = lerpColor(color, Color.White, 0.35f)
    val right = color
    val left = lerpColor(color, Color.Black, 0.35f)
    val sw = (scale * 0.05f).coerceIn(1.5f, 3.5f)
    for (c in cubes.sortedBy { it.x + it.y + it.z }) {
        val x = c.x.toFloat()
        val y = c.y.toFloat()
        val z = c.z.toFloat()
        val faces = listOf(
            listOf(p(x, y + 1, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)) to top,
            listOf(p(x + 1, y, z), p(x + 1, y + 1, z), p(x + 1, y + 1, z + 1), p(x + 1, y, z + 1)) to right,
            listOf(p(x, y, z + 1), p(x + 1, y, z + 1), p(x + 1, y + 1, z + 1), p(x, y + 1, z + 1)) to left,
        )
        for ((pts, col) in faces) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (q in pts.drop(1)) lineTo(q.x, q.y)
                close()
            }
            drawPath(path, col)
            drawPath(path, edge, style = Stroke(sw, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun RotationTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { RotationGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    val edge = if (c.dark) Color(0xFF05070F) else Color(0xFF111735)
    fun decide(mirrored: Boolean) = once.report(
        if (mirrored == task.mirrored) TrialResult.right(since(start))
        else TrialResult.wrong(since(start), if (task.mirrored) "Es war ein Spiegelbild." else "Es war dieselbe Figur – nur gedreht.")
    )
    TrialLayout {
        HintText("Ist die rechte Figur dieselbe – nur gedreht?")
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((cubes, label) in listOf(task.left to "Original", task.right to "Gedreht")) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.surface)
                        .border(1.dp, c.stroke, RoundedCornerShape(22.dp)),
                ) {
                    Canvas(Modifier.matchParentSize().semantics { contentDescription = "Würfelfigur $label aus ${cubes.size} Würfeln" }) {
                        drawCubes(cubes, ctx.accent, edge)
                    }
                    Text(label, Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton(Modifier.weight(1f), accent = ctx.accent, height = 76.dp, onClick = { decide(false) }) { ChoiceText("Gleich") }
            ChoiceButton(Modifier.weight(1f), accent = ctx.accent, height = 76.dp, onClick = { decide(true) }) { ChoiceText("Gespiegelt") }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Wortsalat

@Composable
fun AnagramTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember {
        AnagramGen.generate(ctx.level, ctx.rng(), Repo.recent("anagramm")).also { Repo.markRecent("anagramm", it.word, keep = 120) }
    }
    val letters = remember { mutableStateListOf<Char>().apply { addAll(task.letters) } }
    val used = remember { mutableStateListOf<Int>() } // Indizes in letters, in Eingabereihenfolge
    var attempts by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Tippe die Buchstaben in der richtigen Reihenfolge") }
    val shake = remember { Animatable(0f) }
    var shakeKey by remember { mutableIntStateOf(0) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    val alternatives = remember { AnagramGen.alternatives(task.word).filter { it != task.word } }

    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) { shake.animateTo(x); delay(16) }
        }
    }
    fun solutionText() = "Lösung: ${task.word}" + if (alternatives.isNotEmpty()) " (auch: ${alternatives.joinToString()})" else ""
    fun check() {
        val word = used.map { letters[it] }.joinToString("")
        if (AnagramGen.accepts(task, word)) {
            once.report(TrialResult.right(since(start)))
        } else {
            attempts++
            if (attempts >= 2) {
                once.report(TrialResult.wrong(since(start), solutionText()))
            } else {
                message = "„$word“ kenne ich nicht – noch ein Versuch"
                Feedback.wrong()
                shakeKey++
                used.clear()
            }
        }
    }
    TrialLayout {
        HintText(message)
        Spacer(Modifier.weight(1f))
        // Antwortzeile
        Row(
            Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            for (i in task.word.indices) {
                val ch = used.getOrNull(i)?.let { letters[it] }
                Box(
                    Modifier
                        .width(if (task.word.length > 7) 32.dp else 38.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (ch != null) ctx.accent.copy(alpha = 0.22f) else c.surface)
                        .border(1.5.dp, if (ch != null) ctx.accent else c.stroke, RoundedCornerShape(10.dp))
                        .clickable(enabled = ch != null && i == used.size - 1) { used.removeAt(used.size - 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch?.toString() ?: "", color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        for (row in letters.indices.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                for (i in row) {
                    val isUsed = i in used
                    ChoiceButton(
                        Modifier.size(58.dp),
                        accent = ctx.accent,
                        height = 58.dp,
                        state = if (isUsed) ChoiceState.DIMMED else ChoiceState.NORMAL,
                        onClick = {
                            if (isUsed || once.done) return@ChoiceButton
                            used += i
                            Feedback.tap()
                            if (used.size == letters.size) check()
                        },
                    ) { ChoiceText(letters[i].toString(), size = 26) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Mischen", Modifier.weight(1f)) {
                if (used.isEmpty()) {
                    val shuffled = letters.shuffled()
                    letters.clear(); letters.addAll(shuffled)
                } else used.clear()
            }
            SecondaryButton("Lösung", Modifier.weight(1f), color = c.bad) {
                once.report(TrialResult.wrong(since(start), solutionText()))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Was passt nicht?

@Composable
fun OddOneTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember {
        OddOneGen.generate(ctx.level, ctx.rng(), Repo.recent("oberbegriff"))
            .also { t -> Repo.markRecent("oberbegriff", t.words[t.oddIndex], keep = 120) }
    }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    var picked by remember { mutableIntStateOf(-1) }
    TrialLayout {
        HintText("Welches Wort passt nicht zu den anderen?")
        Spacer(Modifier.weight(1f))
        for (row in task.words.indices.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in row) {
                    ChoiceButton(
                        Modifier.weight(1f), accent = ctx.accent, height = 78.dp,
                        state = when {
                            picked < 0 -> ChoiceState.NORMAL
                            i == task.oddIndex -> ChoiceState.CORRECT
                            i == picked -> ChoiceState.WRONG
                            else -> ChoiceState.DIMMED
                        },
                        onClick = {
                            if (picked >= 0) return@ChoiceButton
                            picked = i
                            once.report(
                                if (i == task.oddIndex) TrialResult.right(since(start))
                                else TrialResult.wrong(since(start), task.explanation)
                            )
                        },
                    ) { ChoiceText(task.words[i], size = if (task.words[i].length > 11) 16 else 19) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
