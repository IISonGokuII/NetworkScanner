package com.mycompany.synapse.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.BalanceGen
import com.mycompany.synapse.game.logic.MatrixCell
import com.mycompany.synapse.game.logic.MatrixGen
import com.mycompany.synapse.game.logic.MatrixTask
import com.mycompany.synapse.game.logic.SequenceGen
import com.mycompany.synapse.game.logic.SyllogismGen
import com.mycompany.synapse.ui.components.AnswerDisplay
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceState
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.NumberPad
import com.mycompany.synapse.ui.components.drawGameShape
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------------------------------------------------------- Zahlenfolgen

@Composable
fun SequenceTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { SequenceGen.generate(ctx.level, ctx.rng()) }
    var input by remember { mutableStateOf("") }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    fun finish(ok: Boolean) = once.report(
        if (ok) TrialResult.right(since(start), task.kind) else TrialResult.wrong(since(start), "${task.answer} – ${task.rule}", task.kind)
    )
    val items = task.shown.map { it.toString() } + "?"
    TrialLayout {
        Spacer(Modifier.weight(1f))
        for (row in items.chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                for (s in row) {
                    val isQ = s == "?"
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isQ) ctx.accent.copy(alpha = 0.2f) else c.surface)
                            .border(1.dp, if (isQ) ctx.accent else c.stroke, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            s, color = c.textPrimary, fontSize = if (s.length > 4) 20.sp else 26.sp, fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
        }
        HintText("Welche Zahl kommt als Nächstes?")
        AnswerDisplay(input, ctx.accent)
        Spacer(Modifier.weight(1f))
        NumberPad(
            ctx.accent,
            onDigit = { d ->
                if (input.length < 6 && !once.done) {
                    input = (input + d).trimStart('0').ifEmpty { "0" }
                    if (input.toLongOrNull() == task.answer) finish(true)
                }
            },
            onDelete = { input = input.dropLast(1) },
            onSubmit = { if (input.isNotEmpty()) finish(input.toLongOrNull() == task.answer) },
        )
    }
}

// ---------------------------------------------------------------- Muster-Matrix

fun DrawScope.drawMatrixCell(task: MatrixTask, cell: MatrixCell, colors: List<Color>) {
    val color = colors[task.colors[cell.color] % colors.size]
    val kind = task.shapes[cell.shape]
    val count = cell.count + 1
    val s = size.minDimension
    val shapeSize = when (count) {
        1 -> s * 0.48f
        2 -> s * 0.34f
        else -> s * 0.30f
    }
    val cx = size.width / 2
    val cy = size.height / 2
    val positions = when (count) {
        1 -> listOf(Offset(cx, cy))
        2 -> listOf(Offset(cx - s * 0.2f, cy), Offset(cx + s * 0.2f, cy))
        else -> listOf(Offset(cx, cy - s * 0.18f), Offset(cx - s * 0.21f, cy + s * 0.17f), Offset(cx + s * 0.21f, cy + s * 0.17f))
    }
    for (p in positions) drawGameShape(kind, p, shapeSize, color, cell.fill)
}

@Composable
fun MatrixTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { MatrixGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    var picked by remember { mutableStateOf(-1) }
    val c = Synapse.colors
    TrialLayout {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .border(1.dp, c.stroke, RoundedCornerShape(22.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (r in 0 until 3) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 3) {
                        val i = r * 3 + col
                        Canvas(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceStrong.copy(alpha = 0.6f))
                        ) {
                            if (i == 8) {
                                drawRoundRect(
                                    ctx.accent, cornerRadius = CornerRadius(14.dp.toPx()),
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                                )
                            } else drawMatrixCell(task, task.grid[i], c.gameColors)
                        }
                    }
                }
            }
        }
        HintText("Welches Feld gehört unten rechts hin?")
        Spacer(Modifier.weight(1f))
        val perRow = if (task.options.size > 4) 3 else 4
        for (row in task.options.indices.chunked(perRow)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in row) {
                    ChoiceButton(
                        modifier = Modifier.weight(1f),
                        accent = ctx.accent,
                        height = if (perRow == 4) 80.dp else 92.dp,
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
                                if (i == task.correctIndex) TrialResult.right(since(start))
                                else TrialResult.wrong(since(start), task.rules.joinToString("\n"))
                            )
                        },
                    ) {
                        Canvas(Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = "Antwort ${i + 1}" }) {
                            drawMatrixCell(task, task.options[i], c.gameColors)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Schlussfolgern

/** Karte, die nach links (Nein) oder rechts (Ja) gewischt werden kann. */
@Composable
fun SwipeDecisionCard(
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onDecide: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val c = Synapse.colors
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val tint = (offset.value / threshold).coerceIn(-1f, 1f)
    Box(
        modifier
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 28f
            }
            .clip(RoundedCornerShape(26.dp))
            .background(c.surfaceStrong)
            .background(if (tint > 0) c.good.copy(alpha = tint * 0.25f) else c.bad.copy(alpha = -tint * 0.25f))
            .border(1.5.dp, if (abs(tint) > 0.2f) (if (tint > 0) c.good else c.bad) else accent.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (abs(v) > threshold) {
                                onDecide(v > 0)
                                offset.animateTo(if (v > 0) size.width * 1.5f else -size.width * 1.5f, tween(220))
                                offset.snapTo(0f)
                            } else offset.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + amount) }
                    },
                )
            }
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (abs(tint) > 0.2f) {
            Text(
                if (tint > 0) "JA" else "NEIN",
                modifier = Modifier.align(if (tint > 0) Alignment.TopStart else Alignment.TopEnd),
                color = if (tint > 0) c.good else c.bad, fontWeight = FontWeight.Black, fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun YesNoButtons(enabled: Boolean, onDecide: (Boolean) -> Unit) {
    val c = Synapse.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceButton(Modifier.weight(1f), accent = c.bad, onClick = { if (enabled) onDecide(false) }) {
            ChoiceText("✕  Nein")
        }
        ChoiceButton(Modifier.weight(1f), accent = c.good, onClick = { if (enabled) onDecide(true) }) {
            ChoiceText("✓  Ja")
        }
    }
}

@Composable
fun SyllogismTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { SyllogismGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    fun decide(yes: Boolean) = once.report(
        if (yes == task.valid) TrialResult.right(since(start), task.type)
        else TrialResult.wrong(since(start), (if (task.valid) "Ja – " else "Nein – ") + task.explanation, task.type)
    )
    TrialLayout {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in task.premises) {
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Text(p, color = c.textPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("FOLGT DARAUS ZWINGEND?", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        SwipeDecisionCard(ctx.accent, enabled = !once.done, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), onDecide = ::decide) {
            Text(
                task.conclusion, color = c.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
            )
        }
        HintText("← wischen für Nein · wischen für Ja →")
        Spacer(Modifier.weight(1f))
        YesNoButtons(!once.done, ::decide)
    }
}

// ---------------------------------------------------------------- Waage

val SHAPE_NAMES = listOf("Kreis", "Quadrat", "Dreieck", "Raute", "Stern", "Sechseck")

private fun DrawScope.drawScale(heavy: Int, light: Int, heavyLeft: Boolean, colors: List<Color>, beam: Color) {
    val w = size.width
    val h = size.height
    val pivot = Offset(w / 2, h * 0.22f)
    val tilt = if (heavyLeft) -11f else 11f
    // Ständer
    drawLine(beam, Offset(w / 2, h * 0.22f), Offset(w / 2, h * 0.92f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(beam, Offset(w * 0.36f, h * 0.92f), Offset(w * 0.64f, h * 0.92f), 3.dp.toPx(), StrokeCap.Round)
    val arm = w * 0.38f
    val rad = Math.toRadians(tilt.toDouble())
    // Ein negativer Winkel hebt die rechte Seite: die linke (schwere) Seite liegt tiefer.
    val leftEnd = Offset(pivot.x - arm * kotlin.math.cos(rad).toFloat(), pivot.y - arm * kotlin.math.sin(rad).toFloat())
    val rightEnd = Offset(pivot.x + arm * kotlin.math.cos(rad).toFloat(), pivot.y + arm * kotlin.math.sin(rad).toFloat())
    drawLine(beam, leftEnd, rightEnd, 3.dp.toPx(), StrokeCap.Round)
    drawCircle(beam, 4.dp.toPx(), pivot)
    val pan = h * 0.48f
    val objSize = w * 0.17f
    val leftObj = if (heavyLeft) heavy else light
    val rightObj = if (heavyLeft) light else heavy
    for ((end, obj) in listOf(leftEnd to leftObj, rightEnd to rightObj)) {
        drawLine(beam.copy(alpha = 0.6f), end, end + Offset(0f, pan), 1.5.dp.toPx())
        drawLine(beam, end + Offset(-objSize * 0.7f, pan), end + Offset(objSize * 0.7f, pan), 3.dp.toPx(), StrokeCap.Round)
        drawGameShape(obj, end + Offset(0f, pan - objSize * 0.62f), objSize, colors[obj % colors.size])
    }
}

@Composable
fun BalanceTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { BalanceGen.generate(ctx.level, ctx.rng()) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    var picked by remember { mutableStateOf(-1) }
    val c = Synapse.colors
    TrialLayout {
        for (row in task.comparisons.indices.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in row) {
                    val (heavy, light) = task.comparisons[i]
                    Canvas(
                        Modifier
                            .weight(1f)
                            .height(124.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.surface)
                            .semantics { contentDescription = "Waage: ${SHAPE_NAMES[heavy]} ist schwerer als ${SHAPE_NAMES[light]}" }
                    ) { drawScale(heavy, light, task.heavyOnLeft[i], c.gameColors, c.textSecondary) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (task.askHeaviest) "Was ist am SCHWERSTEN?" else "Was ist am LEICHTESTEN?",
            color = c.textPrimary, style = MaterialTheme.typography.titleLarge,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (obj in 0 until task.objectCount) {
                ChoiceButton(
                    modifier = Modifier.weight(1f),
                    accent = ctx.accent,
                    height = 70.dp,
                    state = when {
                        picked < 0 -> ChoiceState.NORMAL
                        obj == task.answer -> ChoiceState.CORRECT
                        obj == picked -> ChoiceState.WRONG
                        else -> ChoiceState.DIMMED
                    },
                    onClick = {
                        if (picked >= 0) return@ChoiceButton
                        picked = obj
                        once.report(
                            if (obj == task.answer) TrialResult.right(since(start))
                            else TrialResult.wrong(since(start), "Schwer → leicht: " + task.order.joinToString(" > ") { SHAPE_NAMES[it] })
                        )
                    },
                ) {
                    Canvas(Modifier.fillMaxSize().padding(14.dp).semantics { contentDescription = SHAPE_NAMES[obj] }) {
                        drawGameShape(obj, center, size.minDimension, c.gameColors[obj % c.gameColors.size])
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
