package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ScoreBundleRenderer {
    fun readMusicXml(context: Context, verse: Verse): String {
        ContentStorage.readDownloadedMusicXmlOrNull(context, verse.fileName)?.let { return it }

        val text = JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(verse)))
        val melody = JSONObject(ContentStorage.readBundledAsset(context, melodyFilePath(text, verse)))
        return renderMusicXml(melody, text, verse)
    }

    /**
     * Bouwt een schoon, tekst-eerst geschikt notenmodel (regels -> slots -> noten).
     * Een slot is één lettergreep met de noot/noten waarop die gezongen wordt
     * (melisma = meerdere noten op één lettergreep). Dit model is de bron voor
     * de eigen SVG-renderer (score_renderer.html).
     */
    fun readScoreModel(context: Context, verse: Verse): String {
        val text = JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(verse)))
        val melody = JSONObject(ContentStorage.readBundledAsset(context, melodyFilePath(text, verse)))
        return buildScoreModel(melody, text, verse).toString()
    }

    /**
     * Leest het (optionele) toelichting/achtergrond-veld "about" van een psalm/gezang.
     * Wordt niet in de bladmuziek getoond; bedoeld voor een aparte 'over'-weergave.
     * Geeft null als er geen toelichting is.
     */
    fun readAbout(context: Context, type: String, number: Int): String? {
        return try {
            val path = if (type == "Gezang") {
                "content/gezangen/texts/Gezang${number.toString().padStart(3, '0')}.json"
            } else {
                "content/psalms/texts/Psalm${number.toString().padStart(3, '0')}.json"
            }
            val text = JSONObject(ContentStorage.readBundledAsset(context, path))
            text.optString("about", "").trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Leest de losse (platte) tekstregels per versnummer voor een psalm/gezang.
     * Gebruikt voor de zoekindex (om de matchende regel te kunnen tonen).
     */
    fun readVerseLines(context: Context, type: String, number: Int): Map<Int, List<String>> {
        return try {
            val path = if (type == "Gezang") {
                "content/gezangen/texts/Gezang${number.toString().padStart(3, '0')}.json"
            } else {
                "content/psalms/texts/Psalm${number.toString().padStart(3, '0')}.json"
            }
            val text = JSONObject(ContentStorage.readBundledAsset(context, path))
            val verses = text.getJSONArray("verses")
            val map = HashMap<Int, List<String>>()
            for (i in 0 until verses.length()) {
                val verse = verses.getJSONObject(i)
                val lines = verse.getJSONArray("lines")
                val lineList = ArrayList<String>(lines.length())
                for (j in 0 until lines.length()) {
                    lineList.add(lines.getJSONObject(j).optString("raw"))
                }
                map[verse.getInt("number")] = lineList
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun buildScoreModel(melody: JSONObject, text: JSONObject, verse: Verse): JSONObject {
        val melodyLines = melody.getJSONObject("melody").getJSONArray("lines")
        val verseSource = findVerse(text.getJSONArray("verses"), verse.verse)
        val lyricLines = verseSource.getJSONArray("lines")
        val lineCount = minOf(melodyLines.length(), lyricLines.length())
        val partName = if (verse.type == "Gezang") "Gezang" else "Psalm"

        val linesJson = JSONArray()
        for (lineIndex in 0 until lineCount) {
            val notes = melodyLines.getJSONArray(lineIndex)
            val tokens = lyricLines.getJSONObject(lineIndex).getJSONArray("tokens")
            val slots = JSONArray()
            var tokenIndex = 0
            var currentSlot: JSONObject? = null
            for (noteIndex in 0 until notes.length()) {
                val note = notes.getJSONObject(noteIndex)
                if (note.optBoolean("hidden")) continue
                val noteJson = noteJsonModel(note)
                val isRest = note.getBoolean("rest")
                val hasLyric = note.optBoolean("lyricSlot") && tokenIndex < tokens.length()
                when {
                    isRest -> {
                        slots.put(
                            JSONObject()
                                .put("text", "")
                                .put("syllabic", "")
                                .put("rest", true)
                                .put("notes", JSONArray().put(noteJson))
                        )
                        currentSlot = null
                    }
                    hasLyric -> {
                        val token = tokens.getJSONObject(tokenIndex++)
                        val slot = JSONObject()
                            .put("text", token.getString("text"))
                            .put("syllabic", token.optString("syllabic", "single"))
                            .put("rest", false)
                            .put("notes", JSONArray().put(noteJson))
                        slots.put(slot)
                        currentSlot = slot
                    }
                    else -> {
                        val slot = currentSlot
                        if (slot != null) {
                            slot.getJSONArray("notes").put(noteJson)
                        } else {
                            val newSlot = JSONObject()
                                .put("text", "")
                                .put("syllabic", "")
                                .put("rest", false)
                                .put("notes", JSONArray().put(noteJson))
                            slots.put(newSlot)
                            currentSlot = newSlot
                        }
                    }
                }
            }
            linesJson.put(JSONObject().put("slots", slots))
        }

        return JSONObject()
            .put("title", "$partName ${verse.number} vers ${verse.verse}")
            .put("fifths", melody.getInt("fifths"))
            .put("clef", "G")
            .put("lines", linesJson)
    }

    private fun noteJsonModel(note: JSONObject): JSONObject {
        return JSONObject()
            .put("step", note.optString("step"))
            .put("alter", if (note.isNull("alter")) 0 else note.optInt("alter"))
            .put("octave", note.optInt("octave"))
            .put("type", note.optString("type"))
            .put("dot", note.optBoolean("dot"))
            .put("rest", note.getBoolean("rest"))
    }

    private fun textAssetPath(verse: Verse): String = when (verse.type) {
        "Gezang" -> "content/gezangen/texts/Gezang${verse.number.toString().padStart(3, '0')}.json"
        else -> "content/psalms/texts/Psalm${verse.number.toString().padStart(3, '0')}.json"
    }

    /**
     * Bepaalt het melodiebestand voor een specifiek vers. Een vers mag een eigen
     * "melodyFile" hebben (bijv. Gezang 6, waar vers 1 en vers 2 een andere wijs
     * hebben); anders wordt de algemene "melodyFile" van het lied gebruikt.
     */
    private fun melodyFilePath(text: JSONObject, verse: Verse): String {
        val verseSource = findVerse(text.getJSONArray("verses"), verse.verse)
        val perVerse = verseSource.optString("melodyFile", "").trim()
        return if (perVerse.isNotEmpty()) perVerse else text.getString("melodyFile")
    }

    private fun renderMusicXml(melody: JSONObject, text: JSONObject, verse: Verse): String {
        val divisions = melody.getInt("divisions")
        val melodyLines = melody.getJSONObject("melody").getJSONArray("lines")
        val verseSource = findVerse(text.getJSONArray("verses"), verse.verse)
        val lyricLines = verseSource.getJSONArray("lines")
        val lineCount = minOf(melodyLines.length(), lyricLines.length())
        val partName = if (verse.type == "Gezang") "Gezang" else "Psalm"

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<score-partwise version=\"4.0\">")
            appendLine("  <work><work-title>${escapeXml(partName)} ${verse.number} vers ${verse.verse}</work-title></work>")
            appendLine("  <identification><creator type=\"composer\">${escapeXml(melody.optString("composer"))}</creator></identification>")
            appendLine("  <part-list>")
            appendLine("    <score-part id=\"P1\"><part-name>${escapeXml(partName)}</part-name></score-part>")
            appendLine("  </part-list>")
            appendLine("  <part id=\"P1\">")
            for (lineIndex in 0 until lineCount) {
                val notes = melodyLines.getJSONArray(lineIndex)
                val tokens = lyricLines.getJSONObject(lineIndex).getJSONArray("tokens")
                appendLine("    <measure number=\"${lineIndex + 1}\">")
                appendLine(attributesXml(lineIndex, notes, divisions, melody.getInt("fifths")))
                var tokenIndex = 0
                for (noteIndex in 0 until notes.length()) {
                    val note = notes.getJSONObject(noteIndex)
                    val lyric = if (note.optBoolean("lyricSlot") && tokenIndex < tokens.length()) {
                        tokens.getJSONObject(tokenIndex++)
                    } else {
                        null
                    }
                    appendLine(noteXml(note, lyric))
                }
                appendLine("    </measure>")
            }
            appendLine("  </part>")
            append("</score-partwise>")
        }
    }

    private fun findVerse(verses: JSONArray, verseNumber: Int): JSONObject {
        for (index in 0 until verses.length()) {
            val verse = verses.getJSONObject(index)
            if (verse.getInt("number") == verseNumber) return verse
        }
        throw IllegalArgumentException("Verse $verseNumber not found in score bundle")
    }

    private fun attributesXml(lineIndex: Int, notes: JSONArray, divisions: Int, fifths: Int): String {
        val beats = maxOf(1, totalDuration(notes) / divisions)
        val timeXml = "<time print-object=\"no\"><beats>$beats</beats><beat-type>4</beat-type></time>"
        return if (lineIndex == 0) {
            "      <attributes><divisions>$divisions</divisions><key><fifths>$fifths</fifths></key>$timeXml<clef><sign>G</sign><line>2</line></clef></attributes>"
        } else {
            "      <attributes>$timeXml</attributes>"
        }
    }

    private fun totalDuration(notes: JSONArray): Int {
        var total = 0
        for (index in 0 until notes.length()) {
            total += notes.getJSONObject(index).getInt("duration")
        }
        return total
    }

    private fun noteXml(note: JSONObject, lyric: JSONObject?): String {
        val noteAttributes = if (note.optBoolean("hidden")) " print-object=\"no\"" else ""
        val pitchXml = if (note.getBoolean("rest")) {
            "<rest />"
        } else {
            val alterXml = if (!note.isNull("alter") && note.optInt("alter") != 0) "<alter>${note.getInt("alter")}</alter>" else ""
            "<pitch><step>${note.getString("step")}</step>$alterXml<octave>${note.getInt("octave")}</octave></pitch>"
        }
        val tie = note.optString("tie")
        val tieTag = if (tie.isNotBlank()) "<tie type=\"$tie\"/>" else ""
        val tiedTag = if (tie.isNotBlank()) "<notations><tied type=\"$tie\"/></notations>" else ""
        val dotTag = if (note.optBoolean("dot")) "<dot/>" else ""
        val lyricTag = if (lyric == null) "" else lyricXml(lyric)
        return "      <note$noteAttributes>$pitchXml<duration>${note.getInt("duration")}</duration>$tieTag<voice>1</voice><type>${note.getString("type")}</type>$dotTag$tiedTag$lyricTag</note>"
    }

    private fun lyricXml(lyric: JSONObject): String {
        return "<lyric number=\"1\"><syllabic>${escapeXml(lyric.getString("syllabic"))}</syllabic><text>${escapeXml(lyric.getString("text"))}</text></lyric>"
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}