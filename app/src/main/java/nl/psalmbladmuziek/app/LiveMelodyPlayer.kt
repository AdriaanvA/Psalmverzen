package nl.psalmbladmuziek.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.min
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
        initialEffects: Set<MelodyEffect>
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
    }

    private data class ActiveVoice(
        val voice: PipeVoice,
        val startFrame: Int
    )

    private data class FadingVoice(
        val timbre: MelodyTimbre,
        val voice: PipeVoice,
        val startFrame: Int,
        val fadeStartFrame: Int
    )

    fun play(
        events: List<PlaybackEvent>,
        timbres: List<MelodyTimbre>,
        effects: Set<MelodyEffect>,
        onStateChanged: (Boolean) -> Unit
    ) {
        if (!stopActiveSession(waitMs = 500)) return
        if (events.isEmpty() || timbres.isEmpty()) {
            setPlaying(false, onStateChanged)
            return
        }

        val session = PlaybackSession(onStateChanged, timbres, effects)
        val thread = Thread {
            val sampleRate = 44_100
            var localTrack: AudioTrack? = null

            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuffer, 4096)

                val createdTrack = AudioTrack(
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
                localTrack = createdTrack
                session.track = createdTrack
                // Registration changes reuse the same room; the shared reverb is intentionally not reset live.
                val reverb = OrganReverb.forRegistration(timbres, sampleRate)

                createdTrack.play()
                setPlayingForSession(session, true)
                for (event in events) {
                    if (session.stopRequested) break
                    writeEvent(createdTrack, event, sampleRate, reverb, session)
                }
                if (!session.stopRequested) {
                    writeReverbRelease(createdTrack, sampleRate, reverb, session)
                }
            } finally {
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
        session: PlaybackSession
    ) {
        val totalFrames = maxOf(1, (event.durationSec * sampleRate).toInt())
        val chunk = 1024
        val frequencyHz = event.frequencyHz
        val voicesByTimbre = LinkedHashMap<MelodyTimbre, ActiveVoice>()
        val fadingVoices = ArrayList<FadingVoice>()
        var activeTimbres = emptyList<MelodyTimbre>()
        var tremulantPhase = 0.0

        fun syncVoices(currentFrame: Int) {
            val requestedTimbres = session.timbres
            val requestedSet = requestedTimbres.toSet()
            val removedTimbres = voicesByTimbre.keys.filter { it !in requestedSet }
            for (timbre in removedTimbres) {
                val removedVoice = voicesByTimbre.remove(timbre)
                if (removedVoice != null) {
                    fadingVoices += FadingVoice(timbre, removedVoice.voice, removedVoice.startFrame, currentFrame)
                }
            }
            fadingVoices.removeAll { fadingVoice ->
                fadingVoice.timbre in requestedSet || currentFrame - fadingVoice.fadeStartFrame >= FADE_OUT_FRAMES
            }
            for (timbre in requestedTimbres) {
                if (voicesByTimbre[timbre] == null) {
                    val voice = PipeVoice.forTimbre(timbre).also { voice ->
                        if (frequencyHz != null) voice.startNote(frequencyHz)
                    }
                    voicesByTimbre[timbre] = ActiveVoice(voice, currentFrame)
                }
            }
            activeTimbres = requestedTimbres
        }

        syncVoices(0)

        var frameIndex = 0
        while (frameIndex < totalFrames && !session.stopRequested) {
            syncVoices(frameIndex)
            val count = min(chunk, totalFrames - frameIndex)
            val pcm = ShortArray(count)
            for (i in 0 until count) {
                val absoluteIndex = frameIndex + i
                val drySample = if (frequencyHz == null || (activeTimbres.isEmpty() && fadingVoices.isEmpty())) {
                    0.0
                } else {
                    var mixed = 0.0
                    for (timbre in activeTimbres) {
                        val activeVoice = voicesByTimbre[timbre]
                        if (activeVoice != null) {
                            mixed += activeVoice.voice.sample(
                                sampleRate,
                                absoluteIndex - activeVoice.startFrame,
                                totalFrames - activeVoice.startFrame
                            )
                        }
                    }
                    val soundingCount = activeTimbres.size + fadingVoices.size
                    val mixGain = 0.82 / sqrt(maxOf(1, soundingCount).toDouble())
                    // Tremulant currently models wind/volume undulation, not pitch modulation.
                    val tremulant = if (MelodyEffect.TREMULANT in session.effects) 1.0 + 0.08 * sin(tremulantPhase) else 1.0
                    for (fadingVoice in fadingVoices) {
                        val fadeFrame = absoluteIndex - fadingVoice.fadeStartFrame
                        if (fadeFrame in 0 until FADE_OUT_FRAMES) {
                            val fade = 1.0 - fadeFrame.toDouble() / FADE_OUT_FRAMES
                            mixed += fadingVoice.voice.sample(
                                sampleRate,
                                absoluteIndex - fadingVoice.startFrame,
                                totalFrames - fadingVoice.startFrame
                            ) * fade * fade
                        }
                    }
                    mixed * mixGain * tremulant
                }
                tremulantPhase += TWO_PI * TREMULANT_HZ / sampleRate
                if (tremulantPhase >= TWO_PI) tremulantPhase -= TWO_PI
                val sample = reverb.process(drySample)
                pcm[i] = (softLimit(sample) * Short.MAX_VALUE).toInt().toShort()
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
            val pcm = ShortArray(count)
            for (i in 0 until count) {
                val sample = reverb.process(0.0)
                pcm[i] = (softLimit(sample) * Short.MAX_VALUE).toInt().toShort()
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

        private fun softLimit(sample: Double): Double = tanh(sample * 1.15)
    }
}
