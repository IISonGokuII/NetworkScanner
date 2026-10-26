package com.mycompany.synapse.ui

import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.TrialResult
import org.json.JSONArray
import org.json.JSONObject

/** Zwischenstand einer laufenden Runde – wird nach jeder Aufgabe gespeichert. */
data class ResumeState(
    val gameId: String,
    val mode: GameMode,
    val startLevel: Int,
    val curLevel: Int,
    val seedBase: Long,
    val results: List<TrialResult>,
    val trialLevels: List<Int>,
    val score: Int,
    val combo: Int,
    val bestCombo: Int,
    val upStreak: Int,
    val downStreak: Int,
    val startedAt: Long,
    val elapsedMs: Long,
    val savedAt: Long,
    val workout: WorkoutRun?,
) {
    fun toJson(): String {
        val o = JSONObject()
            .put("game", gameId).put("mode", mode.name).put("start", startLevel).put("cur", curLevel)
            .put("seed", seedBase).put("score", score).put("combo", combo).put("best", bestCombo)
            .put("up", upStreak).put("down", downStreak).put("startedAt", startedAt).put("elapsed", elapsedMs)
            .put("saved", savedAt)
        val res = JSONArray()
        for (r in results) {
            res.put(JSONObject().put("c", r.correct).put("t", r.total).put("ms", r.timeMs).put("tag", r.tag ?: JSONObject.NULL))
        }
        o.put("results", res)
        o.put("levels", JSONArray(trialLevels))
        if (workout != null) {
            val entries = JSONArray()
            for (e in workout.entries) {
                entries.put(
                    JSONObject().put("g", e.gameId).put("s", e.score).put("b", e.levelBefore).put("a", e.levelAfter).put("acc", e.accuracy)
                )
            }
            o.put("workout", JSONObject().put("games", JSONArray(workout.games)).put("index", workout.index).put("entries", entries))
        }
        return o.toString()
    }

    companion object {
        fun fromJson(s: String): ResumeState? = runCatching {
            val o = JSONObject(s)
            val res = o.getJSONArray("results")
            val levels = o.getJSONArray("levels")
            val workout = o.optJSONObject("workout")?.let { w ->
                val games = w.getJSONArray("games")
                val entries = w.getJSONArray("entries")
                WorkoutRun(
                    (0 until games.length()).map { games.getString(it) },
                    w.getInt("index"),
                    (0 until entries.length()).map {
                        val e = entries.getJSONObject(it)
                        WorkoutEntry(e.getString("g"), e.getInt("s"), e.getInt("b"), e.getInt("a"), e.getDouble("acc"))
                    },
                )
            }
            ResumeState(
                gameId = o.getString("game"),
                mode = GameMode.valueOf(o.getString("mode")),
                startLevel = o.getInt("start"),
                curLevel = o.getInt("cur"),
                seedBase = o.getLong("seed"),
                results = (0 until res.length()).map {
                    val r = res.getJSONObject(it)
                    TrialResult(r.getInt("c"), r.getInt("t"), r.getLong("ms"), tag = if (r.isNull("tag")) null else r.getString("tag"))
                },
                trialLevels = (0 until levels.length()).map { levels.getInt(it) },
                score = o.getInt("score"),
                combo = o.getInt("combo"),
                bestCombo = o.getInt("best"),
                upStreak = o.getInt("up"),
                downStreak = o.getInt("down"),
                startedAt = o.getLong("startedAt"),
                elapsedMs = o.getLong("elapsed"),
                savedAt = o.getLong("saved"),
                workout = workout,
            )
        }.getOrNull()
    }
}
