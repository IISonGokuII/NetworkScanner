package com.mycompany.synapse

import com.mycompany.synapse.data.Achievements
import com.mycompany.synapse.data.AchievementContext
import com.mycompany.synapse.data.SessionRecord
import com.mycompany.synapse.data.Stats
import com.mycompany.synapse.data.WorkoutPlanner
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.Leveling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val monday = LocalDate.of(2026, 10, 5) // ein Montag

    private fun record(game: String, day: LocalDate, level: Int, correct: Int = 9, total: Int = 10, combo: Int = 3) = SessionRecord(
        id = 0, gameId = game, mode = GameMode.STANDARD,
        startedAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        durationMs = 120_000, level = level, newLevel = level + 1, correct = correct, total = total,
        avgMs = 2000, score = 100, bestCombo = combo, trials = 10, workout = false,
    )

    @Test fun streakCountsConsecutiveDays() {
        val days = (0..4).map { monday.plusDays(it.toLong()) }.toSet()
        val s = Stats.streak(days, monday.plusDays(4))
        assertEquals(5, s.current)
        assertEquals(5, s.best)
        assertTrue(s.trainedToday)
    }

    @Test fun oneMissedDayPerWeekIsBridgedByJoker() {
        val days = setOf(monday, monday.plusDays(1), monday.plusDays(3), monday.plusDays(4))
        val s = Stats.streak(days, monday.plusDays(4))
        assertEquals(4, s.current)
        assertTrue(s.jokerUsedThisWeek)
    }

    @Test fun twoMissedDaysInOneWeekBreakTheStreak() {
        val days = setOf(monday, monday.plusDays(2), monday.plusDays(4))
        val s = Stats.streak(days, monday.plusDays(4))
        assertEquals(1, s.current)
        assertEquals(2, s.best)
    }

    @Test fun todayStillOpenDoesNotBreakTheStreak() {
        val days = setOf(monday, monday.plusDays(1), monday.plusDays(2))
        val s = Stats.streak(days, monday.plusDays(3))
        assertEquals(3, s.current)
        assertFalse(s.trainedToday)
    }

    @Test fun levelingTargetsTheLearningZone() {
        assertEquals(2, Leveling.defaultDelta(1.0, fast = true))
        assertEquals(1, Leveling.defaultDelta(0.9, fast = false))
        assertEquals(0, Leveling.defaultDelta(0.75, fast = true))
        assertEquals(-1, Leveling.defaultDelta(0.5, fast = false))
        assertEquals(1, Leveling.apply(1, -1))
        assertEquals(50, Leveling.apply(50, 2))
    }

    @Test fun timelineRisesWithLevels() {
        val records = (0 until 10).map { record("blitz", monday.plusDays(it.toLong()), level = 1 + it * 2) }
        val tl = Stats.timeline(records, zone)
        // Der erste Tag zählt noch nicht: ein Spiel fließt erst ab der zweiten Runde in den Index ein
        assertEquals(9, tl.size)
        assertTrue(tl.zipWithNext().all { (a, b) -> b.overall >= a.overall })
        assertTrue(tl.last().overall > tl.first().overall)
        assertTrue(Domain.RECHNEN in tl.last().domains)
    }

    @Test fun weekSummaryCountsSessions() {
        val records = listOf(record("blitz", monday, 3), record("stroop", monday.plusDays(1), 4), record("blitz", monday.minusDays(2), 2))
        val tl = Stats.timeline(records, zone)
        val w = Stats.week(records, tl, monday, zone)
        assertEquals(2, w.sessions)
        assertEquals(4, w.minutes)
    }

    @Test fun workoutPlanUsesDistinctDomains() {
        for (size in listOf(3, 5)) {
            val plan = WorkoutPlanner.plan(emptyList(), monday, size, zone)
            assertEquals(size, plan.size)
            assertEquals(size, plan.map { GameCatalog[it].domain }.toSet().size)
            assertEquals(plan, WorkoutPlanner.plan(emptyList(), monday, size, zone))
        }
    }

    @Test fun workoutPrefersNeglectedDomains() {
        val records = GameCatalog.all.filter { it.domain != Domain.RAUM }.map { record(it.id, monday, 5) }
        val plan = WorkoutPlanner.plan(records, monday.plusDays(1), 3, zone)
        assertTrue(plan.any { GameCatalog[it].domain == Domain.RAUM })
    }

    @Test fun achievementsUnlockFromRecords() {
        val records = listOf(record("blitz", monday, 10, correct = 10, total = 10, combo = 10))
        val ctx = AchievementContext(records, bestStreak = 1, workoutsCompleted = 0, duelsPlayed = 0, overallIndex = 200, firstIndex = 200, zone = zone)
        val unlocked = Achievements.all.filter { it.isUnlocked(ctx) }.map { it.id }.toSet()
        assertTrue("first" in unlocked)
        assertTrue("perfect" in unlocked)
        assertTrue("flow10" in unlocked)
        assertTrue("math10" in unlocked)
        assertFalse("streak3" in unlocked)
    }

    @Test fun everyGameHasMetadata() {
        assertEquals(21, GameCatalog.all.size)
        assertEquals(GameCatalog.all.size, GameCatalog.all.map { it.id }.toSet().size)
        for (d in Domain.entries) assertTrue(GameCatalog.ofDomain(d).isNotEmpty())
        for (g in GameCatalog.all) for (l in 1..50) {
            assertTrue(g.levelLabel(l).isNotBlank())
            assertTrue(g.targetMs(l) > 0)
        }
    }
}
