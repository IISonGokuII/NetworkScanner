package com.mycompany.synapse.game.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Eine Übungsaufgabe. [answer] als Text (Zahl oder Auswahl), [choices] für Auswahl-Aufgaben. */
data class DrillTask(
    val prompt: String,
    val answer: String,
    val explanation: String,
    val choices: List<String>? = null,
    /** Aufgabe wird nur so lange gezeigt (Merktechniken). */
    val flashMs: Long? = null,
)

class Lesson(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val example: String,
    val targetMs: Long,
    val drill: ((Random) -> DrillTask)?,
    /** Lektionen ohne eigene Übung verweisen auf ein Spiel. */
    val gameId: String? = null,
)

object Tricks {
    const val DRILL_SIZE = 10
    private val dateFmt = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

    private fun digits(n: Int) = n.toString().map { it - '0' }

    val lessons: List<Lesson> = listOf(
        Lesson(
            "mal11", "Rechentricks", "× 11 in Sekunden", "Zweistellige Zahlen mal 11: die Ziffern auseinanderziehen.",
            listOf(
                "Schreibe die erste und die letzte Ziffer mit Abstand hin.",
                "In die Mitte kommt die Summe der beiden Ziffern.",
                "Ist die Summe zweistellig, wandert die 1 als Übertrag nach links.",
            ),
            "43 × 11 → 4 | 4+3 | 3 = 473   ·   58 × 11 → 5 | 13 | 8 → 638",
            4000L,
            { r ->
                val a = r.range(12, 99)
                val (x, y) = digits(a)
                val sum = x + y
                val expl = if (sum < 10) "$x | $x+$y | $y → ${a * 11}" else "$x | $sum | $y → Übertrag: ${x + 1} | ${sum - 10} | $y = ${a * 11}"
                DrillTask("$a × 11", "${a * 11}", expl)
            },
        ),
        Lesson(
            "mal5", "Rechentricks", "× 5 = × 10 ÷ 2", "Mal 5 ist dasselbe wie mal 10 und dann halbieren.",
            listOf("Hänge eine 0 an (× 10).", "Halbiere das Ergebnis."),
            "68 × 5 → 680 ÷ 2 = 340",
            3500L,
            { r ->
                val a = r.range(14, 198)
                DrillTask("$a × 5", "${a * 5}", "$a × 10 = ${a * 10}, halbiert = ${a * 5}")
            },
        ),
        Lesson(
            "mal9", "Rechentricks", "× 9 = × 10 minus einmal", "Rechne mal 10 und ziehe die Zahl einmal ab.",
            listOf("Hänge eine 0 an (× 10).", "Ziehe die ursprüngliche Zahl ab."),
            "47 × 9 → 470 − 47 = 423",
            4500L,
            { r ->
                val a = r.range(12, 99)
                DrillTask("$a × 9", "${a * 9}", "${a * 10} $MINUS $a = ${a * 9}")
            },
        ),
        Lesson(
            "mal25", "Rechentricks", "× 25 = × 100 ÷ 4", "Mal 25 ist ein Viertel von mal 100.",
            listOf("Hänge zwei Nullen an (× 100).", "Teile durch 4 – also zweimal halbieren."),
            "36 × 25 → 3600 ÷ 4 = 900",
            5000L,
            { r ->
                val a = r.range(8, 96)
                DrillTask("$a × 25", "${a * 25}", "$a × 100 = ${a * 100}, ÷ 4 = ${a * 25}")
            },
        ),
        Lesson(
            "quadrat5", "Rechentricks", "Quadrate mit 5 am Ende", "Zahlen, die auf 5 enden, im Kopf quadrieren.",
            listOf("Nimm die Zehnerziffer t.", "Rechne t × (t + 1).", "Hänge 25 an."),
            "35² → 3 × 4 = 12, dann 25 → 1225",
            4000L,
            { r ->
                val t = r.range(1, 9)
                val a = t * 10 + 5
                DrillTask("$a²", "${a * a}", "$t × ${t + 1} = ${t * (t + 1)}, dann 25 → ${a * a}")
            },
        ),
        Lesson(
            "quadrat50", "Rechentricks", "Quadrate nahe 50", "Für Zahlen zwischen 40 und 60 gibt es eine Abkürzung.",
            listOf("Abstand d zu 50 bestimmen (mit Vorzeichen).", "2500 + 100 · d rechnen.", "d² dazuzählen."),
            "47² → d = −3: 2500 − 300 + 9 = 2209",
            6000L,
            { r ->
                var a: Int
                do { a = r.range(41, 59) } while (a == 50)
                val d = a - 50
                DrillTask("$a²", "${a * a}", "d = $d: 2500 ${if (d < 0) MINUS else "+"} ${kotlin.math.abs(100 * d)} + ${d * d} = ${a * a}")
            },
        ),
        Lesson(
            "nahe100", "Rechentricks", "Zahlen nahe 100 multiplizieren", "Zwei Zahlen knapp unter 100 – fast ohne Rechnen.",
            listOf(
                "Bestimme die Abstände zu 100 (z. B. 97 → 3, 96 → 4).",
                "Vorne: eine Zahl minus den Abstand der anderen (97 − 4 = 93).",
                "Hinten: Abstände multiplizieren, zweistellig schreiben (3 × 4 = 12).",
            ),
            "97 × 96 → 93 | 12 = 9312",
            6000L,
            { r ->
                val a = r.range(88, 99)
                val b = r.range(88, 99)
                val da = 100 - a
                val db = 100 - b
                DrillTask("$a × $b", "${a * b}", "${a - db} | ${"%02d".format(da * db)} → ${a * b}")
            },
        ),
        Lesson(
            "halbdoppel", "Rechentricks", "Halbieren & Verdoppeln", "Eine Zahl halbieren, die andere verdoppeln – das Ergebnis bleibt gleich.",
            listOf("Suche den geraden Faktor und halbiere ihn.", "Verdopple den anderen Faktor (z. B. 35 → 70).", "Wiederhole, bis es einfach wird."),
            "16 × 35 → 8 × 70 = 560",
            5000L,
            { r ->
                val a = 2 * r.range(6, 24)
                val b = listOf(15, 25, 35, 45)[r.nextInt(4)]
                DrillTask("$a × $b", "${a * b}", "${a / 2} × ${b * 2} = ${a * b}")
            },
        ),
        Lesson(
            "prozenttausch", "Rechentricks", "Prozente tauschen", "x % von y ist dasselbe wie y % von x.",
            listOf("Prüfe, ob die vertauschte Rechnung leichter ist.", "8 % von 50 = 50 % von 8 = 4."),
            "16 % von 25 → 25 % von 16 = 4",
            5000L,
            { r ->
                val y = listOf(10, 20, 25, 50)[r.nextInt(4)]
                var x: Int
                do { x = r.range(3, 96) } while (x * y % 100 != 0)
                DrillTask("$x % von $y", "${x * y / 100}", "$x % von $y = $y % von $x = ${x * y / 100}")
            },
        ),
        Lesson(
            "ergaenzen", "Rechentricks", "Von 1000 abziehen", "1000 minus eine Zahl: jede Ziffer zu 9 ergänzen, die letzte zu 10.",
            listOf("Erste Ziffern: Was fehlt bis 9?", "Letzte Ziffer: Was fehlt bis 10?"),
            "1000 − 367 → 6 | 3 | 3 = 633",
            4500L,
            { r ->
                var n: Int
                do { n = r.range(101, 989) } while (n % 10 == 0)
                DrillTask("1000 $MINUS $n", "${1000 - n}", digits(n).let { d -> "${9 - d[0]} | ${9 - d[1]} | ${10 - d[2]} → ${1000 - n}" })
            },
        ),
        Lesson(
            "runden", "Rechentricks", "Runden & ausgleichen", "Krumme Zahlen zur nächsten runden Zahl machen und danach korrigieren.",
            listOf("Runde den krummen Summanden auf (398 → 400).", "Rechne mit der runden Zahl.", "Ziehe den Rundungsbetrag wieder ab."),
            "398 + 247 → 400 + 247 − 2 = 645",
            5000L,
            { r ->
                val h = r.range(2, 9) * 100
                val d = r.range(1, 4)
                val b = r.range(105, 899)
                DrillTask("${h - d} + $b", "${h - d + b}", "$h + $b $MINUS $d = ${h - d + b}")
            },
        ),
        Lesson(
            "teilbar3", "Rechentricks", "Teilbar durch 3?", "Eine Zahl ist durch 3 teilbar, wenn ihre Quersumme es ist.",
            listOf("Alle Ziffern addieren (Quersumme).", "Ist die Quersumme durch 3 teilbar, ist es die Zahl auch."),
            "4572 → 4+5+7+2 = 18 → teilbar",
            4000L,
            { r ->
                val n = r.range(1000, 999_999)
                val qs = digits(n).sum()
                val yes = n % 3 == 0
                DrillTask("Ist $n durch 3 teilbar?", if (yes) "Ja" else "Nein", "Quersumme $qs → ${if (yes) "teilbar" else "nicht teilbar"}", listOf("Ja", "Nein"))
            },
        ),
        Lesson(
            "teilbar4", "Rechentricks", "Teilbar durch 4?", "Nur die letzten beiden Ziffern zählen.",
            listOf("Schau nur auf die letzten zwei Ziffern.", "Sind sie durch 4 teilbar, ist es die ganze Zahl auch."),
            "73 516 → 16 ist durch 4 teilbar → ja",
            3500L,
            { r ->
                val n = r.range(1000, 999_999)
                val yes = n % 4 == 0
                DrillTask("Ist $n durch 4 teilbar?", if (yes) "Ja" else "Nein", "Letzte zwei Ziffern ${n % 100} → ${if (yes) "teilbar" else "nicht teilbar"}", listOf("Ja", "Nein"))
            },
        ),
        Lesson(
            "doomsday", "Kalender", "Wochentag jedes Datums (Doomsday)",
            "Mit wenigen Merkdaten findest du den Wochentag jedes Datums im Kopf.",
            listOf(
                "Jedes Jahr fallen 4.4., 6.6., 8.8., 10.10., 12.12. auf denselben Wochentag – den „Doomsday“.",
                "Dazu: 9.5. & 5.9. („9 to 5“), 11.7. & 7.11., der letzte Februartag, und der 3.1. (Schaltjahr: 4.1.).",
                "Doomsday 2000 war ein Dienstag. Pro Jahr wandert er 1 Tag weiter, in Schaltjahren 2 Tage.",
                "Vom nächsten Merkdatum aus zählst du die Tage bis zum gesuchten Datum (in Wochen springen!).",
            ),
            "14.3.2026 → Doomsday 2026 = Samstag; 14.3. ist der letzte Februartag (28.2.) + 14 Tage → Samstag",
            25_000L,
            { r ->
                val year = r.range(2000, 2040)
                val date = LocalDate.of(year, r.range(1, 12), 1).let { it.withDayOfMonth(r.range(1, it.lengthOfMonth())) }
                DrillTask(
                    "Wochentag am ${date.format(dateFmt)}?",
                    weekdayName(date.dayOfWeek),
                    "Doomsday $year: ${weekdayName(WeekdayGen.doomsday(year))} → ${weekdayName(date.dayOfWeek)}",
                    WEEKDAYS,
                )
            },
        ),
        Lesson(
            "chunking", "Merktechniken", "Bündeln (Chunking)", "Lange Ziffernfolgen in Dreierblöcke teilen – so merkt man sich 3 Blöcke statt 9 Ziffern.",
            listOf(
                "Teile die Zahl in Blöcke zu je 2–3 Ziffern.",
                "Gib Blöcken eine Bedeutung (Jahreszahl, Uhrzeit, Hausnummer).",
                "Sprich die Blöcke im Kopf rhythmisch nach.",
            ),
            "471932856 → 471 · 932 · 856",
            12_000L,
            { r ->
                val len = r.range(8, 10)
                val digits = (1..len).map { r.nextInt(10) }.joinToString("")
                DrillTask(
                    digits.chunked(3).joinToString("  "), digits,
                    "Blöcke: ${digits.chunked(3).joinToString(" · ")}", flashMs = 3500L,
                )
            },
        ),
        Lesson(
            "geschichte", "Merktechniken", "Geschichtenmethode", "Listen merken, indem du die Dinge zu einer verrückten Geschichte verbindest.",
            listOf(
                "Mach aus jedem Ding ein lebendiges Bild.",
                "Verknüpfe die Bilder der Reihe nach zu einer Geschichte – je absurder, desto besser.",
                "Zum Abrufen gehst du die Geschichte im Kopf noch einmal durch.",
            ),
            "Milch, Brot, Batterien → Eine Milchtüte surft auf einem Brot, das von Batterien angetrieben wird.",
            0L, null, gameId = "einkauf",
        ),
        Lesson(
            "namen", "Merktechniken", "Namen mit Merkmalen verknüpfen", "Namen bleiben hängen, wenn du sie mit einem Merkmal der Person verbindest.",
            listOf(
                "Sag den Namen beim Kennenlernen einmal laut oder im Kopf.",
                "Such ein auffälliges Merkmal (Brille, Locken, Hut).",
                "Bilde eine Eselsbrücke: „Brillen-Ben“, „Locken-Lina“.",
            ),
            "Greta mit rotem Hut → „Greta, die Große mit dem Hut“",
            0L, null, gameId = "namen",
        ),
    )

    fun find(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    /** Gezieltes Üben eines schwachen Aufgabentyps aus Blitzrechnen. */
    fun weaknessDrill(kind: String, level: Int): (Random) -> DrillTask = { r ->
        val t = ArithmeticGen.generate(level, r, only = kind)
        DrillTask("${t.text} =", "${t.answer}", "${t.text} = ${t.answer}")
    }
}
