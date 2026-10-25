package com.loopforge.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import com.loopforge.app.analysis.CandidateKind
import com.loopforge.app.analysis.Detection
import com.loopforge.app.analysis.FrameSampler
import com.loopforge.app.analysis.LoopCandidate
import com.loopforge.app.analysis.LoopEngine
import com.loopforge.app.analysis.SeamStyle
import com.loopforge.app.analysis.VideoInfo
import com.loopforge.app.analysis.VideoProbe
import com.loopforge.app.export.Crossfade
import com.loopforge.app.export.ExportOutput
import com.loopforge.app.export.ExportPlan
import com.loopforge.app.export.LoopExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

class LoopViewModel(app: Application) : AndroidViewModel(app) {

    enum class Stage { HOME, ANALYZING, STUDIO }
    enum class LengthMode { DURATION, REPEATS }

    sealed interface ExportState {
        data object Idle : ExportState
        data class Running(val progress: Float, val stage: String) : ExportState
        data class Done(val output: ExportOutput) : ExportState
        data class Failed(val message: String) : ExportState
    }

    /**
     * Frames around the jump: the last one before it and the first one after it, plus – with a
     * cross-fade – the two pictures that are mixed 50/50 in the middle of the fade.
     */
    class SeamFrames(
        val last: ImageBitmap,
        val first: ImageBitmap,
        val blendA: ImageBitmap? = null,
        val blendB: ImageBitmap? = null,
    )

    var stage by mutableStateOf(Stage.HOME)
        private set
    var info by mutableStateOf<VideoInfo?>(null)
        private set
    val analysisProgress = MutableStateFlow(0f)
    val analysisFrames = MutableStateFlow(0)
    var analysisMessage by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)

    var engine by mutableStateOf<LoopEngine?>(null)
        private set
    var detection by mutableStateOf<Detection?>(null)
        private set
    var similarityMap by mutableStateOf<ImageBitmap?>(null)
        private set
    var filmstrip by mutableStateOf<List<ImageBitmap>>(emptyList())
        private set
    val candidateThumbs = mutableStateMapOf<Int, ImageBitmap>()
    var selection by mutableStateOf<LoopCandidate?>(null)
        private set
    var seamFrames by mutableStateOf<SeamFrames?>(null)
        private set

    var minLoopSec by mutableFloatStateOf(1f)
        private set
    var lengthMode by mutableStateOf(LengthMode.DURATION)
    var targetSec by mutableIntStateOf(60)
    var repeatCount by mutableIntStateOf(5)
    var includeIntro by mutableStateOf(false)
    var includeOutro by mutableStateOf(false)
    var keepAudio by mutableStateOf(true)

    /** Long loops where only the transition has to fit, instead of short perfect ones. */
    var preferLong by mutableStateOf(true)
        private set
    var crossfadeOn by mutableStateOf(true)
        private set
    var crossfadeSec by mutableFloatStateOf(DEFAULT_CROSSFADE_SEC)
        private set
    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)

    private var probe: VideoProbe? = null
    private var analysisJob: Job? = null
    private var exportJob: Job? = null
    private var seamJob: Job? = null
    private var redetectJob: Job? = null
    private var thumbsJob: Job? = null

    private val app get() = getApplication<Application>()

    fun open(uri: Uri) {
        if (exportState.value is ExportState.Running) return
        clear()
        error = null
        stage = Stage.ANALYZING
        analysisMessage = "Video wird geöffnet…"
        analysisJob = viewModelScope.launch {
            try {
                val (videoProbe, videoInfo) = VideoProbe.open(app, uri)
                probe = videoProbe
                info = videoInfo
                if (videoInfo.durationUs in 1 until MIN_VIDEO_US) {
                    throw IllegalStateException("Das Video ist zu kurz – mindestens 0,3 Sekunden.")
                }
                launch {
                    filmstrip = videoProbe.filmstrip(videoInfo.durationUs, FILMSTRIP_FRAMES, 220, 220).map { it.asImageBitmap() }
                }
                analysisMessage = "Bewegungen werden Bild für Bild gelesen…"
                val features = FrameSampler(app).sample(uri) { progress, frames ->
                    analysisProgress.value = progress
                    analysisFrames.value = frames
                }
                analysisMessage = "Wiederholungen werden gesucht…"
                minLoopSec = defaultMinLoop(videoInfo.durationUs)
                val loopEngine = withContext(Dispatchers.Default) { LoopEngine(features) }
                loopEngine.style = seamStyle(loopEngine)
                val found = withContext(Dispatchers.Default) { loopEngine.detect(minLoopSec.toDouble()) }
                val pixels = withContext(Dispatchers.Default) { loopEngine.similarityImage() }
                val n = loopEngine.coarseCount
                similarityMap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
                engine = loopEngine
                detection = found
                keepAudio = videoInfo.hasAudio
                targetSec = if (videoInfo.durationUs > 60_000_000L) 300 else 60
                select(found.candidates.first())
                stage = Stage.STUDIO
                loadCandidateThumbs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Analysis failed", e)
                error = friendly(e)
                clear()
                stage = Stage.HOME
            }
        }
    }

    fun cancelAnalysis() {
        clear()
        stage = Stage.HOME
    }

    fun select(candidate: LoopCandidate) {
        selection = labelled(candidate)
        scheduleSeamFrames()
    }

    /** Tap / drag on the similarity map; snaps to the best seam close to the finger. */
    fun pickOnMap(xFraction: Float, yFraction: Float) {
        val e = engine ?: return
        select(e.snap(xFraction, yFraction, minLoopSec.toDouble()))
    }

    fun setRangeUs(startUs: Long, endUs: Long) {
        val e = engine ?: return
        val start = e.frameAt(startUs)
        val end = e.frameAt(endUs).coerceAtLeast(start + 1)
        val current = selection
        if (current != null && current.startFrame == start && current.endFrame == end) return
        select(e.candidate(start, end, CandidateKind.MANUAL))
    }

    fun nudge(moveStart: Boolean, deltaFrames: Int) {
        val e = engine ?: return
        val c = selection ?: return
        val start = if (moveStart) (c.startFrame + deltaFrames).coerceIn(0, c.endFrame - 1) else c.startFrame
        val end = if (!moveStart) (c.endFrame + deltaFrames).coerceIn(c.startFrame + 1, e.frameCount - 1) else c.endFrame
        select(e.candidate(start, end, CandidateKind.MANUAL))
    }

    fun updateMinLoop(seconds: Float) {
        minLoopSec = seconds
        redetect()
    }

    fun updatePreferLong(value: Boolean) {
        if (preferLong == value) return
        preferLong = value
        redetect(delayMs = 0)
    }

    fun updateCrossfade(on: Boolean = crossfadeOn, seconds: Float = crossfadeSec) {
        crossfadeOn = on
        crossfadeSec = seconds
        redetect()
    }

    /** Re-runs the search with the current settings; a hand-picked loop is kept and re-rated. */
    private fun redetect(delayMs: Long = 300) {
        val e = engine ?: return
        redetectJob?.cancel()
        redetectJob = viewModelScope.launch {
            delay(delayMs)
            val style = seamStyle(e)
            val min = minLoopSec.toDouble()
            val found = withContext(Dispatchers.Default) {
                e.style = style
                e.detect(min)
            }
            detection = found
            val current = selection
            if (current == null || current.kind != CandidateKind.MANUAL) {
                select(found.candidates.first())
            } else {
                select(e.candidate(current.startFrame, current.endFrame, CandidateKind.MANUAL))
            }
            loadCandidateThumbs()
        }
    }

    private fun seamStyle(e: LoopEngine) = SeamStyle(
        blendFrames = if (crossfadeOn) (crossfadeSec * 1e6 / e.frameIntervalUs).roundToInt() else 0,
        preferLong = preferLong,
    )

    /** The plan for the current settings, or null while nothing is selected. */
    fun plan(): ExportPlan? {
        val i = info ?: return null
        val c = selection ?: return null
        val base = ExportPlan(
            source = i.uri,
            sourceDurationUs = i.durationUs,
            loopStartUs = c.startUs,
            loopEndUs = c.endUs,
            repeats = 1,
            includeIntro = includeIntro,
            includeOutro = includeOutro,
            keepAudio = keepAudio && i.hasAudio,
            crossfadeUs = if (crossfadeOn) (crossfadeSec * 1_000_000L).toLong() else 0L,
            frameIntervalUs = engine?.frameIntervalUs?.toLong() ?: 33_333L,
        )
        val repeats = when (lengthMode) {
            LengthMode.REPEATS -> repeatCount
            LengthMode.DURATION -> {
                val rest = targetSec * 1_000_000L - base.introUs - base.outroUs
                ceil(rest.toDouble() / c.durationUs.coerceAtLeast(1)).toInt()
            }
        }.coerceIn(1, MAX_REPEATS)
        return base.copy(repeats = repeats)
    }

    fun startExport() {
        val plan = plan() ?: return
        if (exportState.value is ExportState.Running) return
        exportState.value = ExportState.Running(0f, "Vorbereitung…")
        exportJob = viewModelScope.launch {
            try {
                val output = LoopExporter(app).export(plan) { progress, stage ->
                    exportState.value = ExportState.Running(progress.coerceIn(0f, 1f), stage)
                }
                exportState.value = ExportState.Done(output)
            } catch (e: CancellationException) {
                exportState.value = ExportState.Idle
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Export failed", e)
                exportState.value = ExportState.Failed(friendly(e))
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportState.value = ExportState.Idle
    }

    fun dismissExport() {
        exportState.value = ExportState.Idle
    }

    fun reset() {
        if (exportState.value is ExportState.Running) return
        clear()
        exportState.value = ExportState.Idle
        stage = Stage.HOME
    }

    private fun labelled(c: LoopCandidate): LoopCandidate {
        val period = detection?.periodUs ?: return c
        val e = engine ?: return c
        return c.copy(periodMultiple = e.periodMultiple(c.durationUs, period))
    }

    private fun scheduleSeamFrames() {
        seamJob?.cancel()
        seamJob = viewModelScope.launch {
            delay(200)
            val p = probe ?: return@launch
            val e = engine ?: return@launch
            val c = selection ?: return@launch
            val last = p.frame(e.timestampUs(c.endFrame - 1), SEAM_SIZE, SEAM_SIZE, exact = true)
            val first = p.frame(e.timestampUs(c.startFrame), SEAM_SIZE, SEAM_SIZE, exact = true)
            // The middle of the cross-fade, where both pictures are on screen at 50 %.
            val fade = plan()?.crossfade
            var blendA: Bitmap? = null
            var blendB: Bitmap? = null
            if (fade != null) {
                val half = fade.fadeUs / 2
                val (loopSide, otherSide) = if (fade.side == Crossfade.Side.TAIL) {
                    (c.endUs - half) to (c.startUs - half)
                } else {
                    (c.startUs + half) to (c.endUs + half)
                }
                blendA = p.frame(loopSide, SEAM_SIZE, SEAM_SIZE, exact = true)
                blendB = p.frame(otherSide, SEAM_SIZE, SEAM_SIZE, exact = true)
            }
            if (last != null && first != null) {
                seamFrames = SeamFrames(last.asImageBitmap(), first.asImageBitmap(), blendA?.asImageBitmap(), blendB?.asImageBitmap())
            }
        }
    }

    private fun loadCandidateThumbs() {
        val p = probe ?: return
        val e = engine ?: return
        val list = detection?.candidates ?: return
        thumbsJob?.cancel()
        thumbsJob = viewModelScope.launch {
            for (c in list) {
                if (candidateThumbs.containsKey(c.startFrame)) continue
                p.frame(e.timestampUs(c.startFrame), 320, 320, exact = true)?.let {
                    candidateThumbs[c.startFrame] = it.asImageBitmap()
                }
            }
        }
    }

    private fun clear() {
        analysisJob?.cancel()
        seamJob?.cancel()
        redetectJob?.cancel()
        thumbsJob?.cancel()
        probe?.close()
        probe = null
        info = null
        engine = null
        detection = null
        similarityMap = null
        filmstrip = emptyList()
        candidateThumbs.clear()
        selection = null
        seamFrames = null
        analysisProgress.value = 0f
        analysisFrames.value = 0
        includeIntro = false
        includeOutro = false
    }

    @OptIn(UnstableApi::class)
    private fun friendly(e: Throwable): String = when (e) {
        is ExportException -> "Export fehlgeschlagen (${e.errorCodeName}). Versuche es mit einer kürzeren Ziel-Länge erneut."
        is OutOfMemoryError -> "Zu wenig Arbeitsspeicher für dieses Video."
        is SecurityException -> "Kein Zugriff auf das Video. Bitte erneut auswählen."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler (${e.javaClass.simpleName})"
    }

    override fun onCleared() {
        probe?.close()
    }

    private fun defaultMinLoop(durationUs: Long): Float = when {
        durationUs < 3_000_000L -> 0.4f
        durationUs < 8_000_000L -> 0.7f
        else -> 1f
    }

    companion object {
        private const val TAG = "LoopViewModel"
        private const val MIN_VIDEO_US = 300_000L
        private const val FILMSTRIP_FRAMES = 12
        private const val SEAM_SIZE = 540
        const val MAX_REPEATS = 3000
        const val DEFAULT_CROSSFADE_SEC = 0.6f
    }
}
