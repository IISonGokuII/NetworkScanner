package com.loopforge.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class LoopEngineTest {

    private val frameUs = 33_333L

    /** Renders a bright blob moving along [path] on a textured background, plus sensor noise. */
    private fun synthetic(frames: Int, noise: Int = 3, path: (Int) -> Pair<Float, Float>): FrameFeatures {
        val random = Random(7)
        val builder = FrameFeatureBuilder()
        val g = FrameFeatures.GRID
        for (f in 0 until frames) {
            val slot = builder.next(f * frameUs)
            val (cx, cy) = path(f)
            for (y in 0 until g) for (x in 0 until g) {
                val bg = 60 + ((x * 7 + y * 13) % 40)
                val dx = x - cx
                val dy = y - cy
                val blob = if (dx * dx + dy * dy < 16f) 140 else 0
                val v = (bg + blob + random.nextInt(2 * noise + 1) - noise).coerceIn(0, 255)
                slot.array[slot.offset + y * g + x] = v.toByte()
            }
            for (k in 0 until 2 * FrameFeatures.CHROMA) {
                slot.array[slot.offset + FrameFeatures.LUMA + k] = (128 + random.nextInt(3) - 1).toByte()
            }
            builder.commit()
        }
        return builder.build(frames * frameUs)
    }

    @Test
    fun findsSeamlessLoopInPendulum() {
        val period = 37
        val features = synthetic(300) { f ->
            val phase = 2 * PI * f / period
            Pair(16f + 10f * sin(phase).toFloat(), 16f)
        }
        val engine = LoopEngine(features)
        val detection = engine.detect(minLoopSec = 0.5)
        val best = detection.candidates.first()
        val length = best.endFrame - best.startFrame
        val periods = length / period.toDouble()
        println("pendulum best=$best len=$length periods=$periods period=${detection.periodUs} strength=${detection.periodStrength}")
        assertTrue("loop length should be a multiple of the period, was $length", abs(periods - periods.roundToInt()) < 0.06)
        assertTrue("quality ${best.quality}", best.quality > 0.9f)
        assertNotNull(detection.periodUs)
        assertEquals(period * frameUs.toDouble(), detection.periodUs!!.toDouble(), frameUs * 1.5)
        detection.candidates.forEach { println("  $it") }
    }

    @Test
    fun directionMattersForPendulum() {
        // Frame 0 and frame 18 of a 36-frame pendulum show the blob at the same place, but moving
        // in opposite directions; a half-period loop must not be rated seamless.
        val period = 36
        val features = synthetic(200) { f ->
            Pair(16f + 10f * sin(2 * PI * f / period).toFloat(), 16f)
        }
        val engine = LoopEngine(features)
        val half = engine.evaluate(9, 9 + period / 2)
        val full = engine.evaluate(9, 9 + period)
        println("half=$half full=$full")
        assertTrue(full > 0.9f)
        assertTrue(half < full - 0.2f)
    }

    @Test
    fun linearPanIsNotSeamless() {
        val features = synthetic(240) { f -> Pair(2f + f * 0.12f, 16f) }
        val engine = LoopEngine(features)
        val detection = engine.detect(minLoopSec = 1.0)
        val best = detection.candidates.first()
        println("pan best=$best period=${detection.periodUs}")
        assertTrue("quality ${best.quality}", best.quality < 0.6f)
        assertNull(detection.periodUs)
    }

    @Test
    fun staticNoisyVideoLoopsEverywhere() {
        val features = synthetic(150, noise = 6) { Pair(16f, 16f) }
        val engine = LoopEngine(features)
        val best = engine.detect(minLoopSec = 1.0).candidates.first()
        println("static best=$best")
        assertTrue(best.quality > 0.9f)
        assertTrue(engine.evaluate(10, 100) > 0.9f)
    }

    @Test
    fun manualSnapLandsOnGoodSeam() {
        val period = 40
        val features = synthetic(320) { f ->
            Pair(16f + 9f * sin(2 * PI * f / period).toFloat(), 16f + 6f * sin(4 * PI * f / period).toFloat())
        }
        val engine = LoopEngine(features)
        // Tap roughly where start=50, end=50+2*period would be.
        val n = engine.frameCount - 1f
        val snapped = engine.snap(52 / n, (52 + 2 * period + 3) / n, 0.5)
        println("snapped=$snapped")
        val len = snapped.endFrame - snapped.startFrame
        assertTrue(abs(len - 2 * period) <= 1)
        assertTrue(snapped.quality > 0.9f)
    }

    @Test
    fun clipTimesUseFrameMidpoints() {
        val features = synthetic(60) { Pair(16f, 16f) }
        val engine = LoopEngine(features)
        assertEquals(0L, engine.clipStartUs(0))
        assertEquals((9 * frameUs + 10 * frameUs) / 2, engine.clipStartUs(10))
        assertEquals((29 * frameUs + 30 * frameUs) / 2, engine.clipEndUs(30))
        assertEquals(30, engine.frameAt(30 * frameUs + 1000))
    }

    @Test
    fun longVideoUsesStrideAndStillRefinesExactly() {
        val period = 53
        val features = synthetic(2000) { f ->
            Pair(16f + 10f * sin(2 * PI * f / period).toFloat(), 16f)
        }
        val engine = LoopEngine(features)
        assertTrue(engine.stride > 1)
        val detection = engine.detect(minLoopSec = 1.0)
        val best = detection.candidates.first()
        val len = best.endFrame - best.startFrame
        val periods = len / period.toDouble()
        println("long best=$best periods=$periods stride=${engine.stride}")
        assertTrue(abs(periods - periods.roundToInt()) < 0.03)
        assertTrue(best.quality > 0.9f)
    }

    /** Runs the engine on a real encoded video when ffmpeg is installed (skipped otherwise). */
    @Test
    fun realVideoThroughFfmpeg() {
        val ffmpeg = listOf("/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg").map(::File).firstOrNull { it.canExecute() }
        assumeTrue(ffmpeg != null)
        val dir = File(System.getProperty("java.io.tmpdir"), "loopforge-test").apply { mkdirs() }
        val video = File(dir, "swing.mp4")
        // A ball on a swing (period 1.6 s) in front of a slowly flickering, noisy background.
        run(
            ffmpeg!!.path, "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "smptebars=size=320x240:rate=30:duration=8",
            "-f", "lavfi", "-i", "color=c=yellow:size=40x40:rate=30",
            "-filter_complex", "[0:v]noise=alls=12:allf=t[bg];" +
                "[bg][1:v]overlay=x='140+90*sin(2*PI*t/1.6)':y='100+30*cos(4*PI*t/1.6)':shortest=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", video.path,
        )
        val features = decodeWithFfmpeg(ffmpeg.path, video)
        println("decoded ${features.count} frames")
        val engine = LoopEngine(features)
        val detection = engine.detect(minLoopSec = 1.0)
        detection.candidates.forEach { println("  real: $it (${it.durationUs / 1000} ms)") }
        println("  period=${detection.periodUs} strength=${detection.periodStrength}")
        assertEquals(1_600_000.0, detection.periodUs!!.toDouble(), 60_000.0)
        val best = detection.candidates.first()
        val periods = best.durationUs / 1_600_000.0
        assertTrue(abs(periods - periods.roundToInt()) < 0.05)
        assertTrue("quality ${best.quality}", best.quality > 0.85f)
        for (c in detection.candidates.filter { it.quality > LoopEngine.SEAMLESS }) {
            assertTrue("seamless loops must span whole periods: $c", c.periodMultiple > 0)
        }
    }

    private fun decodeWithFfmpeg(ffmpeg: String, video: File): FrameFeatures {
        val g = FrameFeatures.GRID
        val process = ProcessBuilder(
            ffmpeg, "-loglevel", "error", "-i", video.path,
            "-vf", "scale=$g:$g:flags=area", "-pix_fmt", "yuv444p", "-f", "rawvideo", "-",
        ).redirectErrorStream(false).start()
        val bytes = process.inputStream.readBytes()
        process.waitFor()
        val frameSize = g * g * 3
        val frames = bytes.size / frameSize
        val builder = FrameFeatureBuilder()
        val cg = FrameFeatures.CHROMA_GRID
        val block = g / cg
        for (f in 0 until frames) {
            val base = f * frameSize
            val slot = builder.next(f * frameUs)
            System.arraycopy(bytes, base, slot.array, slot.offset, g * g)
            for (plane in 0 until 2) {
                val p = base + g * g * (plane + 1)
                for (y in 0 until cg) for (x in 0 until cg) {
                    var sum = 0
                    for (yy in 0 until block) for (xx in 0 until block) {
                        sum += bytes[p + (y * block + yy) * g + x * block + xx].toInt() and 0xFF
                    }
                    slot.array[slot.offset + FrameFeatures.LUMA + plane * FrameFeatures.CHROMA + y * cg + x] =
                        (sum / (block * block)).toByte()
                }
            }
            builder.commit()
        }
        return builder.build(frames * frameUs)
    }

    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).inheritIO().start()
        check(p.waitFor() == 0) { "command failed: ${cmd.joinToString(" ")}" }
    }
}
