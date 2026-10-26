package com.mycompany.synapse

import com.mycompany.synapse.game.logic.AnagramGen
import com.mycompany.synapse.game.logic.ArithmeticGen
import com.mycompany.synapse.game.logic.BalanceGen
import com.mycompany.synapse.game.logic.ChainGen
import com.mycompany.synapse.game.logic.CorsiGen
import com.mycompany.synapse.game.logic.DuelGen
import com.mycompany.synapse.game.logic.MINUS
import com.mycompany.synapse.game.logic.MatrixGen
import com.mycompany.synapse.game.logic.NBackGen
import com.mycompany.synapse.game.logic.OddOneGen
import com.mycompany.synapse.game.logic.RotationGen
import com.mycompany.synapse.game.logic.SchulteGen
import com.mycompany.synapse.game.logic.SequenceGen
import com.mycompany.synapse.game.logic.SpanGen
import com.mycompany.synapse.game.logic.StroopGen
import com.mycompany.synapse.game.logic.SyllogismGen
import com.mycompany.synapse.game.logic.TargetGen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Prüft alle Aufgaben-Generatoren über alle 50 Level und viele Zufallswerte. */
class GeneratorTest {
    private val levels = 1..50
    private val seeds = 0L until 40L

    private fun each(block: (level: Int, rng: Random) -> Unit) {
        for (l in levels) for (s in seeds) block(l, Random(s * 1000 + l))
    }

    @Test fun arithmeticAnswersAreCorrect() = each { l, rng ->
        val t = ArithmeticGen.generate(l, rng)
        assertTrue(t.answer >= 0)
        assertEquals(t.text, t.answer.toLong(), eval(t.text))
    }

    @Test fun chainAnswersMatchSteps() = each { l, rng ->
        val t = ChainGen.generate(l, rng)
        var v = t.start
        for (s in t.steps) {
            val n = s.substringAfter(" ").toInt()
            v = when (s.first()) {
                '+' -> v + n
                '×' -> v * n
                '÷' -> { assertEquals(0, v % n); v / n }
                else -> v - n
            }
            assertTrue("Zwischenergebnis negativ in $t", v >= 0)
        }
        assertEquals(t.answer, v)
        assertEquals(ChainGen.stepCount(l), t.steps.size)
    }

    @Test fun targetPuzzlesAreSolvable() = each { l, rng ->
        val t = TargetGen.generate(l, rng)
        assertEquals("Lösung ${t.solution}", t.target.toLong(), eval(t.solution))
        assertFalse(t.target in t.numbers)
        // Die Lösung verwendet nur vorhandene Zahlen
        val used = Regex("\\d+").findAll(t.solution).map { it.value.toInt() }.toMutableList()
        val pool = t.numbers.toMutableList()
        for (u in used) assertTrue("Zahl $u nicht verfügbar in ${t.numbers}", pool.remove(u))
    }

    @Test fun sequencesAreValid() = each { l, rng ->
        val t = SequenceGen.generate(l, rng)
        assertTrue(t.shown.size >= 5)
        assertTrue(t.answer in 0..99_999)
    }

    @Test fun matrixHasExactlyOneCorrectOption() = each { l, rng ->
        val t = MatrixGen.generate(l, rng)
        assertEquals(9, t.grid.size)
        assertEquals(MatrixGen.optionCount(l), t.options.size)
        assertEquals(t.options.size, t.options.toSet().size)
        assertEquals(t.grid[8], t.options[t.correctIndex])
        assertEquals(1, t.options.count { it == t.grid[8] })
    }

    private val more = setOf("größer", "schneller", "älter", "schwerer", "lauter")
    private val less = setOf("kleiner", "langsamer", "jünger", "leichter", "leiser")
    private val relation = Regex("^(\\p{L}+) ist (\\p{L}+) als (\\p{L}+)\\.$")
    private val categorical = Regex("^(Alle|Keine|Einige) (\\p{L}+) sind (keine )?(\\p{L}+)\\.$")

    /** Prüft Vergleichs- und Mengenlogik unabhängig vom Generator. */
    @Test fun syllogismValidityIsCorrect() {
        var relational = 0
        var sets = 0
        each { l, rng ->
            val t = SyllogismGen.generate(l, rng)
            val all = t.premises + t.conclusion
            if (all.all { relation.matches(it) }) {
                relational++
                // Kante a → b bedeutet „a hat mehr als b“
                val edges = mutableSetOf<Pair<String, String>>()
                fun edge(s: String): Pair<String, String> {
                    val (a, rel, b) = relation.find(s)!!.destructured
                    return when (rel) {
                        in more -> a to b
                        in less -> b to a
                        else -> error("Unbekannte Relation $rel")
                    }
                }
                t.premises.forEach { edges += edge(it) }
                var changed = true
                while (changed) {
                    changed = false
                    for ((a, b) in edges.toList()) for ((c, d) in edges.toList()) {
                        if (b == c && edges.add(a to d)) changed = true
                    }
                }
                assertEquals(t.toString(), t.valid, edge(t.conclusion) in edges)
            } else if (all.all { categorical.matches(it) }) {
                sets++
                val terms = all.flatMap { s -> categorical.find(s)!!.destructured.let { listOf(it.component2(), it.component4()) } }.distinct()
                assertEquals(3, terms.size)
                fun holds(s: String, model: Map<String, Int>): Boolean {
                    val (q, x, neg, y) = categorical.find(s)!!.destructured
                    val a = model.getValue(x)
                    val b = model.getValue(y)
                    return when {
                        q == "Alle" -> a and b.inv() == 0
                        q == "Keine" -> a and b == 0
                        neg.isNotEmpty() -> a and b.inv() != 0
                        else -> a and b != 0
                    }
                }
                var valid = true
                for (m in 0 until 4096) {
                    val model = mapOf(terms[0] to (m and 15), terms[1] to ((m shr 4) and 15), terms[2] to ((m shr 8) and 15))
                    if (t.premises.all { holds(it, model) } && !holds(t.conclusion, model)) { valid = false; break }
                }
                assertEquals(t.toString(), valid, t.valid)
            }
        }
        assertTrue(relational > 100)
        assertTrue(sets > 50)
    }

    @Test fun balanceAnswerIsDeterminable() = each { l, rng ->
        val t = BalanceGen.generate(l, rng)
        val heavierThan = t.comparisons.groupBy({ it.first }, { it.second })
        fun reach(o: Int): Set<Int> {
            val seen = mutableSetOf<Int>()
            val stack = ArrayDeque(listOf(o))
            while (stack.isNotEmpty()) for (n in heavierThan[stack.removeLast()].orEmpty()) if (seen.add(n)) stack.add(n)
            return seen
        }
        if (t.askHeaviest) assertEquals(t.objectCount - 1, reach(t.answer).size)
        else assertTrue((0 until t.objectCount).filter { it != t.answer }.all { t.answer in reach(it) })
    }

    @Test fun nbackHasEnoughMatches() {
        for (l in levels) {
            val cfg = NBackGen.config(l)
            val seq = NBackGen.sequence(cfg, Random(l.toLong()))
            assertEquals(cfg.length, seq.size)
            assertTrue((cfg.n until seq.size).count { seq[it].pos == seq[it - cfg.n].pos } >= 4)
        }
    }

    @Test fun memoryAndFocusTasksAreValid() = each { l, rng ->
        val corsi = CorsiGen.generate(l, rng)
        assertEquals(corsi.sequence.size, corsi.sequence.toSet().size)
        assertTrue(corsi.sequence.all { it in 0 until corsi.gridSize * corsi.gridSize })
        val span = SpanGen.generate(l, rng)
        assertTrue(span.digits.zipWithNext().none { (a, b) -> a == b })
        val stroop = StroopGen.generate(l, rng)
        assertTrue(stroop.answer in stroop.options)
        val schulte = SchulteGen.generate(l, rng)
        assertEquals((1..schulte.size * schulte.size).toList(), schulte.numbers.sorted())
    }

    @Test fun rotationGroupHas24Elements() = assertEquals(24, RotationGen.all.size)

    @Test fun rotationMirroredFlagIsCorrect() {
        for (l in levels) for (s in 0L until 8L) {
            val t = RotationGen.generate(l, Random(s * 77 + l))
            assertEquals(RotationGen.cubeCount(l), t.left.size)
            assertEquals("Level $l Seed $s", !t.mirrored, RotationGen.sameShape(t.left, t.right))
            assertTrue(RotationGen.clearlyVisible(t.left) && RotationGen.clearlyVisible(t.right))
        }
    }

    @Test fun anagramsUseTheWordsLetters() = each { l, rng ->
        val t = AnagramGen.generate(l, rng)
        assertEquals(AnagramGen.signature(t.word), t.letters.sorted().joinToString(""))
        assertEquals(AnagramGen.wordLength(l), t.word.length)
        assertTrue(AnagramGen.accepts(t, t.word))
    }

    @Test fun anagramListHasEnoughWordsPerLength() {
        for (len in 4..9) assertTrue("Länge $len", AnagramGen.words.count { it.length == len } >= 14)
    }

    @Test fun oddOneOutIsUnambiguous() = each { l, rng ->
        val t = OddOneGen.generate(l, rng)
        val odd = t.words[t.oddIndex]
        val others = t.words.filterIndexed { i, _ -> i != t.oddIndex }
        val mainCats = OddOneGen.categories.filter { cat -> others.all { it in cat.words } }
        assertTrue("Keine gemeinsame Kategorie für $others", mainCats.isNotEmpty())
        assertTrue("$odd gehört auch zu ${mainCats.first().plural}", mainCats.none { odd in it.words })
        assertEquals(t.words.size, t.words.toSet().size)
    }

    @Test fun categoryWordsAreUnique() {
        val all = OddOneGen.categories.flatMap { it.words }
        assertEquals(all.groupBy { it }.filter { it.value.size > 1 }.keys.toString(), all.size, all.toSet().size)
    }

    @Test fun duelTasksHaveFourDistinctOptions() = each { l, rng ->
        val m = DuelGen.math(l, rng)
        assertEquals(4, m.options.toSet().size)
        assertEquals(m.question.removeSuffix(" = ?").let { eval(it) }.toString(), m.options[m.correct])
        val s = DuelGen.stroop(rng)
        assertEquals(StroopGen.NAMES[s.inkColor!!], s.options[s.correct])
    }
}
