package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONObject

object HarmonyPlaybackModel {
    fun buildEvents(
        context: Context,
        verses: List<Verse>,
        transposeSemitones: Int = 0
    ): List<PlaybackEvent> {
        if (verses.isEmpty()) return emptyList()
        val events = ArrayList<PlaybackEvent>()
        for (verse in verses) {
            val harmony = readHarmony(context, verse) ?: return emptyList()
            val parts = harmony.optJSONArray("parts") ?: return emptyList()
            if (parts.length() != 4) return emptyList()
            val partLines = List(parts.length()) { index ->
                parts.optJSONObject(index)?.optJSONArray("lines") ?: return emptyList()
            }
            val lineCount = partLines.minOf { it.length() }
            for (lineIndex in 0 until lineCount) {
                val lines = partLines.map { it.optJSONArray(lineIndex) ?: return emptyList() }
                val eventCount = lines.minOf { it.length() }
                for (eventIndex in 0 until eventCount) {
                    val notes = lines.map { it.optJSONObject(eventIndex) ?: return emptyList() }
                    val reference = notes.first()
                    val beats = MelodyPlaybackModel.noteBeats(reference)
                    if (beats <= 0.0) continue
                    val frequencies = notes.mapNotNull { note ->
                        if (note.optBoolean("rest")) return@mapNotNull null
                        val step = note.optString("step")
                        val octave = note.optInt("octave", Int.MIN_VALUE)
                        if (step.isBlank() || octave == Int.MIN_VALUE) return@mapNotNull null
                        val alter = if (note.isNull("alter")) 0 else note.optInt("alter", 0)
                        val midi = noteToMidi(step, alter, octave) + transposeSemitones
                        MelodyPlaybackModel.midiToFrequencyHz(midi)
                    }
                    events += PlaybackEvent(
                        frequenciesHz = frequencies,
                        durationBeats = beats,
                        playbackId = if (frequencies.isEmpty()) null else "${verse.fileName}:$lineIndex:$eventIndex"
                    )
                }
            }
            if (verses.size > 1) {
                events += PlaybackEvent(frequenciesHz = emptyList(), durationBeats = 0.5)
            }
        }
        return events
    }

    fun readHarmony(context: Context, verse: Verse): JSONObject? {
        val path = when (verse.type) {
            "Psalm" -> "content/harmonies/Psalm${verse.number.toString().padStart(3, '0')}.json"
            "Psalter" -> "content/harmonies/Psalter${verse.number}.json"
            else -> return null
        }
        return try {
            JSONObject(ContentStorage.readBundledAsset(context, path))
        } catch (_: Exception) {
            null
        }
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
            else -> return Int.MIN_VALUE
        }
        return 12 * (octave + 1) + base + alter
    }
}