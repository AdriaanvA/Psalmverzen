package nl.psalmbladmuziek.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreBundleRendererTest {
    private val verse = Verse("Psalm", 1, 1, "Psalm001_v1.json", "Welzalig")

    @Test
    fun transformsValidJsonIntoScoreSlots() {
        val model = ScoreBundleRenderer.transformScoreModel(
            melody = melodyJson(),
            text = textJson(),
            verse = verse,
            useIsometricRhythm = false
        )

        assertNotNull(model)
        val line = model!!.getJSONArray("lines").getJSONObject(0)
        val slot = line.getJSONArray("slots").getJSONObject(0)
        val note = slot.getJSONArray("notes").getJSONObject(0)
        assertEquals("Psalm 1 vers 1", model.getString("title"))
        assertEquals("Wel", slot.getString("text"))
        assertEquals("Psalm001_v1.json:0:0", note.getString("playbackId"))
    }

    @Test
    fun isometricTransformationLengthensQuarterNote() {
        val model = ScoreBundleRenderer.transformScoreModel(
            melodyJson(), textJson(), verse, useIsometricRhythm = true
        )!!

        val note = firstNote(model)
        assertEquals("half", note.getString("type"))
    }

    @Test
    fun keepRhythmOverridesIsometricTransformation() {
        val text = textJson().put("keepRhythm", true)
        val model = ScoreBundleRenderer.transformScoreModel(
            melodyJson(), text, verse, useIsometricRhythm = true
        )!!

        assertEquals("quarter", firstNote(model).getString("type"))
    }

    @Test
    fun incompleteJsonProducesEmptyButValidModel() {
        val model = ScoreBundleRenderer.transformScoreModel(
            JSONObject(), JSONObject(), verse, useIsometricRhythm = false
        )

        assertNotNull(model)
        assertTrue(model!!.getJSONArray("lines").length() == 0)
    }

    @Test
    fun noteDurationsHandleDotsAndSixteenthNotes() {
        assertEquals(3.0, MelodyPlaybackModel.noteBeats(JSONObject("""{"type":"half","dot":true}""")), 0.0)
        assertEquals(0.25, MelodyPlaybackModel.noteBeats(JSONObject("""{"type":"16th"}""")), 0.0)
    }

    private fun firstNote(model: JSONObject): JSONObject = model.getJSONArray("lines").getJSONObject(0)
        .getJSONArray("slots").getJSONObject(0).getJSONArray("notes").getJSONObject(0)

    private fun melodyJson() = JSONObject(
        """{"fifths":0,"melody":{"lines":[[
            {"step":"C","alter":0,"octave":4,"type":"quarter","lyricSlot":true}
        ]]}}"""
    )

    private fun textJson() = JSONObject(
        """{"verses":[{"number":1,"lines":[{"tokens":[
            {"text":"Wel","syllabic":"single"}
        ]}]}]}"""
    )
}
