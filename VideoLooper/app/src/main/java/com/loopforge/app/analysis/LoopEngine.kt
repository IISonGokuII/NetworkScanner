package com.loopforge.app.analysis

import java.util.Random
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A loop plays frames [startFrame, endFrame) over and over; the frame after the seam is [startFrame]. */
data class LoopCandidate(
    val startFrame: Int,
    val endFrame: Int,
    /** Clip start for the exporter (midpoint before [startFrame]). */
    val startUs: Long,
    /** Clip end for the exporter (midpoint before [endFrame], exclusive). */
    val endUs: Long,
    /** 0..1, 1 = the jump back is not distinguishable from a normal frame step. */
    val quality: Float,
    val kind: CandidateKind,
    /** How many detected periods fit into this loop, 0 if unknown. */
    val periodMultiple: Int = 0,
) {
    val durationUs: Long get() = endUs - startUs
}

enum class CandidateKind { BEST, LONGEST, SHORTEST, ALTERNATIVE, MANUAL }

data class Detection(
    val candidates: List<LoopCandidate>,
    /** Fundamental period of a repeating motion, null if the video is not periodic. */
    val periodUs: Long?,
    /** 0..1, how strongly the video repeats itself. */
    val periodStrength: Float,
)

/**
 * Finds seamless loop points in a video.
 *
 * The idea follows "video textures" (Schödl et al.): a loop [a, b) is seamless when the frames
 * around b look like the frames around a, because then the jump from frame b-1 back to frame a
 * is indistinguishable from the natural step b-1 -> b. Comparing whole neighbourhoods (not only
 * single frames) makes sure that the motion direction matches as well, so a pendulum swinging
 * left is not glued to the same position swinging right.
 *
 * The search runs on a coarse self-similarity matrix (at most [MAX_COARSE] samples) and every
 * hit is refined frame-accurately on the full-resolution fingerprints.
 */
class LoopEngine(val features: FrameFeatures) {

    val frameCount: Int = features.count
    private val ts = features.timestampsUs
    private val fine = features.data

    /** Frames between two coarse samples. */
    val stride: Int = max(1, ceil(frameCount / MAX_COARSE.toDouble()).toInt())

    /** Side length of the self-similarity matrix. */
    val coarseCount: Int = if (frameCount == 0) 0 else (frameCount - 1) / stride + 1

    val frameIntervalUs: Double =
        if (frameCount >= 2) (ts[frameCount - 1] - ts[0]).toDouble() / (frameCount - 1) else 33_333.0

    private val coarse = FloatArray(frameCount * COARSE_DIM)

    /** Squared coarse distances between coarse samples, row-major [coarseCount]². */
    private val matrix: FloatArray

    /**
     * Distance between two consecutive frames in the calmest parts of the clip: roughly the sensor
     * and compression noise, i.e. what a perfect seam costs.
     */
    private val noiseFine: Float
    private val noiseCoarse: Float

    /** Typical distance between two unrelated frames of this video (a "hard cut"). */
    private val randFine: Float
    private val randCoarse: Float

    /** Prefix sums of the motion between consecutive frames, used to favour loops with action. */
    private val motionPrefix = DoubleArray(frameCount + 1)
    private val meanMotion: Double

    init {
        buildCoarseFeatures()
        val n = coarseCount
        matrix = FloatArray(n * n)
        IntStream.range(0, n).parallel().forEach { k ->
            val fk = k * stride
            for (l in k + 1 until n) {
                val d = coarseD2(fk, l * stride)
                matrix[k * n + l] = d
                matrix[l * n + k] = d
            }
        }

        noiseFine = consecutivePercentile(NOISE_PERCENTILE) { i -> fineD2(i, i + 1) }
        noiseCoarse = consecutivePercentile(NOISE_PERCENTILE) { i -> coarseD2(i, i + 1) }
        randFine = randomPairMedian { i, j -> fineD2(i, j) }
        randCoarse = randomPairMedian { i, j -> coarseD2(i, j) }

        for (i in 0 until frameCount) {
            val step = if (i + 1 < frameCount) sqrt(coarseD2(i, i + 1).toDouble()) else 0.0
            motionPrefix[i + 1] = motionPrefix[i] + step
        }
        meanMotion = if (frameCount > 1) motionPrefix[frameCount] / (frameCount - 1) else 0.0
    }

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    fun detect(minLoopSec: Double, maxCandidates: Int = 6): Detection {
        val (periodUs, strength) = detectPeriod()
        if (frameCount < 4) {
            return Detection(listOf(candidate(0, max(1, frameCount - 1), CandidateKind.BEST)), null, 0f)
        }
        val n = coarseCount
        val minFrames = minLoopFrames(minLoopSec)
        val minLenC = max(1, ceil(minFrames / stride.toDouble()).toInt())

        val quality = FloatArray(n * n) { -1f }
        val rank = FloatArray(n * n) { Float.NEGATIVE_INFINITY }
        for (a in 0 until n) {
            for (b in a + minLenC until n) {
                val q = quality(coarseCost(a, b), noiseCoarse, randCoarse)
                quality[a * n + b] = q
                rank[a * n + b] = q + rankBonus(a, b)
            }
        }
        if (rank.none { it > Float.NEGATIVE_INFINITY }) {
            return Detection(listOf(candidate(0, frameCount - 1, CandidateKind.BEST)), periodUs, strength)
        }

        // Greedy picks with non-maximum suppression so the alternatives are really different.
        val radius = max(2, n / 14)
        val picks = ArrayList<Int>()
        repeat(maxCandidates + 2) {
            var bestI = -1
            var bestR = Float.NEGATIVE_INFINITY
            for (i in rank.indices) if (rank[i] > bestR) {
                bestR = rank[i]; bestI = i
            }
            if (bestI < 0) return@repeat
            picks += bestI
            val a = bestI / n
            val b = bestI % n
            for (x in max(0, a - radius)..min(n - 1, a + radius)) {
                for (y in max(0, b - radius)..min(n - 1, b + radius)) rank[x * n + y] = Float.NEGATIVE_INFINITY
            }
        }

        val bestQ = quality[picks.first()]
        val threshold = max(SEAMLESS, bestQ - 0.04f)
        val longest = extremeLength(quality, threshold, longest = true)
        val shortest = extremeLength(quality, threshold, longest = false)

        val result = ArrayList<LoopCandidate>()
        val tol = max(2 * stride, (DUPLICATE_SEC * 1e6 / frameIntervalUs).roundToInt())
        fun add(index: Int, kind: CandidateKind) {
            val c = refine(index / n, index % n, minFrames, kind)
            if (result.none { abs(it.startFrame - c.startFrame) <= tol && abs(it.endFrame - c.endFrame) <= tol }) {
                result += c
            }
        }
        add(picks.first(), CandidateKind.BEST)
        if (longest >= 0) add(longest, CandidateKind.LONGEST)
        if (shortest >= 0) add(shortest, CandidateKind.SHORTEST)
        for (p in picks.drop(1)) add(p, CandidateKind.ALTERNATIVE)

        val labelled = result.take(maxCandidates).map { c ->
            if (periodUs == null) c else c.copy(periodMultiple = periodMultiple(c.durationUs, periodUs))
        }
        return Detection(labelled, periodUs, strength)
    }

    /** Loop chosen by tapping the similarity map; snaps to the best seam near the tap. */
    fun snap(xFraction: Float, yFraction: Float, minLoopSec: Double): LoopCandidate {
        val n = coarseCount
        if (frameCount < 4 || n < 2) return candidate(0, max(1, frameCount - 1), CandidateKind.MANUAL)
        val minFrames = minLoopFrames(minLoopSec)
        val minLenC = max(1, ceil(minFrames / stride.toDouble()).toInt())
        val x = (xFraction.coerceIn(0f, 1f) * (n - 1)).roundToInt()
        val y = (yFraction.coerceIn(0f, 1f) * (n - 1)).roundToInt()
        val a0 = min(x, y)
        val b0 = max(x, y)
        val r = max(2, n / 40)
        var bestA = -1
        var bestB = -1
        var bestQ = -1f
        for (a in max(0, a0 - r)..min(n - 1, a0 + r)) {
            for (b in max(a + minLenC, b0 - r)..min(n - 1, b0 + r)) {
                val q = quality(coarseCost(a, b), noiseCoarse, randCoarse)
                if (q > bestQ) {
                    bestQ = q; bestA = a; bestB = b
                }
            }
        }
        if (bestA < 0) {
            bestA = min(a0, max(0, n - 1 - minLenC))
            bestB = min(n - 1, bestA + minLenC)
        }
        return refine(bestA, bestB, minFrames, CandidateKind.MANUAL)
    }

    /** Builds a candidate for an arbitrary, user-chosen frame range. */
    fun candidate(startFrame: Int, endFrame: Int, kind: CandidateKind = CandidateKind.MANUAL): LoopCandidate {
        val last = max(1, frameCount - 1)
        val e = endFrame.coerceIn(1, last)
        val s = startFrame.coerceIn(0, e - 1)
        return LoopCandidate(s, e, clipStartUs(s), clipEndUs(e), evaluate(s, e), kind)
    }

    /** Seamlessness of the loop [startFrame, endFrame), 0..1. */
    fun evaluate(startFrame: Int, endFrame: Int): Float {
        if (frameCount < 2 || endFrame - startFrame < 1 || endFrame >= frameCount) return 0f
        return quality(fineCost(startFrame, endFrame), noiseFine, randFine)
    }

    fun clipStartUs(frame: Int): Long =
        if (frame <= 0) 0L else (ts[frame - 1] + ts[frame.coerceAtMost(frameCount - 1)]) / 2

    fun clipEndUs(frame: Int): Long =
        if (frame >= frameCount) features.durationUs else (ts[frame - 1] + ts[frame]) / 2

    fun timestampUs(frame: Int): Long = ts[frame.coerceIn(0, frameCount - 1)]

    /** Nearest analysed frame for a timestamp. */
    fun frameAt(timeUs: Long): Int {
        var lo = 0
        var hi = frameCount - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ts[mid] < timeUs) lo = mid + 1 else hi = mid
        }
        return if (lo > 0 && abs(ts[lo - 1] - timeUs) < abs(ts[lo] - timeUs)) lo - 1 else lo
    }

    /** ARGB pixels (coarseCount x coarseCount) of the self-similarity map. Bright = similar. */
    fun similarityImage(): IntArray {
        val n = coarseCount
        val scale = 1f / max(1e-3f, randCoarse * 1.25f)
        return IntArray(n * n) { i ->
            val s = (1f - sqrt(matrix[i]) * scale).coerceIn(0f, 1f)
            PALETTE[(s.pow(1.8f) * 255f).roundToInt()]
        }
    }

    /** Fraction (0..1) of [frame] on the similarity map axes. */
    fun mapFraction(frame: Int): Float =
        if (coarseCount <= 1) 0f else (frame / stride.toFloat() / (coarseCount - 1)).coerceIn(0f, 1f)

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private fun rankBonus(a: Int, b: Int): Float {
        val n = coarseCount
        val lengthBonus = LENGTH_BONUS * (b - a) / max(1, n - 1).toFloat()
        // Prefer loops with something happening in them over a frozen part of the clip.
        val fa = a * stride
        val fb = b * stride
        val motion = (motionPrefix[fb] - motionPrefix[fa]) / max(1, fb - fa)
        val activity = if (meanMotion <= 1e-6) 1.0 else min(1.0, motion / meanMotion)
        return lengthBonus + ACTIVITY_BONUS * activity.toFloat()
    }

    private fun extremeLength(quality: FloatArray, threshold: Float, longest: Boolean): Int {
        val n = coarseCount
        var bestLen = if (longest) -1 else Int.MAX_VALUE
        for (i in quality.indices) {
            if (quality[i] < threshold) continue
            val len = i % n - i / n
            if (if (longest) len > bestLen else len < bestLen) bestLen = len
        }
        if (bestLen < 0 || bestLen == Int.MAX_VALUE) return -1
        var bestI = -1
        var bestQ = -1f
        for (i in quality.indices) {
            val len = i % n - i / n
            if (abs(len - bestLen) <= 1 && quality[i] > bestQ) {
                bestQ = quality[i]; bestI = i
            }
        }
        return bestI
    }

    private fun refine(a: Int, b: Int, minFrames: Int, kind: CandidateKind): LoopCandidate {
        val a0 = a * stride
        val b0 = b * stride
        val r = stride
        var bestA = a0
        var bestB = b0
        var bestCost = Float.MAX_VALUE
        for (s in max(0, a0 - r)..min(frameCount - 2, a0 + r)) {
            for (e in max(s + minFrames, b0 - r)..min(frameCount - 1, b0 + r)) {
                val c = fineCost(s, e)
                if (c < bestCost) {
                    bestCost = c; bestA = s; bestB = e
                }
            }
        }
        if (bestCost == Float.MAX_VALUE) return candidate(a0, b0, kind)
        return LoopCandidate(
            bestA, bestB, clipStartUs(bestA), clipEndUs(bestB),
            quality(bestCost, noiseFine, randFine), kind,
        )
    }

    private fun minLoopFrames(minLoopSec: Double): Int {
        val wanted = (minLoopSec * 1e6 / frameIntervalUs).roundToInt()
        val cap = max(2, (frameCount - 2) * 3 / 4)
        return wanted.coerceIn(2, cap)
    }

    /** How many whole periods of length [periodUs] fit into [durationUs] (0 if it is not a multiple). */
    fun periodMultiple(durationUs: Long, periodUs: Long): Int {
        val k = (durationUs.toDouble() / periodUs).roundToInt()
        if (k < 1) return 0
        val err = abs(durationUs - k * periodUs)
        return if (err <= max(2 * frameIntervalUs, 0.08 * periodUs)) k else 0
    }

    private fun detectPeriod(): Pair<Long?, Float> {
        val n = coarseCount
        if (n < 8) return null to 0f
        val maxLag = n * 3 / 4
        val p = FloatArray(maxLag + 2)
        for (lag in 1..maxLag) {
            var s = 0.0
            for (k in 0 until n - lag) s += sqrt(matrix[k * n + k + lag].toDouble())
            p[lag] = (s / (n - lag)).toFloat()
        }
        p[0] = p[1]
        p[maxLag + 1] = p[maxLag]
        val sm = FloatArray(maxLag + 2)
        for (lag in 1..maxLag) sm[lag] = (p[lag - 1] + p[lag] + p[lag + 1]) / 3f
        sm[0] = sm[1]
        sm[maxLag + 1] = sm[maxLag]

        val minLag = max(2, ceil(200_000.0 / (stride * frameIntervalUs)).toInt())
        if (minLag >= maxLag) return null to 0f
        var globalMin = Float.MAX_VALUE
        for (lag in minLag..maxLag) globalMin = min(globalMin, sm[lag])

        var peak = 0f
        for (lag in 1..maxLag) {
            peak = max(peak, sm[lag])
            if (lag < minLag) continue
            val isLocalMin = sm[lag] <= sm[lag - 1] && sm[lag] <= sm[lag + 1] && sm[lag] < peak
            if (isLocalMin && sm[lag] <= globalMin * 1.15f + 0.02f * randCoarse) {
                val strength = (1f - sm[lag] / max(1e-6f, peak)).coerceIn(0f, 1f)
                if (strength < 0.4f) return null to strength
                val denom = sm[lag - 1] - 2 * sm[lag] + sm[lag + 1]
                val offset = if (abs(denom) > 1e-6f) (0.5f * (sm[lag - 1] - sm[lag + 1]) / denom).coerceIn(-0.5f, 0.5f) else 0f
                val periodUs = ((lag + offset) * stride * frameIntervalUs).toLong()
                return periodUs to strength
            }
        }
        return null to 0f
    }

    private fun quality(cost: Float, noise: Float, random: Float): Float {
        // A floor keeps nearly static (or just noisy) clips from being judged on sensor noise.
        val span = max(random - noise, noise + MIN_VISIBLE_DIFFERENCE)
        val x = ((cost - noise) / span).coerceIn(0f, 1f)
        return (1f - x).pow(QUALITY_GAMMA)
    }

    private fun coarseCost(a: Int, b: Int): Float {
        val n = coarseCount
        var sum = 0f
        var weights = 0f
        for (j in -WINDOW..WINDOW) {
            val x = a + j
            val y = b + j
            if (x < 0 || y >= n) continue
            val w = WINDOW_WEIGHTS[j + WINDOW]
            sum += w * matrix[x * n + y]
            weights += w
        }
        return sqrt(sum / weights)
    }

    private fun fineCost(a: Int, b: Int): Float {
        var sum = 0f
        var weights = 0f
        for (j in -FINE_WINDOW..FINE_WINDOW) {
            val x = a + j
            val y = b + j
            if (x < 0 || y >= frameCount) continue
            val w = FINE_WINDOW_WEIGHTS[j + FINE_WINDOW]
            sum += w * fineD2(x, y)
            weights += w
        }
        return sqrt(sum / weights)
    }

    private fun fineD2(i: Int, j: Int): Float {
        val a = i * FrameFeatures.DIM
        val b = j * FrameFeatures.DIM
        var luma = 0
        for (k in 0 until FrameFeatures.LUMA) {
            val d = (fine[a + k].toInt() and 0xFF) - (fine[b + k].toInt() and 0xFF)
            luma += d * d
        }
        var chroma = 0
        for (k in FrameFeatures.LUMA until FrameFeatures.DIM) {
            val d = (fine[a + k].toInt() and 0xFF) - (fine[b + k].toInt() and 0xFF)
            chroma += d * d
        }
        return (luma + CHROMA_WEIGHT * chroma).toFloat() / FINE_NORM
    }

    private fun coarseD2(i: Int, j: Int): Float {
        val a = i * COARSE_DIM
        val b = j * COARSE_DIM
        var luma = 0f
        for (k in 0 until COARSE_LUMA) {
            val d = coarse[a + k] - coarse[b + k]
            luma += d * d
        }
        var chroma = 0f
        for (k in COARSE_LUMA until COARSE_DIM) {
            val d = coarse[a + k] - coarse[b + k]
            chroma += d * d
        }
        return (luma + CHROMA_WEIGHT * chroma) / COARSE_NORM
    }

    private fun buildCoarseFeatures() {
        val g = FrameFeatures.GRID
        val cg = FrameFeatures.CHROMA_GRID
        for (f in 0 until frameCount) {
            val src = f * FrameFeatures.DIM
            val dst = f * COARSE_DIM
            for (y in 0 until COARSE_GRID) for (x in 0 until COARSE_GRID) {
                val p = src + (2 * y) * g + 2 * x
                val sum = u(p) + u(p + 1) + u(p + g) + u(p + g + 1)
                coarse[dst + y * COARSE_GRID + x] = sum / 4f
            }
            for (plane in 0 until 2) {
                val planeSrc = src + FrameFeatures.LUMA + plane * FrameFeatures.CHROMA
                val planeDst = dst + COARSE_LUMA + plane * COARSE_CHROMA
                for (y in 0 until COARSE_CHROMA_GRID) for (x in 0 until COARSE_CHROMA_GRID) {
                    val p = planeSrc + (2 * y) * cg + 2 * x
                    val sum = u(p) + u(p + 1) + u(p + cg) + u(p + cg + 1)
                    coarse[planeDst + y * COARSE_CHROMA_GRID + x] = sum / 4f
                }
            }
        }
    }

    private fun u(index: Int): Float = (fine[index].toInt() and 0xFF).toFloat()

    private inline fun consecutivePercentile(percentile: Float, d2: (Int) -> Float): Float {
        if (frameCount < 2) return 0f
        val values = FloatArray(frameCount - 1) { sqrt(d2(it)) }
        values.sort()
        return values[((values.size - 1) * percentile).roundToInt()]
    }

    private inline fun randomPairMedian(d2: (Int, Int) -> Float): Float {
        if (frameCount < 3) return 0f
        val minGap = max(2, frameCount / 10)
        val random = Random(42)
        val values = FloatArray(RANDOM_PAIRS)
        var count = 0
        var attempts = 0
        while (count < RANDOM_PAIRS && attempts < RANDOM_PAIRS * 4) {
            attempts++
            val i = random.nextInt(frameCount)
            val j = random.nextInt(frameCount)
            if (abs(i - j) < minGap) continue
            values[count++] = sqrt(d2(i, j))
        }
        if (count == 0) return sqrt(d2(0, frameCount - 1))
        val used = values.copyOf(count)
        used.sort()
        return used[count / 2]
    }

    companion object {
        const val MAX_COARSE = 640

        /** Quality from which a loop counts as "seamless" in the UI. */
        const val SEAMLESS = 0.85f

        private const val COARSE_GRID = FrameFeatures.GRID / 2
        private const val COARSE_CHROMA_GRID = FrameFeatures.CHROMA_GRID / 2
        private const val COARSE_LUMA = COARSE_GRID * COARSE_GRID
        private const val COARSE_CHROMA = COARSE_CHROMA_GRID * COARSE_CHROMA_GRID
        private const val COARSE_DIM = COARSE_LUMA + 2 * COARSE_CHROMA

        private const val CHROMA_WEIGHT = 3
        private const val FINE_NORM = (FrameFeatures.LUMA + CHROMA_WEIGHT * 2 * FrameFeatures.CHROMA).toFloat()
        private const val COARSE_NORM = (COARSE_LUMA + CHROMA_WEIGHT * 2 * COARSE_CHROMA).toFloat()

        private const val WINDOW = 2
        private val WINDOW_WEIGHTS = floatArrayOf(1f, 2f, 4f, 2f, 1f)
        private const val FINE_WINDOW = 3
        private val FINE_WINDOW_WEIGHTS = floatArrayOf(1f, 2f, 3f, 5f, 3f, 2f, 1f)

        private const val RANDOM_PAIRS = 3000
        private const val QUALITY_GAMMA = 1.5f
        private const val MIN_VISIBLE_DIFFERENCE = 2f
        private const val NOISE_PERCENTILE = 0.1f
        private const val DUPLICATE_SEC = 0.25
        private const val LENGTH_BONUS = 0.08f
        private const val ACTIVITY_BONUS = 0.10f

        /** Neon palette: night -> violet -> magenta -> cyan -> white. */
        private val PALETTE: IntArray = run {
            val stops = floatArrayOf(0f, 0.40f, 0.65f, 0.82f, 0.93f, 1f)
            val colors = intArrayOf(0x07070F, 0x241060, 0x7B2CFF, 0xFF3D9A, 0x22E5FF, 0xF0FFFF)
            IntArray(256) { i ->
                val t = i / 255f
                var s = 0
                while (s < stops.size - 2 && t > stops[s + 1]) s++
                val f = ((t - stops[s]) / (stops[s + 1] - stops[s])).coerceIn(0f, 1f)
                val c0 = colors[s]
                val c1 = colors[s + 1]
                fun ch(shift: Int): Int {
                    val v0 = (c0 shr shift) and 0xFF
                    val v1 = (c1 shr shift) and 0xFF
                    return (v0 + (v1 - v0) * f).roundToInt()
                }
                (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }
}
