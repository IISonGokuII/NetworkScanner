package com.mycompany.synapse.game

/** Ein Teil eines Tests: ein Bereich, vertreten durch ein Spiel. */
data class TestPart(val domain: Domain, val gameId: String, val trials: Int, val startLevel: Int, val adaptive: Boolean)

object TestPlans {
    /** Einstufung: Schwierigkeit passt sich Aufgabe für Aufgabe an (Treppenverfahren). */
    val placement = listOf(
        TestPart(Domain.RECHNEN, "blitz", 8, 12, adaptive = true),
        TestPart(Domain.LOGIK, "folgen", 6, 10, adaptive = true),
        TestPart(Domain.GEDAECHTNIS, "spanne", 5, 8, adaptive = true),
        TestPart(Domain.FOKUS, "stroop", 10, 12, adaptive = true),
        TestPart(Domain.RAUM, "rotation", 6, 10, adaptive = true),
        TestPart(Domain.SPRACHE, "oberbegriff", 6, 10, adaptive = true),
    )

    /** Leistungstest: immer dieselbe Schwierigkeit – so sind die Ergebnisse über Monate vergleichbar. */
    val benchmark = listOf(
        TestPart(Domain.RECHNEN, "blitz", 10, 20, adaptive = false),
        TestPart(Domain.LOGIK, "folgen", 6, 18, adaptive = false),
        TestPart(Domain.GEDAECHTNIS, "spanne", 5, 13, adaptive = false),
        TestPart(Domain.FOKUS, "stroop", 12, 20, adaptive = false),
        TestPart(Domain.RAUM, "rotation", 8, 20, adaptive = false),
        TestPart(Domain.SPRACHE, "oberbegriff", 8, 20, adaptive = false),
    )
}
