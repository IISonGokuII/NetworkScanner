package com.mycompany.synapse.ui.games

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.ui.theme.Synapse
import kotlin.random.Random

/** Was jede Aufgabe vom Session-Runner bekommt. */
data class TrialContext(val level: Int, val seed: Long, val zen: Boolean, val accent: Color) {
    fun rng() = Random(seed)
}

fun now(): Long = SystemClock.elapsedRealtime()
fun since(t: Long): Long = (SystemClock.elapsedRealtime() - t).coerceAtLeast(1)

/** Stellt sicher, dass eine Aufgabe genau ein Ergebnis meldet. */
class OnceReporter(private val onResult: (TrialResult) -> Unit) {
    var done = false
        private set

    fun report(r: TrialResult) {
        if (done) return
        done = true
        onResult(r)
    }
}

@Composable
fun GameTrial(gameId: String, ctx: TrialContext, onResult: (TrialResult) -> Unit) {
    when (gameId) {
        "blitz" -> BlitzTrial(ctx, onResult)
        "kette" -> ChainTrial(ctx, onResult)
        "zielzahl" -> TargetTrial(ctx, onResult)
        "folgen" -> SequenceTrial(ctx, onResult)
        "matrix" -> MatrixTrial(ctx, onResult)
        "schluss" -> SyllogismTrial(ctx, onResult)
        "waage" -> BalanceTrial(ctx, onResult)
        "nback" -> NBackTrial(ctx, onResult)
        "corsi" -> CorsiTrial(ctx, onResult)
        "spanne" -> SpanTrial(ctx, onResult)
        "stroop" -> StroopTrial(ctx, onResult)
        "schulte" -> SchulteTrial(ctx, onResult)
        "rotation" -> RotationTrial(ctx, onResult)
        "anagramm" -> AnagramTrial(ctx, onResult)
        "oberbegriff" -> OddOneTrial(ctx, onResult)
        else -> Box(Modifier.fillMaxSize())
    }
}

/** Standard-Layout einer Aufgabe: oben Inhalt, unten Eingabe. */
@Composable
fun TrialLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Große Aufgaben-Schrift, die bei langen Ausdrücken automatisch kleiner wird. */
@Composable
fun BigTaskText(text: String, modifier: Modifier = Modifier, color: Color = Synapse.colors.textPrimary) {
    val size = when {
        text.length <= 7 -> 58
        text.length <= 11 -> 46
        text.length <= 15 -> 36
        else -> 28
    }
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center, maxLines = 2, style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Synapse.colors.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
