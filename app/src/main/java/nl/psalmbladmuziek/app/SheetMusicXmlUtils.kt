package nl.psalmbladmuziek.app

object SheetMusicXmlUtils {
    private val MOBILE_SYSTEM_LAYOUT = """
        <system-layout>
            <system-margins>
                <left-margin>0</left-margin>
                <right-margin>0</right-margin>
            </system-margins>
            <system-distance>70</system-distance>
            <top-system-distance>30</top-system-distance>
        </system-layout>
    """.trimIndent()

    private val MOBILE_SHEET_DEFAULTS = """
    <defaults>
        <scaling>
            <millimeters>7</millimeters>
            <tenths>40</tenths>
        </scaling>
        <page-layout>
            <page-height>1200</page-height>
            <page-width>1800</page-width>
            <page-margins type="both">
                <left-margin>8</left-margin>
                <right-margin>8</right-margin>
                <top-margin>8</top-margin>
                <bottom-margin>8</bottom-margin>
            </page-margins>
        </page-layout>
        <lyric-font font-family="Edwin" font-size="3"/>
$MOBILE_SYSTEM_LAYOUT
    </defaults>
    """.trimIndent()

    fun stripInstrumentLabels(musicXml: String): String = musicXml
        .replace(Regex("<part-name>.*?</part-name>", RegexOption.DOT_MATCHES_ALL), "<part-name></part-name>")
        .replace(Regex("<part-abbreviation>.*?</part-abbreviation>", RegexOption.DOT_MATCHES_ALL), "<part-abbreviation></part-abbreviation>")
        .replace(Regex("<instrument-name>.*?</instrument-name>", RegexOption.DOT_MATCHES_ALL), "<instrument-name></instrument-name>")
        .replace(Regex("<creator[^>]*>.*?</creator>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<work-title>.*?</work-title>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<movement-title>.*?</movement-title>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<part-abbreviation-display>.*?</part-abbreviation-display>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<part-name-display>.*?</part-name-display>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<credit\\b[\\s\\S]*?</credit>"), "")
        .let(::normalizeSheetLayout)

    fun extractLyrics(musicXml: String, fallbackFirstLine: String): String {
        val lyricLines = extractLyricLines(musicXml)
        if (lyricLines.isNotEmpty()) {
            return lyricLines.joinToString("\n")
        }

        val words = Regex("<lyric[\\s\\S]*?<text\\b[^>]*>([\\s\\S]*?)</text>")
            .findAll(musicXml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (words.isEmpty()) {
            return fallbackFirstLine
        }

        return words.joinToString(" ")
            .replace(" - ", "")
            .replace(Regex("\\s+([,.;:?!])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeSheetLayout(musicXml: String): String {
        var normalized = musicXml
            .replace(Regex("<left-margin>[\\s\\S]*?</left-margin>"), "<left-margin>8</left-margin>")
            .replace(Regex("<right-margin>[\\s\\S]*?</right-margin>"), "<right-margin>8</right-margin>")
            .replace(Regex("<top-margin>[\\s\\S]*?</top-margin>"), "<top-margin>8</top-margin>")
            .replace(Regex("<bottom-margin>[\\s\\S]*?</bottom-margin>"), "<bottom-margin>8</bottom-margin>")
            .replace(Regex("<page-width>[\\s\\S]*?</page-width>"), "<page-width>1800</page-width>")
            .replace(Regex("<lyric-font\\b[^>]*/>"), "<lyric-font font-family=\"Edwin\" font-size=\"3\"/>")
            .replace(Regex("<lyric(?:\\s[^>]*)?>")) { match -> normalizeLyricTag(match.value) }

        if (!normalized.contains("<defaults>")) {
            return normalized.replace(
                "<part-list>",
                "$MOBILE_SHEET_DEFAULTS\n  <part-list>"
            )
        }

        if (!normalized.contains("<system-layout>")) {
            normalized = normalized.replace(
                "</defaults>",
                "$MOBILE_SYSTEM_LAYOUT\n    </defaults>"
            )
        }

        return normalized
    }

    private fun normalizeLyricTag(tag: String): String {
        val number = Regex("number=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)
        return if (number == null) "<lyric>" else "<lyric number=\"$number\">"
    }

    private fun extractLyricLines(musicXml: String): List<String> {
        val measures = Regex("<measure\\b[\\s\\S]*?</measure>")
            .findAll(musicXml)
            .map { it.value }
            .toList()

        return measures.mapNotNull { measureXml ->
            val entries = Regex("<lyric\\b[\\s\\S]*?</lyric>")
                .findAll(measureXml)
                .map { lyricMatch ->
                    val lyricXml = lyricMatch.value
                    val syllabic = Regex("<syllabic>([\\s\\S]*?)</syllabic>")
                        .find(lyricXml)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                        .orEmpty()
                    val text = Regex("<text\\b[^>]*>([\\s\\S]*?)</text>")
                        .find(lyricXml)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                        .orEmpty()
                    syllabic to text
                }
                .filter { it.second.isNotEmpty() }
                .toList()

            if (entries.isEmpty()) return@mapNotNull null

            val words = mutableListOf<String>()
            var currentWord = StringBuilder()
            entries.forEach { (syllabic, text) ->
                when (syllabic) {
                    "begin", "middle" -> currentWord.append(text)
                    "end" -> {
                        currentWord.append(text)
                        words += currentWord.toString()
                        currentWord = StringBuilder()
                    }
                    else -> {
                        if (currentWord.isNotEmpty()) {
                            words += currentWord.toString()
                            currentWord = StringBuilder()
                        }
                        words += text
                    }
                }
            }
            if (currentWord.isNotEmpty()) {
                words += currentWord.toString()
            }

            words.joinToString(" ")
                .replace(Regex("\\s+([,.;:?!])"), "$1")
                .replace(Regex("\\s+"), " ")
                .trim()
                .takeIf { it.isNotEmpty() }
        }
    }
}
