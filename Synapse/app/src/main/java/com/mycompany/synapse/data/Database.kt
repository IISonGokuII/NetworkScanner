package com.mycompany.synapse.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameMode

/** Aufgabentyp-Statistik einer Session (für die Schwächen-Analyse). */
data class TagRow(val sessionId: Long, val gameId: String, val tag: String, val correct: Int, val total: Int, val timeMs: Long, val startedAt: Long)

/** Ein Teil eines Einstufungs- oder Leistungstests. */
data class BenchmarkRow(
    val run: Long,
    val kind: String,
    val domain: Domain,
    val gameId: String,
    val level: Int,
    val accuracy: Double,
    val avgMs: Long,
    val score: Int,
)

/**
 * Schlanke SQLite-Datenbank (kein Annotation-Processing nötig). Pro Profil eine eigene Datei.
 * Version 2: gespieltes Level, Aufgabentypen und Tests.
 */
class SynapseDb(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game TEXT NOT NULL,
                mode TEXT NOT NULL,
                started INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                level INTEGER NOT NULL,
                new_level INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                total INTEGER NOT NULL,
                avg_ms INTEGER NOT NULL,
                score INTEGER NOT NULL,
                best_combo INTEGER NOT NULL,
                trials INTEGER NOT NULL,
                workout INTEGER NOT NULL,
                played_level REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(started)")
        createV2Tables(db)
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL, game TEXT NOT NULL, " +
                "tag TEXT NOT NULL, correct INTEGER NOT NULL, total INTEGER NOT NULL, time_ms INTEGER NOT NULL, started INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE benchmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, run INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "domain TEXT NOT NULL, game TEXT NOT NULL, level INTEGER NOT NULL, accuracy REAL NOT NULL, " +
                "avg_ms INTEGER NOT NULL, score INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN played_level REAL")
            createV2Tables(db)
        }
    }

    fun loadAll(): List<SessionRecord> {
        val out = mutableListOf<SessionRecord>()
        readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY started", null).use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iGame = c.getColumnIndexOrThrow("game")
            val iMode = c.getColumnIndexOrThrow("mode")
            val iStarted = c.getColumnIndexOrThrow("started")
            val iDur = c.getColumnIndexOrThrow("duration_ms")
            val iLevel = c.getColumnIndexOrThrow("level")
            val iNew = c.getColumnIndexOrThrow("new_level")
            val iCorrect = c.getColumnIndexOrThrow("correct")
            val iTotal = c.getColumnIndexOrThrow("total")
            val iAvg = c.getColumnIndexOrThrow("avg_ms")
            val iScore = c.getColumnIndexOrThrow("score")
            val iCombo = c.getColumnIndexOrThrow("best_combo")
            val iTrials = c.getColumnIndexOrThrow("trials")
            val iWorkout = c.getColumnIndexOrThrow("workout")
            val iPlayed = c.getColumnIndexOrThrow("played_level")
            while (c.moveToNext()) {
                out += SessionRecord(
                    id = c.getLong(iId),
                    gameId = c.getString(iGame),
                    mode = runCatching { GameMode.valueOf(c.getString(iMode)) }.getOrDefault(GameMode.STANDARD),
                    startedAt = c.getLong(iStarted),
                    durationMs = c.getLong(iDur),
                    level = c.getInt(iLevel),
                    newLevel = c.getInt(iNew),
                    correct = c.getInt(iCorrect),
                    total = c.getInt(iTotal),
                    avgMs = c.getLong(iAvg),
                    score = c.getInt(iScore),
                    bestCombo = c.getInt(iCombo),
                    trials = c.getInt(iTrials),
                    workout = c.getInt(iWorkout) != 0,
                    playedLevel = if (c.isNull(iPlayed)) null else c.getDouble(iPlayed),
                )
            }
        }
        return out
    }

    fun loadTags(): List<TagRow> {
        val out = mutableListOf<TagRow>()
        readableDatabase.rawQuery("SELECT session_id, game, tag, correct, total, time_ms, started FROM tags ORDER BY started", null).use { c ->
            while (c.moveToNext()) {
                out += TagRow(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getLong(5), c.getLong(6))
            }
        }
        return out
    }

    fun loadBenchmarks(): List<BenchmarkRow> {
        val out = mutableListOf<BenchmarkRow>()
        readableDatabase.rawQuery("SELECT run, kind, domain, game, level, accuracy, avg_ms, score FROM benchmarks ORDER BY run, id", null).use { c ->
            while (c.moveToNext()) {
                val domain = runCatching { Domain.valueOf(c.getString(2)) }.getOrNull() ?: continue
                out += BenchmarkRow(c.getLong(0), c.getString(1), domain, c.getString(3), c.getInt(4), c.getDouble(5), c.getLong(6), c.getInt(7))
            }
        }
        return out
    }

    fun insert(r: SessionRecord): Long = writableDatabase.insert("sessions", null, values(r))

    fun insertTags(rows: List<TagRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in rows) db.insert("tags", null, tagValues(t))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertBenchmarks(rows: List<BenchmarkRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (b in rows) db.insert("benchmarks", null, benchValues(b))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Ersetzt den gesamten Inhalt (Import/Zurücksetzen). Session-IDs der Tags werden neu zugeordnet. */
    fun replaceAll(records: List<SessionRecord>, tags: List<TagRow>, benchmarks: List<BenchmarkRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sessions", null, null)
            db.delete("tags", null, null)
            db.delete("benchmarks", null, null)
            val idMap = HashMap<Long, Long>()
            for (r in records) idMap[r.id] = db.insert("sessions", null, values(r))
            for (t in tags) db.insert("tags", null, tagValues(t.copy(sessionId = idMap[t.sessionId] ?: -1L)))
            for (b in benchmarks) db.insert("benchmarks", null, benchValues(b))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun values(r: SessionRecord) = ContentValues().apply {
        put("game", r.gameId)
        put("mode", r.mode.name)
        put("started", r.startedAt)
        put("duration_ms", r.durationMs)
        put("level", r.level)
        put("new_level", r.newLevel)
        put("correct", r.correct)
        put("total", r.total)
        put("avg_ms", r.avgMs)
        put("score", r.score)
        put("best_combo", r.bestCombo)
        put("trials", r.trials)
        put("workout", if (r.workout) 1 else 0)
        if (r.playedLevel != null) put("played_level", r.playedLevel) else putNull("played_level")
    }

    private fun tagValues(t: TagRow) = ContentValues().apply {
        put("session_id", t.sessionId)
        put("game", t.gameId)
        put("tag", t.tag)
        put("correct", t.correct)
        put("total", t.total)
        put("time_ms", t.timeMs)
        put("started", t.startedAt)
    }

    private fun benchValues(b: BenchmarkRow) = ContentValues().apply {
        put("run", b.run)
        put("kind", b.kind)
        put("domain", b.domain.name)
        put("game", b.gameId)
        put("level", b.level)
        put("accuracy", b.accuracy)
        put("avg_ms", b.avgMs)
        put("score", b.score)
    }
}
