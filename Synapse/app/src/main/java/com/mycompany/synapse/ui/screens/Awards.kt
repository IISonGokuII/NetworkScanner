package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.data.Achievement
import com.mycompany.synapse.data.Achievements
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.theme.Synapse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val tierColors = listOf(Color(0xFFD08A4E), Color(0xFFC0C8DA), Color(0xFFFBBF24))

@Composable
fun AchievementBadge(a: Achievement, unlocked: Boolean, size: Int = 56) {
    val c = Synapse.colors
    val col = if (unlocked) tierColors[a.tier] else c.textMuted.copy(alpha = 0.5f)
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension / 2
        val ctr = center
        if (unlocked) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent)), r * 1.0f, ctr)
        // Sechseck-Plakette
        val path = Path()
        for (k in 0 until 6) {
            val ang = Math.toRadians(-90.0 + k * 60)
            val p = ctr + Offset((r * 0.86f * cos(ang)).toFloat(), (r * 0.86f * sin(ang)).toFloat())
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, c.surfaceStrong)
        drawPath(path, col.copy(alpha = if (unlocked) 0.25f else 0.08f))
        drawPath(path, col, style = Stroke(2.dp.toPx()))
        // Stern in der Mitte
        val star = Path()
        for (k in 0 until 10) {
            val ang = Math.toRadians(-90.0 + k * 36)
            val rad = if (k % 2 == 0) r * 0.42f else r * 0.18f
            val p = ctr + Offset((rad * cos(ang)).toFloat(), (rad * sin(ang)).toFloat())
            if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        if (unlocked) drawPath(star, col) else drawPath(star, col, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
fun AwardsScreen() {
    val c = Synapse.colors
    val records = Repo.records
    val ctx = remember(records, Repo.workoutsCompleted, Repo.duelsPlayed) { Repo.achievementContext() }
    val unlockedCount = Achievements.all.count { Repo.isUnlocked(it.id) }
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Erfolge", "$unlockedCount von ${Achievements.all.size} freigeschaltet")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(
                progress = { unlockedCount.toFloat() / Achievements.all.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = tierColors[2], trackColor = c.gridLine,
            )
            Spacer(Modifier.height(4.dp))
            val sorted = Achievements.all.sortedWith(compareByDescending<Achievement> { Repo.isUnlocked(it.id) }.thenBy { it.tier })
            for (row in sorted.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (a in row) {
                        val unlocked = Repo.isUnlocked(a.id)
                        val (cur, target) = a.progress(ctx)
                        GlassCard(Modifier.weight(1f), accent = if (unlocked) tierColors[a.tier] else null, padding = 14.dp) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AchievementBadge(a, unlocked) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                a.title, style = MaterialTheme.typography.titleMedium,
                                color = if (unlocked) c.textPrimary else c.textSecondary,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                a.description, style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            if (unlocked) {
                                val at = Repo.unlockedAt(a.id)?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt) }
                                Text(
                                    "✓ ${at ?: ""}", style = MaterialTheme.typography.labelMedium, color = tierColors[a.tier],
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { cur.toFloat() / target },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = c.textSecondary, trackColor = c.gridLine,
                                )
                                Text(
                                    "$cur / $target", style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
