package com.mycompany.synapse

import com.mycompany.synapse.data.AchievementContext
import com.mycompany.synapse.data.Achievements
import com.mycompany.synapse.data.Insights
import com.mycompany.synapse.data.SessionRecord
import com.mycompany.synapse.data.Stats
import com.mycompany.synapse.data.TagRow
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.Leveling
import com.mycompany.synapse.game.Staircase
import com.mycompany.synapse.game.TestPlans
import com.mycompany.synapse.game.TrialResult
import com.mycompany.synapse.game.logic.ArithmeticGen
import com.mycompany.synapse.game.logic.EstimateGen
import com.mycompany.synapse.game.logic.NamesGen
import com.mycompany.synapse.game.logic.PathGen
import com.mycompany.synapse.game.logic.SequenceGen
import com.mycompany.synapse.game.logic.ShoppingGen
import com.mycompany.synapse.game.logic.SudokuGen
import com.mycompany.synapse.game.logic.Tricks
import com.mycompany.synapse.game.logic.WEEKDAYS
import com.mycompany.synapse.game.logic.WeekdayGen
import com.mycompany.synapse.game.logic.weekdayName
import com.mycompany.synapse.ui.ResumeState
import com.mycompany.synapse.ui.WorkoutEntry
import com.mycompany.synapse.ui.WorkoutRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class NewFeaturesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val monday = LocalDate.of(2026, 10, 5)
    private val dateFmt = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

    private fun each(block: (Int, Random) -> Unit) {
        for (l in 1..50) for (s in 0L until 25L) block(l, Random(s * 991 + l))
    }

    private fun record(game: String, day: LocalDate, level: Int, mode: GameMode = GameMode.STANDARD, correct: Int = 9) = SessionRecord(
        id = 0, gameId = game, mode = mode, startedAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        durationMs = 120_000, level = level, newLevel = level + 1, correct = correct, total = 10,
        avgMs = 2000, score = 100, bestCombo = 3, trials = 10, workout = false,
    )

    // ------------------------------------------------------------ Index & Kalibrierung

    @Test fun newGameDoesNotDragIndexDownAfterOneRound() {
        val trained = (0 until 6).map { record("blitz", monday.plusDays(it.toLong()), 20) }
        val before = Stats.overallIndex(Stats.domainIndices(Stats.gameSkills(trained), Stats.countedGames(trained)))
        val withNew = trained + record("rotation", monday.plusDays(6), 1)
        val after = Stats.overallIndex(Stats.domainIndices(Stats.gameSkills(withNew), Stats.countedGames(withNew)))
        assertEquals(before, after)
        assertTrue(com.mycompany.synapse.game.Domain.RAUM in Stats.pendingDomains(withNew))
    }

    @Test fun placementRecordCountsImmediately() {
        val recs = listOf(record("rotation", monday, 18, GameMode.TEST))
        assertTrue("rotation" in Stats.countedGames(recs))
        assertTrue(Stats.pendingDomains(recs).isEmpty())
    }

    @Test fun firstSessionsClimbFaster() {
        assertEquals(2, Leveling.calibrated(1, 0))
        assertEquals(4, Leveling.calibrated(2, 2))
        assertEquals(1, Leveling.calibrated(1, 3))
        assertEquals(-1, Leveling.calibrated(-1, 0))
    }

    @Test fun staircaseConvergesNearAbility() {
        // Simulierte Person: schafft alles bis Level 22, darüber nichts
        val s = Staircase(12)
        repeat(10) { s.record(s.level <= 22) }
        assertTrue("Ergebnis ${s.result()}", s.result() in 19..25)
    }

    @Test fun benchmarkScoreRewardsAccuracyAndSpeed() {
        assertEquals(100, Insights.benchmarkScore(1.0, 1000, 3000, speedMatters = true))
        assertEquals(70, Insights.benchmarkScore(1.0, 10_000, 3000, speedMatters = true))
        assertEquals(80, Insights.benchmarkScore(0.8, 5000, 3000, speedMatters = false))
        assertTrue(Insights.benchmarkScore(0.9, 3000, 3000, true) > Insights.benchmarkScore(0.9, 4500, 3000, true))
    }

    @Test fun testPlansUseExistingGames() {
        for (p in TestPlans.placement + TestPlans.benchmark) {
            assertEquals(p.domain, GameCatalog[p.gameId].domain)
        }
        assertEquals(6, TestPlans.benchmark.map { it.domain }.toSet().size)
    }

    @Test fun tagStatsSortWeakestFirst() {
        val now = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = listOf(
            TagRow(1, "blitz", "Division", 3, 10, 40_000, now),
            TagRow(1, "blitz", "Addition", 10, 10, 15_000, now),
            TagRow(2, "blitz", "Prozent", 1, 2, 4_000, now),
        )
        val stats = Insights.tagStats(rows, now)
        assertEquals(listOf("Division", "Addition"), stats.map { it.tag })
    }

    @Test fun weekReviewCountsDaysAndBestGame() {
        val recs = listOf(record("blitz", monday, 5), record("blitz", monday.plusDays(2), 6), record("stroop", monday.plusDays(2), 3))
        val r = Insights.weekReview(recs, Stats.timeline(recs, zone), emptyMap(), monday, 5, zone)
        assertEquals(2, r.trainedDays)
        assertEquals(3, r.summary.sessions)
        assertEquals("blitz", r.bestGame)
    }

    @Test fun newAchievementsUnlock() {
        val ctx = AchievementContext(
            emptyList(), 0, 0, 0, 0, null, zone, masteredLessons = 5, benchmarks = 1, placementDone = true,
        )
        val ids = Achievements.all.filter { it.isUnlocked(ctx) }.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("placement", "bench1", "trick1", "trick5")))
        assertFalse("bench3" in ids)
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
    }

    // ------------------------------------------------------------ Neue Spiele

    @Test fun doomsdayDatesShareWeekday() {
        for (y in 1950..2050) {
            val d = WeekdayGen.doomsday(y)
            for ((m, day) in listOf(4 to 4, 6 to 6, 8 to 8, 10 to 10, 12 to 12, 5 to 9, 9 to 5, 7 to 11, 11 to 7)) {
                assertEquals("$day.$m.$y", d, LocalDate.of(y, m, day).dayOfWeek)
            }
        }
    }

    @Test fun weekdayAnswersAreCorrect() = each { l, rng ->
        val t = WeekdayGen.generate(l, rng)
        val rel = Regex("Heute ist (\\p{L}+)\\. Welcher Tag ist (in|vor) (\\d+) Tagen\\?").find(t.question)
        if (rel != null) {
            val (today, dir, n) = rel.destructured
            val start = DayOfWeek.of(WEEKDAYS.indexOf(today) + 1)
            val expected = if (dir == "in") start.plus(n.toLong()) else start.minus(n.toLong())
            assertEquals(expected, t.answer)
        } else {
            val text = t.question.removePrefix("Welcher Wochentag ist der ").removeSuffix("?")
            assertEquals(t.question, LocalDate.parse(text, dateFmt).dayOfWeek, t.answer)
        }
    }

    @Test fun estimateCorrectOptionIsNearest() = each { l, rng ->
        val t = EstimateGen.generate(l, rng)
        val exact = t.exact.removePrefix("Genau: ").replace(",", ".").toDouble()
        val nearest = t.options.minBy { abs(it - exact) }
        assertEquals(t.toString(), nearest, t.options[t.correctIndex])
        assertEquals(4, t.options.toSet().size)
    }

    @Test fun sudokuIsUniqueAndConsistent() {
        for (l in listOf(1, 6, 12, 13, 25, 40, 50)) for (s in 0L until 6L) {
            val t = SudokuGen.generate(l, Random(s + l * 13))
            val n = t.size
            for (i in 0 until n * n) {
                assertTrue(SudokuGen.isValid(t.solution, n, i, t.solution[i]))
                if (t.puzzle[i] != 0) assertEquals(t.solution[i], t.puzzle[i])
            }
            assertEquals(1, SudokuGen.countSolutions(t.puzzle.copyOf(), n))
        }
    }

    @Test fun namesTasksAreAnswerable() = each { l, rng ->
        val t = NamesGen.generate(l, rng)
        assertEquals(NamesGen.count(l), t.people.size)
        assertEquals(t.people.size, t.people.map { it.second }.toSet().size)
        assertEquals(t.people.indices.toSet(), t.quizOrder.toSet())
        t.quizOrder.forEachIndexed { q, idx ->
            val opts = t.options[q]
            assertEquals(4, opts.toSet().size)
            assertTrue(t.people[idx].second in opts)
        }
    }

    @Test fun shoppingGridContainsList() = each { l, rng ->
        val t = ShoppingGen.generate(l, rng)
        assertTrue(t.grid.containsAll(t.items))
        assertEquals(t.grid.size, t.grid.toSet().size)
        assertEquals(ShoppingGen.count(l), t.items.size)
    }

    @Test fun pathEndMatchesWalk() = each { l, rng ->
        val t = PathGen.generate(l, rng)
        assertEquals(t.toString(), t.end, PathGen.walk(t))
        assertTrue(t.end != t.start)
    }

    @Test fun arithmeticKindsCanBeForced() {
        for (kind in ArithmeticGen.kindNames) {
            repeat(20) {
                val t = ArithmeticGen.generate(30, Random(it.toLong()), only = kind)
                assertEquals(kind, t.kind)
                assertEquals(t.text, t.answer.toLong(), eval(t.text))
            }
        }
    }

    @Test fun sequenceTasksHaveKinds() = each { l, rng ->
        assertTrue(SequenceGen.generate(l, rng).kind.isNotBlank())
    }

    // ------------------------------------------------------------ Tricks

    @Test fun trickDrillsHaveCorrectAnswers() {
        for (lesson in Tricks.lessons) {
            val drill = lesson.drill ?: continue
            repeat(60) { i ->
                val t = drill(Random(i.toLong() * 31 + lesson.id.hashCode()))
                when {
                    t.flashMs != null -> assertEquals(t.prompt.replace(" ", ""), t.answer)
                    t.prompt.startsWith("Ist ") -> {
                        val n = Regex("\\d+").find(t.prompt)!!.value.toLong()
                        val d = if ("durch 3" in t.prompt) 3 else 4
                        assertEquals(t.prompt, if (n % d == 0L) "Ja" else "Nein", t.answer)
                    }
                    t.prompt.startsWith("Wochentag am ") -> {
                        val date = LocalDate.parse(t.prompt.removePrefix("Wochentag am ").removeSuffix("?"), dateFmt)
                        assertEquals(weekdayName(date.dayOfWeek), t.answer)
                    }
                    else -> assertEquals("${lesson.id}: ${t.prompt}", eval(t.prompt).toString(), t.answer)
                }
            }
        }
    }

    @Test fun weaknessDrillUsesRequestedKind() {
        val drill = Tricks.weaknessDrill("Division", 25)
        repeat(20) {
            val t = drill(Random(it.toLong()))
            assertTrue(t.prompt.contains("÷"))
            assertEquals(eval(t.prompt.removeSuffix(" =")).toString(), t.answer)
        }
    }

    // ------------------------------------------------------------ Fortsetzen

    @Test fun resumeStateRoundTrips() {
        val s = ResumeState(
            "blitz", GameMode.SPRINT, 7, 9, 12345L,
            listOf(TrialResult(1, 1, 1500, tag = "Addition"), TrialResult(0, 1, 2500)),
            listOf(7, 8), 120, 1, 4, 1, 0, 1_700_000_000_000L, 42_000L, 1_700_000_042_000L,
            WorkoutRun(listOf("blitz", "stroop"), 0, listOf(WorkoutEntry("matrix", 200, 3, 4, 0.9))),
        )
        val back = ResumeState.fromJson(s.toJson())
        assertNotNull(back)
        assertEquals(s, back)
    }
}
