package nl.psalmbladmuziek.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class MelodyTimbre {
    BOURDON16,
    PRESTANT8,
    HOLPIJP8,
    ROERFLUIT8,
    GEDEKT8,
    OCTAAF4,
    FLUIT4,
    QUINTFLUIT,
    FLUIT_SOLO,
    ORCHESTRAL_STRINGS
}

/** Live playback transport for monophonic melodies. PipeVoice owns the organ synthesis. */
class LiveMelodyPlayer {
    private val playbackLock = Any()

    @Volatile
    var isPlaying: Boolean = false
        private set

    private var playbackThread: Thread? = null
    private var activeSession: PlaybackSession? = null

    private class PlaybackSession(
        val onStateChanged: (Boolean) -> Unit,
        initialTimbres: List<MelodyTimbre>,
        initialEffects: Set<MelodyEffect>,
        initialTempoPercent: Int
    ) {
        @Volatile
        var stopRequested: Boolean = false

        @Volatile
        var thread: Thread? = null

        @Volatile
        var track: AudioTrack? = null

        @Volatile
        var timbres: List<MelodyTimbre> = initialTimbres

        @Volatile
        var effects: Set<MelodyEffect> = initialEffects

        @Volatile
        var tempoPercent: Int = initialTempoPercent
    }

    private data class VoiceKey(
        val timbre: MelodyTimbre,
        val frequencyHz: Double
    )

    private data class ActiveVoice(
        val key: VoiceKey,
        val voice: PipeVoice,
        val startFrame: Int,
        val leftPanGain: Double,
        val rightPanGain: Double
    )

    private data class FadingVoice(
        val activeVoice: ActiveVoice,
        val fadeStartFrame: Int
    )

    private class SynthesisState {
        val activeVoices = LinkedHashMap<VoiceKey, ActiveVoice>()
        val fadingVoices = ArrayList<FadingVoice>()
        var tremulantPhase = 0.0
        var frameIndex = 0
    }

    fun play(
        events: List<PlaybackEvent>,
        timbres: List<MelodyTimbre>,
        effects: Set<MelodyEffect>,
        tempoPercent: Int,
        onEventChanged: (String?) -> Unit,
        onStateChanged: (Boolean) -> Unit
    ) {
        if (!stopActiveSession(waitMs = 500)) return
        if (events.isEmpty() || timbres.isEmpty()) {
            setPlaying(false, onStateChanged)
            return
        }

        val session = PlaybackSession(onStateChanged, timbres, effects, tempoPercent)
        val thread = Thread {
            val sampleRate = 44_100
            var localTrack: AudioTrack? = null

            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuffer, 8192)

                val createdTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                localTrack = createdTrack
                session.track = createdTrack
                // Registration changes reuse the same room; the shared reverb is intentionally not reset live.
                val reverb = OrganReverb.forRegistration(timbres, sampleRate)

                createdTrack.play()
                setPlayingForSession(session, true)
                val synthesisState = SynthesisState()
                for (event in events) {
                    if (session.stopRequested) break
                    onEventChanged(event.playbackId)
                    writeEvent(createdTrack, event, sampleRate, reverb, session, synthesisState)
                }
                if (!session.stopRequested) {
                    onEventChanged(null)
                    writeEvent(
                        createdTrack,
                        PlaybackEvent(frequenciesHz = emptyList(), durationBeats = FINAL_RELEASE_BEATS),
                        sampleRate,
                        reverb,
                        session,
                        synthesisState
                    )
                    writeReverbRelease(createdTrack, sampleRate, reverb, session)
                }
            } finally {
                onEventChanged(null)
                try {
                    localTrack?.pause()
                    localTrack?.flush()
                    localTrack?.stop()
                } catch (_: Exception) {
                }
                try {
                    localTrack?.release()
                } catch (_: Exception) {
                }
                session.track = null
                val wasActive = synchronized(playbackLock) {
                    if (activeSession === session) {
                        activeSession = null
                        playbackThread = null
                        true
                    } else {
                        false
                    }
                }
                if (wasActive) {
                    setPlaying(false, session.onStateChanged)
                }
            }
        }.apply {
            name = "LiveMelodyPlayer"
        }

        session.thread = thread
        synchronized(playbackLock) {
            activeSession = session
            playbackThread = thread
        }
        thread.start()
    }

    fun updateRegistration(timbres: List<MelodyTimbre>, effects: Set<MelodyEffect>) {
        synchronized(playbackLock) { activeSession }?.let { session ->
            session.timbres = timbres
            session.effects = effects
        }
    }

    fun updateTempo(tempoPercent: Int) {
        synchronized(playbackLock) { activeSession }?.tempoPercent = tempoPercent.coerceIn(
            AppSettings.MIN_PLAYBACK_TEMPO,
            AppSettings.MAX_PLAYBACK_TEMPO
        )
    }

    fun stop(onStateChanged: (Boolean) -> Unit = {}) {
        if (stopActiveSession(waitMs = 500)) {
            setPlaying(false, onStateChanged)
        }
    }

    private fun setPlaying(value: Boolean, onStateChanged: (Boolean) -> Unit) {
        if (isPlaying == value) return
        isPlaying = value
        onStateChanged(value)
    }

    private fun setPlayingForSession(session: PlaybackSession, value: Boolean) {
        val isCurrent = synchronized(playbackLock) { activeSession === session }
        if (isCurrent) {
            setPlaying(value, session.onStateChanged)
        }
    }

    private fun stopActiveSession(waitMs: Long): Boolean {
        val session = synchronized(playbackLock) { activeSession }
        if (session == null) return true

        session.stopRequested = true
        try {
            session.track?.pause()
            session.track?.flush()
            session.track?.stop()
        } catch (_: Exception) {
        }
        val thread = session.thread
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        return thread == null || !thread.isAlive
    }

    private fun writeEvent(
        track: AudioTrack,
        event: PlaybackEvent,
        sampleRate: Int,
        reverb: OrganReverb,
        session: PlaybackSession,
        state: SynthesisState
    ) {
        val chunk = 1024

        fun syncVoices(currentFrame: Int, retrigger: Boolean) {
            val requestedKeys = session.timbres.flatMap { timbre ->
                event.frequenciesHz.map { frequencyHz -> VoiceKey(timbre, frequencyHz) }
            }
            val requestedSet = requestedKeys.toSet()
            val removedKeys = if (retrigger) {
                state.activeVoices.keys.toList()
            } else {
                state.activeVoices.keys.filter { it !in requestedSet }
            }
            for (key in removedKeys) {
                val removedVoice = state.activeVoices.remove(key)
                if (removedVoice != null) {
                    state.fadingVoices += FadingVoice(removedVoice, currentFrame)
                }
            }
            state.fadingVoices.removeAll { fadingVoice ->
                currentFrame - fadingVoice.fadeStartFrame >= FADE_OUT_FRAMES
            }
            for (key in requestedKeys) {
                if (state.activeVoices[key] == null) {
                    val (leftPanGain, rightPanGain) = stereoPanGains(key.frequencyHz)
                    val voice = PipeVoice.forTimbre(key.timbre).also { it.startNote(key.frequencyHz) }
                    state.activeVoices[key] = ActiveVoice(
                        key = key,
                        voice = voice,
                        startFrame = currentFrame,
                        leftPanGain = leftPanGain,
                        rightPanGain = rightPanGain
                    )
                }
            }
        }

        syncVoices(state.frameIndex, retrigger = true)

        var beatsPlayed = 0.0
        while (beatsPlayed < event.durationBeats && !session.stopRequested) {
            syncVoices(state.frameIndex, retrigger = false)
            val beatsPerFrame = MelodyPlaybackModel.beatsPerSecond(session.tempoPercent) / sampleRate
            val remainingBeats = event.durationBeats - beatsPlayed
            val remainingFrames = maxOf(1, ceil(remainingBeats / beatsPerFrame).toInt())
            val count = min(chunk, remainingFrames)
            val pcm = ShortArray(count * 2)
            for (i in 0 until count) {
                val absoluteIndex = state.frameIndex + i
                var mixedMono = 0.0
                var mixedLeft = 0.0
                var mixedRight = 0.0
                for (activeVoice in state.activeVoices.values) {
                    val voiceSample = activeVoice.voice.sample(
                        sampleRate,
                        absoluteIndex - activeVoice.startFrame,
                        Int.MAX_VALUE
                    )
                    mixedMono += voiceSample
                    mixedLeft += voiceSample * activeVoice.leftPanGain
                    mixedRight += voiceSample * activeVoice.rightPanGain
                }
                for (fadingVoice in state.fadingVoices) {
                    val fadeFrame = absoluteIndex - fadingVoice.fadeStartFrame
                    if (fadeFrame in 0 until FADE_OUT_FRAMES) {
                        val fade = 1.0 - fadeFrame.toDouble() / FADE_OUT_FRAMES
                        val activeVoice = fadingVoice.activeVoice
                        val voiceSample = activeVoice.voice.sample(
                            sampleRate,
                            absoluteIndex - activeVoice.startFrame,
                            Int.MAX_VALUE
                        ) * fade * fade
                        mixedMono += voiceSample
                        mixedLeft += voiceSample * activeVoice.leftPanGain
                        mixedRight += voiceSample * activeVoice.rightPanGain
                    }
                }
                val soundingCount = state.activeVoices.size + state.fadingVoices.size
                val mixGain = 0.82 / sqrt(maxOf(1, soundingCount).toDouble())
                val tremulant = if (MelodyEffect.TREMULANT in session.effects) {
                    1.0 + 0.08 * sin(state.tremulantPhase)
                } else {
                    1.0
                }
                mixedMono *= mixGain * tremulant
                mixedLeft *= mixGain * tremulant
                mixedRight *= mixGain * tremulant
                state.tremulantPhase += TWO_PI * TREMULANT_HZ / sampleRate
                if (state.tremulantPhase >= TWO_PI) state.tremulantPhase -= TWO_PI
                val wetSample = reverb.processWet(mixedMono)
                val left = reverb.applyDryLevel(mixedLeft) + wetSample * CENTER_GAIN
                val right = reverb.applyDryLevel(mixedRight) + wetSample * CENTER_GAIN
                pcm[i * 2] = (softLimit(left) * Short.MAX_VALUE).toInt().toShort()
                pcm[i * 2 + 1] = (softLimit(right) * Short.MAX_VALUE).toInt().toShort()
            }
            val written = try {
                track.write(pcm, 0, pcm.size)
            } catch (_: Exception) {
                session.stopRequested = true
                break
            }
            if (written < 0) {
                session.stopRequested = true
                break
            }
            state.frameIndex += count
            beatsPlayed += count * beatsPerFrame
        }
    }

    private fun writeReverbRelease(
        track: AudioTrack,
        sampleRate: Int,
        reverb: OrganReverb,
        session: PlaybackSession
    ) {
        val totalFrames = (sampleRate * REVERB_RELEASE_SECONDS).toInt()
        val chunk = 1024
        var frameIndex = 0
        while (frameIndex < totalFrames && !session.stopRequested) {
            val count = min(chunk, totalFrames - frameIndex)
            val pcm = ShortArray(count * 2)
            for (i in 0 until count) {
                val wetSample = reverb.processWet(0.0) * CENTER_GAIN
                val sample = (softLimit(wetSample) * Short.MAX_VALUE).toInt().toShort()
                pcm[i * 2] = sample
                pcm[i * 2 + 1] = sample
            }
            val written = try {
                track.write(pcm, 0, pcm.size)
            } catch (_: Exception) {
                session.stopRequested = true
                break
            }
            if (written < 0) {
                session.stopRequested = true
                break
            }
            frameIndex += count
        }
    }

    private companion object {
        private const val TWO_PI = 6.283185307179586
        private const val TREMULANT_HZ = 5.2
        private const val REVERB_RELEASE_SECONDS = 0.85
        private const val FADE_OUT_FRAMES = 768
        private const val FINAL_RELEASE_BEATS = 0.12
        private const val WINDCHEST_PAN = 0.18
        private const val CENTER_GAIN = 0.7071067811865476
        private const val LN_TWO = 0.6931471805599453

        private fun softLimit(sample: Double): Double = tanh(sample * 1.15)

        private fun stereoPanGains(frequencyHz: Double?): Pair<Double, Double> {
            if (frequencyHz == null || frequencyHz <= 0.0) return CENTER_GAIN to CENTER_GAIN
            val midi = (69.0 + 12.0 * ln(frequencyHz / 440.0) / LN_TWO).roundToInt()
            val pan = if (midi and 1 == 0) -WINDCHEST_PAN else WINDCHEST_PAN
            val angle = (pan + 1.0) * Math.PI / 4.0
            return cos(angle) to sin(angle)
        }
    }
}
