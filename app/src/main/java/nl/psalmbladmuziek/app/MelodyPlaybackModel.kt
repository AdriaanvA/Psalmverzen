package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.pow

/** Eén afspeel-event: een toon of stilte met een duur in seconden. */
data class PlaybackEvent(
    val frequenciesHz: List<Double>,
    val durationBeats: Double,
    val playbackId: String? = null
)

object MelodyPlaybackModel {
    // Relatieve schuiving: 100 in de UI klinkt als de eerdere 115-instelling.
    private const val BASE_BPM_AT_100_PERCENT = 115.0

    fun beatsPerSecond(tempoPercent: Int): Double {
        val safeTempo = tempoPercent.coerceIn(AppSettings.MIN_PLAYBACK_TEMPO, AppSettings.MAX_PLAYBACK_TEMPO)
        return BASE_BPM_AT_100_PERCENT * (safeTempo / 100.0) / 60.0
    }

    fun buildEvents(
        context: Context,
        verses: List<Verse>,
        transposeSemitones: Int = 0
    ): List<PlaybackEvent> {
        if (verses.isEmpty()) return emptyList()

        val events = ArrayList<PlaybackEvent>()
        verses.forEach { verse ->
            val scoreModel = JSONObject(ScoreBundleRenderer.readScoreModel(context, verse))
            val lines = scoreModel.optJSONArray("lines") ?: return@forEach
            for (lineIndex in 0 until lines.length()) {
                val slots = lines.optJSONObject(lineIndex)?.optJSONArray("slots") ?: continue
                for (slotIndex in 0 until slots.length()) {
                    val slot = slots.optJSONObject(slotIndex) ?: continue
                    val notes = slot.optJSONArray("notes") ?: continue
                    for (noteIndex in 0 until notes.length()) {
                        val note = notes.optJSONObject(noteIndex) ?: continue
                        val beats = noteBeats(note)
                        if (beats <= 0.0) continue
                        if (note.optBoolean("rest")) {
                            events += PlaybackEvent(frequenciesHz = emptyList(), durationBeats = beats)
                            continue
                        }
                        val step = note.optString("step")
                        val octave = note.optInt("octave", Int.MIN_VALUE)
                        if (step.isBlank() || octave == Int.MIN_VALUE) {
                            events += PlaybackEvent(frequenciesHz = emptyList(), durationBeats = beats)
                            continue
                        }
                        val alter = if (note.isNull("alter")) 0 else note.optInt("alter", 0)
                        val midi = noteToMidi(step, alter, octave) + transposeSemitones
                        events += PlaybackEvent(
                            frequenciesHz = listOf(midiToFrequencyHz(midi)),
                            durationBeats = beats,
                            playbackId = note.optString("playbackId").ifBlank { null }
                        )
                    }
                }
            }
            // Kleine adempauze tussen verzen bij multi-verse playback.
            if (verses.size > 1) {
                events += PlaybackEvent(frequenciesHz = emptyList(), durationBeats = 0.5)
            }
        }
        return events
    }

    fun noteBeats(note: JSONObject): Double {
        val base = when (note.optString("type")) {
            "whole" -> 4.0
            "half" -> 2.0
            "quarter" -> 1.0
            "eighth" -> 0.5
            "16th" -> 0.25
            else -> 1.0
        }
        return if (note.optBoolean("dot")) base * 1.5 else base
    }

    private fun noteToMidi(step: String, alter: Int, octave: Int): Int {
        val base = when (step.uppercase()) {
            "C" -> 0
            "D" -> 2
            "E" -> 4
            "F" -> 5
            "G" -> 7
            "A" -> 9
            "B" -> 11
            else -> 0
        }
        return 12 * (octave + 1) + base + alter
    }

    fun midiToFrequencyHz(midi: Int): Double {
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }
}
