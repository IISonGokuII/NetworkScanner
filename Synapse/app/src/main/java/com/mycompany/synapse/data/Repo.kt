package com.mycompany.synapse.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mycompany.synapse.game.Domain
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
    /** Bereich ist noch nicht eingestuft (zählt ab der nächsten Runde zum Index). */
    val calibrating: Boolean,
)

data class TagSample(val tag: String, val correct: Int, val total: Int, val timeMs: Long)

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
    /** Level am Ende der Runde (Mikro-Anpassung); Basis für das nächste Level. */
    val endLevel: Int = level,
    val playedLevel: Double? = null,
    val tags: List<TagSample> = emptyList(),
)

data class Profile(val id: Int, val name: String, val color: Int)

data class LessonProgress(val bestCorrect: Int, val total: Int, val bestAvgMs: Long, val mastered: Boolean, val attempts: Int)

/** Ergebnis eines Test-Teils (Einstufung oder Leistungstest). */
data class TestPartResult(
    val domain: Domain,
    val gameId: String,
    val level: Int,
    val correct: Int,
    val total: Int,
    val avgMs: Long,
    val durationMs: Long,
)

/**
 * Zentrale Datenhaltung. Compose-State, damit alle Bildschirme automatisch aktualisieren.
 * Geräte-Einstellungen liegen in „synapse_global“, alles Persönliche pro Profil
 * (Profil 0 nutzt die ursprünglichen Dateien, damit bestehende Daten erhalten bleiben).
 */
object Repo {
    private lateinit var appContext: Context
    private lateinit var db: SynapseDb
    private lateinit var prefs: SharedPreferences
    private lateinit var global: SharedPreferences
    private var initialized = false

    var records by mutableStateOf<List<SessionRecord>>(emptyList())
        private set
    var tagRows by mutableStateOf<List<TagRow>>(emptyList())
        private set
    var benchmarkRows by mutableStateOf<List<BenchmarkRow>>(emptyList())
        private set
    private val levels = mutableStateMapOf<String, Int>()
    private val unlocked = mutableStateMapOf<String, Long>()
    private val lessons = mutableStateMapOf<String, LessonProgress>()

    // Profile
    var profiles by mutableStateOf<List<Profile>>(emptyList())
        private set
    var profileId by mutableStateOf(0)
        private set
    val profile: Profile get() = profiles.firstOrNull { it.id == profileId } ?: Profile(0, "Ich", 0)

    // Geräte-Einstellungen
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
    var reviewNotifOn by mutableStateOf(false)
        private set
    var reduceMotion by mutableStateOf(false)
        private set
    var onboarded by mutableStateOf(false)
        private set

    // Profil-Einstellungen & Zähler
    var workoutSize by mutableStateOf(3)
        private set
    var weeklyGoal by mutableStateOf(5)
        private set
    var cvdMode by mutableStateOf(false)
        private set
    var workoutsCompleted by mutableStateOf(0)
        private set
    var duelsPlayed by mutableStateOf(0)
        private set
    var placementDone by mutableStateOf(false)
        private set
    var placementSkipped by mutableStateOf(false)
        private set
    var resumeJson by mutableStateOf<String?>(null)
        private set
    var reviewSeenWeek by mutableStateOf(-1L)
        private set
    var autoBackupUri by mutableStateOf<String?>(null)
        private set
    var autoBackupLast by mutableStateOf(0L)
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
    val countedGames by derivedStateOf { Stats.countedGames(records) }
    val domainIndices by derivedStateOf { Stats.domainIndices(gameSkills, countedGames) }
    val pendingDomains by derivedStateOf { Stats.pendingDomains(records) }
    val overallIndex by derivedStateOf { Stats.overallIndex(domainIndices) }
    val trainedDays by derivedStateOf { records.map { it.day() }.toSet() }
    val benchmarkRuns by derivedStateOf { Insights.runs(benchmarkRows, KIND_BENCHMARK) }

    const val KIND_PLACEMENT = "placement"
    const val KIND_BENCHMARK = "benchmark"

    // ------------------------------------------------------------ Start & Profile

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        global = appContext.getSharedPreferences("synapse_global", Context.MODE_PRIVATE)
        migrateIfNeeded()
        loadGlobal()
        openProfile(global.getInt("current_profile", 0))
    }

    private fun prefsName(id: Int) = if (id == 0) "synapse" else "synapse_p$id"
    private fun dbName(id: Int) = if (id == 0) "synapse.db" else "synapse_p$id.db"

    /** Version 1 hatte alles in einer Datei: Geräte-Einstellungen einmalig herauskopieren. */
    private fun migrateIfNeeded() {
        if (global.getBoolean("migrated", false)) return
        val legacy = appContext.getSharedPreferences("synapse", Context.MODE_PRIVATE)
        global.edit()
            .putInt("theme", legacy.getInt("theme", 0))
            .putBoolean("sound", legacy.getBoolean("sound", true))
            .putBoolean("haptics", legacy.getBoolean("haptics", true))
            .putBoolean("reminder", legacy.getBoolean("reminder", false))
            .putInt("reminder_h", legacy.getInt("reminder_h", 18))
            .putInt("reminder_m", legacy.getInt("reminder_m", 30))
            .putBoolean("onboarded", legacy.getBoolean("onboarded", false))
            .putString("profiles", JSONArray().put(JSONObject().put("id", 0).put("name", "Ich").put("color", 0)).toString())
            .putInt("current_profile", 0)
            .putBoolean("migrated", true)
            .commit()
    }

    private fun loadGlobal() {
        themeMode = global.getInt("theme", 0)
        soundOn = global.getBoolean("sound", true)
        hapticsOn = global.getBoolean("haptics", true)
        reminderOn = global.getBoolean("reminder", false)
        reminderHour = global.getInt("reminder_h", 18)
        reminderMinute = global.getInt("reminder_m", 30)
        reviewNotifOn = global.getBoolean("review_notif", false)
        reduceMotion = global.getBoolean("reduce_motion", false)
        onboarded = global.getBoolean("onboarded", false)
        profiles = runCatching {
            val arr = JSONArray(global.getString("profiles", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Profile(o.getInt("id"), o.getString("name"), o.optInt("color"))
            }
        }.getOrDefault(emptyList()).ifEmpty { listOf(Profile(0, "Ich", 0)) }
    }

    private fun saveProfiles() {
        val arr = JSONArray()
        for (p in profiles) arr.put(JSONObject().put("id", p.id).put("name", p.name).put("color", p.color))
        global.edit().putString("profiles", arr.toString()).apply()
    }

    private fun openProfile(id: Int) {
        val target = profiles.firstOrNull { it.id == id }?.id ?: profiles.first().id
        if (::db.isInitialized) runCatching { db.close() }
        profileId = target
        global.edit().putInt("current_profile", target).apply()
        db = SynapseDb(appContext, dbName(target))
        prefs = appContext.getSharedPreferences(prefsName(target), Context.MODE_PRIVATE)
        records = runCatching { db.loadAll() }.getOrDefault(emptyList())
        tagRows = runCatching { db.loadTags() }.getOrDefault(emptyList())
        benchmarkRows = runCatching { db.loadBenchmarks() }.getOrDefault(emptyList())
        loadPrefs()
        if (unlocked.isEmpty() && records.isNotEmpty()) syncAchievements(silent = true)
        ensureWorkout()
    }

    fun switchProfile(id: Int) {
        if (id != profileId) openProfile(id)
    }

    fun addProfile(name: String, color: Int): Profile {
        val p = Profile((profiles.maxOfOrNull { it.id } ?: 0) + 1, name.trim().ifEmpty { "Profil" }, color)
        profiles = profiles + p
        saveProfiles()
        return p
    }

    fun renameProfile(id: Int, name: String, color: Int) {
        profiles = profiles.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, color = color) else it }
        saveProfiles()
    }

    fun deleteProfile(id: Int) {
        if (profiles.size <= 1) return
        profiles = profiles.filter { it.id != id }
        saveProfiles()
        if (id == profileId) openProfile(profiles.first().id)
        appContext.deleteDatabase(dbName(id))
        appContext.deleteSharedPreferences(prefsName(id))
    }

    private fun loadPrefs() {
        levels.clear()
        unlocked.clear()
        lessons.clear()
        for ((k, v) in prefs.all) {
            if (k.startsWith("level_") && v is Int) levels[k.removePrefix("level_")] = v
            if (k.startsWith("ach_") && v is Long) unlocked[k.removePrefix("ach_")] = v
            if (k.startsWith("lesson_") && v is String) parseLesson(v)?.let { lessons[k.removePrefix("lesson_")] = it }
        }
        workoutSize = prefs.getInt("workout_size", 3)
        weeklyGoal = prefs.getInt("weekly_goal", 5)
        cvdMode = prefs.getBoolean("cvd", false)
        workoutsCompleted = prefs.getInt("workouts_completed", 0)
        duelsPlayed = prefs.getInt("duels", 0)
        placementDone = prefs.getBoolean("placement_done", false)
        placementSkipped = prefs.getBoolean("placement_skipped", false)
        resumeJson = prefs.getString("resume", null)
        reviewSeenWeek = prefs.getLong("review_seen", -1L)
        autoBackupUri = prefs.getString("autobackup_uri", null)
        autoBackupLast = prefs.getLong("autobackup_last", 0L)
        workoutDay = prefs.getLong("wk_day", -1L)
        workoutPlan = prefs.getString("wk_plan", "").orEmpty().split(",").filter { GameCatalog.find(it) != null }
        workoutDone = prefs.getString("wk_done", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
    }

    // ------------------------------------------------------------ Einstellungen

    fun setTheme(mode: Int) { themeMode = mode; global.edit().putInt("theme", mode).apply() }
    fun setSound(on: Boolean) { soundOn = on; global.edit().putBoolean("sound", on).apply() }
    fun setHaptics(on: Boolean) { hapticsOn = on; global.edit().putBoolean("haptics", on).apply() }
    fun changeReduceMotion(on: Boolean) { reduceMotion = on; global.edit().putBoolean("reduce_motion", on).apply() }
    fun setReviewNotif(on: Boolean) { reviewNotifOn = on; global.edit().putBoolean("review_notif", on).apply() }
    fun setReminder(on: Boolean, hour: Int = reminderHour, minute: Int = reminderMinute) {
        reminderOn = on; reminderHour = hour; reminderMinute = minute
        global.edit().putBoolean("reminder", on).putInt("reminder_h", hour).putInt("reminder_m", minute).apply()
    }
    fun completeOnboarding() { onboarded = true; global.edit().putBoolean("onboarded", true).apply() }

    fun changeWorkoutSize(size: Int) {
        workoutSize = size
        prefs.edit().putInt("workout_size", size).remove("wk_day").apply()
        workoutDay = -1L
        ensureWorkout()
    }
    fun changeWeeklyGoal(days: Int) { weeklyGoal = days.coerceIn(1, 7); prefs.edit().putInt("weekly_goal", weeklyGoal).apply() }
    fun changeCvdMode(on: Boolean) { cvdMode = on; prefs.edit().putBoolean("cvd", on).apply() }
    fun markReviewSeen(weekStart: LocalDate) { reviewSeenWeek = weekStart.toEpochDay(); prefs.edit().putLong("review_seen", reviewSeenWeek).apply() }
    fun skipPlacement() { placementSkipped = true; prefs.edit().putBoolean("placement_skipped", true).apply() }

    fun level(gameId: String): Int = levels[gameId] ?: 1
    fun hasPlayed(gameId: String): Boolean = records.any { it.gameId == gameId }

    fun isUnlocked(id: String) = unlocked.containsKey(id)
    fun unlockedAt(id: String): Long? = unlocked[id]
    fun unlockedMap(): Map<String, Long> = unlocked.toMap()

    // ------------------------------------------------------------ Wiederholungsschutz

    fun recent(key: String): Set<String> = prefs.getString("recent_$key", "").orEmpty().split("|").filter { it.isNotBlank() }.toSet()

    fun markRecent(key: String, item: String, keep: Int = 80) {
        val list = prefs.getString("recent_$key", "").orEmpty().split("|").filter { it.isNotBlank() && it != item } + item
        prefs.edit().putString("recent_$key", list.takeLast(keep).joinToString("|")).apply()
    }

    // ------------------------------------------------------------ Lektionen

    private fun parseLesson(s: String): LessonProgress? = runCatching {
        val o = JSONObject(s)
        LessonProgress(o.getInt("c"), o.getInt("t"), o.getLong("ms"), o.getBoolean("m"), o.optInt("a", 1))
    }.getOrNull()

    fun lesson(id: String): LessonProgress? = lessons[id]

    /** Speichert ein Übungsergebnis; gemeistert = alles richtig und im Mittel schneller als [targetMs]. */
    fun saveLesson(id: String, correct: Int, total: Int, avgMs: Long, targetMs: Long): Boolean {
        val old = lessons[id]
        val masteredNow = correct == total && avgMs in 1..targetMs
        val better = old == null || correct > old.bestCorrect || (correct == old.bestCorrect && avgMs < old.bestAvgMs)
        val p = LessonProgress(
            bestCorrect = if (better) correct else old!!.bestCorrect,
            total = total,
            bestAvgMs = if (better) avgMs else old!!.bestAvgMs,
            mastered = masteredNow || (old?.mastered == true),
            attempts = (old?.attempts ?: 0) + 1,
        )
        lessons[id] = p
        prefs.edit().putString(
            "lesson_$id",
            JSONObject().put("c", p.bestCorrect).put("t", p.total).put("ms", p.bestAvgMs).put("m", p.mastered).put("a", p.attempts).toString(),
        ).apply()
        return masteredNow && old?.mastered != true
    }

    val masteredLessons: Int get() = lessons.values.count { it.mastered }

    // ------------------------------------------------------------ Unterbrochene Runde

    fun saveResume(json: String) { resumeJson = json; prefs.edit().putString("resume", json).apply() }
    fun clearResume() { resumeJson = null; prefs.edit().remove("resume").apply() }

    // ------------------------------------------------------------ Tagestraining

    fun ensureWorkout(today: LocalDate = LocalDate.now()) {
        if (!::prefs.isInitialized) return
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
        val previous = records.filter { it.gameId == input.gameId && !it.isTest }
        val delta = Leveling.calibrated(game.levelDelta(accuracy, input.avgMs, input.endLevel, input.mode), previous.size)
        val newLevel = Leveling.apply(input.endLevel, delta)
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
            trials = input.trials, workout = input.workout, playedLevel = input.playedLevel,
        )
        val id = runCatching { db.insert(record) }.getOrDefault(-1L)
        val saved = record.copy(id = id)
        val tags = input.tags.map { TagRow(id, input.gameId, it.tag, it.correct, it.total, it.timeMs, input.startedAt) }
        if (tags.isNotEmpty()) runCatching { db.insertTags(tags) }
        tagRows = tagRows + tags
        records = records + saved
        levels[input.gameId] = newLevel
        prefs.edit().putInt("level_${input.gameId}", newLevel).apply()
        if (input.workout) markWorkoutGame(input.gameId)

        val newAch = syncAchievements(silent = false)
        val history = records.filter { it.gameId == input.gameId && !it.isTest }.takeLast(12).map { it.skill }
        return SessionOutcome(
            record = saved, levelBefore = input.level, levelAfter = newLevel,
            newBestScore = previous.any { it.mode == input.mode } && input.score > prevBest,
            previousBestScore = prevBest, speedVsAverage = speedRatio, accuracyVsAverage = accRatio,
            newAchievements = newAch, history = history, indexBefore = indexBefore, indexAfter = overallIndex,
            calibrating = GameCatalog[input.gameId].domain in pendingDomains,
        )
    }

    // ------------------------------------------------------------ Einstufung & Leistungstest

    /**
     * Einstufung speichern: Das getestete Spiel bekommt das ermittelte Level, die anderen Spiele des Bereichs
     * starten etwas darunter. Bereits fleißig gespielte Spiele behalten ihr Level.
     */
    fun savePlacement(parts: List<TestPartResult>): List<Achievement> {
        val run = System.currentTimeMillis()
        val edit = prefs.edit()
        val newRecords = mutableListOf<SessionRecord>()
        for (p in parts) {
            for (g in GameCatalog.ofDomain(p.domain)) {
                val played = records.count { it.gameId == g.id && !it.isTest }
                if (played >= 3) continue
                val target = if (g.id == p.gameId) p.level else (p.level - 2).coerceAtLeast(1)
                val lvl = maxOf(level(g.id), target)
                levels[g.id] = lvl
                edit.putInt("level_${g.id}", lvl)
            }
            val r = SessionRecord(
                id = 0, gameId = p.gameId, mode = GameMode.TEST, startedAt = run, durationMs = p.durationMs,
                level = p.level, newLevel = p.level, correct = p.correct, total = p.total, avgMs = p.avgMs,
                score = 0, bestCombo = 0, trials = p.total, workout = false, playedLevel = p.level.toDouble(),
            )
            newRecords += r.copy(id = runCatching { db.insert(r) }.getOrDefault(-1L))
        }
        val rows = parts.map { p ->
            BenchmarkRow(run, KIND_PLACEMENT, p.domain, p.gameId, p.level, ratio(p), p.avgMs, p.level)
        }
        runCatching { db.insertBenchmarks(rows) }
        benchmarkRows = benchmarkRows + rows
        records = records + newRecords
        placementDone = true
        edit.putBoolean("placement_done", true).apply()
        ensureWorkout()
        return syncAchievements(silent = false)
    }

    fun saveBenchmark(parts: List<TestPartResult>): BenchmarkRun {
        val run = System.currentTimeMillis()
        val rows = parts.map { p ->
            val g = GameCatalog[p.gameId]
            BenchmarkRow(
                run, KIND_BENCHMARK, p.domain, p.gameId, p.level, ratio(p), p.avgMs,
                Insights.benchmarkScore(ratio(p), p.avgMs, g.targetMs(p.level), g.speedMatters),
            )
        }
        runCatching { db.insertBenchmarks(rows) }
        benchmarkRows = benchmarkRows + rows
        return BenchmarkRun(run, KIND_BENCHMARK, rows)
    }

    private fun ratio(p: TestPartResult) = if (p.total == 0) 0.0 else p.correct.toDouble() / p.total

    /** Leistungstest ist fällig, wenn noch keiner gemacht wurde oder der letzte 28+ Tage her ist. */
    fun benchmarkDue(now: Long = System.currentTimeMillis()): Boolean {
        val last = benchmarkRuns.lastOrNull()?.run ?: return records.size >= 3
        return now - last >= 28L * 86_400_000L
    }

    // ------------------------------------------------------------ Erfolge

    fun achievementContext(): AchievementContext {
        val streak = Stats.streak(trainedDays, LocalDate.now())
        return AchievementContext(
            records = records, bestStreak = streak.best, workoutsCompleted = workoutsCompleted,
            duelsPlayed = duelsPlayed, overallIndex = overallIndex, firstIndex = timeline.firstOrNull()?.overall,
            masteredLessons = masteredLessons, benchmarks = benchmarkRuns.size, placementDone = placementDone,
        )
    }

    /** Schaltet neu erreichte Erfolge frei und gibt sie zurück. */
    fun syncAchievements(silent: Boolean): List<Achievement> {
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
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("profile", profile.name)
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            if (k == "autobackup_uri" || k == "autobackup_last" || k == "resume") continue
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
                    .put("id", r.id).put("game", r.gameId).put("mode", r.mode.name).put("started", r.startedAt)
                    .put("duration", r.durationMs).put("level", r.level).put("newLevel", r.newLevel)
                    .put("correct", r.correct).put("total", r.total).put("avgMs", r.avgMs)
                    .put("score", r.score).put("combo", r.bestCombo).put("trials", r.trials)
                    .put("workout", r.workout).put("played", r.playedLevel ?: JSONObject.NULL)
            )
        }
        root.put("sessions", arr)
        val tags = JSONArray()
        for (t in tagRows) {
            tags.put(
                JSONObject().put("s", t.sessionId).put("g", t.gameId).put("tag", t.tag).put("c", t.correct)
                    .put("t", t.total).put("ms", t.timeMs).put("at", t.startedAt)
            )
        }
        root.put("tags", tags)
        val bench = JSONArray()
        for (b in benchmarkRows) {
            bench.put(
                JSONObject().put("run", b.run).put("kind", b.kind).put("domain", b.domain.name).put("game", b.gameId)
                    .put("level", b.level).put("acc", b.accuracy).put("ms", b.avgMs).put("score", b.score)
            )
        }
        root.put("benchmarks", bench)
        return root.toString(2)
    }

    /** Ersetzt alle Daten des aktuellen Profils durch das Backup. Gibt die Anzahl der Sessions zurück. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        require(root.optString("app") == "synapse") { "Keine Synapse-Sicherung" }
        val arr = root.getJSONArray("sessions")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SessionRecord(
                id = o.optLong("id", i.toLong()), gameId = o.getString("game"),
                mode = runCatching { GameMode.valueOf(o.getString("mode")) }.getOrDefault(GameMode.STANDARD),
                startedAt = o.getLong("started"), durationMs = o.getLong("duration"), level = o.getInt("level"),
                newLevel = o.getInt("newLevel"), correct = o.getInt("correct"), total = o.getInt("total"),
                avgMs = o.getLong("avgMs"), score = o.getInt("score"), bestCombo = o.optInt("combo"),
                trials = o.optInt("trials"), workout = o.optBoolean("workout"),
                playedLevel = if (o.isNull("played")) null else o.optDouble("played"),
            )
        }.filter { GameCatalog.find(it.gameId) != null }
        val tagsArr = root.optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArr.length()).map { i ->
            val o = tagsArr.getJSONObject(i)
            TagRow(o.getLong("s"), o.getString("g"), o.getString("tag"), o.getInt("c"), o.getInt("t"), o.getLong("ms"), o.getLong("at"))
        }
        val benchArr = root.optJSONArray("benchmarks") ?: JSONArray()
        val bench = (0 until benchArr.length()).mapNotNull { i ->
            val o = benchArr.getJSONObject(i)
            val d = runCatching { Domain.valueOf(o.getString("domain")) }.getOrNull() ?: return@mapNotNull null
            BenchmarkRow(o.getLong("run"), o.getString("kind"), d, o.getString("game"), o.getInt("level"), o.getDouble("acc"), o.getLong("ms"), o.getInt("score"))
        }
        val keepUri = prefs.getString("autobackup_uri", null)
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
        // Version-1-Sicherungen enthalten Geräte-Einstellungen – die gehören nicht ins Profil.
        for (k in listOf("theme", "sound", "haptics", "reminder", "reminder_h", "reminder_m", "onboarded")) edit.remove(k)
        if (keepUri != null) edit.putString("autobackup_uri", keepUri)
        edit.commit()
        db.replaceAll(list, tags, bench)
        records = db.loadAll()
        tagRows = db.loadTags()
        benchmarkRows = db.loadBenchmarks()
        loadPrefs()
        if (unlocked.isEmpty()) syncAchievements(silent = true)
        return list.size
    }

    // ------------------------------------------------------------ Automatische Sicherung

    fun setAutoBackup(uri: String?) {
        autoBackupUri = uri
        val e = prefs.edit()
        if (uri == null) e.remove("autobackup_uri") else e.putString("autobackup_uri", uri)
        e.apply()
    }

    /** Schreibt die Sicherung in die gewählte Datei. Gibt false zurück, wenn der Zugriff nicht mehr klappt. */
    fun writeAutoBackup(context: Context): Boolean {
        val uri = autoBackupUri ?: return false
        val ok = runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(exportJson().toByteArray()) }
                ?: error("Kein Zugriff")
        }.isSuccess
        if (ok) {
            autoBackupLast = System.currentTimeMillis()
            prefs.edit().putLong("autobackup_last", autoBackupLast).apply()
        }
        return ok
    }

    /** Einmal pro Tag beim Öffnen der App, falls eingerichtet. */
    fun autoBackupIfDue(context: Context) {
        if (autoBackupUri == null || records.isEmpty()) return
        if (System.currentTimeMillis() - autoBackupLast < 86_400_000L) return
        writeAutoBackup(context)
    }

    /** Löscht den Trainingsfortschritt dieses Profils, behält aber die Einstellungen. */
    fun resetProgress() {
        db.replaceAll(emptyList(), emptyList(), emptyList())
        records = emptyList()
        tagRows = emptyList()
        benchmarkRows = emptyList()
        val edit = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("level_") || k.startsWith("ach_") || k.startsWith("wk_") || k.startsWith("lesson_") ||
                k.startsWith("recent_") || k in setOf("workouts_completed", "duels", "placement_done", "placement_skipped", "resume", "review_seen")
            ) edit.remove(k)
        }
        edit.apply()
        loadPrefs()
        workoutDay = -1L
        ensureWorkout()
    }
}
