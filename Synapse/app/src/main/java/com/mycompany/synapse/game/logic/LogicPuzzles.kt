package com.mycompany.synapse.game.logic

import kotlin.random.Random

// ---------------------------------------------------------------- Zahlenfolgen

data class SequenceTask(val shown: List<Long>, val answer: Long, val rule: String, val kind: String = "")

object SequenceGen {
    private const val LIMIT = 99_999L

    private class Kind(val minLevel: Int, val name: String, val build: (Int, Random) -> Pair<List<Long>, String>?)

    private fun arith(start: Long, d: Long, n: Int) = List(n) { start + d * it }

    private val primes = listOf(2L, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113)

    private val kinds = listOf(
        Kind(1, "Plus-Folge") { l, r ->
            val d = r.range(1, 3 + l / 3).toLong()
            arith(r.range(1, 20).toLong(), d, 6) to "immer +$d"
        },
        Kind(4, "Minus-Folge") { l, r ->
            val d = r.range(2, 4 + l / 3).toLong()
            val start = d * 7 + r.range(0, 30)
            arith(start, -d, 6) to "immer $MINUS$d"
        },
        Kind(6, "Verdopplung") { _, r ->
            val s = r.range(1, 6).toLong()
            List(6) { s shl it } to "immer ×2"
        },
        Kind(9, "Wechsel-Abstände") { l, r ->
            val a = r.range(1, 4 + l / 5)
            var b = r.range(1, 6 + l / 5)
            if (b == a) b += 2
            var v = r.range(1, 15).toLong()
            val list = mutableListOf<Long>()
            repeat(7) { i -> list += v; v += if (i % 2 == 0) a else b }
            list to "abwechselnd +$a und +$b"
        },
        Kind(11, "Quadratzahlen") { _, r ->
            val k = r.range(1, 6)
            List(6) { ((k + it) * (k + it)).toLong() } to "Quadratzahlen"
        },
        Kind(13, "Plus/Minus im Wechsel") { l, r ->
            val a = r.range(3, 6 + l / 6)
            val b = r.range(1, a - 1)
            var v = r.range(5, 20).toLong()
            val list = mutableListOf<Long>()
            repeat(7) { i -> list += v; v = if (i % 2 == 0) v + a else v - b }
            list to "abwechselnd +$a und $MINUS$b"
        },
        Kind(14, "Wachsende Abstände") { l, r ->
            val d = r.range(1, 5).toLong()
            val c = r.range(1, 2 + l / 15).toLong()
            var v = r.range(1, 12).toLong()
            var step = d
            val list = mutableListOf<Long>()
            repeat(6) { list += v; v += step; step += c }
            list to "Abstand wächst jeweils um $c"
        },
        Kind(16, "Verdreifachung") { _, r ->
            val s = r.range(1, 4).toLong()
            List(6) { var x = s; repeat(it) { x *= 3 }; x } to "immer ×3"
        },
        Kind(19, "Fibonacci") { _, r ->
            var a = r.range(1, 5).toLong()
            var b = r.range(a.toInt(), 8).toLong()
            val list = mutableListOf(a, b)
            repeat(5) { val c = a + b; list += c; a = b; b = c }
            list to "Summe der beiden Vorgänger"
        },
        Kind(21, "Rechteckzahlen") { _, r ->
            val k = r.range(1, 5)
            List(6) { val n = (k + it).toLong(); n * (n + 1) } to "n · (n + 1)"
        },
        Kind(23, "Gemischte Rechenarten") { _, r ->
            val a = r.range(1, 6)
            val m = r.range(2, 3)
            var v = r.range(1, 6).toLong()
            val list = mutableListOf<Long>()
            repeat(7) { i -> list += v; v = if (i % 2 == 0) v + a else v * m }
            list to "abwechselnd +$a und ×$m"
        },
        Kind(26, "Verschränkte Folgen") { l, r ->
            val a = r.range(2, 5 + l / 10)
            val b = r.range(1, 4)
            var x = r.range(1, 10).toLong()
            var y = r.range(30, 60).toLong()
            val list = mutableListOf<Long>()
            repeat(4) { list += x; list += y; x += a; y -= b }
            list.take(8) to "zwei Folgen ineinander: +$a und $MINUS$b"
        },
        Kind(27, "Dreieckszahlen") { _, r ->
            val k = r.range(1, 6)
            List(6) { val n = (k + it).toLong(); n * (n + 1) / 2 } to "Dreieckszahlen (Abstand wächst um 1)"
        },
        Kind(29, "×2 ± c") { _, r ->
            val c = r.range(1, 3).toLong()
            val plus = r.nextBoolean()
            var v = r.range(2, 6).toLong()
            val list = mutableListOf<Long>()
            repeat(6) { list += v; v = if (plus) v * 2 + c else v * 2 - c }
            list to if (plus) "×2, dann +$c" else "×2, dann $MINUS$c"
        },
        Kind(31, "×2 und ×3 im Wechsel") { _, r ->
            var v = r.range(1, 4).toLong()
            val list = mutableListOf<Long>()
            repeat(7) { i -> list += v; v *= if (i % 2 == 0) 2 else 3 }
            list to "abwechselnd ×2 und ×3"
        },
        Kind(32, "Quadrat ± c") { _, r ->
            val k = r.range(1, 5)
            val c = r.range(1, 5).toLong()
            val plus = r.nextBoolean()
            List(6) { val q = ((k + it) * (k + it)).toLong(); if (plus) q + c else q - c } to
                if (plus) "Quadratzahl +$c" else "Quadratzahl $MINUS$c"
        },
        Kind(34, "Tribonacci") { _, r ->
            val list = mutableListOf(r.range(0, 2).toLong(), r.range(1, 2).toLong(), r.range(1, 3).toLong())
            repeat(4) { list += list[list.size - 1] + list[list.size - 2] + list[list.size - 3] }
            list to "Summe der drei Vorgänger"
        },
        Kind(35, "Kubikzahlen") { _, r ->
            val k = r.range(1, 4)
            List(6) { val x = (k + it).toLong(); x * x * x } to "Kubikzahlen (n³)"
        },
        Kind(38, "Abstand verdoppelt") { _, r ->
            val d = r.range(1, 3).toLong()
            var v = r.range(1, 10).toLong()
            var step = d
            val list = mutableListOf<Long>()
            repeat(6) { list += v; v += step; step *= 2 }
            list to "Abstand verdoppelt sich"
        },
        Kind(39, "Quadratische Abstände") { _, r ->
            var v = r.range(1, 10).toLong()
            val list = mutableListOf<Long>()
            for (n in 1..7) { list += v; v += (n * n).toLong() }
            list to "Abstände sind Quadratzahlen 1, 4, 9, …"
        },
        Kind(41, "Primzahlen") { _, r ->
            val off = r.range(0, primes.size - 7)
            primes.subList(off, off + 7) to "Primzahlen"
        },
        Kind(42, "Zweierpotenzen ± k") { _, r ->
            val k = r.range(1, 5).toLong()
            val plus = r.nextBoolean()
            val start = r.range(1, 3)
            List(6) { val p2 = 1L shl (start + it); if (plus) p2 + k else p2 - k } to
                if (plus) "Zweierpotenzen +$k" else "Zweierpotenzen $MINUS$k"
        },
        Kind(44, "×k − m") { _, r ->
            val k = r.range(2, 3)
            val m = r.range(1, 4).toLong()
            var v = r.range(2, 5).toLong()
            val list = mutableListOf<Long>()
            repeat(6) { list += v; v = v * k - m }
            list to "×$k, dann $MINUS$m"
        },
        Kind(47, "Verschränkt ×2 und +3") { _, r ->
            var x = r.range(1, 4).toLong()
            var y = r.range(2, 9).toLong()
            val list = mutableListOf<Long>()
            repeat(4) { list += x; list += y; x *= 2; y += 3 }
            list.take(8) to "zwei Folgen ineinander: ×2 und +3"
        },
    )

    fun generate(level: Int, rng: Random): SequenceTask {
        val l = level.coerceIn(1, 50)
        val unlocked = kinds.filter { it.minLevel <= l }
        repeat(200) {
            val kind = rng.pickFavoringNewest(unlocked)
            val (seq, rule) = kind.build(l, rng) ?: return@repeat
            if (seq.size < 5) return@repeat
            val shownCount = if (seq.size >= 7 && l >= 9) seq.size - 1 else minOf(seq.size - 1, 5)
            val shown = seq.take(shownCount)
            val answer = seq[shownCount]
            if (answer < 0 || answer > LIMIT || shown.any { it < 0 || it > LIMIT }) return@repeat
            return SequenceTask(shown, answer, rule, kind.name)
        }
        return SequenceTask(listOf(2, 4, 6, 8, 10), 12, "immer +2", "Plus-Folge")
    }

    fun label(level: Int): String {
        val unlocked = kinds.filter { it.minLevel <= level }
        val count = unlocked.size
        return (if (count == 1) "1 Regeltyp" else "$count Regeltypen") + " · neu: ${unlocked.last().name}"
    }
}

// ---------------------------------------------------------------- Muster-Matrix

/** Attribute als Index 0..2 in die Werte-Listen des jeweiligen Rätsels. */
data class MatrixCell(val shape: Int, val color: Int, val count: Int, val fill: Int)

data class MatrixTask(
    val shapes: List<Int>,      // 3 Formen (Index in ShapeKind)
    val colors: List<Int>,      // 3 Farben (Index in Spielpalette)
    val grid: List<MatrixCell>, // 9 Zellen, Index 8 ist die gesuchte
    val options: List<MatrixCell>,
    val correctIndex: Int,
    val rules: List<String>,
)

object MatrixGen {
    const val SHAPE_KINDS = 6
    const val COLOR_KINDS = 5

    enum class Rule { CONSTANT, ROW, COL, LATIN1, LATIN2 }

    fun varyingCount(level: Int) = when {
        level <= 8 -> 1
        level <= 20 -> 2
        level <= 34 -> 3
        else -> 4
    }

    fun optionCount(level: Int) = if (level <= 15) 4 else 6

    private fun value(rule: Rule, perm: List<Int>, row: Int, col: Int): Int = when (rule) {
        Rule.CONSTANT -> perm[0]
        Rule.ROW -> perm[row]
        Rule.COL -> perm[col]
        Rule.LATIN1 -> perm[(row + col) % 3]
        Rule.LATIN2 -> perm[(row + 2 * col) % 3]
    }

    private val attrNames = listOf("Form", "Farbe", "Anzahl", "Füllung")

    fun generate(level: Int, rng: Random): MatrixTask {
        val l = level.coerceIn(1, 50)
        val shapes = (0 until SHAPE_KINDS).shuffled(rng).take(3)
        val colors = (0 until COLOR_KINDS).shuffled(rng).take(3)
        val varying = (0 until 4).shuffled(rng).take(varyingCount(l)).toSet()
        val pool = when {
            l <= 5 -> listOf(Rule.ROW, Rule.COL)
            l <= 12 -> listOf(Rule.ROW, Rule.COL, Rule.LATIN1)
            else -> listOf(Rule.COL, Rule.LATIN1, Rule.LATIN2, Rule.LATIN1)
        }
        val rules = List(4) { a -> if (a in varying) pool[rng.nextInt(pool.size)] else Rule.CONSTANT }
        val perms = List(4) { listOf(0, 1, 2).shuffled(rng) }
        val grid = List(9) { i ->
            val r = i / 3
            val c = i % 3
            val v = IntArray(4) { a -> value(rules[a], perms[a], r, c) }
            MatrixCell(v[0], v[1], v[2], v[3])
        }
        val answer = grid[8]
        val optionTotal = optionCount(l)
        val options = mutableListOf(answer)
        // Ablenker: unterscheiden sich in einem Merkmal – bevorzugt in den variierenden (schwerer)
        val attrOrder = varying.shuffled(rng) + (0 until 4).filter { it !in varying }.shuffled(rng)
        val candidates = mutableListOf<MatrixCell>()
        for (a in attrOrder) {
            for (delta in listOf(1, 2).shuffled(rng)) candidates += modify(answer, a, (get(answer, a) + delta) % 3)
        }
        // Bei vielen Optionen: zusätzlich Zwei-Merkmal-Varianten
        if (optionTotal > candidates.size + 1 || l > 25) {
            for (a in varying) for (b in varying) if (a < b) {
                candidates += modify(modify(answer, a, (get(answer, a) + 1) % 3), b, (get(answer, b) + 2) % 3)
            }
        }
        val easy = l <= 10
        val ordered = if (easy) candidates.shuffled(rng) else candidates
        for (c in ordered) {
            if (options.size >= optionTotal) break
            if (c !in options) options += c
        }
        while (options.size < optionTotal) {
            val c = MatrixCell(rng.nextInt(3), rng.nextInt(3), rng.nextInt(3), rng.nextInt(3))
            if (c !in options) options += c
        }
        val shuffled = options.shuffled(rng)
        val ruleText = (0 until 4).filter { it in varying }.map { a ->
            val how = when (rules[a]) {
                Rule.ROW -> "gleich pro Zeile"
                Rule.COL -> "gleich pro Spalte"
                else -> "jede Variante einmal pro Zeile"
            }
            "${attrNames[a]}: $how"
        }
        return MatrixTask(shapes, colors, grid, shuffled, shuffled.indexOf(answer), ruleText)
    }

    private fun get(c: MatrixCell, a: Int) = when (a) {
        0 -> c.shape
        1 -> c.color
        2 -> c.count
        else -> c.fill
    }

    private fun modify(c: MatrixCell, a: Int, v: Int) = when (a) {
        0 -> c.copy(shape = v)
        1 -> c.copy(color = v)
        2 -> c.copy(count = v)
        else -> c.copy(fill = v)
    }
}

// ---------------------------------------------------------------- Schlussfolgern

data class SyllogismTask(val premises: List<String>, val conclusion: String, val valid: Boolean, val explanation: String, val type: String = "")

object SyllogismGen {
    private val names = listOf("Anna", "Ben", "Clara", "David", "Emil", "Frida", "Greta", "Hugo", "Ida", "Jonas")
    private val relations = listOf(
        "größer" to "kleiner", "schneller" to "langsamer", "älter" to "jünger",
        "schwerer" to "leichter", "lauter" to "leiser",
    )
    private val terms = listOf("Wumpel", "Fliks", "Glorbs", "Zinken", "Trabels", "Morks", "Plinsen", "Quarbs", "Snorks", "Brimmel")

    fun personCount(level: Int) = when {
        level <= 6 -> 3
        level <= 16 -> 4
        level <= 28 -> 5
        else -> 6
    }

    fun generate(level: Int, rng: Random): SyllogismTask {
        val l = level.coerceIn(1, 50)
        val roll = rng.nextDouble()
        return when {
            l < 12 -> relational(l, rng)
            l < 24 -> if (roll < 0.5) relational(l, rng) else categorical(rng)
            else -> when {
                roll < 0.34 -> relational(l, rng)
                roll < 0.67 -> categorical(rng)
                else -> conditional(rng)
            }
        }
    }

    private fun relational(l: Int, rng: Random): SyllogismTask {
        val n = personCount(l)
        val order = names.shuffled(rng).take(n) // order[0] hat am meisten
        val (more, less) = relations[rng.nextInt(relations.size)]
        val mixed = l >= 5
        val premises = (0 until n - 1).map { i ->
            if (mixed && rng.nextBoolean()) "${order[i + 1]} ist $less als ${order[i]}."
            else "${order[i]} ist $more als ${order[i + 1]}."
        }.let { if (l >= 3) it.shuffled(rng) else it }
        var i: Int
        var j: Int
        do {
            i = rng.nextInt(n)
            j = rng.nextInt(n)
        } while (i == j || (l >= 10 && n > 3 && kotlin.math.abs(i - j) == 1 && rng.nextDouble() < 0.7))
        val useMore = rng.nextBoolean()
        val conclusion = if (useMore) "${order[i]} ist $more als ${order[j]}." else "${order[i]} ist $less als ${order[j]}."
        val valid = if (useMore) i < j else i > j
        val chain = order.joinToString(" > ")
        return SyllogismTask(premises, conclusion, valid, "Reihenfolge ($more): $chain", "Vergleiche")
    }

    private data class Form(val p1: String, val p2: String, val c: String, val valid: Boolean, val why: String)

    private val forms = listOf(
        Form("Alle {A} sind {B}.", "Alle {B} sind {C}.", "Alle {A} sind {C}.", true, "Zwingend: {A} liegt in {B}, {B} liegt in {C}."),
        Form("Keine {B} sind {C}.", "Alle {A} sind {B}.", "Keine {A} sind {C}.", true, "Zwingend: {A} gehört zu {B} – und {B} hat nichts mit {C} gemeinsam."),
        Form("Alle {B} sind {C}.", "Einige {A} sind {B}.", "Einige {A} sind {C}.", true, "Zwingend: die {A}, die {B} sind, sind auch {C}."),
        Form("Keine {B} sind {C}.", "Einige {A} sind {B}.", "Einige {A} sind keine {C}.", true, "Zwingend: die {A}, die {B} sind, können keine {C} sein."),
        Form("Alle {A} sind {B}.", "Keine {B} sind {C}.", "Keine {C} sind {A}.", true, "Zwingend: alle {A} liegen in {B}, und {B} und {C} überschneiden sich nicht."),
        Form("Alle {A} sind {B}.", "Alle {C} sind {B}.", "Alle {A} sind {C}.", false, "Nicht zwingend: {A} und {C} liegen beide in {B}, aber nicht unbedingt ineinander."),
        Form("Alle {A} sind {B}.", "Einige {B} sind {C}.", "Einige {A} sind {C}.", false, "Nicht zwingend: die {B}, die {C} sind, müssen keine {A} sein."),
        Form("Einige {A} sind {B}.", "Einige {B} sind {C}.", "Einige {A} sind {C}.", false, "Nicht zwingend: es können verschiedene {B} gemeint sein."),
        Form("Keine {A} sind {B}.", "Keine {B} sind {C}.", "Keine {A} sind {C}.", false, "Nicht zwingend: {A} und {C} könnten sich trotzdem überschneiden."),
        Form("Alle {A} sind {B}.", "Keine {C} sind {A}.", "Keine {C} sind {B}.", false, "Nicht zwingend: {C} könnte im Teil von {B} liegen, der nicht {A} ist."),
        Form("Alle {A} sind {B}.", "Alle {B} sind {C}.", "Alle {C} sind {A}.", false, "Nicht zwingend: die Umkehrung gilt nicht – {C} kann größer sein."),
        Form("Alle {B} sind {C}.", "Einige {A} sind {B}.", "Alle {A} sind {C}.", false, "Nicht zwingend: nur über einige {A} ist etwas bekannt."),
    )

    private fun categorical(rng: Random): SyllogismTask {
        val form = forms[rng.nextInt(forms.size)]
        val (a, b, c) = terms.shuffled(rng).take(3)
        fun fill(s: String) = s.replace("{A}", a).replace("{B}", b).replace("{C}", c)
        val premises = listOf(fill(form.p1), fill(form.p2)).shuffled(rng)
        return SyllogismTask(premises, fill(form.c), form.valid, fill(form.why), "Mengenlogik")
    }

    private data class Scenario(val pSub: String, val p: String, val notP: String, val qInv: String, val q: String, val notQ: String)

    private val scenarios = listOf(
        Scenario("die Ampel rot ist", "Die Ampel ist rot.", "Die Ampel ist nicht rot.", "halten die Autos", "Die Autos halten.", "Die Autos halten nicht."),
        Scenario("es regnet", "Es regnet.", "Es regnet nicht.", "ist die Straße nass", "Die Straße ist nass.", "Die Straße ist nicht nass."),
        Scenario("der Knopf gedrückt wird", "Der Knopf wird gedrückt.", "Der Knopf wird nicht gedrückt.", "piept das Gerät", "Das Gerät piept.", "Das Gerät piept nicht."),
        Scenario("Mia Zeit hat", "Mia hat Zeit.", "Mia hat keine Zeit.", "geht sie schwimmen", "Mia geht schwimmen.", "Mia geht nicht schwimmen."),
        Scenario("der Zorg blau ist", "Der Zorg ist blau.", "Der Zorg ist nicht blau.", "summt er", "Der Zorg summt.", "Der Zorg summt nicht."),
        Scenario("die Sonne scheint", "Die Sonne scheint.", "Die Sonne scheint nicht.", "ist der Park voll", "Der Park ist voll.", "Der Park ist nicht voll."),
        Scenario("der Akku leer ist", "Der Akku ist leer.", "Der Akku ist nicht leer.", "geht das Handy aus", "Das Handy geht aus.", "Das Handy geht nicht aus."),
        Scenario("Tom pfeift", "Tom pfeift.", "Tom pfeift nicht.", "bellt der Hund", "Der Hund bellt.", "Der Hund bellt nicht."),
    )

    private fun conditional(rng: Random): SyllogismTask {
        val s = scenarios[rng.nextInt(scenarios.size)]
        val rule = "Wenn ${s.pSub}, dann ${s.qInv}."
        return when (rng.nextInt(4)) {
            0 -> SyllogismTask(listOf(rule, s.p), s.q, true, "Zwingend: die Bedingung ist erfüllt, also tritt die Folge ein.", "Wenn-dann")
            1 -> SyllogismTask(listOf(rule, s.notQ), s.notP, true, "Zwingend: wäre die Bedingung erfüllt, müsste die Folge eintreten.", "Wenn-dann")
            2 -> SyllogismTask(listOf(rule, s.q), s.p, false, "Nicht zwingend: die Folge kann auch andere Gründe haben.", "Wenn-dann")
            else -> SyllogismTask(listOf(rule, s.notP), s.notQ, false, "Nicht zwingend: über den Fall ohne Bedingung sagt die Regel nichts.", "Wenn-dann")
        }
    }

    fun label(level: Int): String = when {
        level < 12 -> "${personCount(level)} Personen"
        level < 24 -> "${personCount(level)} Personen + Mengenlogik"
        else -> "Mengen, Wenn-dann & ${personCount(level)} Personen"
    }
}

// ---------------------------------------------------------------- Waage

/** comparisons: Paare (schwerer, leichter) als Objekt-Index; leftHeavy: Seite des schwereren Objekts. */
data class BalanceTask(
    val objectCount: Int,
    val comparisons: List<Pair<Int, Int>>,
    val heavyOnLeft: List<Boolean>,
    val askHeaviest: Boolean,
    val answer: Int,
    val order: List<Int>,
)

object BalanceGen {
    fun objectCount(level: Int) = when {
        level <= 10 -> 3
        level <= 24 -> 4
        level <= 38 -> 5
        else -> 6
    }

    fun generate(level: Int, rng: Random): BalanceTask {
        val l = level.coerceIn(1, 50)
        val n = objectCount(l)
        val order = (0 until n).shuffled(rng) // order[0] = schwerstes
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n - 1) pairs += order[i] to order[i + 1]
        if (l >= 15 && n >= 4) {
            val i = rng.nextInt(n - 2)
            pairs += order[i] to order[i + 2]
        }
        val comps = pairs.shuffled(rng)
        val ask = if (l < 20) true else rng.nextBoolean()
        return BalanceTask(n, comps, List(comps.size) { rng.nextBoolean() }, ask, if (ask) order.first() else order.last(), order)
    }
}
