package nl.psalmbladmuziek.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

enum class MelodyTimbre {
    PRESTANT,
    HOLPIJP,
    FLUIT,
    STRINGS,
    VOL16
}

/**
 * Simpele live-synth voor monofone melodieën. Geen assets nodig:
 * de orgelachtige klank wordt runtime uit harmonischen opgebouwd.
 */
class LiveMelodyPlayer {
    @Volatile
    private var shouldStop = false

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var playbackThread: Thread? = null
    private var track: AudioTrack? = null

    fun play(events: List<PlaybackEvent>, timbre: MelodyTimbre, onStateChanged: (Boolean) -> Unit) {
        stop(onStateChanged)
        if (events.isEmpty()) return

        shouldStop = false
        playbackThread = Thread {
            val sampleRate = 44_100
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, 4096)

            val localTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            track = localTrack
            var phase = 0.0

            try {
                localTrack.play()
                setPlaying(true, onStateChanged)
                for (event in events) {
                    if (shouldStop) break
                    phase = writeEvent(localTrack, event, sampleRate, phase, timbre)
                }
            } finally {
                try {
                    localTrack.pause()
                    localTrack.flush()
                    localTrack.stop()
                } catch (_: Exception) {
                }
                try {
                    localTrack.release()
                } catch (_: Exception) {
                }
                track = null
                setPlaying(false, onStateChanged)
            }
        }.apply {
            name = "LiveMelodyPlayer"
            start()
        }
    }

    fun stop(onStateChanged: (Boolean) -> Unit = {}) {
        shouldStop = true
        val localTrack = track
        if (localTrack != null) {
            try {
                localTrack.pause()
                localTrack.flush()
                localTrack.stop()
            } catch (_: Exception) {
            }
            try {
                localTrack.release()
            } catch (_: Exception) {
            }
            track = null
        }
        playbackThread?.join(100)
        playbackThread = null
        setPlaying(false, onStateChanged)
    }

    private fun setPlaying(value: Boolean, onStateChanged: (Boolean) -> Unit) {
        if (isPlaying == value) return
        isPlaying = value
        onStateChanged(value)
    }

    private fun writeEvent(
        track: AudioTrack,
        event: PlaybackEvent,
        sampleRate: Int,
        startPhase: Double,
        timbre: MelodyTimbre
    ): Double {
        val totalFrames = maxOf(1, (event.durationSec * sampleRate).toInt())
        val chunk = 1024
        val attackFrames = min(totalFrames / 2, (sampleRate * 0.01).toInt().coerceAtLeast(1))
        val releaseFrames = min(totalFrames / 2, (sampleRate * 0.04).toInt().coerceAtLeast(1))

        var phase = startPhase
        var frameIndex = 0
        while (frameIndex < totalFrames && !shouldStop) {
            val count = min(chunk, totalFrames - frameIndex)
            val pcm = ShortArray(count)
            for (i in 0 until count) {
                val absoluteIndex = frameIndex + i
                val envAttack = min(1.0, absoluteIndex.toDouble() / attackFrames)
                val framesLeft = totalFrames - absoluteIndex
                val envRelease = min(1.0, framesLeft.toDouble() / releaseFrames)
                val envelope = min(envAttack, envRelease)

                val sample = if (event.frequencyHz == null) {
                    0.0
                } else {
                    val freq = event.frequencyHz
                    val p = phase
                    val timbreSample = synthSampleForTimbre(timbre, p, freq)

                    phase += 2.0 * PI * freq / sampleRate
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                    timbreSample * envelope
                }
                pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            track.write(pcm, 0, pcm.size)
            frameIndex += count
        }
        return phase
    }

    private fun synthSampleForTimbre(timbre: MelodyTimbre, phase: Double, freq: Double): Double {
        val highTame = (1.0 - ((freq - 440.0) / 650.0)).coerceIn(0.35, 1.0)
        return when (timbre) {
            MelodyTimbre.PRESTANT -> {
                val raw = (
                    1.00 * sin(phase) +
                        0.72 * sin(2.0 * phase) +
                        (0.34 * highTame) * sin(3.0 * phase) +
                        (0.26 * highTame) * sin(4.0 * phase) +
                        (0.10 * highTame * highTame) * sin(5.0 * phase)
                    ) / (1.72 + (0.60 * highTame) + (0.10 * highTame * highTame))
                raw * 0.74
            }

            MelodyTimbre.HOLPIJP -> {
                val raw = (
                    1.00 * sin(phase) +
                        0.24 * sin(2.0 * phase) +
                        0.10 * sin(3.0 * phase)
                    ) / 1.34
                raw * 0.80
            }

            MelodyTimbre.FLUIT -> {
                val breath = 0.04 * sin(7.0 * phase)
                val raw = (
                    1.00 * sin(phase) +
                        (0.20 * highTame) * sin(2.0 * phase) +
                        (0.10 * highTame) * sin(3.0 * phase)
                    ) / (1.00 + (0.30 * highTame))
                (raw + breath) * 0.76
            }

            MelodyTimbre.STRINGS -> {
                val shimmer = 0.015 * sin(phase * 1.005)
                val raw = (
                    1.00 * sin(phase + shimmer) +
                        0.46 * sin(2.0 * phase) +
                        0.28 * sin(3.0 * phase) +
                        0.16 * sin(4.0 * phase)
                    ) / 1.90
                raw * 0.72
            }

            MelodyTimbre.VOL16 -> {
                val p16 = phase * 0.5
                val raw = (
                    0.95 * sin(p16) +
                        1.00 * sin(phase) +
                        0.82 * sin(2.0 * phase) +
                        (0.55 * highTame) * sin(3.0 * phase) +
                        (0.38 * highTame) * sin(4.0 * phase) +
                        (0.22 * highTame * highTame) * sin(5.0 * phase)
                    ) / (2.77 + (0.93 * highTame) + (0.22 * highTame * highTame))
                raw * 0.98
            }
        }
    }
}
