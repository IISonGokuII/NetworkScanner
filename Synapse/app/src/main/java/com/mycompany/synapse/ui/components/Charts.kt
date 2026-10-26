package com.mycompany.synapse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.ui.theme.Synapse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val dayFmt = DateTimeFormatter.ofPattern("dd.MM.")
private val longDayFmt = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", java.util.Locale.GERMAN)

data class ChartPoint(val day: LocalDate, val value: Float)

/** Verlaufslinie mit Fadenkreuz: Finger auf das Diagramm legen und verschieben zeigt den Wert. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 190,
    format: (Float) -> String = { it.toInt().toString() },
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    ticks: Int = 3,
) {
    val c = Synapse.colors
    val tm = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val labelStyle = TextStyle(color = c.textMuted, fontSize = 10.sp)
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
            Text("Noch keine Daten", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val rawMin = fixedMin ?: points.minOf { it.value }
    val rawMax = fixedMax ?: points.maxOf { it.value }
    val span = (rawMax - rawMin).let { if (it < 1e-3f) maxOf(abs(rawMax) * 0.2f, 1f) else it }
    val yMin = fixedMin ?: floor(rawMin - span * 0.12f)
    val yMax = fixedMax ?: (yMin + ticks * maxOf(ceil((rawMax + span * 0.12f - yMin) / ticks), 1f))
    val d0 = points.first().day.toEpochDay()
    val d1 = points.last().day.toEpochDay().let { if (it == d0) d0 + 1 else it }

    val summary = "Verlauf vom ${points.first().day.format(dayFmt)} bis ${points.last().day.format(dayFmt)}: " +
        "von ${format(points.first().value)} auf ${format(points.last().value)}, höchster Wert ${format(points.maxOf { it.value })}"
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics { contentDescription = summary }
            .pointerInput(points) {
                detectTapGestures(onPress = { pos ->
                    val l = 34.dp.toPx()
                    selected = nearest(points, pos.x, l, size.width - l - 10.dp.toPx(), d0, d1)
                    tryAwaitRelease()
                })
            }
            .pointerInput(points) {
                val l = 34.dp.toPx()
                val w = size.width - l - 10.dp.toPx()
                detectDragGestures(
                    onDragStart = { pos -> selected = nearest(points, pos.x, l, w, d0, d1) },
                    onDrag = { change, _ -> selected = nearest(points, change.position.x, l, w, d0, d1) },
                )
            }
    ) {
        val left = 34.dp.toPx()
        val right = 10.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 20.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun x(day: LocalDate) = left + w * (day.toEpochDay() - d0).toFloat() / (d1 - d0)
        fun y(v: Float) = top + h * (1f - (v - yMin) / (yMax - yMin))

        // Raster (zurückhaltend)
        for (k in 0..ticks) {
            val v = yMin + (yMax - yMin) * k / ticks
            val yy = y(v)
            drawLine(c.gridLine, Offset(left, yy), Offset(left + w, yy), 1.dp.toPx())
            val t = tm.measure(format(v), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
        }
        val first = tm.measure(points.first().day.format(dayFmt), labelStyle)
        drawText(first, topLeft = Offset(left, size.height - first.size.height))
        if (points.size > 1) {
            val last = tm.measure(points.last().day.format(dayFmt), labelStyle)
            drawText(last, topLeft = Offset(left + w - last.size.width, size.height - last.size.height))
        }

        val pts = points.map { Offset(x(it.day), y(it.value)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        if (pts.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, top + h)
                lineTo(pts.first().x, top + h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = top + h))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val lastP = pts.last()
        drawCircle(c.surfaceStrong, 6.dp.toPx(), lastP)
        drawCircle(color, 4.dp.toPx(), lastP)

        selected?.let { i ->
            val p = pts[i]
            drawLine(c.textMuted, Offset(p.x, top), Offset(p.x, top + h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawCircle(c.surfaceStrong, 7.dp.toPx(), p)
            drawCircle(color, 5.dp.toPx(), p)
            val label = tm.measure(
                "${points[i].day.format(longDayFmt)}\n${format(points[i].value)}",
                TextStyle(color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
            val pad = 8.dp.toPx()
            val bw = label.size.width + pad * 2
            val bh = label.size.height + pad * 2
            val bx = (p.x - bw / 2).coerceIn(0f, size.width - bw)
            val by = (p.y - bh - 12.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(c.surfaceStrong, Offset(bx, by), Size(bw, bh), CornerRadius(10.dp.toPx()))
            drawRoundRect(c.stroke, Offset(bx, by), Size(bw, bh), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(label, topLeft = Offset(bx + pad, by + pad))
        }
    }
}

private fun nearest(points: List<ChartPoint>, px: Float, left: Float, width: Float, d0: Long, d1: Long): Int {
    val rel = ((px - left) / width).coerceIn(0f, 1f)
    val day = d0 + rel * (d1 - d0)
    return points.indices.minBy { abs(points[it].day.toEpochDay() - day) }
}

/** Kleine Verlaufslinie ohne Achsen. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 1e-6 } ?: 1.0
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, (size.height - 4f) * (1f - ((v - min) / span).toFloat()) + 2f)
        }
        if (pts.size == 1) {
            drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

data class RadarSeries(val label: String, val values: List<Float>, val color: Color, val dashed: Boolean)

/** Netzdiagramm des Gehirnprofils; mehrere Zeitpunkte übereinander zeigen die Entwicklung. */
@Composable
fun RadarChart(
    axes: List<String>,
    axisColors: List<Color>,
    series: List<RadarSeries>,
    maxValue: Float,
    modifier: Modifier = Modifier,
) {
    val c = Synapse.colors
    val tm = rememberTextMeasurer()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(270.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = minOf(size.width, size.height) / 2 - 40.dp.toPx()
            val n = axes.size
            fun point(i: Int, frac: Float): Offset {
                val ang = -PI / 2 + i * 2 * PI / n
                return center + Offset((cos(ang) * radius * frac).toFloat(), (sin(ang) * radius * frac).toFloat())
            }
            for (ring in 1..4) {
                val path = Path().apply {
                    for (i in 0 until n) {
                        val p = point(i, ring / 4f)
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(path, c.gridLine, style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) drawLine(c.gridLine, center, point(i, 1f), 1.dp.toPx())
            for (s in series) {
                val path = Path().apply {
                    for (i in 0 until n) {
                        val p = point(i, (s.values[i] / maxValue).coerceIn(0f, 1f))
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                if (s.dashed) {
                    drawPath(path, s.color, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                } else {
                    drawPath(path, s.color.copy(alpha = 0.22f))
                    drawPath(path, s.color, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                    for (i in 0 until n) {
                        val p = point(i, (s.values[i] / maxValue).coerceIn(0f, 1f))
                        drawCircle(c.surfaceStrong, 5.dp.toPx(), p)
                        drawCircle(axisColors[i], 3.5.dp.toPx(), p)
                    }
                }
            }
            val current = series.lastOrNull()
            for (i in 0 until n) {
                val p = point(i, 1.18f)
                val value = current?.values?.getOrNull(i)?.toInt() ?: 0
                val t = tm.measure(
                    "${axes[i]}\n$value",
                    TextStyle(color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
                drawText(t, topLeft = p - Offset(t.size.width / 2f, t.size.height / 2f))
            }
        }
        // Legende
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
            for (s in series) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(22.dp).height(10.dp)) {
                        drawLine(
                            s.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(),
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                        )
                    }
                    Text("  ${s.label}", color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Trainingskalender: eine Spalte pro Woche, Farbe = Trainingsminuten. */
@Composable
fun TrainingHeatmap(minutes: Map<LocalDate, Int>, today: LocalDate, weeks: Int = 18, modifier: Modifier = Modifier) {
    val c = Synapse.colors
    val tm = rememberTextMeasurer()
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    fun level(m: Int) = when {
        m <= 0 -> 0
        m < 5 -> 1
        m < 10 -> 2
        m < 20 -> 3
        else -> 4
    }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(today) {
                    detectTapGestures { pos ->
                        val left = 22.dp.toPx()
                        val top = 16.dp.toPx()
                        val cell = minOf((size.width - left) / weeks, (size.height - top) / 7f)
                        val col = ((pos.x - left) / cell).toInt()
                        val row = ((pos.y - top) / cell).toInt()
                        if (col in 0 until weeks && row in 0..6) {
                            val d = firstMonday.plusWeeks(col.toLong()).plusDays(row.toLong())
                            selected = if (d.isAfter(today)) null else d
                        }
                    }
                }
        ) {
            val left = 22.dp.toPx()
            val top = 16.dp.toPx()
            val cell = minOf((size.width - left) / weeks, (size.height - top) / 7f)
            val gap = 2.dp.toPx()
            val labelStyle = TextStyle(color = c.textMuted, fontSize = 9.sp)
            listOf(0 to "Mo", 2 to "Mi", 4 to "Fr", 6 to "So").forEach { (r, s) ->
                val t = tm.measure(s, labelStyle)
                drawText(t, topLeft = Offset(0f, top + r * cell + (cell - t.size.height) / 2))
            }
            var lastMonth = firstMonday.monthValue
            var labelEnd = 0f
            for (w in 0 until weeks) {
                val weekStart = firstMonday.plusWeeks(w.toLong())
                // Beschriftung in der ersten Woche, die in einem neuen Monat beginnt
                if (weekStart.monthValue != lastMonth || (w == 0 && weekStart.dayOfMonth <= 7)) {
                    lastMonth = weekStart.monthValue
                    val t = tm.measure(weekStart.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN), labelStyle)
                    val x = left + w * cell
                    if (x >= labelEnd && x + t.size.width < size.width) {
                        drawText(t, topLeft = Offset(x, 0f))
                        labelEnd = x + t.size.width + 4.dp.toPx()
                    }
                }
                for (d in 0 until 7) {
                    val day = weekStart.plusDays(d.toLong())
                    if (day.isAfter(today)) continue
                    val m = minutes[day] ?: 0
                    val tl = Offset(left + w * cell + gap / 2, top + d * cell + gap / 2)
                    val sz = Size(cell - gap, cell - gap)
                    drawRoundRect(c.heatRamp[level(m)], tl, sz, CornerRadius(3.dp.toPx()))
                    if (day == selected) drawRoundRect(c.textPrimary, tl, sz, CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    if (day == today) drawRoundRect(c.textSecondary, tl, sz, CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                selected?.let { "${it.format(longDayFmt)}: ${minutes[it] ?: 0} Min." } ?: "Tippe auf einen Tag",
                color = c.textSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
            )
            Text("weniger ", color = c.textMuted, style = MaterialTheme.typography.labelSmall)
            for (col in c.heatRamp) {
                Canvas(Modifier.size(11.dp)) { drawRoundRect(col, cornerRadius = CornerRadius(2.dp.toPx())) }
                Box(Modifier.width(2.dp))
            }
            Text(" mehr", color = c.textMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
