package com.mycompany.synapse.game.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
private val dateFmt = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

fun weekdayName(d: DayOfWeek) = WEEKDAYS[d.value - 1]

// ---------------------------------------------------------------- Wochentag

data class WeekdayTask(val question: String, val anchor: String?, val answer: DayOfWeek, val explanation: String, val kind: String)

object WeekdayGen {
    fun mode(level: Int) = when {
        level <= 8 -> "Relativ"
        level <= 20 -> "Mit Jahresanker"
        level <= 34 -> "Doomsday mit Anker"
        else -> "Ohne Anker"
    }

    /** Doomsday eines Jahres (Wochentag von 4.4., 6.6., 8.8., 10.10., 12.12., letzter Februartag). */
    fun doomsday(year: Int): DayOfWeek = LocalDate.of(year, 4, 4).dayOfWeek

    fun generate(level: Int, rng: Random): WeekdayTask {
        val l = level.coerceIn(1, 50)
        return when (mode(l)) {
            "Relativ" -> {
                val today = DayOfWeek.of(rng.range(1, 7))
                val n = rng.range(2, 5 + l * 2)
                val back = l >= 5 && rng.nextBoolean()
                val answer = if (back) today.minus(n.toLong()) else today.plus(n.toLong())
                WeekdayTask(
                    "Heute ist ${weekdayName(today)}. Welcher Tag ist " + if (back) "vor $n Tagen?" else "in $n Tagen?",
                    null, answer,
                    "$n Tage = ${n / 7} Wochen + ${n % 7} Tage → ${weekdayName(answer)}", "Relativ",
                )
            }
            "Mit Jahresanker" -> {
                val year = rng.range(2000, 2035)
                val jan1 = LocalDate.of(year, 1, 1)
                val maxDay = (40 + (l - 9) * 28).coerceAtMost(364)
                val date = jan1.plusDays(rng.range(20, maxDay).toLong())
                WeekdayTask(
                    "Welcher Wochentag ist der ${date.format(dateFmt)}?",
                    "Der 1. Januar $year ist ein ${weekdayName(jan1.dayOfWeek)}.",
                    date.dayOfWeek,
                    "${date.format(dateFmt)} ist ein ${weekdayName(date.dayOfWeek)} (${jan1.until(date).toTotalMonths()} Monate nach dem 1.1.)",
                    "Mit Jahresanker",
                )
            }
            "Doomsday mit Anker" -> {
                val year = rng.range(1990, 2040)
                val date = LocalDate.of(year, rng.range(1, 12), 1).let { it.withDayOfMonth(rng.range(1, it.lengthOfMonth())) }
                WeekdayTask(
                    "Welcher Wochentag ist der ${date.format(dateFmt)}?",
                    "Doomsday $year: ${weekdayName(doomsday(year))} (4.4., 6.6., 8.8., 10.10., 12.12., 9.5., 5.9., 11.7., 7.11.)",
                    date.dayOfWeek,
                    "${date.format(dateFmt)} ist ein ${weekdayName(date.dayOfWeek)}",
                    "Doomsday mit Anker",
                )
            }
            else -> {
                val year = rng.range(1950, 2050)
                val date = LocalDate.of(year, rng.range(1, 12), 1).let { it.withDayOfMonth(rng.range(1, it.lengthOfMonth())) }
                WeekdayTask(
                    "Welcher Wochentag ist der ${date.format(dateFmt)}?", null, date.dayOfWeek,
                    "${date.format(dateFmt)} ist ein ${weekdayName(date.dayOfWeek)} (Doomsday $year: ${weekdayName(doomsday(year))})",
                    "Ohne Anker",
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Schätzen

data class EstimateTask(val question: String, val options: List<Long>, val correctIndex: Int, val exact: String, val kind: String)

object EstimateGen {
    fun spread(level: Int): Double = (0.45 - level * 0.0075).coerceAtLeast(0.08)

    fun roundNice(v: Double, digits: Int): Long {
        if (v <= 0) return 0
        val mag = 10.0.pow(floor(log10(v)) - (digits - 1))
        return ((v / mag).roundToLong() * mag).roundToLong().coerceAtLeast(1)
    }

    private fun fmt(v: Double): String = if (abs(v - v.roundToLong()) < 1e-9) v.roundToLong().toString()
    else String.format(Locale.GERMAN, "%.1f", v)

    fun generate(level: Int, rng: Random): EstimateTask {
        val l = level.coerceIn(1, 50)
        repeat(100) {
            val kinds = mutableListOf("Multiplikation")
            if (l >= 8) kinds += "Prozent"
            if (l >= 16) kinds += "Division"
            if (l >= 24) kinds += "Brüche"
            val kind = kinds[rng.nextInt(kinds.size)]
            val (question, exact) = when (kind) {
                "Multiplikation" -> {
                    val a = rng.range(12, 40 + l * 6)
                    val b = rng.range(11, 30 + l * 3)
                    "$a × $b" to (a * b).toDouble()
                }
                "Prozent" -> {
                    val p = rng.range(3, 97)
                    val base = rng.range(80, 400 + l * 40)
                    "$p % von $base" to p * base / 100.0
                }
                "Division" -> {
                    val b = rng.range(7, 60)
                    val a = rng.range(500, 5000 + l * 300)
                    "$a ÷ $b" to a.toDouble() / b
                }
                else -> {
                    val den = listOf(3, 4, 5, 6, 7, 8, 9, 12)[rng.nextInt(8)]
                    val num = rng.range(1, den - 1)
                    val x = rng.range(100, 2000 + l * 50)
                    "$num/$den von $x" to x.toDouble() * num / den
                }
            }
            if (exact < 10) return@repeat
            val s = spread(l)
            val digits = if (s < 0.2) 3 else 2
            val correct = roundNice(exact, digits)
            val factors = listOf(1 - 2 * s, 1 - s, 1 + s, 1 + 2 * s).shuffled(rng).take(3)
            val options = (factors.map { roundNice(exact * it, digits) } + correct).distinct()
            if (options.size < 4) return@repeat
            val nearest = options.minBy { abs(it - exact) }
            if (nearest != correct) return@repeat
            // eindeutig: nächstbeste Option deutlich weiter weg
            val second = options.filter { it != correct }.minOf { abs(it - exact) }
            if (second < abs(correct - exact) * 1.5 + exact * s * 0.4) return@repeat
            val shuffled = options.shuffled(rng)
            return EstimateTask("$question ≈ ?", shuffled, shuffled.indexOf(correct), "Genau: ${fmt(exact)}", kind)
        }
        return EstimateTask("48 × 52 ≈ ?", listOf(2500, 1900, 3100, 3700), 0, "Genau: 2496", "Multiplikation")
    }

    fun label(level: Int) = "±${(spread(level) * 100).toInt()} % Abstand"
}

// ---------------------------------------------------------------- Mini-Sudoku

class SudokuTask(val size: Int, val boxRows: Int, val boxCols: Int, val puzzle: IntArray, val solution: IntArray) {
    val givens: Int get() = puzzle.count { it != 0 }
}

object SudokuGen {
    fun size(level: Int) = if (level <= 12) 4 else 6

    fun givens(level: Int): Int = if (level <= 12) (11 - (level - 1) / 2).coerceAtLeast(5) else (24 - (level - 13) / 3).coerceAtLeast(12)

    private fun box(size: Int) = if (size == 4) 2 to 2 else 2 to 3

    fun isValid(grid: IntArray, size: Int, idx: Int, v: Int): Boolean {
        val (br, bc) = box(size)
        val r = idx / size
        val c = idx % size
        for (k in 0 until size) {
            if (grid[r * size + k] == v && r * size + k != idx) return false
            if (grid[k * size + c] == v && k * size + c != idx) return false
        }
        val r0 = r / br * br
        val c0 = c / bc * bc
        for (rr in r0 until r0 + br) for (cc in c0 until c0 + bc) {
            val j = rr * size + cc
            if (j != idx && grid[j] == v) return false
        }
        return true
    }

    private fun fill(grid: IntArray, size: Int, rng: Random): Boolean {
        val idx = grid.indexOf(0)
        if (idx < 0) return true
        for (v in (1..size).shuffled(rng)) {
            if (isValid(grid, size, idx, v)) {
                grid[idx] = v
                if (fill(grid, size, rng)) return true
                grid[idx] = 0
            }
        }
        return false
    }

    /** Zählt Lösungen (bis [limit]) – für die Eindeutigkeit. */
    fun countSolutions(grid: IntArray, size: Int, limit: Int = 2): Int {
        val idx = grid.indexOf(0)
        if (idx < 0) return 1
        var count = 0
        for (v in 1..size) {
            if (isValid(grid, size, idx, v)) {
                grid[idx] = v
                count += countSolutions(grid, size, limit - count)
                grid[idx] = 0
                if (count >= limit) break
            }
        }
        return count
    }

    fun generate(level: Int, rng: Random): SudokuTask {
        val l = level.coerceIn(1, 50)
        val size = size(l)
        val (br, bc) = box(size)
        val solution = IntArray(size * size)
        fill(solution, size, rng)
        val puzzle = solution.copyOf()
        val target = givens(l)
        for (idx in (0 until size * size).shuffled(rng)) {
            if (puzzle.count { it != 0 } <= target) break
            val keep = puzzle[idx]
            puzzle[idx] = 0
            if (countSolutions(puzzle.copyOf(), size) != 1) puzzle[idx] = keep
        }
        return SudokuTask(size, br, bc, puzzle, solution)
    }

    fun label(level: Int) = "${size(level)}×${size(level)} · ${givens(level)} Vorgaben"
}

// ---------------------------------------------------------------- Namen merken

/** Gezeichnetes Gesicht – alle Merkmale als Index. */
data class Face(
    val skin: Int,
    val hairStyle: Int,
    val hairColor: Int,
    val eyes: Int,
    val glasses: Boolean,
    val beard: Int,
    val shirt: Int,
    val accessory: Int,
) {
    fun differences(o: Face): Int = listOf(
        skin != o.skin, hairStyle != o.hairStyle, hairColor != o.hairColor, eyes != o.eyes,
        glasses != o.glasses, beard != o.beard, shirt != o.shirt, accessory != o.accessory,
    ).count { it }
}

data class NamesTask(
    val people: List<Pair<Face, String>>,
    val studyMs: Long,
    val quizOrder: List<Int>,
    val options: List<List<String>>,
)

object NamesGen {
    val names = listOf(
        "Anna", "Ben", "Clara", "David", "Emil", "Frida", "Greta", "Hugo", "Ida", "Jonas", "Karla", "Leon", "Mia",
        "Noah", "Olga", "Paul", "Rosa", "Samuel", "Tilda", "Uwe", "Vera", "Willi", "Yara", "Zoe", "Anton", "Bettina",
        "Carlo", "Doris", "Erik", "Fiona", "Georg", "Helga", "Igor", "Jana", "Kurt", "Lina", "Moritz", "Nina", "Otto",
        "Petra", "Ralf", "Sophie", "Theo", "Ute", "Valentin", "Wanda", "Xaver", "Yvonne", "Zeno", "Ella", "Max", "Lotte",
    )

    fun count(level: Int) = (3 + (level.coerceIn(1, 50) - 1) / 8).coerceAtMost(8)
    fun studyMs(level: Int) = (3600L - level * 40L).coerceAtLeast(1600L)

    fun generate(level: Int, rng: Random): NamesTask {
        val l = level.coerceIn(1, 50)
        val n = count(l)
        val minDiff = when {
            l <= 15 -> 4
            l <= 35 -> 3
            else -> 2
        }
        val faces = mutableListOf<Face>()
        var guard = 0
        while (faces.size < n && guard++ < 2000) {
            val f = Face(
                rng.nextInt(5), rng.nextInt(6), rng.nextInt(6), rng.nextInt(3), rng.nextDouble() < 0.3,
                if (rng.nextDouble() < 0.6) 0 else rng.range(1, 2), rng.nextInt(6), rng.nextInt(4),
            )
            if (faces.all { it.differences(f) >= minDiff }) faces += f
        }
        val picked = names.shuffled(rng).take(n)
        val people = faces.zip(picked)
        val order = people.indices.shuffled(rng)
        val options = order.map { i ->
            val correct = people[i].second
            val pool = (picked - correct).shuffled(rng) + (names - picked.toSet()).shuffled(rng)
            (pool.take(3) + correct).shuffled(rng)
        }
        return NamesTask(people, studyMs(l), order, options)
    }

    fun label(level: Int) = "${count(level)} Personen"
}

// ---------------------------------------------------------------- Einkaufsliste

data class ShoppingTask(val items: List<String>, val grid: List<String>, val studyMs: Long, val ordered: Boolean)

object ShoppingGen {
    val items = listOf(
        "🍎 Äpfel", "🍌 Bananen", "🥛 Milch", "🧀 Käse", "🥚 Eier", "🍞 Brot", "🧈 Butter", "🥕 Karotten", "🍅 Tomaten",
        "🥔 Kartoffeln", "🧅 Zwiebeln", "🧄 Knoblauch", "🍋 Zitronen", "🍇 Trauben", "🍓 Erdbeeren", "🥒 Gurke",
        "🥦 Brokkoli", "🌽 Mais", "🍄 Pilze", "🍚 Reis", "🍝 Nudeln", "🥫 Dosensuppe", "🍯 Honig", "☕ Kaffee",
        "🍵 Tee", "🧃 Saft", "🍫 Schokolade", "🍪 Kekse", "🥜 Erdnüsse", "🐟 Fisch", "🍗 Hähnchen", "🥩 Steak",
        "🌭 Würstchen", "🧂 Salz", "🫒 Oliven", "🥑 Avocado", "🍐 Birnen", "🍊 Orangen", "🥥 Kokosnuss", "🍍 Ananas",
        "🧻 Klopapier", "🧼 Seife", "🪥 Zahnbürste", "🧴 Shampoo", "🕯️ Kerzen", "🔋 Batterien", "💡 Glühbirne",
        "🧽 Schwamm", "🌶️ Chili", "🥨 Brezel", "🥐 Croissant", "🍦 Eis", "🥤 Limo", "🍿 Popcorn", "🫑 Paprika",
        "🥬 Salat", "🍑 Pfirsiche", "🍒 Kirschen", "🥝 Kiwis", "🧇 Waffeln",
    )

    fun count(level: Int) = (4 + (level.coerceIn(1, 50) - 1) / 6).coerceAtMost(12)
    fun ordered(level: Int) = level >= 30

    fun generate(level: Int, rng: Random): ShoppingTask {
        val l = level.coerceIn(1, 50)
        val k = count(l)
        val shuffled = items.shuffled(rng)
        val list = shuffled.take(k)
        val distractors = shuffled.drop(k).take(maxOf(4, (k + 1) / 2 + 2))
        val per = (1500L - l * 15L).coerceAtLeast(700L)
        return ShoppingTask(list, (list + distractors).shuffled(rng), k * per, ordered(l))
    }

    fun label(level: Int) = "${count(level)} Dinge" + if (ordered(level)) " · in Reihenfolge" else ""
}

// ---------------------------------------------------------------- Weg im Kopf

data class PathTask(
    val size: Int,
    val start: Int,
    val startDir: Int, // 0 = Norden, 1 = Osten, 2 = Süden, 3 = Westen
    val steps: List<String>,
    val end: Int,
    val relative: Boolean,
    val stepMs: Long,
)

object PathGen {
    private val arrows = listOf("↑", "→", "↓", "←")
    fun size(level: Int) = when {
        level <= 6 -> 4
        level <= 24 -> 5
        else -> 6
    }

    fun stepCount(level: Int) = (3 + (level.coerceIn(1, 50) - 1) / 6).coerceAtMost(10)
    fun relative(level: Int) = level >= 13

    fun generate(level: Int, rng: Random): PathTask {
        val l = level.coerceIn(1, 50)
        val n = size(l)
        val rel = relative(l)
        val stepMs = (1900L - l * 20L).coerceAtLeast(900L)
        repeat(500) {
            var r = rng.nextInt(n)
            var c = rng.nextInt(n)
            val start = r * n + c
            var dir = rng.nextInt(4)
            val startDir = dir
            val steps = mutableListOf<String>()
            var ok = true
            repeat(stepCount(l)) {
                if (rel && steps.isNotEmpty() && !steps.last().startsWith("dreh") && rng.nextDouble() < 0.45) {
                    val right = rng.nextBoolean()
                    dir = (dir + if (right) 1 else 3) % 4
                    steps += if (right) "drehen: rechts" else "drehen: links"
                } else {
                    if (!rel) dir = rng.nextInt(4)
                    val k = rng.range(1, if (n >= 5) 3 else 2)
                    r += when (dir) { 0 -> -k; 2 -> k; else -> 0 }
                    c += when (dir) { 1 -> k; 3 -> -k; else -> 0 }
                    if (r !in 0 until n || c !in 0 until n) ok = false
                    steps += if (rel) "$k vor" else "$k ${arrows[dir]}"
                }
            }
            val end = r * n + c
            if (ok && end != start && steps.count { !it.startsWith("dreh") } >= 2) {
                return PathTask(n, start, startDir, steps, end, rel, stepMs)
            }
        }
        return PathTask(4, 0, 1, listOf("1 →", "1 ↓"), 5, false, stepMs)
    }

    /** Endfeld unabhängig vom Generator nachrechnen (für Tests). */
    fun walk(task: PathTask): Int? {
        var r = task.start / task.size
        var c = task.start % task.size
        var dir = task.startDir
        for (s in task.steps) {
            when {
                s == "drehen: rechts" -> dir = (dir + 1) % 4
                s == "drehen: links" -> dir = (dir + 3) % 4
                else -> {
                    val k = s.substringBefore(" ").toInt()
                    val d = if (task.relative) dir else arrows.indexOf(s.substringAfter(" "))
                    r += when (d) { 0 -> -k; 2 -> k; else -> 0 }
                    c += when (d) { 1 -> k; 3 -> -k; else -> 0 }
                    if (r !in 0 until task.size || c !in 0 until task.size) return null
                }
            }
        }
        return r * task.size + c
    }

    fun label(level: Int) = "${size(level)}×${size(level)} · ${stepCount(level)} Schritte" + if (relative(level)) " · Blickrichtung" else ""
}
