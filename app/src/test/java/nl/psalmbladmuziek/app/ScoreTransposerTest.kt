package nl.psalmbladmuziek.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTransposerTest {
    @Test
    fun transposesNotesKeyAndOctaveWithoutTouchingRests() {
        val model = JSONObject(
            """{"fifths":0,"lines":[{"slots":[{"notes":[
                {"step":"B","alter":0,"octave":4,"type":"quarter","rest":false},
                {"type":"quarter","rest":true}
            ]}]}]}"""
        )

        ScoreTransposer.transpose(model, 1)

        assertEquals(-5, model.getInt("fifths"))
        val notes = model.getJSONArray("lines").getJSONObject(0)
            .getJSONArray("slots").getJSONObject(0).getJSONArray("notes")
        assertEquals("C", notes.getJSONObject(0).getString("step"))
        assertEquals(5, notes.getJSONObject(0).getInt("octave"))
        assertEquals(true, notes.getJSONObject(1).getBoolean("rest"))
    }

    @Test
    fun roundTripRestoresPitchAndKey() {
        val model = JSONObject(
            """{"fifths":2,"lines":[{"slots":[{"notes":[
                {"step":"F","alter":1,"octave":4,"rest":false}
            ]}]}]}"""
        )

        ScoreTransposer.transpose(model, 5)
        ScoreTransposer.transpose(model, -5)

        val note = model.getJSONArray("lines").getJSONObject(0)
            .getJSONArray("slots").getJSONObject(0).getJSONArray("notes").getJSONObject(0)
        assertEquals(2, model.getInt("fifths"))
        assertEquals("F", note.getString("step"))
        assertEquals(1, note.getInt("alter"))
        assertEquals(4, note.getInt("octave"))
    }
}
