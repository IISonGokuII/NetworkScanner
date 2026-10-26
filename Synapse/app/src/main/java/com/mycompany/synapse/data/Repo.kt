package com.mycompany.synapse.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mycompany.synapse.game.GameCatalog
import com.mycompany.synapse.game.GameMode
import com.mycompany.synapse.game.Leveling
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class SessionOutcome(
    val record: SessionRecord,
    val levelBefore: Int,
    val levelAfter: Int,
    val newBestScore: Boolean,
    val previousBestScore: Int,
    /** Verhältnis Ø-Zeit dieser Session zu deinem bisherigen Schnitt (0.8 = 20 % schneller). */
    val speedVsAverage: Double?,
    val accuracyVsAverage: Double?,
    val newAchievements: List<Achievement>,
    val history: List<Double>,
    val indexBefore: Int,
    val indexAfter: Int,
)

data class SessionInput(
    val gameId: String,
    val mode: GameMode,
    val startedAt: Long,
    val durationMs: Long,
    val level: Int,
    val correct: Int,
    val total: Int,
    val avgMs: Long,
    val score: Int,
    val bestCombo: Int,
    val trials: Int,
    val workout: Boolean,
)

/** Zentrale Datenhaltung. Compose-State, damit alle Bildschirme automatisch aktualisieren. */
object Repo {
    private lateinit var db: SynapseDb
    private lateinit var prefs: SharedPreferences
    private var initialized = false

    var records by mutableStateOf<List<SessionRecord>>(emptyList())
        private set
    private val levels = mutableStateMapOf<String, Int>()
    private val unlocked = mutableStateMapOf<String, Long>()

    // Einstellungen
    var themeMode by mutableStateOf(0) // 0 = System, 1 = Dunkel, 2 = Hell
        private set
    var soundOn by mutableStateOf(true)
        private set
    var hapticsOn by mutableStateOf(true)
        private set
    var reminderOn by mutableStateOf(false)
        private set
    var reminderHour by mutableStateOf(18)
        private set
    var reminderMinute by mutableStateOf(30)
        private set
    var workoutSize by mutableStateOf(3)
        private set
    var onboarded by mutableStateOf(false)
        private set
    var workoutsCompleted by mutableStateOf(0)
        private set
    var duelsPlayed by mutableStateOf(0)
        private set

    // Tagestraining
    private var workoutDay by mutableStateOf(-1L)
    var workoutPlan by mutableStateOf<List<String>>(emptyList())
        private set
    var workoutDone by mutableStateOf<Set<String>>(emptySet())
        private set

    // Abgeleitete Statistik (wird nur bei Änderungen neu berechnet)
    val timeline by derivedStateOf { Stats.timeline(records) }
    val gameSkills by derivedStateOf { Stats.gameSkills(records) }
    val domainIndices by derivedStateOf { Stats.domainIndices(gameSkills) }
    val overallIndex by derivedStateOf { Stats.overallIndex(domainIndices) }
    val trainedDays by derivedStateOf { records.map { it.day() }.toSet() }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        db = SynapseDb(context.applicationContext)
        prefs = context.applicationContext.getSharedPreferences("synapse", Context.MODE_PRIVATE)
        records = runCatching { db.loadAll() }.getOrDefault(emptyList())
        loadPrefs()
        if (unlocked.isEmpty() && records.isNotEmpty()) syncAchievements(silent = true)
    }

    private fun loadPrefs() {
        levels.clear()
        unlocked.clear()
        for ((k, v) in prefs.all) {
            if (k.startsWith("level_") && v is Int) levels[k.removePrefix("level_")] = v
            if (k.startsWith("ach_") && v is Long) unlocked[k.removePrefix("ach_")] = v
        }
        themeMode = prefs.getInt("theme", 0)
        soundOn = prefs.getBoolean("sound", true)
        hapticsOn = prefs.getBoolean("haptics", true)
        reminderOn = prefs.getBoolean("reminder", false)
        reminderHour = prefs.getInt("reminder_h", 18)
        reminderMinute = prefs.getInt("reminder_m", 30)
        workoutSize = prefs.getInt("workout_size", 3)
        onboarded = prefs.getBoolean("onboarded", false)
        workoutsCompleted = prefs.getInt("workouts_completed", 0)
        duelsPlayed = prefs.getInt("duels", 0)
        workoutDay = prefs.getLong("wk_day", -1L)
        workoutPlan = prefs.getString("wk_plan", "").orEmpty().split(",").filter { GameCatalog.find(it) != null }
        workoutDone = prefs.getString("wk_done", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    }

    // ------------------------------------------------------------ Einstellungen

    fun setTheme(mode: Int) { themeMode = mode; prefs.edit().putInt("theme", mode).apply() }
    fun setSound(on: Boolean) { soundOn = on; prefs.edit().putBoolean("sound", on).apply() }
    fun setHaptics(on: Boolean) { hapticsOn = on; prefs.edit().putBoolean("haptics", on).apply() }
    fun setReminder(on: Boolean, hour: Int = reminderHour, minute: Int = reminderMinute) {
        reminderOn = on; reminderHour = hour; reminderMinute = minute
        prefs.edit().putBoolean("reminder", on).putInt("reminder_h", hour).putInt("reminder_m", minute).apply()
    }
    fun changeWorkoutSize(size: Int) {
        workoutSize = size
        prefs.edit().putInt("workout_size", size).remove("wk_day").apply()
        workoutDay = -1L
        ensureWorkout()
    }
    fun completeOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }

    fun level(gameId: String): Int = levels[gameId] ?: 1

    fun isUnlocked(id: String) = unlocked.containsKey(id)
    fun unlockedAt(id: String): Long? = unlocked[id]

    // ------------------------------------------------------------ Tagestraining

    fun ensureWorkout(today: LocalDate = LocalDate.now()) {
        if (workoutDay == today.toEpochDay() && workoutPlan.isNotEmpty()) return
        workoutDay = today.toEpochDay()
        workoutPlan = WorkoutPlanner.plan(records, today, workoutSize)
        workoutDone = emptySet()
        prefs.edit()
            .putLong("wk_day", workoutDay)
            .putString("wk_plan", workoutPlan.joinToString(","))
            .putString("wk_done", "")
            .putBoolean("wk_counted", false)
            .apply()
    }

    val workoutRemaining: List<String> get() = workoutPlan.filter { it !in workoutDone }
    val workoutComplete: Boolean get() = workoutPlan.isNotEmpty() && workoutRemaining.isEmpty()

    private fun markWorkoutGame(gameId: String) {
        ensureWorkout()
        if (gameId !in workoutPlan) return
        workoutDone = workoutDone + gameId
        val edit = prefs.edit().putString("wk_done", workoutDone.joinToString(","))
        if (workoutComplete && !prefs.getBoolean("wk_counted", false)) {
            workoutsCompleted++
            edit.putBoolean("wk_counted", true).putInt("workouts_completed", workoutsCompleted)
        }
        edit.apply()
    }

    fun recordDuel(): List<Achievement> {
        duelsPlayed++
        prefs.edit().putInt("duels", duelsPlayed).apply()
        return syncAchievements(silent = false)
    }

    // ------------------------------------------------------------ Sessions

    fun saveSession(input: SessionInput): SessionOutcome {
        val game = GameCatalog[input.gameId]
        val accuracy = if (input.total == 0) 0.0 else input.correct.toDouble() / input.total
        val delta = game.levelDelta(accuracy, input.avgMs, input.level, input.mode)
        val newLevel = Leveling.apply(input.level, delta)
        val previous = records.filter { it.gameId == input.gameId }
        val prevBest = previous.filter { it.mode == input.mode }.maxOfOrNull { it.score } ?: 0
        val prevTimed = previous.filter { it.mode != GameMode.ZEN && it.avgMs > 0 }.takeLast(10)
        val speedRatio = if (input.mode != GameMode.ZEN && input.avgMs > 0 && prevTimed.size >= 2 && game.speedMatters)
            input.avgMs / prevTimed.map { it.avgMs }.average() else null
        val accRatio = if (previous.size >= 2) accuracy - previous.takeLast(10).map { it.accuracy }.average() else null
        val indexBefore = overallIndex

        val record = SessionRecord(
            id = 0, gameId = input.gameId, mode = input.mode, startedAt = input.startedAt,
            durationMs = input.durationMs, level = input.level, newLevel = newLevel, correct = input.correct,
            total = input.total, avgMs = input.avgMs, score = input.score, bestCombo = input.bestCombo,
            trials = input.trials, workout = input.workout,
        )
        val id = runCatching { db.insert(record) }.getOrDefault(-1L)
        val saved = record.copy(id = id)
        records = records + saved
        levels[input.gameId] = newLevel
        prefs.edit().putInt("level_${input.gameId}", newLevel).apply()
        if (input.workout) markWorkoutGame(input.gameId)

        val newAch = syncAchievements(silent = false)
        val history = records.filter { it.gameId == input.gameId }.takeLast(12).map { it.skill }
        return SessionOutcome(
            record = saved, levelBefore = input.level, levelAfter = newLevel,
            newBestScore = previous.any { it.mode == input.mode } && input.score > prevBest,
            previousBestScore = prevBest, speedVsAverage = speedRatio, accuracyVsAverage = accRatio,
            newAchievements = newAch, history = history, indexBefore = indexBefore, indexAfter = overallIndex,
        )
    }

    fun achievementContext(): AchievementContext {
        val streak = Stats.streak(trainedDays, LocalDate.now())
        return AchievementContext(
            records = records, bestStreak = streak.best, workoutsCompleted = workoutsCompleted,
            duelsPlayed = duelsPlayed, overallIndex = overallIndex, firstIndex = timeline.firstOrNull()?.overall,
        )
    }

    /** Schaltet neu erreichte Erfolge frei und gibt sie zurück. */
    private fun syncAchievements(silent: Boolean): List<Achievement> {
        val ctx = achievementContext()
        val now = System.currentTimeMillis()
        val fresh = Achievements.all.filter { !unlocked.containsKey(it.id) && it.isUnlocked(ctx) }
        if (fresh.isEmpty()) return emptyList()
        val edit = prefs.edit()
        for (a in fresh) {
            unlocked[a.id] = now
            edit.putLong("ach_${a.id}", now)
        }
        edit.apply()
        return if (silent) emptyList() else fresh
    }

    // ------------------------------------------------------------ Backup

    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "synapse")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            val entry = JSONObject()
            when (v) {
                is Int -> entry.put("t", "i").put("v", v)
                is Long -> entry.put("t", "l").put("v", v)
                is Boolean -> entry.put("t", "b").put("v", v)
                is String -> entry.put("t", "s").put("v", v)
                is Float -> entry.put("t", "f").put("v", v.toDouble())
                else -> continue
            }
            p.put(k, entry)
        }
        root.put("prefs", p)
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject()
                    .put("game", r.gameId).put("mode", r.mode.name).put("started", r.startedAt)
                    .put("duration", r.durationMs).put("level", r.level).put("newLevel", r.newLevel)
                    .put("correct", r.correct).put("total", r.total).put("avgMs", r.avgMs)
                    .put("score", r.score).put("combo", r.bestCombo).put("trials", r.trials)
                    .put("workout", r.workout)
            )
        }
        root.put("sessions", arr)
        return root.toString(2)
    }

    /** Ersetzt alle Daten durch das Backup. Gibt die Anzahl der importierten Sessions zurück. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        require(root.optString("app") == "synapse") { "Keine Synapse-Sicherung" }
        val arr = root.getJSONArray("sessions")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SessionRecord(
                id = 0, gameId = o.getString("game"),
                mode = runCatching { GameMode.valueOf(o.getString("mode")) }.getOrDefault(GameMode.STANDARD),
                startedAt = o.getLong("started"), durationMs = o.getLong("duration"), level = o.getInt("level"),
                newLevel = o.getInt("newLevel"), correct = o.getInt("correct"), total = o.getInt("total"),
                avgMs = o.getLong("avgMs"), score = o.getInt("score"), bestCombo = o.optInt("combo"),
                trials = o.optInt("trials"), workout = o.optBoolean("workout"),
            )
        }.filter { GameCatalog.find(it.gameId) != null }
        val p = root.optJSONObject("prefs") ?: JSONObject()
        val edit = prefs.edit().clear()
        for (k in p.keys()) {
            val e = p.getJSONObject(k)
            when (e.optString("t")) {
                "i" -> edit.putInt(k, e.getInt("v"))
                "l" -> edit.putLong(k, e.getLong("v"))
                "b" -> edit.putBoolean(k, e.getBoolean("v"))
                "s" -> edit.putString(k, e.getString("v"))
                "f" -> edit.putFloat(k, e.getDouble("v").toFloat())
            }
        }
        edit.commit()
        db.replaceAll(list)
        records = db.loadAll()
        loadPrefs()
        if (unlocked.isEmpty()) syncAchievements(silent = true)
        return list.size
    }

    /** Löscht den Trainingsfortschritt, behält aber die Einstellungen. */
    fun resetProgress() {
        db.replaceAll(emptyList())
        records = emptyList()
        val edit = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("level_") || k.startsWith("ach_") || k.startsWith("wk_") ||
                k == "workouts_completed" || k == "duels"
            ) edit.remove(k)
        }
        edit.apply()
        loadPrefs()
        workoutDay = -1L
        ensureWorkout()
    }
}
