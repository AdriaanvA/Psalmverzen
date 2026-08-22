package nl.psalmbladmuziek.app

import org.json.JSONObject

object ScoreTransposer {
    fun transpose(model: JSONObject, semitones: Int) {
        val originalFifths = model.optInt("fifths", 0)
        val targetFifths = transposeKeyFifths(originalFifths, semitones)
        val preferFlats = targetFifths < 0
        model.put("fifths", targetFifths)

        val lines = model.optJSONArray("lines") ?: return
        for (i in 0 until lines.length()) {
            val slots = lines.getJSONObject(i).optJSONArray("slots") ?: continue
            for (j in 0 until slots.length()) {
                val notes = slots.getJSONObject(j).optJSONArray("notes") ?: continue
                for (k in 0 until notes.length()) {
                    val note = notes.getJSONObject(k)
                    if (note.optBoolean("rest")) continue
                    transposeModelNote(note, semitones, preferFlats)
                }
            }
        }
    }

    fun transposeHarmony(model: JSONObject, semitones: Int) {
        val originalFifths = model.optInt("fifths", 0)
        val targetFifths = transposeKeyFifths(originalFifths, semitones)
        val preferFlats = targetFifths < 0
        model.put("fifths", targetFifths)
        val parts = model.optJSONArray("parts") ?: return
        for (partIndex in 0 until parts.length()) {
            val lines = parts.getJSONObject(partIndex).optJSONArray("lines") ?: continue
            for (lineIndex in 0 until lines.length()) {
                val notes = lines.optJSONArray(lineIndex) ?: continue
                for (noteIndex in 0 until notes.length()) {
                    val note = notes.optJSONObject(noteIndex) ?: continue
                    if (!note.optBoolean("rest")) {
                        transposeModelNote(note, semitones, preferFlats)
                    }
                }
            }
        }
    }

    private fun transposeKeyFifths(fifths: Int, semitones: Int): Int {
        val targetPitchClass = Math.floorMod(fifths * 7 + semitones, 12)
        return KEY_FIFTHS_BY_PITCH_CLASS[targetPitchClass]
    }

    private fun transposeModelNote(note: JSONObject, semitones: Int, preferFlats: Boolean) {
        val step = note.optString("step").ifBlank { return }
        val alter = note.optInt("alter", 0)
        val octave = note.optInt("octave")
        val pitchClass = STEP_PITCH_CLASSES.getValue(step) + alter
        val transposedMidi = octave * 12 + pitchClass + semitones
        val transposedPitchClass = Math.floorMod(transposedMidi, 12)
        val transposedOctave = Math.floorDiv(transposedMidi, 12)
        val spelling = if (preferFlats) FLAT_SPELLINGS[transposedPitchClass] else SHARP_SPELLINGS[transposedPitchClass]
        note.put("step", spelling.step)
        note.put("alter", spelling.alter)
        note.put("octave", transposedOctave)
    }

    private val STEP_PITCH_CLASSES = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)
    private val KEY_FIFTHS_BY_PITCH_CLASS = intArrayOf(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5)
    private val SHARP_SPELLINGS = arrayOf(
        PitchSpelling("C", 0), PitchSpelling("C", 1), PitchSpelling("D", 0), PitchSpelling("D", 1),
        PitchSpelling("E", 0), PitchSpelling("F", 0), PitchSpelling("F", 1), PitchSpelling("G", 0),
        PitchSpelling("G", 1), PitchSpelling("A", 0), PitchSpelling("B", -1), PitchSpelling("B", 0)
    )
    private val FLAT_SPELLINGS = arrayOf(
        PitchSpelling("C", 0), PitchSpelling("D", -1), PitchSpelling("D", 0), PitchSpelling("E", -1),
        PitchSpelling("E", 0), PitchSpelling("F", 0), PitchSpelling("F", 1), PitchSpelling("G", 0),
        PitchSpelling("A", -1), PitchSpelling("A", 0), PitchSpelling("B", -1), PitchSpelling("B", 0)
    )

    private data class PitchSpelling(val step: String, val alter: Int)
}
