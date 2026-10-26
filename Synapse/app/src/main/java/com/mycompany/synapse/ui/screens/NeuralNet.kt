package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.mycompany.synapse.ui.components.reducedMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.ui.components.drawDomainGlyph
import com.mycompany.synapse.ui.theme.Palette
import com.mycompany.synapse.ui.theme.Synapse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Neuron(val x: Float, val y: Float, val speed: Float, val phase: Float, val radius: Float)

/** Position eines Bereichs-Knotens – geteilt von Zeichnung und Tipp-Erkennung. */
private fun nodeCenter(i: Int, size: Size, t: Float, breath: Float): Offset {
    val c = Offset(size.width / 2, size.height / 2)
    val r = minOf(size.width, size.height) * 0.355f
    val ang = (-90.0 + i * 60.0) * PI / 180
    val wobble = sin(t * 0.6f + i * 1.3f) * breath
    return c + Offset(((r + wobble) * cos(ang)).toFloat(), ((r + wobble) * sin(ang)).toFloat())
}

/**
 * Das lebendige Neuronen-Netz des Startbildschirms.
 * Jeder Bereich ist ein Knoten: je mehr du trainierst, desto heller leuchtet er und desto mehr
 * Impulse fließen. Lange nicht trainierte Bereiche dimmen langsam ab. In der Mitte: das Tagestraining.
 */
@Composable
fun NeuralNetwork(
    vitality: Map<Domain, Float>,
    indices: Map<Domain, Int>,
    workoutColors: List<Color>,
    workoutDone: List<Boolean>,
    modifier: Modifier = Modifier,
    onDomain: (Domain) -> Unit,
    onCenter: () -> Unit,
) {
    val c = Synapse.colors
    val tm = rememberTextMeasurer()
    val still = reducedMotion()
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) withInfiniteAnimationFrameNanos { now -> time = (now - start) / 1_000_000_000f }
    }
    val neurons = remember {
        val rng = Random(42)
        List(44) { Neuron(rng.nextFloat(), rng.nextFloat(), 0.2f + rng.nextFloat() * 0.5f, rng.nextFloat() * 6.28f, 1f + rng.nextFloat() * 1.6f) }
    }
    // Nachbarschaften nur einmal bestimmen – die Punkte bewegen sich nur minimal
    val pairs = remember(neurons) {
        buildList {
            for (i in neurons.indices) for (j in i + 1 until neurons.size) {
                if (hypot(neurons[i].x - neurons[j].x, neurons[i].y - neurons[j].y) < 0.19f) add(i to j)
            }
        }
    }
    // Beschriftungen nur neu messen, wenn sich Werte oder Farben ändern
    val labels = remember(indices, c) {
        Domain.entries.map { d ->
            val idx = indices[d]
            tm.measure(d.title, TextStyle(color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)) to
                tm.measure(idx?.toString() ?: "neu", TextStyle(color = if (idx != null) c.domain(d) else c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
    val doneCount = workoutDone.count { it }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val sizePx = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val center = Offset(size.width / 2, size.height / 2)
            drawAmbient(neurons, pairs, t, c)
            val breath = if (still) 0f else 3.dp.toPx()
            val nodes = Domain.entries.map { nodeCenter(it.ordinal, size, t, breath) }

            // Verbindungen: Ring und Speichen
            for (d in Domain.entries) {
                val i = d.ordinal
                val v = vitality[d] ?: 0f
                val col = c.domain(d)
                val next = nodes[(i + 1) % nodes.size]
                drawLine(c.textMuted.copy(alpha = 0.16f), nodes[i], next, 1.dp.toPx())
                drawLine(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f + v * 0.2f), col.copy(alpha = 0.25f + v * 0.55f)), center, nodes[i]),
                    center, nodes[i], (1.2f + v * 1.6f).dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (v < 0.05f) PathEffect.dashPathEffect(floatArrayOf(8f, 10f)) else null,
                )
                // Impulse, die von der Mitte zum Knoten wandern
                val count = 1 + (v * 3).toInt()
                for (k in 0 until count) {
                    val p = ((t * (0.18f + v * 0.35f) + k.toFloat() / count + i * 0.13f) % 1f)
                    val pos = center + (nodes[i] - center) * p
                    val a = sin(p * PI).toFloat() * (0.35f + v * 0.65f)
                    drawCircle(col.copy(alpha = a * 0.35f), 6.dp.toPx(), pos)
                    drawCircle(col.copy(alpha = a), 2.4.dp.toPx(), pos)
                }
            }

            // Knoten
            for (d in Domain.entries) {
                val i = d.ordinal
                val v = vitality[d] ?: 0f
                val col = c.domain(d)
                val p = nodes[i]
                val r = (25f + v * 8f).dp.toPx()
                val pulse = 1f + sin(t * 1.6f + i) * 0.06f * (0.3f + v)
                drawCircle(
                    Brush.radialGradient(listOf(col.copy(alpha = 0.12f + v * 0.42f), Color.Transparent), p, r * 2.5f * pulse),
                    r * 2.5f * pulse, p,
                )
                drawCircle(c.surfaceStrong, r, p)
                drawCircle(col.copy(alpha = 0.18f + v * 0.2f), r, p)
                drawCircle(col.copy(alpha = 0.45f + v * 0.55f), r, p, style = Stroke(2.dp.toPx()))
                drawDomainGlyph(d, p, r * 0.95f, col.copy(alpha = 0.65f + v * 0.35f))
                val (title, sub) = labels[i]
                val below = p.y + r + 4.dp.toPx()
                drawText(title, topLeft = Offset(p.x - title.size.width / 2f, below))
                drawText(sub, topLeft = Offset(p.x - sub.size.width / 2f, below + title.size.height))
            }

            drawWorkoutCore(center, t, workoutColors, workoutDone, c, tm)
        }
        // Unsichtbare, große Tippflächen – auch für Screenreader beschriftet
        for (d in Domain.entries) {
            val p = nodeCenter(d.ordinal, sizePx, 0f, 0f)
            val half = with(density) { 40.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((p.x - half).toInt(), (p.y - half).toInt()) }
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable { onDomain(d) }
                    .semantics {
                        contentDescription = "${d.title}, " + (indices[d]?.let { "Index $it" } ?: "noch nicht eingestuft") + ". Spiele öffnen"
                        role = Role.Button
                    }
            )
        }
        val half = with(density) { 66.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset((sizePx.width / 2 - half).toInt(), (sizePx.height / 2 - half).toInt()) }
                .size(132.dp)
                .clip(CircleShape)
                .clickable(onClick = onCenter)
                .semantics {
                    contentDescription = "Tagestraining, $doneCount von ${workoutColors.size} erledigt. Starten"
                    role = Role.Button
                }
        )
    }
}

private fun DrawScope.drawAmbient(neurons: List<Neuron>, pairs: List<Pair<Int, Int>>, t: Float, c: Palette) {
    val pts = neurons.map { n ->
        Offset(
            (n.x + sin(t * n.speed * 0.3f + n.phase) * 0.025f) * size.width,
            (n.y + cos(t * n.speed * 0.25f + n.phase) * 0.025f) * size.height,
        )
    }
    val maxDist = size.minDimension * 0.17f
    val base = if (c.dark) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
    for ((i, j) in pairs) {
        val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
        if (d < maxDist) drawLine(base.copy(alpha = (1 - d / maxDist) * 0.10f), pts[i], pts[j], 1f)
    }
    for ((i, p) in pts.withIndex()) {
        val twinkle = 0.25f + 0.25f * sin(t * 1.2f + neurons[i].phase)
        drawCircle(base.copy(alpha = twinkle), neurons[i].radius.dp.toPx(), p)
    }
}

private fun DrawScope.drawWorkoutCore(
    center: Offset,
    t: Float,
    colors: List<Color>,
    done: List<Boolean>,
    c: Palette,
    tm: androidx.compose.ui.text.TextMeasurer,
) {
    val ringR = 54.dp.toPx()
    val complete = done.isNotEmpty() && done.all { it }
    val pulse = if (complete) 1f else 1f + sin(t * 2.2f) * 0.05f
    val glowColor = if (complete) c.good else c.domain(Domain.RECHNEN)
    drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.30f), Color.Transparent), center, ringR * 1.9f * pulse), ringR * 1.9f * pulse, center)
    drawCircle(c.surfaceStrong, ringR - 6.dp.toPx(), center)
    val n = colors.size.coerceAtLeast(1)
    val gap = if (n > 1) 10f else 0f
    val sweep = 360f / n - gap
    val stroke = 9.dp.toPx()
    val tl = center - Offset(ringR, ringR)
    for (i in 0 until n) {
        val col = colors.getOrElse(i) { c.textMuted }
        val isDone = done.getOrElse(i) { false }
        drawArc(
            col.copy(alpha = if (isDone) 1f else 0.22f), -90f + gap / 2 + i * (sweep + gap), sweep, false,
            tl, Size(ringR * 2, ringR * 2), style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    val doneCount = done.count { it }
    if (complete) {
        val big = tm.measure("✓", TextStyle(color = c.good, fontSize = 30.sp, fontWeight = FontWeight.Black))
        drawText(big, topLeft = center - Offset(big.size.width / 2f, big.size.height * 0.72f))
        val small = tm.measure("Geschafft", TextStyle(color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
        drawText(small, topLeft = center + Offset(-small.size.width / 2f, big.size.height * 0.25f))
    } else {
        val label = tm.measure("TAGES-\nTRAINING", TextStyle(color = c.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 1.sp))
        drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height.toFloat() + 1.dp.toPx()))
        val count = tm.measure("$doneCount/$n", TextStyle(color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black))
        drawText(count, topLeft = center - Offset(count.size.width / 2f, -1.dp.toPx()))
    }
}
