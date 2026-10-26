package com.mycompany.synapse.data

import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.Leveling
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.exp

data class SessionRecord(
    val id: Long,
    val gameId: String,
    val mode: GameMode,
    val startedAt: Long,
    val durationMs: Long,
    val level: Int,
    val newLevel: Int,
    val correct: Int,
    val total: Int,
    val avgMs: Long,
    val score: Int,
    val bestCombo: Int,
    val trials: Int,
    val workout: Boolean,
) {
    val accuracy: Double get() = if (total <= 0) 0.0 else correct.toDouble() / total
    val skill: Double get() = Leveling.skill(level, accuracy)
    val domain: Domain get() = GameCatalog[gameId].domain
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
}

data class IndexPoint(val day: LocalDate, val overall: Int, val domains: Map<Domain, Int>)

data class StreakInfo(val current: Int, val best: Int, val jokerUsedThisWeek: Boolean, val trainedToday: Boolean)

data class WeekSummary(val sessions: Int, val minutes: Int, val accuracy: Double, val indexChange: Int?)

data class GameSummary(
    val gameId: String,
    val sessions: Int,
    val firstLevel: Int,
    val bestLevel: Int,
    val bestScore: Int,
    val bestAccuracy: Double,
    val avgAccuracy: Double,
    val firstAvgMs: Long?,
    val recentAvgMs: Long?,
    val skillSeries: List<Double>,
    val lastPlayed: Long?,
)

/**
 * Reine Berechnungen ohne Android-Abhängigkeiten – damit sie per Unit-Test geprüft werden können.
 * Alle Funktionen erwarten Records in beliebiger Reihenfolge.
 */
object Stats {
    private const val EMA_ALPHA = 0.4

    /** Geglätteter Leistungswert pro Spiel (exponentiell gewichteter Mittelwert der Sessions). */
    fun gameSkills(records: List<SessionRecord>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (r in records.sortedBy { it.startedAt }) {
            val prev = out[r.gameId]
            out[r.gameId] = if (prev == null) r.skill else prev + EMA_ALPHA * (r.skill - prev)
        }
        return out
    }

    fun domainIndices(gameSkills: Map<String, Double>): Map<Domain, Int> {
        val out = HashMap<Domain, Int>()
        for (d in Domain.entries) {
            val values = GameCatalog.ofDomain(d).mapNotNull { gameSkills[it.id] }
            if (values.isNotEmpty()) out[d] = Leveling.index(values.average())
        }
        return out
    }

    /** Gesamt-Index = Mittel der trainierten Bereiche. */
    fun overallIndex(domains: Map<Domain, Int>): Int = if (domains.isEmpty()) 0 else domains.values.average().toInt()

    /** Index-Verlauf: ein Punkt pro Trainingstag (Stand am Ende des Tages). */
    fun timeline(records: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): List<IndexPoint> {
        if (records.isEmpty()) return emptyList()
        val sorted = records.sortedBy { it.startedAt }
        val skills = HashMap<String, Double>()
        val points = mutableListOf<IndexPoint>()
        var i = 0
        while (i < sorted.size) {
            val day = sorted[i].day(zone)
            while (i < sorted.size && sorted[i].day(zone) == day) {
                val r = sorted[i]
                val prev = skills[r.gameId]
                skills[r.gameId] = if (prev == null) r.skill else prev + EMA_ALPHA * (r.skill - prev)
                i++
            }
            val d = domainIndices(skills)
            points += IndexPoint(day, overallIndex(d), d)
        }
        return points
    }

    /** Stand an einem bestimmten Tag (letzter Punkt bis einschließlich [day]). */
    fun indexAt(timeline: List<IndexPoint>, day: LocalDate): IndexPoint? = timeline.lastOrNull { !it.day.isAfter(day) }

    /**
     * Serie mit Joker: Pro Kalenderwoche darf ein Tag fehlen, ohne dass die Serie reißt.
     * Die Serie zählt die tatsächlich trainierten Tage. Heute zählt als „noch offen“.
     */
    fun streak(trainedDays: Set<LocalDate>, today: LocalDate): StreakInfo {
        if (trainedDays.isEmpty()) return StreakInfo(0, 0, false, false)
        val first = trainedDays.min()
        var current = 0
        var best = 0
        var jokerWeek: LocalDate? = null
        var day = first
        while (!day.isAfter(today)) {
            val week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (day in trainedDays) {
                current++
            } else if (day != today) {
                if (current > 0 && jokerWeek != week) {
                    jokerWeek = week
                } else {
                    current = 0
                    jokerWeek = null
                }
            }
            best = maxOf(best, current)
            day = day.plusDays(1)
        }
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return StreakInfo(current, best, jokerWeek == thisWeek && current > 0, today in trainedDays)
    }

    fun minutesPerDay(records: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> =
        records.groupBy { it.day(zone) }.mapValues { (_, list) -> ((list.sumOf { it.durationMs } + 59_999) / 60_000).toInt() }

    fun week(records: List<SessionRecord>, timeline: List<IndexPoint>, weekStart: LocalDate, zone: ZoneId = ZoneId.systemDefault()): WeekSummary {
        val end = weekStart.plusDays(7)
        val list = records.filter { val d = it.day(zone); !d.isBefore(weekStart) && d.isBefore(end) }
        val correct = list.sumOf { it.correct }
        val total = list.sumOf { it.total }
        val startIdx = indexAt(timeline, weekStart.minusDays(1))?.overall
        val endIdx = indexAt(timeline, end.minusDays(1))?.overall
        val change = if (endIdx != null && list.isNotEmpty()) endIdx - (startIdx ?: 0) else null
        return WeekSummary(
            sessions = list.size,
            minutes = ((list.sumOf { it.durationMs } + 59_999) / 60_000).toInt(),
            accuracy = if (total == 0) 0.0 else correct.toDouble() / total,
            indexChange = change,
        )
    }

    fun gameSummary(gameId: String, records: List<SessionRecord>): GameSummary {
        val list = records.filter { it.gameId == gameId }.sortedBy { it.startedAt }
        val timed = list.filter { it.mode != GameMode.ZEN && it.avgMs > 0 }
        return GameSummary(
            gameId = gameId,
            sessions = list.size,
            firstLevel = list.firstOrNull()?.level ?: 1,
            bestLevel = list.maxOfOrNull { maxOf(it.level, it.newLevel) } ?: 1,
            bestScore = list.maxOfOrNull { it.score } ?: 0,
            bestAccuracy = list.maxOfOrNull { it.accuracy } ?: 0.0,
            avgAccuracy = if (list.isEmpty()) 0.0 else list.map { it.accuracy }.average(),
            firstAvgMs = timed.take(3).takeIf { it.isNotEmpty() }?.map { it.avgMs }?.average()?.toLong(),
            recentAvgMs = timed.takeLast(3).takeIf { timed.size >= 4 }?.map { it.avgMs }?.average()?.toLong(),
            skillSeries = list.map { it.skill },
            lastPlayed = list.lastOrNull()?.startedAt,
        )
    }

    /** „Lebendigkeit“ eines Bereichs für das Neuronen-Netz: 0 = lange nicht trainiert, 1 = sehr aktiv. */
    fun vitality(records: List<SessionRecord>, now: Long): Map<Domain, Float> {
        val out = HashMap<Domain, Float>()
        for (d in Domain.entries) {
            val sum = records.filter { it.domain == d }.sumOf {
                val ageDays = (now - it.startedAt).coerceAtLeast(0) / 86_400_000.0
                exp(-ageDays / 5.0)
            }
            out[d] = (sum / 3.0).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
