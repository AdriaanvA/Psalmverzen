package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ScoreBundleRenderer {
    private data class ScoreSource(
        val melody: JSONObject,
        val text: JSONObject
    )

    /**
     * Bouwt een schoon, tekst-eerst geschikt notenmodel (regels -> slots -> noten).
     * Een slot is één lettergreep met de noot/noten waarop die gezongen wordt
     * (melisma = meerdere noten op één lettergreep). Dit model is de bron voor
     * de eigen SVG-renderer (score_renderer.html).
     */
    fun readScoreModel(context: Context, verse: Verse): String {
        return try {
            val source = readScoreSource(context, verse)
            transformScoreModel(
                melody = source.melody,
                text = source.text,
                verse = verse,
                useIsometricRhythm = AppSettings.rhythmMode(context) == AppSettings.RHYTHM_ISOMETRIC
            )?.toString() ?: "{}"
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
            val path = textAssetPath(context, type, number)
            val text = JSONObject(ContentStorage.readBundledAsset(context, path))
            text.safeString("about").trim().ifBlank { null }?.let(::enrichMusicalInscription)
        } catch (_: Exception) {
            null
        }
    }

    private fun enrichMusicalInscription(about: String): String {
        val terms = listOf(
            "Neginoth" to "Hebreeuws: עַל־הַנְּגִינוֹת; doorgaans verbonden met snaarinstrumenten",
            "Nehiloth" to "Hebreeuws: אֶל־הַנְּחִילוֹת; muzikale betekenis niet zeker",
            "Scheminith" to "Hebreeuws: עַל־הַשְּׁמִינִית; ‘achtste’, muzikale betekenis niet zeker",
            "Gittith" to "Hebreeuws: עַל־הַגִּתִּית; verbonden met een muziekinstrument of met Gath",
            "Muth-labben" to "Hebreeuws: עַל־מוּת לַבֵּן; betekenis niet zeker",
            "Aijeleth Schahar" to "Hebreeuws: עַל־אַיֶּלֶת הַשַּׁחַר; ‘hinde van de dageraad’",
            "Jeduthun" to "Hebreeuws: לַיְדֻתוּן; naam van een Leviet en zanger, verbonden aan de tempeldienst",
            "Alamoth" to "Hebreeuws: עַל־עֲלָמוֹת; ‘jonge vrouwen’ of ‘meisjes’",
            "Mahalath Leannoth" to "Hebreeuws: עַל־מָחֲלַת לְעַנּוֹת; betekenis en muzikale functie niet zeker",
            "Mahalath" to "Hebreeuws: עַל־מָחֲלַת; betekenis en muzikale functie niet zeker",
            "Al-taschith" to "Hebreeuws: אַל־תַּשְׁחֵת; ‘verderf niet’ of ‘breng niet om’",
            "Jonath-elem-rechokim" to "Hebreeuws: עַל־יוֹנַת אֵלֶם רְחֹקִים; betekenis niet zeker",
            "Shushan-eduth" to "Hebreeuws: עַל־שׁוּשַׁן עֵדוּת; ‘lelie van het getuigenis’",
            "Neginah" to "Hebreeuws: עַל־נְגִינָה; verbonden met muziek, waarschijnlijk in het bijzonder snaarinstrumenten."
        )
        val citationMarker = " De Statenvertaling luidt:"
        val citationStart = about.indexOf(citationMarker)
        val descriptivePart = if (citationStart >= 0) about.substring(0, citationStart) else about
        val citationPart = if (citationStart >= 0) about.substring(citationStart) else ""
        var result = descriptivePart
        terms.forEach { (term, explanation) ->
            if (result.contains(term) && !result.contains("$term (Hebreeuws:")) {
                result = result.replace(term, "$term ($explanation)")
            }
        }
        return result + citationPart
    }

    /**
     * Leest de losse (platte) tekstregels per versnummer voor een psalm/gezang.
     * Gebruikt voor de zoekindex (om de matchende regel te kunnen tonen).
     */
    fun readVerseLines(context: Context, type: String, number: Int): Map<Int, List<String>> {
        return try {
            val texts = when {
                type == "Psalm" && AppSettings.psalmVersion(context) != AppSettings.PSALM_VERSION_1773 -> listOf(
                    JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(context, type, number))),
                    JSONObject(ContentStorage.readBundledAsset(context, canonicalTextAssetPath(type, number)))
                )
                else -> listOf(JSONObject(ContentStorage.readBundledAsset(context, textAssetPath(context, type, number))))
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

    fun readVerseText(context: Context, verse: Verse): String = try {
        val text = readTextForVerse(context, verse)
        val verseLines = findVerse(text.safeJSONArray("verses"), verse.verse)
            ?.safeJSONArray("lines")
            ?.toRawLines()
            .orEmpty()
        val refrainLines = text.safeJSONObject("refrain")
            ?.safeJSONArray("lines")
            ?.toRawLines()
            .orEmpty()
        (verseLines + refrainLines).joinToString("\n")
    } catch (_: Exception) {
        ""
    }

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

    internal fun transformScoreModel(
        melody: JSONObject,
        text: JSONObject,
        verse: Verse,
        useIsometricRhythm: Boolean
    ): JSONObject? {
        return try {
            val melodyLines = combinedLines(melody.safeJSONObject("melody")?.safeJSONArray("lines"), melody.safeJSONObject("refrain")?.safeJSONArray("lines"))
            val verseSource = findVerse(text.safeJSONArray("verses"), verse.verse)
            val lyricLines = combinedLines(verseSource?.safeJSONArray("lines"), text.safeJSONObject("refrain")?.safeJSONArray("lines"))
            val lineCount = minOf(melodyLines?.length() ?: 0, lyricLines?.length() ?: 0)
            val partName = when (verse.type) {
                "Gezang" -> "Gezang"
                "Psalter" -> "Psalter"
                else -> "Psalm"
            }
            // Sommige liederen (bv. Gezang 26 'Ere zij God') houden hun ritmische notatie,
            // ongeacht de iso-ritmische instelling. Vlag staat in het lied-JSON zelf.
            val keepRhythm = text.safeBoolean("keepRhythm")
            val isometric = useIsometricRhythm && !keepRhythm

            val linesJson = JSONArray()
            for (lineIndex in 0 until lineCount) {
                val notes = melodyLines?.optJSONArray(lineIndex) ?: continue
                val line = lyricLines?.optJSONObject(lineIndex)
                val tokens = line?.safeJSONArray("tokens") ?: JSONArray()
                val slots = JSONArray()
                var tokenIndex = 0
                var currentSlot: JSONObject? = null
                for (noteIndex in 0 until notes.length()) {
                    val note = notes.safeJSONObject(noteIndex) ?: continue
                    if (note.safeBoolean("hidden")) continue
                    val playbackId = "${verse.fileName}:$lineIndex:$noteIndex"
                    val noteJson = noteJsonModel(note, isometric, playbackId)
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

    private fun noteJsonModel(note: JSONObject, isometric: Boolean, playbackId: String): JSONObject {
        val rest = note.safeBoolean("rest")
        val originalType = note.safeString("type")
        val type = if (isometric && !rest && originalType == "quarter") "half" else originalType
        return JSONObject()
            .put("playbackId", playbackId)
            .put("step", note.safeString("step"))
            .put("alter", if (note.safeIsNull("alter")) 0 else note.safeInt("alter"))
            .put("octave", note.safeInt("octave"))
            .put("type", type)
            .put("dot", if (isometric && !rest && originalType == "quarter") false else note.safeBoolean("dot"))
            .put("rest", rest)
    }

    private fun combinedLines(primary: JSONArray?, appended: JSONArray?): JSONArray {
        val result = JSONArray()
        listOfNotNull(primary, appended).forEach { source ->
            for (index in 0 until source.length()) result.put(source.opt(index))
        }
        return result
    }

    private fun JSONArray.toRawLines(): List<String> = List(length()) { index ->
        safeJSONObject(index)?.safeString("raw").orEmpty()
    }

    private fun JSONArray.hasVisibleNoteAfter(index: Int): Boolean {
        for (nextIndex in index + 1 until length()) {
            val note = safeJSONObject(nextIndex) ?: continue
            if (!note.safeBoolean("hidden") && !note.safeBoolean("rest")) return true
        }
        return false
    }

    private fun readScoreSource(context: Context, verse: Verse): ScoreSource {
        val text = readTextForVerse(context, verse)
        val melody = JSONObject(ContentStorage.readBundledAsset(context, melodyFilePath(text, verse)))
        return ScoreSource(melody = melody, text = text)
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

    private fun textAssetPath(
        context: Context,
        type: String,
        number: Int,
        verseNumber: Int? = null
    ): String = when (type) {
        "Gezang" -> canonicalTextAssetPath(type, number)
        "Psalter" -> "content/psalters/texts/Psalter${number}.json"
        else -> when (AppSettings.psalmVersion(context)) {
            AppSettings.PSALM_VERSION_DATHEEN -> "content/psalms/datheen/texts/Psalm${pad3(number)}.json"
            AppSettings.PSALM_VERSION_REVIUS -> "content/psalms/revius/texts/Psalm${pad3(number)}.json"
            AppSettings.PSALM_VERSION_MARNIX -> "content/psalms/marnix/texts/Psalm${pad3(number)}.json"
            else -> canonicalTextAssetPath(type, number)
        }
    }

    private fun canonicalTextAssetPath(type: String, number: Int): String = when (type) {
        "Gezang" -> "content/gezangen/texts/Gezang${pad3(number)}.json"
        "Psalter" -> "content/psalters/texts/Psalter${number}.json"
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