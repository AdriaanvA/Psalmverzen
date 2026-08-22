package nl.psalmbladmuziek.app

/** Lightweight mono Schroeder-style room shared by the whole live melody. */
class OrganReverb private constructor(
    sampleRate: Int,
    private val wetLevel: Double,
    private val dryLevel: Double,
    feedback: Double,
    damping: Double
) {
    private val combs = arrayOf(
        CombFilter(delaySamples(sampleRate, 0.0297), feedback * 0.98, damping),
        CombFilter(delaySamples(sampleRate, 0.0371), feedback * 0.96, damping * 0.94),
        CombFilter(delaySamples(sampleRate, 0.0411), feedback * 0.93, damping * 1.04),
        CombFilter(delaySamples(sampleRate, 0.0533), feedback * 0.90, damping * 1.08)
    )
    private val allPasses = arrayOf(
        AllPassFilter(delaySamples(sampleRate, 0.0050), 0.68),
        AllPassFilter(delaySamples(sampleRate, 0.0017), 0.62)
    )

    fun process(dry: Double): Double {
        return applyDryLevel(dry) + processWet(dry)
    }

    fun applyDryLevel(dry: Double): Double = dry * dryLevel

    fun processWet(input: Double): Double {
        var reverberated = 0.0
        for (comb in combs) {
            reverberated += comb.process(input)
        }
        reverberated *= 0.25

        for (allPass in allPasses) {
            reverberated = allPass.process(reverberated)
        }

        return reverberated * wetLevel
    }

    private class CombFilter(
        delaySamples: Int,
        private val feedback: Double,
        private val damping: Double
    ) {
        private val buffer = DoubleArray(delaySamples)
        private var index = 0
        private var filterState = 0.0

        fun process(input: Double): Double {
            val delayed = buffer[index]
            filterState = delayed * (1.0 - damping) + filterState * damping
            buffer[index] = input + filterState * feedback
            index++
            if (index == buffer.size) index = 0
            return delayed
        }
    }

    private class AllPassFilter(
        delaySamples: Int,
        private val feedback: Double
    ) {
        private val buffer = DoubleArray(delaySamples)
        private var index = 0

        fun process(input: Double): Double {
            val delayed = buffer[index]
            val output = delayed - input
            buffer[index] = input + delayed * feedback
            index++
            if (index == buffer.size) index = 0
            return output
        }
    }

    companion object {
        fun forRegistration(timbres: List<MelodyTimbre>, sampleRate: Int): OrganReverb {
            val includesFluteSolo = timbres.any { it == MelodyTimbre.FLUIT_SOLO }
            val includesStrings = timbres.any { it == MelodyTimbre.ORCHESTRAL_STRINGS }
            val includesSoftFlutes = timbres.any { it == MelodyTimbre.HOLPIJP8 || it == MelodyTimbre.ROERFLUIT8 || it == MelodyTimbre.GEDEKT8 || it == MelodyTimbre.FLUIT4 }
            val includesBourdon = timbres.any { it == MelodyTimbre.BOURDON16 }
            return OrganReverb(
                sampleRate = sampleRate,
                wetLevel = when {
                    includesFluteSolo -> 0.085
                    includesStrings -> 0.18
                    includesSoftFlutes -> 0.22
                    includesBourdon -> 0.18
                    else -> 0.19
                },
                dryLevel = when {
                    includesFluteSolo -> 0.98
                    else -> 0.92
                },
                feedback = when {
                    includesFluteSolo -> 0.58
                    includesSoftFlutes -> 0.76
                    includesBourdon -> 0.74
                    else -> 0.74
                },
                damping = when {
                    includesFluteSolo -> 0.24
                    includesStrings -> 0.45
                    includesSoftFlutes -> 0.50
                    includesBourdon -> 0.52
                    else -> 0.50
                }
            )
        }

        fun forTimbre(timbre: MelodyTimbre, sampleRate: Int): OrganReverb {
            val wet = when (timbre) {
                MelodyTimbre.HOLPIJP8 -> 0.22
                MelodyTimbre.FLUIT_SOLO -> 0.14
                MelodyTimbre.ORCHESTRAL_STRINGS -> 0.18
                MelodyTimbre.BOURDON16 -> 0.15
                MelodyTimbre.ROERFLUIT8 -> 0.21
                MelodyTimbre.OCTAAF4 -> 0.16
                MelodyTimbre.GEDEKT8 -> 0.20
                else -> 0.17
            }
            val feedback = when (timbre) {
                MelodyTimbre.HOLPIJP8 -> 0.76
                MelodyTimbre.FLUIT_SOLO -> 0.66
                MelodyTimbre.ORCHESTRAL_STRINGS -> 0.73
                MelodyTimbre.BOURDON16 -> 0.70
                MelodyTimbre.ROERFLUIT8 -> 0.75
                MelodyTimbre.OCTAAF4 -> 0.71
                MelodyTimbre.GEDEKT8 -> 0.73
                else -> 0.72
            }
            val damping = when (timbre) {
                MelodyTimbre.HOLPIJP8 -> 0.48
                MelodyTimbre.FLUIT_SOLO -> 0.30
                MelodyTimbre.ORCHESTRAL_STRINGS -> 0.46
                MelodyTimbre.BOURDON16 -> 0.55
                MelodyTimbre.ROERFLUIT8 -> 0.46
                MelodyTimbre.OCTAAF4 -> 0.50
                MelodyTimbre.GEDEKT8 -> 0.42
                else -> 0.50
            }
            return OrganReverb(
                sampleRate = sampleRate,
                wetLevel = wet,
                dryLevel = 1.0 - wet * 0.28,
                feedback = feedback,
                damping = damping
            )
        }

        private fun delaySamples(sampleRate: Int, seconds: Double): Int {
            return (sampleRate * seconds).toInt().coerceAtLeast(1)
        }
    }
}