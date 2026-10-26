package com.mycompany.synapse.game

/** Die sechs Trainingsbereiche. Reihenfolge = Anordnung im Neuronen-Netz. */
enum class Domain(val title: String, val subtitle: String) {
    RECHNEN("Rechnen", "Kopfrechnen & Zahlengefühl"),
    LOGIK("Logik", "Muster erkennen & Schlussfolgern"),
    GEDAECHTNIS("Gedächtnis", "Arbeitsgedächtnis & Merkfähigkeit"),
    FOKUS("Fokus", "Konzentration & Reaktionstempo"),
    RAUM("Raumdenken", "Räumliche Vorstellungskraft"),
    SPRACHE("Sprache", "Wortschatz & Wortlogik"),
}

enum class GameMode(val title: String, val description: String) {
    STANDARD("Training", "Feste Rundenzahl – Tempo zählt mit"),
    SPRINT("Sprint", "60 Sekunden – so viele wie möglich"),
    ZEN("Zen", "Ohne Zeitdruck, ganz in deinem Tempo"),
    TEST("Einstufung", "Einstufungstest"),
    ;

    companion object {
        /** Modi, die man selbst wählen kann. */
        val playable = listOf(STANDARD, SPRINT, ZEN)
    }
}

/**
 * Ergebnis einer einzelnen Aufgabe. Einfache Aufgaben liefern 1/1 oder 0/1,
 * zusammengesetzte (N-Back-Block, Schulte-Tafel) liefern Teilpunkte.
 */
data class TrialResult(
    val correct: Int,
    val total: Int,
    val timeMs: Long,
    val solution: String? = null,
    /** Aufgabentyp für die Schwächen-Analyse (z. B. „Division“, „Fibonacci“). */
    val tag: String? = null,
) {
    val fraction: Double get() = if (total <= 0) 0.0 else correct.toDouble() / total
    val success: Boolean get() = fraction >= 0.999 || (total > 1 && fraction >= 0.8)

    companion object {
        fun right(timeMs: Long, tag: String? = null) = TrialResult(1, 1, timeMs, tag = tag)
        fun wrong(timeMs: Long, solution: String? = null, tag: String? = null) = TrialResult(0, 1, timeMs, solution, tag)
    }
}

/** Gemeinsame Stufen-Logik: 50 unsichtbare Level, Ziel ist eine Trefferquote von 70–85 %. */
object Leveling {
    const val MAX_LEVEL = 50

    fun defaultDelta(accuracy: Double, fast: Boolean): Int = when {
        accuracy >= 0.95 && fast -> 2
        accuracy >= 0.85 -> 1
        accuracy < 0.6 -> -1
        else -> 0
    }

    fun apply(level: Int, delta: Int): Int = (level + delta).coerceIn(1, MAX_LEVEL)

    /** In den ersten Runden eines Spiels geht es schneller nach oben, damit niemand lange unterfordert ist. */
    fun calibrated(delta: Int, previousSessions: Int): Int = if (delta > 0 && previousSessions < 3) delta * 2 else delta

    /** Leistungswert einer Session: gespieltes Level plus Korrektur nach Trefferquote. */
    fun skill(level: Double, accuracy: Double): Double =
        (level + ((accuracy - 0.75) * 4.0).coerceIn(-2.0, 2.0)).coerceAtLeast(0.0)

    /** Index-Skala 0…1000 für die Anzeige. */
    fun index(skill: Double): Int = (skill * 20.0).toInt().coerceIn(0, 1000)
}

/**
 * Treppenverfahren für den Einstufungstest: richtig → schwerer, falsch → leichter,
 * die Schrittweite halbiert sich bei jedem Richtungswechsel.
 */
class Staircase(start: Int, private var step: Int = 6) {
    var level: Int = start.coerceIn(1, Leveling.MAX_LEVEL)
        private set
    private var lastUp: Boolean? = null
    private val history = mutableListOf<Int>()

    fun record(success: Boolean) {
        history += level
        if (lastUp != null && lastUp != success) step = maxOf(1, step / 2)
        lastUp = success
        level = (level + if (success) step else -step).coerceIn(1, 40)
    }

    /** Ergebnis: Mittel der letzten drei gespielten Stufen und der nächsten. */
    fun result(): Int {
        val tail = (history.takeLast(3) + level)
        return tail.average().let { kotlin.math.round(it).toInt() }.coerceIn(1, 40)
    }
}
