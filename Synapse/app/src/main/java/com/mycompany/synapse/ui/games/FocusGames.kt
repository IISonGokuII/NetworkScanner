package com.mycompany.synapse.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.SchulteGen
import com.mycompany.synapse.game.logic.StroopGen
import com.mycompany.synapse.ui.components.ChoiceButton
import com.mycompany.synapse.ui.components.ChoiceText
import com.mycompany.synapse.ui.components.Pill
import com.mycompany.synapse.ui.theme.Synapse
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Stroop / Farbwort

@Composable
fun StroopTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val c = Synapse.colors
    val names = c.stroopNames
    val task = remember { StroopGen.generate(ctx.level, ctx.rng(), names.size) }
    val start = remember { now() }
    val once = remember { OnceReporter(onResult) }
    val tag = when {
        task.askWord -> "Regelwechsel"
        task.word == task.ink -> "Kongruent"
        else -> "Inkongruent"
    }
    val limit = if (ctx.zen) null else task.timeLimitMs
    val timeLeft = remember { Animatable(1f) }
    val solution = "Richtig: ${names[task.answer]}"
    LaunchedEffect(Unit) {
        if (limit != null) {
            timeLeft.animateTo(0f, tween(limit.toInt(), easing = LinearEasing))
            once.report(TrialResult.wrong(since(start), "Zu langsam – $solution", tag))
        }
    }
    TrialLayout {
        if (ctx.level >= 26) {
            Pill(if (task.askWord) "WORT  –  was steht da?" else "FARBE  –  wie ist es gefärbt?", if (task.askWord) c.textSecondary else ctx.accent)
        } else {
            HintText("Tippe die SCHRIFTFARBE")
        }
        if (limit != null) {
            LinearProgressIndicator(
                progress = { timeLeft.value },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = ctx.accent, trackColor = c.gridLine,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            names[task.word], color = c.stroopColors[task.ink],
            fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        for (row in task.options.chunked(if (task.options.size > 4) 3 else 2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (o in row) {
                    ChoiceButton(Modifier.weight(1f), accent = ctx.accent, height = 70.dp, onClick = {
                        once.report(
                            if (o == task.answer) TrialResult.right(since(start), tag) else TrialResult.wrong(since(start), solution, tag)
                        )
                    }) { ChoiceText(names[o]) }
                }
                if (task.options.size > 4 && row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------------------------------------------------------- Schulte-Tabelle

@Composable
fun SchulteTrial(ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    val task = remember { SchulteGen.generate(ctx.level, ctx.rng()) }
    val cells = task.size * task.size
    var next by remember { mutableIntStateOf(1) }
    var errors by remember { mutableIntStateOf(0) }
    var flashWrong by remember { mutableIntStateOf(-1) }
    val start = remember { now() }
    var elapsed by remember { mutableLongStateOf(0L) }
    val once = remember { OnceReporter(onResult) }
    val c = Synapse.colors
    LaunchedEffect(Unit) {
        while (!once.done) {
            elapsed = since(start)
            delay(100)
        }
    }
    LaunchedEffect(flashWrong) {
        if (flashWrong >= 0) { delay(300); flashWrong = -1 }
    }
    TrialLayout {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("FINDE", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Text("${next.coerceAtMost(cells)}", color = ctx.accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ZEIT", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Text(
                    String.format(java.util.Locale.GERMAN, "%.1f s", elapsed / 1000.0), color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until task.size) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until task.size) {
                        val i = r * task.size + col
                        val number = task.numbers[i]
                        val found = number < next
                        val base = if (task.tinted) c.gameColors[task.tints[i]].copy(alpha = 0.22f) else c.surface
                        val bg by animateColorAsState(
                            when {
                                flashWrong == i -> c.bad.copy(alpha = 0.55f)
                                found -> ctx.accent.copy(alpha = 0.10f)
                                else -> base
                            }, tween(120), label = "schulte",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, c.stroke, RoundedCornerShape(12.dp))
                                .clickable(enabled = !found && !once.done) {
                                    if (number == next) {
                                        next++
                                        Feedback.tap()
                                        if (next > cells) {
                                            once.report(
                                                TrialResult(
                                                    (cells - errors).coerceAtLeast(0), cells, since(start),
                                                    "$errors Fehlgriff${if (errors == 1) "" else "e"} – ruhig in der Mitte bleiben",
                                                )
                                            )
                                        }
                                    } else {
                                        errors++
                                        flashWrong = i
                                        Feedback.wrong()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$number",
                                modifier = Modifier.rotate(task.rotations[i]).padding(2.dp),
                                color = if (found) c.textMuted.copy(alpha = 0.4f) else c.textPrimary,
                                fontSize = if (task.size >= 6) 20.sp else 24.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HintText(if (errors == 0) "Fehlerfrei bisher" else "$errors Fehlgriff${if (errors > 1) "e" else ""}")
    }
}
