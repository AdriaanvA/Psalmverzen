package nl.psalmbladmuziek.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PipeSpectrum(
    val harmonics: DoubleArray,
    val attackBrightness: Double,
    val attackTime: Double,
    val breathLevel: Double,
    val breathSustain: Double = 0.0,
    val pitchDriftCents: Double,
    val amplitudeDrift: Double,
    val frequencyMultiplier: Double = 1.0,
    val tuningOffsetCents: Double = 0.0,
    val phaseOffset: Double = 0.0,
    val frequencyRatios: DoubleArray = DoubleArray(harmonics.size) { (it + 1).toDouble() },
    val voiceDetuneCents: DoubleArray = doubleArrayOf(0.0),
    val voiceGains: DoubleArray = doubleArrayOf(1.0),
    val gain: Double = 0.74,
    val releaseTime: Double = 0.05,
    val pitchDriftHz: Double = 4.1,
    val amplitudeDriftHz: Double = 2.3,
    val bloomDepth: Double = 0.0,
    val bloomTime: Double = 0.18,
    val breathBrightness: Double = 0.0,
    val vibratoOnsetTime: Double = 0.0
)

/**
 * Runtime model for one monophonic organ pipe voice.
 */
class PipeVoice private constructor(
    private val spectrum: PipeSpectrum
) {
    private val oscillatorSines = Array(spectrum.voiceDetuneCents.size) { DoubleArray(spectrum.harmonics.size) }
    private val oscillatorCosines = Array(spectrum.voiceDetuneCents.size) { DoubleArray(spectrum.harmonics.size) { 1.0 } }
    private val phaseDeltaSines = Array(spectrum.voiceDetuneCents.size) { DoubleArray(spectrum.harmonics.size) }
    private val phaseDeltaCosines = Array(spectrum.voiceDetuneCents.size) { DoubleArray(spectrum.harmonics.size) { 1.0 } }
    private val harmonicAmplitudes = DoubleArray(spectrum.harmonics.size)
    private val frequencyRatios = spectrum.frequencyRatios
    private val voiceFrequencyRatios = DoubleArray(spectrum.voiceDetuneCents.size)
    private val voiceGains = DoubleArray(spectrum.voiceDetuneCents.size)
    private val harmonicSum = spectrum.harmonics.sum().coerceAtLeast(0.001)
    private val voiceGainSum = spectrum.voiceGains.sum().coerceAtLeast(0.001)
    private val highestFrequencyRatio = frequencyRatios.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    private val attackShapeTime = (spectrum.attackTime * 0.65).coerceAtLeast(0.004)
    private var fundamentalFrequencyHz = 440.0
    private var pitchLfoPhase = 0.61
    private var amplitudeLfoPhase = 2.17
    private var breathState = 0.0
    private var noiseSeed = 0x13579BDF.toInt()
    private var framesUntilDeltaRefresh = 0
    private var framesUntilNormalize = NORMALIZE_INTERVAL_FRAMES
    private var currentVibratoDepth = 1.0

    init {
        for (index in voiceFrequencyRatios.indices) {
            voiceFrequencyRatios[index] = 2.0.pow((spectrum.voiceDetuneCents[index] + spectrum.tuningOffsetCents) / 1200.0)
            voiceGains[index] = spectrum.voiceGains[index] / voiceGainSum
        }
        for (voiceIndex in oscillatorSines.indices) {
            for (index in oscillatorSines[voiceIndex].indices) {
                val phase = spectrum.phaseOffset * (index + 1)
                oscillatorSines[voiceIndex][index] = sin(phase)
                oscillatorCosines[voiceIndex][index] = cos(phase)
            }
        }
    }

    fun startNote(melodyFrequencyHz: Double) {
        fundamentalFrequencyHz = melodyFrequencyHz * spectrum.frequencyMultiplier
        val highTame = (1.0 - ((fundamentalFrequencyHz - 440.0) / 1300.0)).coerceIn(0.48, 1.0)
        for (index in harmonicAmplitudes.indices) {
            val frequencyRatio = frequencyRatios[index]
            val highOrderTame = if (frequencyRatio <= 2.0) {
                1.0
            } else {
                highTame.pow((frequencyRatio - 2.0) * 0.42)
            }
            harmonicAmplitudes[index] = spectrum.harmonics[index] * highOrderTame / harmonicSum
        }
        framesUntilDeltaRefresh = 0
    }

    fun sample(sampleRate: Int, frameInNote: Int, totalFrames: Int): Double {
        val time = frameInNote.toDouble() / sampleRate
        val attackEnvelope = (1.0 - exp(-time / attackShapeTime)).coerceIn(0.0, 1.0)
        val releaseEnvelope = releaseEnvelope(frameInNote, totalFrames, sampleRate)
        val noteEnvelope = min(attackEnvelope, releaseEnvelope)

        val attackTransient = exp(-time / spectrum.attackTime.coerceAtLeast(0.004))
        // Vibrato zwelt na de inzet aan, zodat de toonkern zuiver begint en pas daarna gaat 'zingen'.
        currentVibratoDepth = if (spectrum.vibratoOnsetTime > 0.0) {
            (time / spectrum.vibratoOnsetTime).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        if (framesUntilDeltaRefresh <= 0) {
            updatePhaseDeltas(sampleRate)
            framesUntilDeltaRefresh = PHASE_DELTA_REFRESH_FRAMES
        }

        var value = 0.0
        for (voiceIndex in oscillatorSines.indices) {
            val voiceSines = oscillatorSines[voiceIndex]
            val voiceCosines = oscillatorCosines[voiceIndex]
            val deltaSines = phaseDeltaSines[voiceIndex]
            val deltaCosines = phaseDeltaCosines[voiceIndex]
            val voiceGain = voiceGains[voiceIndex]
            for (index in harmonicAmplitudes.indices) {
                val frequencyRatio = frequencyRatios[index]
                val brightnessWeight = ((frequencyRatio - 1.0) / (highestFrequencyRatio - 1.0)).coerceIn(0.0, 1.0)
                val attackGain = 1.0 + spectrum.attackBrightness * attackTransient * brightnessWeight
                val sine = voiceSines[index]
                val cosine = voiceCosines[index]
                value += voiceGain * harmonicAmplitudes[index] * attackGain * sine

                voiceSines[index] = sine * deltaCosines[index] + cosine * deltaSines[index]
                voiceCosines[index] = cosine * deltaCosines[index] - sine * deltaSines[index]
            }
        }

        val amplitudeLfo = 1.0 + spectrum.amplitudeDrift * currentVibratoDepth * sin(amplitudeLfoPhase)
        val breath = if (spectrum.breathLevel > 0.0) {
            val rawNoise = nextNoise()
            breathState = breathState * 0.88 + rawNoise * 0.12
            // breathBrightness mengt de ongefilterde ruis erbij voor een luchtiger, minder gedekte fluitadem.
            val shapedNoise = breathState + spectrum.breathBrightness * (rawNoise - breathState)
            val breathEnvelope = spectrum.breathSustain + (1.0 - spectrum.breathSustain) * attackTransient
            shapedNoise * spectrum.breathLevel * breathEnvelope * attackEnvelope
        } else {
            0.0
        }

        advanceLfos(sampleRate)
        framesUntilDeltaRefresh--
        framesUntilNormalize--
        if (framesUntilNormalize <= 0) {
            normalizeOscillators()
            framesUntilNormalize = NORMALIZE_INTERVAL_FRAMES
        }
        val bloomEnvelope = 1.0 - spectrum.bloomDepth * exp(-time / spectrum.bloomTime.coerceAtLeast(0.01))
        return (value + breath) * noteEnvelope * bloomEnvelope * amplitudeLfo * spectrum.gain
    }

    private fun updatePhaseDeltas(sampleRate: Int) {
        val pitchDrift = centsToLinearRatio(spectrum.pitchDriftCents * currentVibratoDepth * sin(pitchLfoPhase))
        val phaseIncrement = TWO_PI * fundamentalFrequencyHz * pitchDrift / sampleRate
        for (voiceIndex in phaseDeltaSines.indices) {
            val voicePhaseIncrement = phaseIncrement * voiceFrequencyRatios[voiceIndex]
            val deltaSines = phaseDeltaSines[voiceIndex]
            val deltaCosines = phaseDeltaCosines[voiceIndex]
            for (index in frequencyRatios.indices) {
                val delta = voicePhaseIncrement * frequencyRatios[index]
                deltaSines[index] = sin(delta)
                deltaCosines[index] = cos(delta)
            }
        }
    }

    private fun normalizeOscillators() {
        for (voiceIndex in oscillatorSines.indices) {
            val voiceSines = oscillatorSines[voiceIndex]
            val voiceCosines = oscillatorCosines[voiceIndex]
            for (index in voiceSines.indices) {
                val sine = voiceSines[index]
                val cosine = voiceCosines[index]
                val length = sqrt(sine * sine + cosine * cosine)
                if (length > 0.0) {
                    voiceSines[index] = sine / length
                    voiceCosines[index] = cosine / length
                } else {
                    voiceSines[index] = 0.0
                    voiceCosines[index] = 1.0
                }
            }
        }
    }

    private fun releaseEnvelope(frameInNote: Int, totalFrames: Int, sampleRate: Int): Double {
        val releaseFrames = min(totalFrames / 2, (sampleRate * spectrum.releaseTime).toInt().coerceAtLeast(1))
        val framesLeft = totalFrames - frameInNote
        return if (framesLeft >= releaseFrames) {
            1.0
        } else {
            val amount = (framesLeft.toDouble() / releaseFrames).coerceIn(0.0, 1.0)
            amount * amount * (3.0 - 2.0 * amount)
        }
    }

    private fun centsToLinearRatio(cents: Double): Double {
        return 1.0 + CENT_TO_LINEAR * cents
    }

    private fun nextNoise(): Double {
        noiseSeed = noiseSeed * 1664525 + 1013904223
        return ((noiseSeed ushr 1) / INT_POSITIVE_MAX) * 2.0 - 1.0
    }

    private fun advanceLfos(sampleRate: Int) {
        pitchLfoPhase = wrapPhase(pitchLfoPhase + TWO_PI * spectrum.pitchDriftHz / sampleRate)
        amplitudeLfoPhase = wrapPhase(amplitudeLfoPhase + TWO_PI * spectrum.amplitudeDriftHz / sampleRate)
    }

    private fun wrapPhase(phase: Double): Double {
        return if (phase >= TWO_PI) phase - TWO_PI else phase
    }

    companion object {
        private val TWO_PI = 2.0 * PI
        private const val INT_POSITIVE_MAX = 2147483647.0
        private const val PHASE_DELTA_REFRESH_FRAMES = 64
        private const val NORMALIZE_INTERVAL_FRAMES = 2048
        private val CENT_TO_LINEAR = ln(2.0) / 1200.0

        fun forTimbre(timbre: MelodyTimbre): PipeVoice {
            return PipeVoice(spectrumFor(timbre))
        }

        private fun spectrumFor(timbre: MelodyTimbre): PipeSpectrum {
            return when (timbre) {
                MelodyTimbre.PRESTANT8 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.32, 0.48, 0.17, 0.11, 0.040, 0.022, 0.010, 0.004),
                    attackBrightness = 0.14,
                    attackTime = 0.038,
                    breathLevel = 0.008,
                    pitchDriftCents = 0.07,
                    amplitudeDrift = 0.004,
                    tuningOffsetCents = -1.1,
                    phaseOffset = 0.37,
                    gain = 0.86,
                    releaseTime = 0.070,
                    pitchDriftHz = 4.0,
                    amplitudeDriftHz = 2.2,
                    bloomDepth = 0.045,
                    bloomTime = 0.20
                )

                MelodyTimbre.HOLPIJP8 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.15, 0.22, 0.085, 0.035, 0.014, 0.006, 0.0025, 0.001),
                    attackBrightness = 0.18,
                    attackTime = 0.045,
                    breathLevel = 0.005,
                    pitchDriftCents = 0.040,
                    amplitudeDrift = 0.0025,
                    tuningOffsetCents = 1.4,
                    phaseOffset = 1.18,
                    gain = 0.88,
                    releaseTime = 0.096,
                    pitchDriftHz = 3.3,
                    amplitudeDriftHz = 1.6,
                    bloomDepth = 0.12,
                    bloomTime = 0.26
                )

                MelodyTimbre.FLUIT_SOLO -> PipeSpectrum(
                    harmonics = doubleArrayOf(0.30, 0.95, 0.120, 0.035, 0.010, 0.003),
                    attackBrightness = 0.06,
                    attackTime = 0.028,
                    breathLevel = 0.040,
                    breathSustain = 0.24,
                    pitchDriftCents = 3.0,
                    amplitudeDrift = 0.018,
                    frequencyMultiplier = 2.0,
                    tuningOffsetCents = 1.5,
                    phaseOffset = 2.10,
                    frequencyRatios = doubleArrayOf(0.5, 1.0, 2.0, 3.0, 4.0, 5.0),
                    gain = 0.80,
                    releaseTime = 0.085,
                    pitchDriftHz = 4.6,
                    amplitudeDriftHz = 4.6,
                    breathBrightness = 0.46,
                    vibratoOnsetTime = 0.35,
                    bloomDepth = 0.0,
                    bloomTime = 0.10
                )

                MelodyTimbre.ORCHESTRAL_STRINGS -> PipeSpectrum(
                    harmonics = doubleArrayOf(0.78, 0.82, 0.54, 0.33, 0.205, 0.13, 0.082, 0.052, 0.032, 0.020, 0.012, 0.007),
                    attackBrightness = 0.24,
                    attackTime = 0.030,
                    breathLevel = 0.004,
                    pitchDriftCents = 0.09,
                    amplitudeDrift = 0.0045,
                    tuningOffsetCents = -2.0,
                    phaseOffset = 2.72,
                    gain = 0.73,
                    releaseTime = 0.066,
                    pitchDriftHz = 4.2,
                    amplitudeDriftHz = 1.9,
                    voiceDetuneCents = doubleArrayOf(-3.2, 0.0, 2.7),
                    voiceGains = doubleArrayOf(0.34, 0.40, 0.32),
                    bloomDepth = 0.070,
                    bloomTime = 0.24
                )

                MelodyTimbre.BOURDON16 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.18, 0.18, 0.055, 0.018, 0.006, 0.002),
                    attackBrightness = 0.08,
                    attackTime = 0.060,
                    breathLevel = 0.004,
                    pitchDriftCents = 0.035,
                    amplitudeDrift = 0.0025,
                    frequencyMultiplier = 0.5,
                    tuningOffsetCents = -1.7,
                    phaseOffset = 0.82,
                    gain = 0.82,
                    releaseTime = 0.120,
                    pitchDriftHz = 2.8,
                    amplitudeDriftHz = 1.4,
                    bloomDepth = 0.095,
                    bloomTime = 0.32
                )

                MelodyTimbre.ROERFLUIT8 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.08, 0.30, 0.12, 0.050, 0.020, 0.008, 0.003, 0.001),
                    attackBrightness = 0.16,
                    attackTime = 0.043,
                    breathLevel = 0.010,
                    pitchDriftCents = 0.045,
                    amplitudeDrift = 0.0028,
                    tuningOffsetCents = 0.9,
                    phaseOffset = 1.63,
                    gain = 0.86,
                    releaseTime = 0.090,
                    pitchDriftHz = 3.1,
                    amplitudeDriftHz = 1.7,
                    bloomDepth = 0.105,
                    bloomTime = 0.25
                )

                MelodyTimbre.OCTAAF4 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.12, 0.62, 0.22, 0.20, 0.070, 0.050, 0.026, 0.016, 0.008, 0.004),
                    attackBrightness = 0.16,
                    attackTime = 0.032,
                    breathLevel = 0.007,
                    pitchDriftCents = 0.060,
                    amplitudeDrift = 0.0035,
                    frequencyMultiplier = 2.0,
                    tuningOffsetCents = -0.7,
                    phaseOffset = 2.46,
                    gain = 0.70,
                    releaseTime = 0.074,
                    pitchDriftHz = 3.9,
                    amplitudeDriftHz = 2.0,
                    bloomDepth = 0.050,
                    bloomTime = 0.20
                )

                MelodyTimbre.GEDEKT8 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.12, 0.19, 0.070, 0.028, 0.010, 0.004, 0.0015),
                    attackBrightness = 0.14,
                    attackTime = 0.040,
                    breathLevel = 0.006,
                    pitchDriftCents = 0.045,
                    amplitudeDrift = 0.0028,
                    tuningOffsetCents = 1.8,
                    phaseOffset = 1.94,
                    gain = 0.86,
                    releaseTime = 0.088,
                    pitchDriftHz = 3.2,
                    amplitudeDriftHz = 1.8,
                    bloomDepth = 0.120,
                    bloomTime = 0.26
                )

                MelodyTimbre.FLUIT4 -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.02, 0.13, 0.040, 0.014, 0.004, 0.0012),
                    attackBrightness = 0.10,
                    attackTime = 0.048,
                    breathLevel = 0.012,
                    pitchDriftCents = 0.050,
                    amplitudeDrift = 0.0025,
                    frequencyMultiplier = 2.0,
                    tuningOffsetCents = 1.2,
                    phaseOffset = 0.54,
                    gain = 0.66,
                    releaseTime = 0.082,
                    pitchDriftHz = 3.0,
                    amplitudeDriftHz = 1.6,
                    bloomDepth = 0.100,
                    bloomTime = 0.24
                )

                MelodyTimbre.QUINTFLUIT -> PipeSpectrum(
                    harmonics = doubleArrayOf(1.00, 0.10, 0.030, 0.010, 0.003),
                    attackBrightness = 0.08,
                    attackTime = 0.052,
                    breathLevel = 0.010,
                    pitchDriftCents = 0.045,
                    amplitudeDrift = 0.0022,
                    frequencyMultiplier = 3.0,
                    tuningOffsetCents = -1.3,
                    phaseOffset = 2.92,
                    gain = 0.52,
                    releaseTime = 0.080,
                    pitchDriftHz = 3.1,
                    amplitudeDriftHz = 1.5,
                    bloomDepth = 0.075,
                    bloomTime = 0.22
                )
            }
        }
    }
}