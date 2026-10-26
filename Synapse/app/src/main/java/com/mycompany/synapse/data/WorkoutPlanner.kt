package com.mycompany.synapse.data

import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Stellt das Tagestraining zusammen: bevorzugt Bereiche, die länger nicht dran waren,
 * und innerhalb des Bereichs das am längsten nicht gespielte Spiel. Pro Tag deterministisch.
 */
object WorkoutPlanner {
    fun plan(records: List<SessionRecord>, date: LocalDate, size: Int, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val rng = Random(date.toEpochDay() * 7919 + 17)
        val lastDomainDay = HashMap<Domain, LocalDate>()
        val lastGame = HashMap<String, Long>()
        for (r in records) {
            val d = r.day(zone)
            val prev = lastDomainDay[r.domain]
            if (prev == null || d.isAfter(prev)) lastDomainDay[r.domain] = d
            lastGame[r.gameId] = maxOf(lastGame[r.gameId] ?: 0L, r.startedAt)
        }
        val domains = Domain.entries.sortedByDescending { d ->
            val since = lastDomainDay[d]?.let { Stats.daysBetween(it, date).toDouble() } ?: 30.0
            since.coerceAtMost(30.0) + rng.nextDouble() * 2.5
        }.take(size.coerceIn(1, Domain.entries.size))
        return domains.map { d ->
            GameCatalog.ofDomain(d).minBy { g -> (lastGame[g.id] ?: 0L) + (rng.nextDouble() * 3_600_000).toLong() }.id
        }.shuffled(rng)
    }
}
