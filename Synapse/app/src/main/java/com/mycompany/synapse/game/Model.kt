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
) {
    val fraction: Double get() = if (total <= 0) 0.0 else correct.toDouble() / total
    val success: Boolean get() = fraction >= 0.999 || (total > 1 && fraction >= 0.8)

    companion object {
        fun right(timeMs: Long) = TrialResult(1, 1, timeMs)
        fun wrong(timeMs: Long, solution: String? = null) = TrialResult(0, 1, timeMs, solution)
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

    /** Leistungswert einer Session: Level plus Korrektur nach Trefferquote. */
    fun skill(level: Int, accuracy: Double): Double =
        (level + ((accuracy - 0.75) * 4.0).coerceIn(-2.0, 2.0)).coerceAtLeast(0.0)

    /** Index-Skala 0…1000 für die Anzeige. */
    fun index(skill: Double): Int = (skill * 20.0).toInt().coerceIn(0, 1000)
}
