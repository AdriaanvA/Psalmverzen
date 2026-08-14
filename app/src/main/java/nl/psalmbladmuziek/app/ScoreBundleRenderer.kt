package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ScoreBundleRenderer {
    /**
     * Bouwt een schoon, tekst-eerst geschikt notenmodel (regels -> slots -> noten).
     * Een slot is één lettergreep met de noot/noten waarop die gezongen wordt
     * (melisma = meerdere noten op één lettergreep). Dit model is de bron voor
     * de eigen SVG-renderer (score_renderer.html).
     */
    fun readScoreModel(context: Context, verse: Verse): String {
        return try {
            val text = readTextForVerse(context, verse)
            val melody = JSONObject(ContentStorage.readBundledAsset(context, melodyFilePath(text, verse)))
            buildScoreModelOrNull(context, melody, text, verse)?.toString() ?: "{}"
        } catch (_: Exception) {
            "{}"
        }
    }

    /**
     * Leest het (optionele) toelichting/achtergrond-veld "about" van een psalm/gezang.
     * Wordt niet in de bladmuziek getoond; bedoeld voor een aparte 'over'-weergave.
     * Geeft null als er geen toelichting is.
     */    fun readAbout(context: Context, type: String, number: Int): String? {
        return try {
            val path = if (type == "Gezang") {
                canonicalTextAssetPath("Gezang", number)
            } else {
                textAssetPath(context, "Psalm", number)
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
            val texts = if (type == "Psalm" && AppSettings.psalmVersion(context) != AppSettings.PSALM_VERSION_1773) {
                listOf(
                    JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(context, type, number))),
                    JSONObject(ContentStorage.readBundledAsset(context, canonicalTextAssetPath(type, number)))
                )
            } else {
                listOf(JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(context, type, number))))
            }
            val map = HashMap<Int, List<String>>()
            texts.asReversed().forEach { text ->
                val verses = text.safeJSONArray("verses")
                for (i in 0 until (verses?.length() ?: 0)) {
                    val verse = verses?.safeJSONObject(i) ?: continue
                    val lines = verse.safeJSONArray("lines")
                    val lineList = ArrayList<String>(lines?.length() ?: 0)
                    for (j in 0 until (lines?.length() ?: 0)) {
                        lineList.add(lines?.safeJSONObject(j)?.safeString("raw") ?: "")
                    }
                    map[verse.safeInt("number")] = lineList
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun readVerseText(context: Context, verse: Verse): String =
        readVerseLines(context, verse.type, verse.number)[verse.verse]
            ?.joinToString("\n")
            .orEmpty()

    /** Metadata uit een gezang-JSON: uitschakel-vlag + korte header-afkorting (max ~10 tekens). */
    data class HymnMeta(val disabled: Boolean, val abbreviation: String?)

    fun readHymnMeta(context: Context, number: Int): HymnMeta {
        return try {
            val text = JSONObject(ContentStorage.readBundledAsset(context, canonicalTextAssetPath("Gezang", number)))
            HymnMeta(
                disabled = text.safeBoolean("disabled"),
                abbreviation = text.safeString("abbreviation").trim().ifBlank { null }
            )
        } catch (_: Exception) {
            HymnMeta(disabled = false, abbreviation = null)
        }
    }

    fun buildScoreModelOrNull(context: Context, melody: JSONObject, text: JSONObject, verse: Verse): JSONObject? {
        return try {
            val melodyLines = melody.safeJSONObject("melody")?.safeJSONArray("lines")
            val verseSource = findVerse(text.safeJSONArray("verses"), verse.verse)
            val lyricLines = verseSource?.safeJSONArray("lines")
            val lineCount = minOf(melodyLines?.length() ?: 0, lyricLines?.length() ?: 0)
            val partName = if (verse.type == "Gezang") "Gezang" else "Psalm"
            // Sommige liederen (bv. Gezang 26 'Ere zij God') houden hun ritmische notatie,
            // ongeacht de iso-ritmische instelling. Vlag staat in het lied-JSON zelf.
            val keepRhythm = text.safeBoolean("keepRhythm")
            val isometric = AppSettings.rhythmMode(context) == AppSettings.RHYTHM_ISOMETRIC && !keepRhythm

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
                    val noteJson = noteJsonModel(note, isometric)
                    val isRest = note.safeBoolean("rest")
                    val isTrailingVerseRest = isRest && lineIndex == lineCount - 1 && !notes.hasVisibleNoteAfter(noteIndex)
                    val hasLyric = note.safeBoolean("lyricSlot") && tokenIndex < tokens.length()
                    when {
                        isTrailingVerseRest -> {
                            currentSlot = null
                        }
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

    private fun noteJsonModel(note: JSONObject, isometric: Boolean): JSONObject {
        val rest = note.safeBoolean("rest")
        val originalType = note.safeString("type")
        val type = if (isometric && !rest && originalType == "quarter") "half" else originalType
        return JSONObject()
            .put("step", note.safeString("step"))
            .put("alter", if (note.safeIsNull("alter")) 0 else note.safeInt("alter"))
            .put("octave", note.safeInt("octave"))
            .put("type", type)
            .put("dot", if (isometric && !rest && originalType == "quarter") false else note.safeBoolean("dot"))
            .put("rest", rest)
    }

    private fun JSONArray.hasVisibleNoteAfter(index: Int): Boolean {
        for (nextIndex in index + 1 until length()) {
            val note = safeJSONObject(nextIndex) ?: continue
            if (!note.safeBoolean("hidden") && !note.safeBoolean("rest")) return true
        }
        return false
    }

    private fun readTextForVerse(context: Context, verse: Verse): JSONObject {
        val preferred = JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(context, verse.type, verse.number)))
        if (verse.type != "Psalm" || AppSettings.psalmVersion(context) == AppSettings.PSALM_VERSION_1773) {
            return preferred
        }
        return if (findVerse(preferred.safeJSONArray("verses"), verse.verse) != null) {
            preferred
        } else {
            JSONObject(ContentStorage.readBundledAsset(context, canonicalTextAssetPath(verse.type, verse.number)))
        }
    }

    private fun textAssetPath(context: Context, type: String, number: Int): String = when (type) {
        "Gezang" -> canonicalTextAssetPath(type, number)
        else -> when (AppSettings.psalmVersion(context)) {
            AppSettings.PSALM_VERSION_DATHEEN -> "content/psalms/datheen/texts/Psalm${pad3(number)}.json"
            AppSettings.PSALM_VERSION_REVIUS -> "content/psalms/revius/texts/Psalm${pad3(number)}.json"
            else -> canonicalTextAssetPath(type, number)
        }
    }

    private fun canonicalTextAssetPath(type: String, number: Int): String = when (type) {
        "Gezang" -> "content/gezangen/texts/Gezang${pad3(number)}.json"
        else -> "content/psalms/texts/Psalm${pad3(number)}.json"
    }

    private fun pad3(number: Int): String = number.toString().padStart(3, '0')

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

    private fun findVerse(verses: JSONArray?, verseNumber: Int): JSONObject? {
        if (verses == null) return null
        for (index in 0 until verses.length()) {
            val verse = verses.safeJSONObject(index) ?: continue
            if (verse.safeInt("number") == verseNumber) return verse
        }
        return null
    }

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