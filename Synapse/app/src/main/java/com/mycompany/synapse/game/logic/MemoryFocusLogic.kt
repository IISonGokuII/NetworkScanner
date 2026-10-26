package com.mycompany.synapse.game.logic

import kotlin.random.Random

// ---------------------------------------------------------------- N-Back

data class NBackConfig(val n: Int, val dual: Boolean, val intervalMs: Long, val length: Int)
data class NBackStimulus(val pos: Int, val letter: Char)

object NBackGen {
    const val LETTERS = "CHKLQRST"

    fun config(level: Int): NBackConfig {
        val l = level.coerceIn(1, 50)
        val (n, dual, bandStart) = when {
            l <= 5 -> Triple(1, false, 1)
            l <= 12 -> Triple(2, false, 6)
            l <= 19 -> Triple(3, false, 13)
            l <= 27 -> Triple(2, true, 20)
            l <= 43 -> Triple(3, true, 28)
            else -> Triple(4, true, 44)
        }
        val interval = (2700L - (l - bandStart) * 110L).coerceAtLeast(1700L)
        return NBackConfig(n, dual, interval, 20 + n)
    }

    fun sequence(cfg: NBackConfig, rng: Random): List<NBackStimulus> {
        repeat(100) {
            val seq = mutableListOf<NBackStimulus>()
            for (i in 0 until cfg.length) {
                val back = if (i >= cfg.n) seq[i - cfg.n] else null
                val pos = if (back != null && rng.nextDouble() < 0.3) back.pos
                else randomExcept(rng, 9, back?.pos)
                val letter = if (!cfg.dual) 'X' else if (back != null && rng.nextDouble() < 0.3) back.letter
                else LETTERS[randomExcept(rng, LETTERS.length, back?.letter?.let { LETTERS.indexOf(it) })]
                seq += NBackStimulus(pos, letter)
            }
            val posMatches = (cfg.n until seq.size).count { seq[it].pos == seq[it - cfg.n].pos }
            val letterMatches = (cfg.n until seq.size).count { seq[it].letter == seq[it - cfg.n].letter }
            if (posMatches >= 4 && (!cfg.dual || letterMatches >= 4)) return seq
        }
        return List(cfg.length) { NBackStimulus(it % 9, 'C') }
    }

    private fun randomExcept(rng: Random, size: Int, except: Int?): Int {
        if (except == null) return rng.nextInt(size)
        val v = rng.nextInt(size - 1)
        return if (v >= except) v + 1 else v
    }

    fun label(level: Int): String {
        val c = config(level)
        return "${c.n}-Back · " + if (c.dual) "Position + Buchstabe" else "Position"
    }
}

// ---------------------------------------------------------------- Corsi-Pfad

data class CorsiTask(val gridSize: Int, val sequence: List<Int>, val backwards: Boolean, val stepMs: Long)

object CorsiGen {
    fun length(level: Int): Int {
        val l = level.coerceIn(1, 50)
        return if (l <= 24) 3 + (l - 1) / 4 else 3 + (l - 25) / 4
    }

    fun gridSize(level: Int) = when {
        level < 10 -> 3
        level < 35 -> 4
        else -> 5
    }

    fun generate(level: Int, rng: Random): CorsiTask {
        val l = level.coerceIn(1, 50)
        val size = gridSize(l)
        val seq = (0 until size * size).shuffled(rng).take(length(l))
        return CorsiTask(size, seq, l >= 25, (800L - l * 7L).coerceAtLeast(450L))
    }

    fun label(level: Int) = "${length(level)} Felder · " + if (level >= 25) "rückwärts" else "vorwärts"
}

// ---------------------------------------------------------------- Zahlen merken

data class SpanTask(val digits: String, val backwards: Boolean, val stepMs: Long) {
    val expected: String get() = if (backwards) digits.reversed() else digits
}

object SpanGen {
    fun length(level: Int): Int {
        val l = level.coerceIn(1, 50)
        return if (l <= 25) 3 + (l - 1) / 3 else 3 + (l - 26) / 3
    }

    fun generate(level: Int, rng: Random): SpanTask {
        val l = level.coerceIn(1, 50)
        val sb = StringBuilder()
        var last = -1
        repeat(length(l)) {
            var d: Int
            do { d = rng.nextInt(10) } while (d == last)
            sb.append(d)
            last = d
        }
        return SpanTask(sb.toString(), l > 25, (950L - l * 8L).coerceAtLeast(550L))
    }

    fun label(level: Int) = "${length(level)} Ziffern · " + if (level > 25) "rückwärts" else "vorwärts"
}

// ---------------------------------------------------------------- Stroop

data class StroopTask(val word: Int, val ink: Int, val askWord: Boolean, val options: List<Int>, val timeLimitMs: Long?) {
    val answer: Int get() = if (askWord) word else ink
}

object StroopGen {
    val NAMES = listOf("ROT", "BLAU", "GRÜN", "GELB", "LILA")

    fun colorCount(level: Int) = when {
        level < 6 -> 3
        level < 26 -> 4
        else -> 5
    }

    /** [available]: Anzahl Farben der aktuellen Palette (Farbschwäche-Modus hat weniger). */
    fun generate(level: Int, rng: Random, available: Int = NAMES.size): StroopTask {
        val l = level.coerceIn(1, 50)
        val n = minOf(colorCount(l), available)
        val incongruent = if (l < 6) 0.5 else 0.75
        val word = rng.nextInt(n)
        val ink = if (rng.nextDouble() < incongruent) (word + 1 + rng.nextInt(n - 1)) % n else word
        val askWord = l >= 26 && rng.nextDouble() < 0.3
        val options = if (l >= 36) (0 until n).shuffled(rng) else (0 until n).toList()
        val limit = if (l >= 16) (3200L - (l - 16) * 55L).coerceAtLeast(1300L) else null
        return StroopTask(word, ink, askWord, options, limit)
    }

    fun label(level: Int): String {
        val parts = mutableListOf("${colorCount(level)} Farben")
        if (level >= 16) parts += "Zeitlimit"
        if (level >= 26) parts += "Regelwechsel"
        if (level >= 36) parts += "wechselnde Tasten"
        return parts.joinToString(" · ")
    }
}

// ---------------------------------------------------------------- Schulte-Tabelle

data class SchulteTask(val size: Int, val numbers: List<Int>, val tinted: Boolean, val tints: List<Int>, val rotations: List<Float>)

object SchulteGen {
    fun size(level: Int) = when {
        level <= 12 -> 4
        level <= 40 -> 5
        else -> 6
    }

    fun targetBoardMs(level: Int): Long {
        val s = size(level)
        val perCell = (1400L - level * 14L).coerceAtLeast(650L)
        return s * s * perCell
    }

    fun generate(level: Int, rng: Random): SchulteTask {
        val l = level.coerceIn(1, 50)
        val s = size(l)
        val numbers = (1..s * s).shuffled(rng)
        val tinted = l >= 29
        return SchulteTask(
            s, numbers, tinted,
            List(s * s) { rng.nextInt(5) },
            List(s * s) { if (tinted) rng.nextInt(-35, 36).toFloat() else 0f },
        )
    }

    fun label(level: Int) = "${size(level)}×${size(level)}" + if (level >= 29) " · mit Ablenkung" else ""
}
