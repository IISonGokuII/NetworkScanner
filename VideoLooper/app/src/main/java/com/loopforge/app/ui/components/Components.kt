package com.loopforge.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loopforge.app.ui.theme.Neon
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Slowly drifting neon nebula behind every screen. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
        label = "phase",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Neon.Night)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
                    val center = Offset(cx, cy)
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
                        radius = radius,
                        center = center,
                    )
                }
                blob(Neon.Violet, w * (0.15f + 0.18f * sin(phase)), h * (0.12f + 0.06f * cos(phase)), w * 0.95f, 0.30f)
                blob(Neon.Magenta, w * (0.95f + 0.12f * cos(phase * 2f)), h * (0.42f + 0.10f * sin(phase)), w * 0.75f, 0.20f)
                blob(Neon.Cyan, w * (0.25f + 0.25f * cos(phase)), h * (0.98f + 0.04f * sin(phase * 3f)), w * 0.85f, 0.18f)
            },
        content = content,
    )
}

/** Frosted glass panel with a hairline border; [glow] switches the border to the neon gradient. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x1CFFFFFF), Color(0x0AFFFFFF))))
            .border(
                width = if (glow) 1.5.dp else 1.dp,
                brush = if (glow) Neon.brush else Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x0DFFFFFF))),
                shape = shape,
            )
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(Neon.Spectrum)),
        )
        Spacer(Modifier.width(10.dp))
        Text(title.uppercase(Locale.GERMAN), style = MaterialTheme.typography.labelMedium, color = Neon.TextDim, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Pill button with the neon gradient, a soft glow and a light sweep running across it. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 60.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sweep by shimmer.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep",
    )
    Box(
        modifier
            .scale(scale)
            .height(height)
            .shadow(if (enabled) 22.dp else 0.dp, shape, ambientColor = Neon.Magenta, spotColor = Neon.Violet)
            .clip(shape)
            .background(if (enabled) Neon.brush else SolidColor(Color(0x22FFFFFF)))
            .drawWithContent {
                drawContent()
                if (enabled) {
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(x - size.height * 2, 0f),
                            end = Offset(x, size.height),
                        ),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Neon.TextFaint)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(Neon.Glass)
            .border(1.dp, Neon.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Neon.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.Text)
    }
}

@Composable
fun NeonChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Neon.brush else SolidColor(Neon.Glass))
            .border(1.dp, if (selected) SolidColor(Color.Transparent) else SolidColor(Neon.Stroke), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = if (selected) Color.White else Neon.TextDim)
    }
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, color: Color = Neon.TextDim, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Neon.Glass)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Neon.Text else Neon.TextFaint)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Neon.TextFaint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Neon.Violet,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Neon.TextDim,
                uncheckedTrackColor = Color(0x22FFFFFF),
                uncheckedBorderColor = Neon.Stroke,
            ),
        )
    }
}

/** Circular gauge for a seam quality (0..1). */
@Composable
fun ScoreRing(quality: Float, modifier: Modifier = Modifier, size: Dp = 54.dp, stroke: Dp = 5.dp, showLabel: Boolean = true) {
    val animated by animateFloatAsState(quality.coerceIn(0f, 1f), tween(600), label = "score")
    val color = Neon.quality(quality)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color, color)),
                -90f, 360f * animated, false, topLeft, arcSize,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        if (showLabel) {
            Text(
                "${(animated * 100).roundToInt()}",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.30f).sp, fontWeight = FontWeight.Black),
                color = Neon.Text,
            )
        }
    }
}

/** Animated infinity sign with a comet racing along it – the main "pick a video" call to action. */
@Composable
fun InfinityOrb(modifier: Modifier = Modifier, size: Dp = 280.dp, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "travel")
    val pulse by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2
            drawCircle(
                Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.45f), Neon.Magenta.copy(alpha = 0.12f), Color.Transparent), c, r * pulse),
                radius = r * pulse,
                center = c,
            )
            drawCircle(Color(0x10FFFFFF), radius = r * 0.78f, center = c)
            drawCircle(Neon.brush, radius = r * 0.78f, center = c, style = Stroke(1.2.dp.toPx()), alpha = 0.5f)

            val a = r * 0.62f
            val path = Path()
            val steps = 240
            for (i in 0..steps) {
                val t = (i.toFloat() / steps) * 2 * PI.toFloat()
                val d = 1 + sin(t) * sin(t)
                val x = c.x + a * cos(t) / d
                val y = c.y + a * sin(t) * cos(t) / d
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val stroke = 9.dp.toPx()
            drawPath(path, Neon.sweep, style = Stroke(stroke * 2.6f, cap = StrokeCap.Round), alpha = 0.18f)
            drawPath(path, Brush.linearGradient(Neon.Spectrum, Offset(c.x - a, c.y), Offset(c.x + a, c.y)), style = Stroke(stroke, cap = StrokeCap.Round))

            val measure = PathMeasure().apply { setPath(path, false) }
            val length = measure.length
            for (k in 0 until 26) {
                val pos = ((travel - k * 0.007f + 1f) % 1f) * length
                val p = measure.getPosition(pos)
                val fade = 1f - k / 26f
                drawCircle(Color.White, radius = stroke * 0.55f * fade + 1f, center = p, alpha = fade * 0.9f)
            }
        }
    }
}

/** Big ring for long running work; [progress] null = indeterminate. */
@Composable
fun ProgressOrb(progress: Float?, modifier: Modifier = Modifier, size: Dp = 230.dp, center: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "progress")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val animated by animateFloatAsState(progress ?: 0f, tween(300), label = "p")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 12.dp.toPx()
            val inset = s * 1.5f
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawCircle(
                Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.25f), Color.Transparent)),
                radius = this.size.minDimension / 2,
            )
            drawArc(Color(0x18FFFFFF), 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (progress != null) {
                rotate(-90f) {
                    drawArc(Neon.sweep, 0f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
                }
            }
            rotate(spin) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Neon.Cyan.copy(alpha = 0.9f))),
                    0f, 90f, false,
                    Offset(inset - s * 1.2f, inset - s * 1.2f),
                    Size(arcSize.width + 2.4f * s, arcSize.height + 2.4f * s),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            for (k in 0 until 3) {
                val angle = Math.toRadians((spin * (1 + k * 0.35f) + k * 120f).toDouble())
                val radius = this.size.minDimension / 2 - inset * 0.25f
                drawCircle(
                    Neon.Spectrum[k],
                    radius = 3.dp.toPx(),
                    center = Offset(this.center.x + radius * cos(angle).toFloat(), this.center.y + radius * sin(angle).toFloat()),
                )
            }
        }
        center()
    }
}

@Composable
fun BigPercent(progress: Float, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.displayLarge.copy(brush = Neon.brush),
        )
        Text(caption, style = MaterialTheme.typography.labelMedium, color = Neon.TextDim, textAlign = TextAlign.Center)
    }
}

fun formatPrecise(us: Long): String {
    val tenths = (us / 100_000L).coerceAtLeast(0)
    val minutes = tenths / 600
    val seconds = (tenths / 10) % 60
    return String.format(Locale.GERMAN, "%d:%02d,%d", minutes, seconds, tenths % 10)
}

fun formatClock(us: Long): String {
    val total = ((us + 500_000L) / 1_000_000L).coerceAtLeast(0)
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) String.format(Locale.GERMAN, "%d:%02d:%02d", h, m, s) else String.format(Locale.GERMAN, "%d:%02d", m, s)
}

fun formatSeconds(us: Long): String = String.format(Locale.GERMAN, "%.2f s", us / 1e6)
