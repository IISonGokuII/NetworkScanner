package com.mycompany.synapse.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mycompany.synapse.data.Repo
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Sanfte, selbst erzeugte Klänge (keine Audiodateien nötig) und Vibrationsimpulse.
 * Richtige Antworten klingen mit wachsender Serie leicht höher – Teil des Flow-Gefühls.
 */
object Feedback {
    private const val RATE = 44_100
    private val executor = Executors.newSingleThreadExecutor()
    private var vibrator: Vibrator? = null
    private val tracks = HashMap<String, AudioTrack>()
    @Volatile private var ready = false

    // Pentatonik: C6 D6 E6 G6 A6 C7
    private val chimeNotes = listOf(1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 2093.0)

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        executor.execute {
            runCatching {
                chimeNotes.forEachIndexed { i, f -> tracks["ok$i"] = build(tone(listOf(f, f * 1.5), 0.20, 0.32, 9.0)) }
                tracks["bad"] = build(tone(listOf(220.0, 207.0), 0.26, 0.30, 10.0))
                tracks["tick"] = build(tone(listOf(880.0), 0.06, 0.22, 40.0))
                tracks["go"] = build(tone(listOf(1318.5, 1975.5), 0.18, 0.30, 12.0))
                tracks["up"] = build(arpeggio(listOf(784.0, 1046.5, 1318.5, 1568.0)))
                tracks["done"] = build(arpeggio(listOf(523.3, 659.3, 784.0, 1046.5, 1318.5)))
                ready = true
            }
        }
    }

    private fun tone(freqs: List<Double>, seconds: Double, volume: Double, decay: Double): ShortArray {
        val n = (RATE * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val attack = min(1.0, t / 0.006)
            val env = attack * exp(-decay * t)
            var s = 0.0
            freqs.forEachIndexed { k, f -> s += sin(2 * PI * f * t) / (k + 1) }
            (s * env * volume * Short.MAX_VALUE / 1.5).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun arpeggio(freqs: List<Double>): ShortArray {
        val step = (RATE * 0.075).toInt()
        val noteLen = (RATE * 0.35).toInt()
        val out = DoubleArray(step * freqs.size + noteLen)
        freqs.forEachIndexed { k, f ->
            for (i in 0 until noteLen) {
                val t = i.toDouble() / RATE
                val env = min(1.0, t / 0.006) * exp(-7.0 * t)
                out[k * step + i] += sin(2 * PI * f * t) * env * 0.22
            }
        }
        return ShortArray(out.size) { (out[it] * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun build(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private fun play(key: String) {
        if (!Repo.soundOn || !ready) return
        executor.execute {
            runCatching {
                val t = tracks[key] ?: return@runCatching
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.play()
            }
        }
    }

    private fun vibrate(effect: () -> VibrationEffect) {
        if (!Repo.hapticsOn) return
        runCatching { vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect()) }
    }

    fun correct(combo: Int) {
        play("ok${combo.coerceIn(0, chimeNotes.size - 1)}")
        vibrate { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }
    }

    fun wrong() {
        play("bad")
        vibrate { VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK) }
    }

    fun tap() = vibrate { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }
    fun tick() = play("tick")
    fun go() = play("go")

    fun levelUp() {
        play("up")
        vibrate { VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30, 60, 50), -1) }
    }

    fun complete() {
        play("done")
        vibrate { VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40, 80, 90), -1) }
    }
}
