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

    @Volatile
    var isReady = false
        private set

    /**
     * Gezang-nummers (Schriftliederen) die (nog) niet toegankelijk zijn: ze tonen
     * in het overzicht als placeholder en worden overgeslagen bij vorige/volgende.
     * Eén centrale bron zodat lijst én swipe-navigatie hetzelfde gedrag hebben.
     */
    val disabledHymnNumbers = setOf(23)   // 23 = Jesaja 38 – Lofzang van Hizkia

    fun isHymnDisabled(type: String, number: Int): Boolean =
        type == "Gezang" && number in disabledHymnNumbers

    private var bundledVerses = emptyList<Verse>()
    private var downloadedVerses = emptyList<Verse>()

    @Volatile
    private var allVersesCache: List<Verse>? = null

    @Volatile
    private var groupsCache = mutableMapOf<String, List<VerseGroup>>()

    fun refreshDownloadedContent(context: Context) {
        isReady = false
        bundledVerses = readBundledManifest(context)

        val manifestFile = ContentStorage.downloadedManifestFile(context)
        downloadedVerses = if (manifestFile.exists()) {
            try {
                parseDownloadedManifest(manifestFile.readText().trimStart('\uFEFF'))
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        allVersesCache = null
        groupsCache.clear()

        // Pre-build caches
        allVerses()
        bookTitles.forEach { groupedVersesForBook(it) }
        isReady = true
    }

    private fun readBundledManifest(context: Context): List<Verse> = try {
        context.assets.open(BUNDLED_MANIFEST_FILE).bufferedReader().use { reader ->
            parseDownloadedManifest(reader.readText().trimStart('\uFEFF'))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun groupedVersesForBook(bookTitle: String): List<VerseGroup> {
        groupsCache[bookTitle]?.let { return it }

        val versesByNumber = allVerses()
            .filter { it.type == typeForBook(bookTitle) }
            .groupBy { it.number }

        val result = when (bookTitle) {
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
        groupsCache[bookTitle] = result
        return result
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

    private fun allVerses(): List<Verse> {
        allVersesCache?.let { return it }
        val built = (bundledVerses + downloadedVerses)
            .distinctBy { "${it.type}-${it.number}-${it.verse}" }
            .sortedWith(compareBy<Verse> { it.type }.thenBy { it.number }.thenBy { it.verse })
        allVersesCache = built
        return built
    }

    private fun parseDownloadedManifest(json: String): List<Verse> {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: return emptyList()

        val parsed = mutableListOf<Verse>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val number = item.optInt("number", -1)
            val verse = item.optInt("verse", -1)
            val fileName = item.optString("fileName", "").trim()
            if (number <= 0 || verse <= 0 || fileName.isBlank()) continue

            parsed += Verse(
                type = when (item.optString("book")) {
                    "hymns", "gezangen" -> "Gezang"
                    else -> "Psalm"
                },
                number = number,
                verse = verse,
                fileName = fileName,
                firstLine = item.optString("firstLine", ""),
                title = item.optString("title", "")
            )
        }
        return parsed
    }

    private fun typeForBook(bookTitle: String): String = when (bookTitle) {
        PSALMS_TITLE -> "Psalm"
        HYMNS_TITLE -> "Gezang"
        else -> bookTitle.removeSuffix("en")
    }

    private const val BUNDLED_MANIFEST_FILE = "bundled-content-manifest.json"
}
