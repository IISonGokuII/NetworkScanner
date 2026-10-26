package com.mycompany.synapse.game.logic

import kotlin.random.Random

/** Zufallszahl inklusive beider Grenzen. */
internal fun Random.range(from: Int, to: Int): Int = if (to <= from) from else nextInt(from, to + 1)

/** Wählt bevorzugt (50 %) eine der zuletzt freigeschalteten Varianten, damit Neues geübt wird. */
internal fun <T> Random.pickFavoringNewest(items: List<T>, newest: Int = 3): T {
    if (items.size > newest && nextDouble() < 0.5) return items[nextInt(items.size - newest, items.size)]
    return items[nextInt(items.size)]
}

const val MINUS = "−"

// ---------------------------------------------------------------- Blitzrechnen

data class ArithmeticTask(val text: String, val answer: Int)

object ArithmeticGen {
    fun generate(level: Int, rng: Random): ArithmeticTask {
        val l = level.coerceIn(1, 50)
        val addMax = 10 + l * 4
        val kinds = mutableListOf<() -> ArithmeticTask>()
        kinds += {
            val a = rng.range(1, addMax)
            val b = rng.range(1, addMax)
            ArithmeticTask("$a + $b", a + b)
        }
        kinds += {
            val a = rng.range(2, addMax)
            val b = rng.range(1, a)
            ArithmeticTask("$a $MINUS $b", a - b)
        }
        if (l >= 6) kinds += {
            val a = rng.range(2, minOf(4 + l / 2, 15))
            val b = rng.range(2, 10 + l / 10)
            ArithmeticTask("$a × $b", a * b)
        }
        if (l >= 12) kinds += {
            val b = rng.range(2, 10 + l / 10)
            val q = rng.range(2, 9 + l / 4)
            ArithmeticTask("${b * q} ÷ $b", q)
        }
        if (l >= 20) kinds += {
            val b = rng.range(2, 9)
            val c = rng.range(2, 9 + l / 10)
            val a = rng.range(1, 20 + l)
            if (rng.nextBoolean()) ArithmeticTask("$a + $b × $c", a + b * c)
            else ArithmeticTask("$b × $c + $a", b * c + a)
        }
        if (l >= 24) kinds += {
            val a = rng.range(2, 9)
            val b = rng.range(2, 9)
            val c = rng.range(2, 6)
            ArithmeticTask("($a + $b) × $c", (a + b) * c)
        }
        if (l >= 26) kinds += {
            val a = rng.range(12, 39 + l)
            val b = rng.range(3, 9)
            ArithmeticTask("$a × $b", a * b)
        }
        if (l >= 30) kinds += {
            val p = listOf(10, 20, 25, 50, 75)[rng.nextInt(5)]
            val base = rng.range(2, 20) * 20
            ArithmeticTask("$p % von $base", base * p / 100)
        }
        if (l >= 32) kinds += {
            val a = rng.range(11, 25)
            ArithmeticTask("$a²", a * a)
        }
        if (l >= 36) kinds += {
            val a = rng.range(11, 30 + l / 2)
            val b = rng.range(11, 19 + l / 5)
            ArithmeticTask("$a × $b", a * b)
        }
        if (l >= 40) kinds += {
            val a = rng.range(150, 999)
            val b = rng.range(100, a)
            if (rng.nextBoolean()) ArithmeticTask("$a + $b", a + b) else ArithmeticTask("$a $MINUS $b", a - b)
        }
        if (l >= 44) kinds += {
            val p = listOf(5, 15, 30, 40, 60)[rng.nextInt(5)]
            val base = rng.range(2, 30) * 20
            ArithmeticTask("$p % von $base", base * p / 100)
        }
        return rng.pickFavoringNewest(kinds)()
    }

    fun label(level: Int): String = when {
        level < 6 -> "Plus & Minus"
        level < 12 -> "+ − ×"
        level < 20 -> "+ − × ÷"
        level < 30 -> "Punkt vor Strich"
        level < 40 -> "Prozent & Quadrate"
        else -> "Profi-Mix"
    }
}

// ---------------------------------------------------------------- Kettenrechnen

data class ChainTask(val start: Int, val steps: List<String>, val answer: Int, val stepMs: Long)

object ChainGen {
    fun stepCount(level: Int) = 3 + (level.coerceIn(1, 50) - 1) / 6

    fun generate(level: Int, rng: Random): ChainTask {
        val l = level.coerceIn(1, 50)
        val count = stepCount(l)
        val stepMs = (1900 - l * 24L).coerceAtLeast(700)
        val maxAdd = 5 + l / 2
        var total = rng.range(2, 5 + l)
        val start = total
        val steps = mutableListOf<String>()
        repeat(count) {
            val options = mutableListOf<String>()
            options += "+"
            if (total > 3) options += "-"
            if (l >= 8 && total in 2..60) options += "*"
            if (l >= 16 && (total % 2 == 0 || total % 3 == 0 || total % 5 == 0) && total >= 6) options += "/"
            when (options[rng.nextInt(options.size)]) {
                "+" -> {
                    val n = rng.range(1, maxAdd)
                    total += n; steps += "+ $n"
                }
                "-" -> {
                    val n = rng.range(1, minOf(maxAdd, total - 1))
                    total -= n; steps += "$MINUS $n"
                }
                "*" -> {
                    val n = if (total <= 20 && l >= 24) rng.range(2, 4) else 2
                    total *= n; steps += "× $n"
                }
                else -> {
                    val divisors = listOf(2, 3, 5).filter { total % it == 0 }
                    val n = divisors[rng.nextInt(divisors.size)]
                    total /= n; steps += "÷ $n"
                }
            }
        }
        return ChainTask(start, steps, total, stepMs)
    }
}

// ---------------------------------------------------------------- Zielzahl

data class TargetTask(val numbers: List<Int>, val target: Int, val solution: String, val ops: Set<Char>)

object TargetGen {
    fun numberCount(level: Int) = when {
        level <= 8 -> 3
        level <= 20 -> 4
        level <= 34 -> 5
        else -> 6
    }

    fun ops(level: Int): Set<Char> = when {
        level <= 4 -> setOf('+', '-')
        level <= 12 -> setOf('+', '-', '*')
        else -> setOf('+', '-', '*', '/')
    }

    private data class Expr(val value: Int, val text: String, val atom: Boolean)

    fun apply(a: Int, op: Char, b: Int): Int? = when (op) {
        '+' -> a + b
        '-' -> if (a > b) a - b else null
        '*' -> if (a == 1 || b == 1 || a * b > 2000) null else a * b
        '/' -> if (b > 1 && a % b == 0) a / b else null
        else -> null
    }

    fun symbol(op: Char) = when (op) {
        '-' -> MINUS
        '*' -> "×"
        '/' -> "÷"
        else -> "+"
    }

    fun generate(level: Int, rng: Random): TargetTask {
        val l = level.coerceIn(1, 50)
        val count = numberCount(l)
        val ops = ops(l)
        repeat(500) {
            val numbers = MutableList(count) { rng.range(1, 9) }
            if (l >= 20) numbers[0] = listOf(10, 25, 50)[rng.nextInt(3)]
            if (l >= 34) numbers[1] = listOf(25, 50, 75, 100)[rng.nextInt(4)]
            numbers.shuffle(rng)
            val use = rng.range(maxOf(2, count - 1), count)
            val exprs = numbers.shuffled(rng).take(use).map { Expr(it, it.toString(), true) }.toMutableList()
            var ok = true
            while (exprs.size > 1 && ok) {
                val i = rng.nextInt(exprs.size)
                var j = rng.nextInt(exprs.size - 1)
                if (j >= i) j++
                val a = exprs[i]
                val b = exprs[j]
                val candidates = ops.shuffled(rng)
                var combined: Expr? = null
                for (op in candidates) {
                    val v = apply(a.value, op, b.value) ?: continue
                    val at = if (a.atom) a.text else "(${a.text})"
                    val bt = if (b.atom) b.text else "(${b.text})"
                    combined = Expr(v, "$at ${symbol(op)} $bt", false)
                    break
                }
                if (combined == null) {
                    ok = false
                } else {
                    exprs.removeAt(maxOf(i, j))
                    exprs.removeAt(minOf(i, j))
                    exprs.add(combined)
                }
            }
            if (!ok) return@repeat
            val target = exprs[0].value
            val minTarget = if (l <= 8) 6 else 12
            if (target < minTarget || target > 999 || target in numbers) return@repeat
            return TargetTask(numbers, target, exprs[0].text, ops)
        }
        // Rückfall (praktisch nie erreicht)
        return TargetTask(listOf(3, 4, 5), 17, "3 × 4 + 5", setOf('+', '-', '*'))
    }
}

// ---------------------------------------------------------------- Duell

data class DuelTask(val question: String, val inkColor: Int?, val options: List<String>, val correct: Int)

object DuelGen {
    fun math(level: Int, rng: Random): DuelTask {
        val t = ArithmeticGen.generate(level, rng)
        val offsets = listOf(-10, -2, -1, 1, 2, 10, -3, 3, 5, -5).shuffled(rng)
        val wrong = offsets.map { t.answer + it }.filter { it >= 0 && it != t.answer }.distinct().take(3)
        val options = (wrong + t.answer).shuffled(rng)
        return DuelTask("${t.text} = ?", null, options.map { it.toString() }, options.indexOf(t.answer))
    }

    /** Farbwort-Duell: Schriftfarbe antippen. Farben-Index wie StroopGen.NAMES. */
    fun stroop(rng: Random): DuelTask {
        val word = rng.nextInt(4)
        val ink = if (rng.nextDouble() < 0.8) (word + 1 + rng.nextInt(3)) % 4 else word
        val order = (0 until 4).shuffled(rng)
        return DuelTask(StroopGen.NAMES[word], ink, order.map { StroopGen.NAMES[it] }, order.indexOf(ink))
    }
}
