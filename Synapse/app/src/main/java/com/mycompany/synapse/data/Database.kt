package com.mycompany.synapse.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mycompany.synapse.game.GameMode

/** Schlanke SQLite-Datenbank für alle Trainings-Sessions (kein Annotation-Processing nötig). */
class SynapseDb(context: Context) : SQLiteOpenHelper(context, "synapse.db", null, 1) {

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
                workout INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(started)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
                )
            }
        }
        return out
    }

    fun insert(r: SessionRecord): Long = writableDatabase.insert("sessions", null, values(r))

    fun replaceAll(records: List<SessionRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sessions", null, null)
            for (r in records) db.insert("sessions", null, values(r))
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
    }
}
