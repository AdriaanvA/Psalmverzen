package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
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
        return try {
            val text = JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(verse)))
            val melody = JSONObject(ContentStorage.readBundledAsset(context, melodyFilePath(text, verse)))
            buildScoreModelOrNull(melody, text, verse)?.toString() ?: "{}"
        } catch (_: Exception) {
            "{}"
        }
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
            text.safeString("about").trim().ifBlank { null }
        } catch (_: Exception) {
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
            val verses = text.safeJSONArray("verses")
            val map = HashMap<Int, List<String>>()
            for (i in 0 until (verses?.length() ?: 0)) {
                val verse = verses?.safeJSONObject(i) ?: continue
                val lines = verse.safeJSONArray("lines")
                val lineList = ArrayList<String>(lines?.length() ?: 0)
                for (j in 0 until (lines?.length() ?: 0)) {
                    lineList.add(lines?.safeJSONObject(j)?.safeString("raw") ?: "")
                }
                map[verse.safeInt("number")] = lineList
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun buildScoreModelOrNull(melody: JSONObject, text: JSONObject, verse: Verse): JSONObject? {
        return try {
            val melodyLines = melody.safeJSONObject("melody")?.safeJSONArray("lines")
            val verseSource = findVerse(text.safeJSONArray("verses"), verse.verse)
            val lyricLines = verseSource?.safeJSONArray("lines")
            val lineCount = minOf(melodyLines?.length() ?: 0, lyricLines?.length() ?: 0)
            val partName = if (verse.type == "Gezang") "Gezang" else "Psalm"

            val linesJson = JSONArray()
            for (lineIndex in 0 until lineCount) {
                val notes = melodyLines?.safeJSONArray(lineIndex) ?: continue
                val line = lyricLines?.safeJSONObject(lineIndex)
                val tokens = line?.safeJSONArray("tokens") ?: JSONArray()
                val slots = JSONArray()
                var tokenIndex = 0
                var currentSlot: JSONObject? = null
                for (noteIndex in 0 until notes.length()) {
                    val note = notes.safeJSONObject(noteIndex) ?: continue
                    if (note.safeBoolean("hidden")) continue
                    val noteJson = noteJsonModel(note)
                    val isRest = note.safeBoolean("rest")
                    val hasLyric = note.safeBoolean("lyricSlot") && tokenIndex < tokens.length()
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
                            val token = tokens.safeJSONObject(tokenIndex++) ?: continue
                            val slot = JSONObject()
                                .put("text", token.safeString("text", ""))
                                .put("syllabic", token.safeString("syllabic", "single"))
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

            JSONObject()
                .put("title", "$partName ${verse.number} vers ${verse.verse}")
                .put("fifths", melody.safeInt("fifths", 0))
                .put("clef", "G")
                .put("lines", linesJson)
        } catch (_: Exception) {
            null
        }
    }

    private fun noteJsonModel(note: JSONObject): JSONObject {
        return JSONObject()
            .put("step", note.safeString("step"))
            .put("alter", if (note.safeIsNull("alter")) 0 else note.safeInt("alter"))
            .put("octave", note.safeInt("octave"))
            .put("type", note.safeString("type"))
            .put("dot", note.safeBoolean("dot"))
            .put("rest", note.safeBoolean("rest"))
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
        val verseSource = findVerse(text.safeJSONArray("verses"), verse.verse)
        val perVerse = verseSource?.safeString("melodyFile")?.trim()
        return if (!perVerse.isNullOrEmpty()) perVerse else text.safeString("melodyFile")
    }

    private fun renderMusicXml(melody: JSONObject, text: JSONObject, verse: Verse): String {
        val divisions = melody.safeInt("divisions")
        val melodyLines = melody.safeJSONObject("melody")?.safeJSONArray("lines") ?: JSONArray()
        val verseSource = findVerse(text.safeJSONArray("verses"), verse.verse)
        val lyricLines = verseSource?.safeJSONArray("lines") ?: JSONArray()
        val lineCount = minOf(melodyLines.length(), lyricLines.length())
        val partName = if (verse.type == "Gezang") "Gezang" else "Psalm"

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<score-partwise version=\"4.0\">")
            appendLine("  <work><work-title>${escapeXml(partName)} ${verse.number} vers ${verse.verse}</work-title></work>")
            appendLine("  <identification><creator type=\"composer\">${escapeXml(melody.safeString("composer"))}</creator></identification>")
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
                    val lyric = if (note.safeBoolean("lyricSlot") && tokenIndex < tokens.length()) {
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

    private fun findVerse(verses: JSONArray?, verseNumber: Int): JSONObject? {
        if (verses == null) return null
        for (index in 0 until verses.length()) {
            val verse = verses.safeJSONObject(index) ?: continue
            if (verse.safeInt("number") == verseNumber) return verse
        }
        return null
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
            total += notes.safeJSONObject(index)?.safeInt("duration") ?: 0
        }
        return total
    }

    private fun noteXml(note: JSONObject, lyric: JSONObject?): String {
        val noteAttributes = if (note.safeBoolean("hidden")) " print-object=\"no\"" else ""
        val pitchXml = if (note.safeBoolean("rest")) {
            "<rest />"
        } else {
            val alterXml = if (!note.safeIsNull("alter") && note.safeInt("alter") != 0) "<alter>${note.safeInt("alter")}</alter>" else ""
            "<pitch><step>${note.safeString("step")}</step>$alterXml<octave>${note.safeInt("octave")}</octave></pitch>"
        }
        val tie = note.safeString("tie")
        val tieTag = if (tie.isNotBlank()) "<tie type=\"$tie\"/>" else ""
        val tiedTag = if (tie.isNotBlank()) "<notations><tied type=\"$tie\"/></notations>" else ""
        val dotTag = if (note.safeBoolean("dot")) "<dot/>" else ""
        val lyricTag = if (lyric == null) "" else lyricXml(lyric)
        return "      <note$noteAttributes>$pitchXml<duration>${note.safeInt("duration")}</duration>$tieTag<voice>1</voice><type>${note.safeString("type")}</type>$dotTag$tiedTag$lyricTag</note>"
    }

    private fun lyricXml(lyric: JSONObject): String {
        return "<lyric number=\"1\"><syllabic>${escapeXml(lyric.safeString("syllabic"))}</syllabic><text>${escapeXml(lyric.safeString("text"))}</text></lyric>"
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun JSONObject.safeJSONObject(key: String): JSONObject? = try {
        getJSONObject(key)
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.safeJSONArray(key: String): JSONArray? = try {
        getJSONArray(key)
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.safeInt(key: String, default: Int = 0): Int = try {
        getInt(key)
    } catch (_: JSONException) {
        default
    }

    private fun JSONObject.safeString(key: String, default: String = ""): String = try {
        getString(key)
    } catch (_: JSONException) {
        default
    }

    private fun JSONObject.safeBoolean(key: String, default: Boolean = false): Boolean = try {
        getBoolean(key)
    } catch (_: JSONException) {
        default
    }

    private fun JSONObject.safeIsNull(key: String): Boolean = try {
        isNull(key)
    } catch (_: JSONException) {
        true
    }

    private fun JSONArray.safeJSONObject(index: Int): JSONObject? = try {
        getJSONObject(index)
    } catch (_: JSONException) {
        null
    }

    private fun JSONArray.safeJSONArray(index: Int): JSONArray? = try {
        getJSONArray(index)
    } catch (_: JSONException) {
        null
    }
}