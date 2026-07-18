package nl.psalmbladmuziek.app

import android.content.Context
import org.json.JSONObject

data class Verse(
    val type: String,
    val number: Int,
    val verse: Int,
    val fileName: String,
    val firstLine: String,
    val title: String = ""
)

data class VerseGroup(
    val title: String,
    val verses: List<Verse>
)

object HymnRepository {
    const val PSALMS_TITLE = "Psalmen"
    const val HYMNS_TITLE = "Enige gezangen"

    val bookTitles = listOf(PSALMS_TITLE, HYMNS_TITLE)

    /**
     * Gezang-nummers (Schriftliederen) die (nog) niet toegankelijk zijn: ze tonen
     * in het overzicht als placeholder en worden overgeslagen bij vorige/volgende.
     * Eén centrale bron zodat lijst én swipe-navigatie hetzelfde gedrag hebben.
     */
    val disabledHymnNumbers = setOf(22)   // 22 = Jesaja 38 – Lofzang van Hizkia

    fun isHymnDisabled(type: String, number: Int): Boolean =
        type == "Gezang" && number in disabledHymnNumbers

    private val fallbackBundledVerses = listOf(
        Verse("Psalm", 1, 1, "Psalm1_v1.xml", "Welzalig hij, die in der bozen raad"),
        Verse("Psalm", 2, 1, "Psalm2_v1.xml", "Wat drift beheerst het woedend heidendom"),
        Verse("Psalm", 3, 1, "Psalm3_v1.xml", "Hoe vrees'lijk groeit, o God"),
        Verse("Psalm", 4, 1, "Psalm4_v1.xml", "Wil mij, wanneer ik roep, verhoren"),
        Verse("Psalm", 5, 1, "Psalm5_v1.xml", "Neem, HEER', mijn bange klacht ter oren"),
        Verse("Psalm", 42, 1, "Psalm42_v1.xml", "'t Hijgend hert, der jacht ontkomen"),
        Verse("Psalm", 116, 1, "Psalm116_v1.xml", "God heb ik lief, want die getrouwe HEER"),
        Verse("Psalm", 116, 2, "Psalm116_v2.xml", "Want Hij neigt Zijn oor tot mij")
    )

    private var bundledVerses = fallbackBundledVerses
    private var downloadedVerses = emptyList<Verse>()

    fun refreshDownloadedContent(context: Context) {
        bundledVerses = readBundledManifest(context).ifEmpty { fallbackBundledVerses }

        val manifestFile = ContentStorage.downloadedManifestFile(context)
        downloadedVerses = if (manifestFile.exists()) {
            parseDownloadedManifest(manifestFile.readText())
        } else {
            emptyList()
        }
    }

    private fun readBundledManifest(context: Context): List<Verse> = try {
        context.assets.open(BUNDLED_MANIFEST_FILE).bufferedReader().use { reader ->
            parseDownloadedManifest(reader.readText().trimStart('\uFEFF'))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun groupedVersesForBook(bookTitle: String): List<VerseGroup> {
        val versesByNumber = allVerses()
            .filter { it.type == typeForBook(bookTitle) }
            .groupBy { it.number }

        return when (bookTitle) {
            PSALMS_TITLE -> (1..150).map { number ->
                VerseGroup(
                    title = "Psalm $number",
                    verses = versesByNumber[number].orEmpty().sortedBy { it.verse }
                )
            }

            else -> versesByNumber
                .toSortedMap()
                .map { (number, verses) ->
                    val sortedVerses = verses.sortedBy { it.verse }
                    VerseGroup(
                        title = sortedVerses.firstOrNull { it.title.isNotBlank() }?.title ?: "${verses.first().type} $number",
                        verses = sortedVerses
                    )
                }
        }
    }

    fun verseByFileName(fileName: String): Verse? = allVerses().firstOrNull { it.fileName == fileName }

    fun versesForPsalm(number: Int): List<Verse> = allVerses()
        .filter { it.type == "Psalm" && it.number == number }
        .sortedBy { it.verse }

    fun versesFor(type: String, number: Int): List<Verse> = allVerses()
        .filter { it.type == type && it.number == number }
        .sortedBy { it.verse }

    /** Beschikbare (niet-uitgeschakelde) nummers van een boektype, oplopend gesorteerd.
     *  Gebruikt voor de psalm-/gezang-keuzelijst in de bladmuziek. */
    fun availableNumbers(type: String): List<Int> = allVerses()
        .filter { it.type == type && !isHymnDisabled(it.type, it.number) }
        .map { it.number }
        .distinct()
        .sorted()

    fun groupsForBook(bookTitle: String): List<VerseGroup> = groupedVersesForBook(bookTitle)
        .filter { it.verses.isNotEmpty() }

    fun nextVerse(currentFileName: String): Verse? = adjacentVerse(currentFileName, offset = 1)

    fun previousVerse(currentFileName: String): Verse? = adjacentVerse(currentFileName, offset = -1)

    private fun adjacentVerse(currentFileName: String, offset: Int): Verse? {
        val verses = allVerses()
        val currentIndex = verses.indexOfFirst { it.fileName == currentFileName }
        if (currentIndex < 0) return null
        // Sla verzen van uitgeschakelde liederen (bv. Gezang 22) over, zodat je er
        // via vorige/volgende niet alsnog belandt.
        var targetIndex = currentIndex + offset
        while (targetIndex in verses.indices) {
            val candidate = verses[targetIndex]
            if (!isHymnDisabled(candidate.type, candidate.number)) return candidate
            targetIndex += offset
        }
        return null
    }

    private fun allVerses(): List<Verse> = (bundledVerses + downloadedVerses)
        .distinctBy { "${it.type}-${it.number}-${it.verse}" }
        .sortedWith(compareBy<Verse> { it.type }.thenBy { it.number }.thenBy { it.verse })

    private fun parseDownloadedManifest(json: String): List<Verse> {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: return emptyList()

        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Verse(
                type = when (item.optString("book")) {
                    "hymns", "gezangen" -> "Gezang"
                    else -> "Psalm"
                },
                number = item.getInt("number"),
                verse = item.getInt("verse"),
                fileName = item.getString("fileName"),
                firstLine = item.optString("firstLine", ""),
                title = item.optString("title", "")
            )
        }
    }

    private fun typeForBook(bookTitle: String): String = when (bookTitle) {
        PSALMS_TITLE -> "Psalm"
        HYMNS_TITLE -> "Gezang"
        else -> bookTitle.removeSuffix("en")
    }

    private const val BUNDLED_MANIFEST_FILE = "bundled-content-manifest.json"
}
