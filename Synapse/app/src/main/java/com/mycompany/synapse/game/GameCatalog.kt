package com.mycompany.synapse.game

import com.mycompany.synapse.game.logic.AnagramGen
import com.mycompany.synapse.game.logic.ArithmeticGen
import com.mycompany.synapse.game.logic.BalanceGen
import com.mycompany.synapse.game.logic.ChainGen
import com.mycompany.synapse.game.logic.CorsiGen
import com.mycompany.synapse.game.logic.EstimateGen
import com.mycompany.synapse.game.logic.MatrixGen
import com.mycompany.synapse.game.logic.NBackGen
import com.mycompany.synapse.game.logic.NamesGen
import com.mycompany.synapse.game.logic.OddOneGen
import com.mycompany.synapse.game.logic.PathGen
import com.mycompany.synapse.game.logic.RotationGen
import com.mycompany.synapse.game.logic.SchulteGen
import com.mycompany.synapse.game.logic.SequenceGen
import com.mycompany.synapse.game.logic.ShoppingGen
import com.mycompany.synapse.game.logic.SpanGen
import com.mycompany.synapse.game.logic.StroopGen
import com.mycompany.synapse.game.logic.SudokuGen
import com.mycompany.synapse.game.logic.SyllogismGen
import com.mycompany.synapse.game.logic.TargetGen
import com.mycompany.synapse.game.logic.WeekdayGen

/** Metadaten eines Spiels – unabhängig von der Oberfläche, damit Statistik & Tests ohne UI laufen. */
open class GameInfo(
    val id: String,
    val domain: Domain,
    val title: String,
    val tagline: String,
    val howTo: String,
    val trials: Int,
    val supportsSprint: Boolean = false,
    val speedMatters: Boolean = true,
    private val target: (Int) -> Long = { 3000L },
    private val labelFn: (Int) -> String = { "" },
    /** Schwierigkeit passt sich schon innerhalb der Runde an (nur bei vielen kurzen Aufgaben sinnvoll). */
    val microAdapt: Boolean = trials >= 8,
) {
    fun targetMs(level: Int): Long = target(level.coerceIn(1, Leveling.MAX_LEVEL))
    fun levelLabel(level: Int): String = labelFn(level.coerceIn(1, Leveling.MAX_LEVEL))

    open fun levelDelta(accuracy: Double, avgMs: Long, level: Int, mode: GameMode): Int {
        val fast = speedMatters && mode != GameMode.ZEN && avgMs > 0 && avgMs < targetMs(level) * 0.75
        return Leveling.defaultDelta(accuracy, fast)
    }
}

object GameCatalog {
    val all: List<GameInfo> = listOf(
        GameInfo(
            "blitz", Domain.RECHNEN, "Blitzrechnen", "Kopfrechnen gegen die Uhr",
            "Löse die Aufgaben so schnell du kannst. Richtige Ergebnisse werden sofort erkannt – " +
                "bei falscher Eingabe mit ✓ bestätigen.",
            trials = 15, supportsSprint = true,
            target = { 2500L + it * 100L }, labelFn = ArithmeticGen::label,
        ),
        GameInfo(
            "kette", Domain.RECHNEN, "Kettenrechnen", "Im Kopf mitrechnen",
            "Du siehst eine Startzahl, danach erscheinen nacheinander Rechenschritte. " +
                "Rechne im Kopf mit und gib am Ende das Ergebnis ein.",
            trials = 8, target = { 5000L }, labelFn = { "${ChainGen.stepCount(it)} Schritte" },
        ),
        GameInfo(
            "zielzahl", Domain.RECHNEN, "Zielzahl", "Zahlen kombinieren",
            "Erreiche die Zielzahl: Tippe eine Zahl, eine Rechenart und eine zweite Zahl – " +
                "die beiden verschmelzen zum Ergebnis. Nicht alle Zahlen müssen genutzt werden.",
            trials = 5, target = { 15000L + it * 600L },
            labelFn = { "${TargetGen.numberCount(it)} Zahlen · ${TargetGen.ops(it).joinToString(" ") { c -> TargetGen.symbol(c) }}" },
        ),
        GameInfo(
            "wochentag", Domain.RECHNEN, "Wochentag", "Kalenderrechnen im Kopf",
            "Finde den Wochentag. Erst geht es um „in 9 Tagen“, später um ganze Daten – mit Anker-Hinweis " +
                "und schließlich ganz ohne. Der Lernen-Bereich erklärt die Doomsday-Methode.",
            trials = 10, supportsSprint = true, target = { 6000L + it * 250L }, labelFn = WeekdayGen::mode,
        ),
        GameInfo(
            "schaetzen", Domain.RECHNEN, "Schätzen", "Überschlagen statt ausrechnen",
            "Nicht genau rechnen – schnell überschlagen! Tippe den Wert, der dem Ergebnis am nächsten liegt.",
            trials = 12, supportsSprint = true, target = { 4500L }, labelFn = EstimateGen::label,
        ),
        GameInfo(
            "folgen", Domain.LOGIK, "Zahlenfolgen", "Das System dahinter finden",
            "Welche Zahl kommt als Nächstes? Finde die Regel hinter der Folge.",
            trials = 10, supportsSprint = true, target = { 6000L + it * 120L }, labelFn = SequenceGen::label,
        ),
        GameInfo(
            "sudoku", Domain.LOGIK, "Mini-Sudoku", "Zahlen logisch platzieren",
            "Jede Zahl darf in jeder Zeile, Spalte und jedem Kasten nur einmal vorkommen. Tippe ein Feld, dann die Zahl.",
            trials = 3, target = { if (SudokuGen.size(it) == 4) 50_000L else 170_000L - it * 1500L }, labelFn = SudokuGen::label,
        ),
        GameInfo(
            "matrix", Domain.LOGIK, "Muster-Matrix", "Regeln in Formen erkennen",
            "In jeder Zeile und Spalte gelten Regeln für Form, Farbe, Anzahl und Füllung. " +
                "Welches Feld ergänzt die Matrix unten rechts?",
            trials = 8, supportsSprint = true, target = { 9000L + it * 150L },
            labelFn = { val n = MatrixGen.varyingCount(it); "$n ${if (n == 1) "Regel" else "Regeln"} · ${MatrixGen.optionCount(it)} Optionen" },
        ),
        GameInfo(
            "schluss", Domain.LOGIK, "Schlussfolgern", "Folgt das zwingend?",
            "Lies die Aussagen und entscheide, ob die Schlussfolgerung zwingend daraus folgt. " +
                "Wische nach rechts für Ja, nach links für Nein.",
            trials = 12, supportsSprint = true, target = { 7000L + it * 120L }, labelFn = SyllogismGen::label,
        ),
        GameInfo(
            "waage", Domain.LOGIK, "Waage", "Gewichte ableiten",
            "Die Waagen zeigen, was schwerer ist. Finde daraus das schwerste – oder leichteste – Objekt.",
            trials = 10, supportsSprint = true,
            target = { 3000L + BalanceGen.objectCount(it) * 1500L }, labelFn = { "${BalanceGen.objectCount(it)} Objekte" },
        ),
        object : GameInfo(
            "nback", Domain.GEDAECHTNIS, "N-Back", "Der Klassiker fürs Arbeitsgedächtnis",
            "Ein Feld leuchtet nach dem anderen auf. Tippe auf »Position«, wenn das Feld dasselbe ist wie N Schritte " +
                "vorher. Bei Dual zusätzlich »Buchstabe«, wenn der Buchstabe übereinstimmt.",
            trials = 2, speedMatters = false, labelFn = NBackGen::label,
        ) {
            override fun levelDelta(accuracy: Double, avgMs: Long, level: Int, mode: GameMode) = when {
                accuracy >= 0.8 -> 1
                accuracy < 0.5 -> -1
                else -> 0
            }
        },
        GameInfo(
            "namen", Domain.GEDAECHTNIS, "Namen merken", "Gesichter & Namen verbinden",
            "Du lernst einige Personen kennen. Danach siehst du die Gesichter wieder – wie hieß wer? " +
                "Tipp: Verknüpfe den Namen mit einem auffälligen Merkmal.",
            trials = 2, speedMatters = false, labelFn = NamesGen::label,
        ),
        GameInfo(
            "einkauf", Domain.GEDAECHTNIS, "Einkaufsliste", "Dinge im Kopf behalten",
            "Präge dir die Einkaufsliste ein. Danach tippst du alle Dinge an, die darauf standen – " +
                "ab höheren Stufen in der richtigen Reihenfolge.",
            trials = 3, speedMatters = false, labelFn = ShoppingGen::label,
        ),
        GameInfo(
            "corsi", Domain.GEDAECHTNIS, "Corsi-Pfad", "Räumliche Folgen merken",
            "Felder leuchten nacheinander auf. Tippe sie danach in derselben Reihenfolge an – ab höheren Stufen rückwärts.",
            trials = 8, speedMatters = false, labelFn = CorsiGen::label,
        ),
        GameInfo(
            "spanne", Domain.GEDAECHTNIS, "Zahlen merken", "Ziffernfolgen behalten",
            "Ziffern erscheinen nacheinander. Gib sie danach in derselben Reihenfolge ein – ab höheren Stufen rückwärts.",
            trials = 8, speedMatters = false, labelFn = SpanGen::label,
        ),
        GameInfo(
            "stroop", Domain.FOKUS, "Farbwort", "Lass dich nicht täuschen",
            "Tippe die Farbe, in der das Wort geschrieben ist – nicht das, was dort steht. " +
                "Später wechselt die Regel: Achte auf den Hinweis FARBE oder WORT.",
            trials = 20, supportsSprint = true, target = { 1800L }, labelFn = StroopGen::label,
        ),
        object : GameInfo(
            "schulte", Domain.FOKUS, "Schulte-Tabelle", "Blick und Fokus schärfen",
            "Tippe die Zahlen der Reihe nach an – von 1 bis zur höchsten. Versuche, den Blick in der Mitte zu halten.",
            trials = 3, target = SchulteGen::targetBoardMs, labelFn = SchulteGen::label,
        ) {
            override fun levelDelta(accuracy: Double, avgMs: Long, level: Int, mode: GameMode): Int {
                val target = targetMs(level)
                return when {
                    accuracy >= 0.9 && avgMs < target * 0.7 -> 2
                    accuracy >= 0.9 && avgMs < target -> 1
                    accuracy < 0.7 || avgMs > target * 1.6 -> -1
                    else -> 0
                }
            }
        },
        GameInfo(
            "rotation", Domain.RAUM, "Würfel-Rotation", "Gleich oder gespiegelt?",
            "Die rechte Figur ist gedreht. Ist sie dieselbe Figur wie links – oder ein Spiegelbild?",
            trials = 12, supportsSprint = true, target = { 6000L + it * 100L }, labelFn = RotationGen::label,
        ),
        GameInfo(
            "weg", Domain.RAUM, "Weg im Kopf", "Wege vorstellen ohne Karte",
            "Merke dir den Startpunkt. Dann kommen Wegbeschreibungen – gehe sie im Kopf mit und tippe das Ziel an. " +
                "Ab höheren Stufen zählt die Blickrichtung: „rechts“ heißt drehen.",
            trials = 8, speedMatters = false, labelFn = PathGen::label,
        ),
        GameInfo(
            "anagramm", Domain.SPRACHE, "Wortsalat", "Buchstaben ordnen",
            "Bilde aus den Buchstaben ein Wort. Tippe die Buchstaben der Reihe nach an. Zwei Versuche pro Wort.",
            trials = 8, supportsSprint = true, target = { 8000L + AnagramGen.wordLength(it) * 1000L }, labelFn = AnagramGen::label,
        ),
        GameInfo(
            "oberbegriff", Domain.SPRACHE, "Was passt nicht?", "Kategorien erkennen",
            "Alle Wörter bis auf eines gehören zu einer Gruppe. Tippe auf das Wort, das nicht dazugehört.",
            trials = 12, supportsSprint = true, target = { 4000L + OddOneGen.wordCount(it) * 400L }, labelFn = OddOneGen::label,
        ),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): GameInfo = byId[id] ?: all.first()
    fun find(id: String): GameInfo? = byId[id]
    fun ofDomain(d: Domain) = all.filter { it.domain == d }
}
