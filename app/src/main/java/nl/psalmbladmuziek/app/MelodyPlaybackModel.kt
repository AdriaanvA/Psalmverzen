package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONObject

/** Eén afspeel-event: een toon of stilte met een duur in seconden. */
data class PlaybackEvent(
    val frequencyHz: Double?,
    val durationSec: Double
)

object MelodyPlaybackModel {
    // Relatieve schuiving: 100 in de UI klinkt als de eerdere 115-instelling.
    private const val BASE_BPM_AT_100_PERCENT = 115.0

    fun buildEvents(
        context: Context,
        verses: List<Verse>,
        tempoPercent: Int,
        transposeSemitones: Int = 0
    ): List<PlaybackEvent> {
        if (verses.isEmpty()) return emptyList()
        val safeTempo = tempoPercent.coerceIn(AppSettings.MIN_PLAYBACK_TEMPO, AppSettings.MAX_PLAYBACK_TEMPO)
        val bpm = BASE_BPM_AT_100_PERCENT * (safeTempo / 100.0)
        val quarterSec = 60.0 / bpm

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
                        val durationSec = beats * quarterSec
                        if (note.optBoolean("rest")) {
                            events += PlaybackEvent(frequencyHz = null, durationSec = durationSec)
                            continue
                        }
                        val step = note.optString("step")
                        val octave = note.optInt("octave", Int.MIN_VALUE)
                        if (step.isBlank() || octave == Int.MIN_VALUE) {
                            events += PlaybackEvent(frequencyHz = null, durationSec = durationSec)
                            continue
                        }
                        val alter = if (note.isNull("alter")) 0 else note.optInt("alter", 0)
                        val midi = noteToMidi(step, alter, octave) + transposeSemitones
                        events += PlaybackEvent(frequencyHz = midiToFrequencyHz(midi), durationSec = durationSec)
                    }
                }
            }
            // Kleine adempauze tussen verzen bij multi-verse playback.
            if (verses.size > 1) {
                events += PlaybackEvent(frequencyHz = null, durationSec = quarterSec * 0.5)
            }
        }
        return events
    }

    private fun noteBeats(note: JSONObject): Double {
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

    private fun midiToFrequencyHz(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
    }
}
