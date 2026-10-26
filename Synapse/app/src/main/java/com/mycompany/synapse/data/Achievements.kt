package com.mycompany.synapse.data

import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.logic.NBackGen
import java.time.Instant
import java.time.ZoneId

/** Alles, was zur Prüfung von Erfolgen gebraucht wird. */
class AchievementContext(
    val records: List<SessionRecord>,
    val bestStreak: Int,
    val workoutsCompleted: Int,
    val duelsPlayed: Int,
    val overallIndex: Int,
    val firstIndex: Int?,
    val zone: ZoneId = ZoneId.systemDefault(),
    val masteredLessons: Int = 0,
    val benchmarks: Int = 0,
    val placementDone: Boolean = false,
) {
    val totalMinutes: Int by lazy { (records.sumOf { it.durationMs } / 60_000).toInt() }
    val trainingRecords: List<SessionRecord> by lazy { records.filter { !it.isTest } }
    fun maxLevel(gameId: String) = records.filter { it.gameId == gameId }.maxOfOrNull { maxOf(it.level, it.newLevel) } ?: 0
}

class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: Int, // 0 Bronze, 1 Silber, 2 Gold
    /** Fortschritt (aktuell, Ziel). */
    val progress: (AchievementContext) -> Pair<Int, Int>,
) {
    fun isUnlocked(ctx: AchievementContext): Boolean = progress(ctx).let { it.first >= it.second }
}

object Achievements {
    private fun count(n: Int, target: Int) = n.coerceAtMost(target) to target
    private fun flag(b: Boolean) = (if (b) 1 else 0) to 1

    private fun hourOf(ctx: AchievementContext, r: SessionRecord) =
        Instant.ofEpochMilli(r.startedAt).atZone(ctx.zone).hour

    val all: List<Achievement> = listOf(
        Achievement("first", "Erster Schritt", "Schließe dein erstes Training ab", 0) { count(it.trainingRecords.size, 1) },
        Achievement("s50", "Fleißig", "50 Trainings abgeschlossen", 1) { count(it.trainingRecords.size, 50) },
        Achievement("s250", "Unermüdlich", "250 Trainings abgeschlossen", 2) { count(it.trainingRecords.size, 250) },
        Achievement("streak3", "Dranbleiber", "3 Tage in Folge trainiert", 0) { count(it.bestStreak, 3) },
        Achievement("streak7", "Gewohnheit", "7-Tage-Serie", 1) { count(it.bestStreak, 7) },
        Achievement("streak30", "Eiserner Wille", "30-Tage-Serie", 2) { count(it.bestStreak, 30) },
        Achievement("workout1", "Tagesziel", "Erstes Tagestraining komplett", 0) { count(it.workoutsCompleted, 1) },
        Achievement("workout20", "Routine", "20 Tagestrainings komplett", 1) { count(it.workoutsCompleted, 20) },
        Achievement("allround", "Allrounder", "Jeden der 6 Bereiche trainiert", 0) { ctx ->
            count(ctx.records.map { it.domain }.toSet().size, Domain.entries.size)
        },
        Achievement("collector", "Entdecker", "Alle ${GameCatalog.all.size} Spiele ausprobiert", 1) { ctx ->
            count(ctx.trainingRecords.map { it.gameId }.toSet().size, GameCatalog.all.size)
        },
        Achievement("perfect", "Perfektionist", "100 % Trefferquote in einer Session mit mind. 8 Aufgaben", 1) { ctx ->
            flag(ctx.trainingRecords.any { it.trials >= 8 && it.correct == it.total && it.total > 0 })
        },
        Achievement("flow10", "Im Flow", "10 richtige Antworten in Folge", 0) { ctx -> count(ctx.records.maxOfOrNull { it.bestCombo } ?: 0, 10) },
        Achievement("flow25", "Flow-Legende", "25 richtige Antworten in Folge", 2) { ctx -> count(ctx.records.maxOfOrNull { it.bestCombo } ?: 0, 25) },
        Achievement("math10", "Kopfrechner", "Blitzrechnen Level 10", 0) { count(it.maxLevel("blitz"), 10) },
        Achievement("math25", "Rechenkünstler", "Blitzrechnen Level 25", 2) { count(it.maxLevel("blitz"), 25) },
        Achievement("logic15", "Logiker", "Muster-Matrix Level 15", 1) { count(it.maxLevel("matrix"), 15) },
        Achievement("nback3", "Elefantengedächtnis", "3-Back erreicht", 2) { ctx ->
            val n = ctx.maxLevel("nback").takeIf { it > 0 }?.let { NBackGen.config(it).n } ?: 0
            count(n, 3)
        },
        Achievement("sprint", "Sprinter", "Einen Sprint gespielt", 0) { ctx -> flag(ctx.records.any { it.mode == GameMode.SPRINT }) },
        Achievement("zen", "Zen-Meister", "10 Sessions im Zen-Modus", 1) { ctx -> count(ctx.records.count { it.mode == GameMode.ZEN }, 10) },
        Achievement("hour", "Eine Stunde", "60 Minuten Gesamt-Trainingszeit", 0) { count(it.totalMinutes, 60) },
        Achievement("tenhours", "Zehn Stunden", "600 Minuten Gesamt-Trainingszeit", 2) { count(it.totalMinutes, 600) },
        Achievement("early", "Frühaufsteher", "Vor 7 Uhr trainiert", 0) { ctx -> flag(ctx.records.any { hourOf(ctx, it) < 7 }) },
        Achievement("night", "Nachteule", "Nach 23 Uhr trainiert", 0) { ctx -> flag(ctx.records.any { hourOf(ctx, it) >= 23 }) },
        Achievement("rise100", "Aufsteiger", "Gesamt-Index um 100 Punkte gesteigert", 1) { ctx ->
            count((ctx.overallIndex - (ctx.firstIndex ?: ctx.overallIndex)).coerceAtLeast(0), 100)
        },
        Achievement("index500", "Denkmaschine", "Gesamt-Index 500 erreicht", 2) { count(it.overallIndex, 500) },
        Achievement("duel", "Duellant", "Ein Duell gespielt", 0) { count(it.duelsPlayed, 1) },
        Achievement("placement", "Standortbestimmung", "Einstufungstest abgeschlossen", 0) { flag(it.placementDone) },
        Achievement("bench1", "Messlatte", "Ersten Leistungstest gemacht", 0) { count(it.benchmarks, 1) },
        Achievement("bench3", "Langstrecke", "3 Leistungstests über mehrere Monate", 2) { count(it.benchmarks, 3) },
        Achievement("trick1", "Trickkiste", "Einen Rechentrick gemeistert", 0) { count(it.masteredLessons, 1) },
        Achievement("trick5", "Kopfrechen-Profi", "5 Tricks gemeistert", 1) { count(it.masteredLessons, 5) },
        Achievement("calendar", "Kalender-Genie", "Wochentag Level 25 erreicht", 2) { count(it.maxLevel("wochentag"), 25) },
        Achievement("sudoku", "Rätselfuchs", "Mini-Sudoku Level 15 erreicht", 1) { count(it.maxLevel("sudoku"), 15) },
    )
}
