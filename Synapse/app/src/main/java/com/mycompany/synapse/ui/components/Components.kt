package com.mycompany.synapse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.ui.theme.Synapse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Grundflächen

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val c = Synapse.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.domain(Domain.LOGIK).copy(alpha = if (c.dark) 0.10f else 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f, center = Offset(size.width * 0.85f, size.height * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.domain(Domain.RECHNEN).copy(alpha = if (c.dark) 0.08f else 0.05f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.75f), radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.1f, size.height * 0.75f),
                )
            },
        content = content,
    )
}

/** Leichtes Eindrücken beim Antippen – Buttons fühlen sich „physisch“ an. */
@Composable
fun pressedScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 900f), label = "press")
    return s
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Synapse.colors
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale = if (onClick != null) pressedScale(interaction) else 1f
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(c.surface)
            .background(
                Brush.linearGradient(
                    listOf((accent ?: Color.White).copy(alpha = if (c.dark) 0.09f else 0.06f), Color.Transparent),
                )
            )
            .border(
                BorderStroke(1.dp, Brush.linearGradient(listOf((accent ?: c.textPrimary).copy(alpha = 0.30f), c.stroke))),
                shape,
            )
            .then(if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Synapse.colors.domain(Domain.RECHNEN),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressedScale(interaction)
    val shape = RoundedCornerShape(20.dp)
    val c = Synapse.colors
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color, lerpColor(color, if (c.dark) Color(0xFF6366F1) else Color(0xFF4338CA), 0.35f))))
            .clickable(interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (c.dark) Color(0xFF05070F) else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = Synapse.colors.textPrimary, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressedScale(interaction)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Synapse.colors.surface)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clickable(interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Synapse.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else c.surface)
            .border(1.dp, if (selected) color else c.stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.textPrimary else c.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val c = Synapse.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = c.textPrimary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Synapse.colors.textMuted,
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, hintColor: Color? = null) {
    val c = Synapse.colors
    GlassCard(modifier, padding = 14.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = c.textPrimary, maxLines = 1)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelMedium, color = hintColor ?: c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
fun LevelBadge(level: Int, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("Level $level", color = Synapse.colors.textPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier, stroke: Dp = 8.dp, track: Color = Synapse.colors.gridLine) {
    Canvas(modifier) {
        val w = stroke.toPx()
        val d = min(size.width, size.height) - w
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(w))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

// ---------------------------------------------------------------- Symbole

@Composable
fun DomainGlyph(domain: Domain, color: Color, modifier: Modifier = Modifier.size(28.dp)) {
    Canvas(modifier) { drawDomainGlyph(domain, center, min(size.width, size.height), color) }
}

fun DrawScope.drawDomainGlyph(domain: Domain, c: Offset, s: Float, color: Color) {
    val w = s * 0.09f
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val h = s / 2f
    when (domain) {
        Domain.RECHNEN -> {
            val q = s * 0.22f
            val a = s * 0.11f
            // +
            drawLine(color, Offset(c.x - q - a, c.y - q), Offset(c.x - q + a, c.y - q), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - q, c.y - q - a), Offset(c.x - q, c.y - q + a), w, StrokeCap.Round)
            // −
            drawLine(color, Offset(c.x + q - a, c.y - q), Offset(c.x + q + a, c.y - q), w, StrokeCap.Round)
            // ×
            drawLine(color, Offset(c.x - q - a * 0.8f, c.y + q - a * 0.8f), Offset(c.x - q + a * 0.8f, c.y + q + a * 0.8f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - q + a * 0.8f, c.y + q - a * 0.8f), Offset(c.x - q - a * 0.8f, c.y + q + a * 0.8f), w, StrokeCap.Round)
            // =
            drawLine(color, Offset(c.x + q - a, c.y + q - a * 0.45f), Offset(c.x + q + a, c.y + q - a * 0.45f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x + q - a, c.y + q + a * 0.45f), Offset(c.x + q + a, c.y + q + a * 0.45f), w, StrokeCap.Round)
        }
        Domain.LOGIK -> {
            val p1 = Offset(c.x, c.y - h * 0.62f)
            val p2 = Offset(c.x - h * 0.6f, c.y + h * 0.42f)
            val p3 = Offset(c.x + h * 0.6f, c.y + h * 0.42f)
            drawLine(color.copy(alpha = 0.7f), p1, p2, w * 0.8f)
            drawLine(color.copy(alpha = 0.7f), p1, p3, w * 0.8f)
            drawLine(color.copy(alpha = 0.7f), p2, p3, w * 0.8f)
            for (p in listOf(p1, p2, p3)) drawCircle(color, s * 0.11f, p)
        }
        Domain.GEDAECHTNIS -> {
            val cell = s * 0.2f
            val gap = s * 0.07f
            val start = c - Offset(cell * 1.5f + gap, cell * 1.5f + gap)
            val filled = setOf(0, 4, 5, 7)
            for (i in 0 until 9) {
                val tl = start + Offset((i % 3) * (cell + gap), (i / 3) * (cell + gap))
                if (i in filled) drawRoundRect(color, tl, Size(cell, cell), CornerRadius(cell * 0.3f))
                else drawRoundRect(color.copy(alpha = 0.45f), tl, Size(cell, cell), CornerRadius(cell * 0.3f), style = Stroke(w * 0.6f))
            }
        }
        Domain.FOKUS -> {
            drawCircle(color, h * 0.82f, c, style = stroke)
            drawCircle(color.copy(alpha = 0.75f), h * 0.48f, c, style = stroke)
            drawCircle(color, h * 0.16f, c)
        }
        Domain.RAUM -> {
            val r = h * 0.85f
            val pts = (0 until 6).map { k ->
                val ang = (-90 + k * 60) * PI / 180
                Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
            }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
                close()
            }
            drawPath(path, color.copy(alpha = 0.18f))
            drawPath(path, color, style = stroke)
            drawLine(color, c, pts[0], w, StrokeCap.Round)
            drawLine(color, c, pts[2], w, StrokeCap.Round)
            drawLine(color, c, pts[4], w, StrokeCap.Round)
        }
        Domain.SPRACHE -> {
            val tl = Offset(c.x - h * 0.85f, c.y - h * 0.7f)
            val bs = Size(h * 1.7f, h * 1.15f)
            drawRoundRect(color, tl, bs, CornerRadius(s * 0.14f), style = stroke)
            val tail = Path().apply {
                moveTo(c.x - h * 0.35f, tl.y + bs.height - w / 2)
                lineTo(c.x - h * 0.55f, c.y + h * 0.82f)
                lineTo(c.x + h * 0.05f, tl.y + bs.height - w / 2)
            }
            drawPath(tail, color, style = stroke)
            drawLine(color, Offset(c.x - h * 0.45f, c.y - h * 0.28f), Offset(c.x + h * 0.45f, c.y - h * 0.28f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - h * 0.45f, c.y + h * 0.05f), Offset(c.x + h * 0.15f, c.y + h * 0.05f), w, StrokeCap.Round)
        }
    }
}

/** Formen für Matrix, Waage & Co. kind 0..5, fill 0 = gefüllt, 1 = Umriss, 2 = Umriss mit Punkt. */
fun DrawScope.drawGameShape(kind: Int, center: Offset, size: Float, color: Color, fill: Int = 0, rotation: Float = 0f) {
    val r = size / 2f
    val path = Path()
    fun poly(n: Int, start: Double, radius: Float = r) {
        for (k in 0 until n) {
            val ang = start + k * 2 * PI / n + rotation * PI / 180
            val x = center.x + radius * cos(ang).toFloat()
            val y = center.y + radius * sin(ang).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    when (kind % 6) {
        0 -> path.addOval(androidx.compose.ui.geometry.Rect(center, r * 0.92f))
        1 -> poly(4, PI / 4, r * 1.12f)
        2 -> poly(3, -PI / 2, r * 1.08f)
        3 -> poly(4, -PI / 2, r * 1.05f)
        4 -> {
            for (k in 0 until 10) {
                val ang = -PI / 2 + k * PI / 5 + rotation * PI / 180
                val rad = if (k % 2 == 0) r * 1.08f else r * 0.48f
                val x = center.x + rad * cos(ang).toFloat()
                val y = center.y + rad * sin(ang).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        else -> poly(6, 0.0, r * 0.98f)
    }
    val sw = (size * 0.11f).coerceAtLeast(2f)
    when (fill) {
        0 -> drawPath(path, color)
        1 -> drawPath(path, color, style = Stroke(sw, join = StrokeJoin.Round))
        else -> {
            drawPath(path, color, style = Stroke(sw, join = StrokeJoin.Round))
            drawCircle(color, size * 0.14f, center)
        }
    }
}

// ---------------------------------------------------------------- Effekte

/** Partikel-Explosion bei richtigen Antworten. Bei jeder Änderung von [trigger] neu. */
@Composable
fun ParticleBurst(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val particles = remember(trigger) {
        val rng = Random(trigger * 7919 + 13)
        List(28) { Triple(rng.nextFloat() * 2f * PI.toFloat(), 0.45f + rng.nextFloat() * 0.75f, 2.5f + rng.nextFloat() * 4.5f) }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(750, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val reach = size.minDimension * 0.42f
        val origin = Offset(size.width / 2, size.height * 0.42f)
        drawCircle(color.copy(alpha = (1 - p) * 0.55f), reach * 0.55f * p, origin, style = Stroke((1 - p) * 10.dp.toPx()))
        for ((ang, speed, rad) in particles) {
            val dist = reach * speed * p
            val pos = origin + Offset(cos(ang) * dist, sin(ang) * dist)
            drawCircle(color.copy(alpha = (1 - p) * 0.9f), rad.dp.toPx() * (1 - p * 0.5f), pos)
        }
    }
}

// ---------------------------------------------------------------- Eingabe

@Composable
fun NumberPad(
    accent: Color,
    modifier: Modifier = Modifier,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in keys.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in row) {
                    PadKey(
                        label = k,
                        accent = accent,
                        highlight = k == "✓",
                        modifier = Modifier.weight(1f),
                    ) {
                        when (k) {
                            "⌫" -> onDelete()
                            "✓" -> onSubmit()
                            else -> onDigit(k.toInt())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PadKey(label: String, accent: Color, highlight: Boolean, modifier: Modifier = Modifier, height: Dp = 60.dp, onClick: () -> Unit) {
    val c = Synapse.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = pressedScale(interaction)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (highlight) accent.copy(alpha = 0.28f) else c.surface)
            .border(1.dp, if (highlight) accent else c.stroke, shape)
            .clickable(interaction, indication = androidx.compose.material3.ripple(color = accent), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

enum class ChoiceState { NORMAL, CORRECT, WRONG, DIMMED }

@Composable
fun ChoiceButton(
    modifier: Modifier = Modifier,
    accent: Color,
    state: ChoiceState = ChoiceState.NORMAL,
    height: Dp = 64.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Synapse.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = pressedScale(interaction)
    val shape = RoundedCornerShape(18.dp)
    val border = when (state) {
        ChoiceState.CORRECT -> c.good
        ChoiceState.WRONG -> c.bad
        ChoiceState.DIMMED -> c.stroke
        ChoiceState.NORMAL -> accent.copy(alpha = 0.45f)
    }
    val bg = when (state) {
        ChoiceState.CORRECT -> c.good.copy(alpha = 0.2f)
        ChoiceState.WRONG -> c.bad.copy(alpha = 0.2f)
        else -> c.surface
    }
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (state == ChoiceState.DIMMED) 0.5f else 1f }
            .clip(shape)
            .background(bg)
            .border(1.5.dp, border, shape)
            .clickable(interaction, indication = androidx.compose.material3.ripple(color = accent), onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun ChoiceText(text: String, modifier: Modifier = Modifier, size: Int = 20) {
    Text(
        text, modifier = modifier.padding(horizontal = 8.dp), color = Synapse.colors.textPrimary,
        fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2,
    )
}

/** Großes Anzeigefeld für Zahleneingaben. */
@Composable
fun AnswerDisplay(text: String, accent: Color, modifier: Modifier = Modifier, placeholder: String = "?") {
    val c = Synapse.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, accent.copy(alpha = if (text.isEmpty()) 0.3f else 0.8f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.ifEmpty { placeholder },
            color = if (text.isEmpty()) c.textMuted else c.textPrimary,
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = Synapse.colors.textPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
