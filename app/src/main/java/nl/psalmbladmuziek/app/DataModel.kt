package nl.psalmbladmuziek.app

import android.content.Context
import android.os.Handler
import android.os.Looper
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

const val SCHRIFTLIEDEREN_ABOUT = """Deze categorie bevat Schriftliederen: liederen die in de Bijbel zelf voorkomen of waarvan de Schrift vermeldt dat zij gezongen werden.

Voorbeelden hiervan zijn de lofzangen van Mozes, Mirjam, Hanna, Maria, Zacharias en Simeon, maar ook de hemelse lofzangen uit het boek Openbaring. Deze liederen nemen binnen de Bijbel een bijzondere plaats in doordat zij zowel deel uitmaken van de Schrift als bedoeld zijn om gezongen te worden.

De berijmingen zijn primair gebaseerd op de Statenvertaling. Daarbij is gestreefd naar een zo getrouw mogelijke weergave van de inhoud, woordkeus en beeldspraak van de brontekst.

Het doel is een zingbare Schriftberijming te bieden in de stijl van de Psalmberijming van 1773 en de Enige Gezangen. Betekenis ging daarbij voor rijm en er is bewust vermeden om nieuwe gedachten, uitleg of toepassingen toe te voegen die niet in de brontekst aanwezig zijn.

Waar de oorspronkelijke tekst weinig woorden bevatte, is gebruikgemaakt van herhaling, parallelismen of bijbelse formuleringen uit hetzelfde Schriftgedeelte om een goed zingbaar geheel te vormen.

Deze berijmingen beogen de bijbelse liederen zo getrouw mogelijk weer te geven binnen de Nederlandse gereformeerde psalm- en gezangtraditie."""

const val PSALTERS_ABOUT = """De Psalter van 1912 (The Psalter 1912) is een invloedrijk Engelstalig psalmboek met berijmde psalmen, oorspronkelijk uitgegeven door Noord-Amerikaanse kerken. Het wordt tot op de dag van vandaag veel gebruikt in orthodox-gereformeerde kerken van Nederlandse afkomst in Noord-Amerika en daarbuiten."""


object HymnRepository {
    const val PSALMS_TITLE = "Psalmen"
    const val HYMNS_TITLE = "Enige gezangen"
    const val PSALTERS_TITLE = "Psalters"

    fun bookTitles(context: Context): List<String> = buildList {
        add(PSALMS_TITLE)
        add(HYMNS_TITLE)
    }

    @Volatile
    var isReady = false
        private set

    /**
     * Gezang-nummers die (nog) niet toegankelijk zijn: ze tonen in het overzicht als
     * placeholder en worden overgeslagen bij vorige/volgende. De vlag staat per lied in
     * het lied-JSON ("disabled": true) en wordt bij het laden verzameld.
     */
    @Volatile
    var disabledHymnNumbers: Set<Int> = emptySet()
        private set

    /** Korte header-afkorting per gezang (uit "abbreviation" in het lied-JSON). */
    @Volatile
    var hymnAbbreviations: Map<Int, String> = emptyMap()
        private set

    fun hymnAbbreviation(number: Int): String? = hymnAbbreviations[number]

    fun isHymnDisabled(type: String, number: Int): Boolean =
        type == "Gezang" && number in disabledHymnNumbers

    private var bundledVerses = emptyList<Verse>()

    @Volatile
    private var allVersesCache: List<Verse>? = null

    // Afgeleide opzoektabellen zodat veelgebruikte lookups O(1) zijn i.p.v. een lineaire scan.
    @Volatile
    private var verseByFileNameCache: Map<String, Verse> = emptyMap()

    @Volatile
    private var versesByTypeNumberCache: Map<String, List<Verse>> = emptyMap()

    @Volatile
    private var indexByFileNameCache: Map<String, Int> = emptyMap()

    @Volatile
    private var groupsCache = mutableMapOf<String, List<VerseGroup>>()

    private val loadLock = Any()

    /**
     * Laadt de gebundelde content eenmalig (synchroon) en bouwt de caches op.
     * Doet niets als de content al geladen is; veilig vanaf meerdere threads.
     */
    fun ensureLoaded(context: Context) {
        if (isReady) return
        synchronized(loadLock) {
            if (isReady) return
            loadNow(context.applicationContext)
        }
    }

    /**
     * Laadt de content op een achtergrondthread (indien nog niet geladen) en roept
     * [onReady] op de main-thread aan zodra de caches klaar zijn. Voorkomt jank/ANR
     * bij het opstarten doordat het parsen van het manifest niet op de UI-thread gebeurt.
     */
    fun loadAsync(context: Context, onReady: () -> Unit) {
        if (isReady) {
            onReady()
            return
        }
        val appContext = context.applicationContext
        Thread {
            ensureLoaded(appContext)
            Handler(Looper.getMainLooper()).post(onReady)
        }.start()
    }

    private fun loadNow(context: Context) {
        bundledVerses = readBundledManifest(context)
        allVersesCache = null
        groupsCache.clear()
        VerseSearchIndex.clear()

        // Pre-build caches
        allVerses()
        listOf(PSALMS_TITLE, HYMNS_TITLE).forEach { groupedVersesForBook(it) }
        computeHymnMeta(context)
        isReady = true
    }

    // Leest per bestaand gezang de metadata (uitschakel-vlag + header-afkorting) in één keer.
    private fun computeHymnMeta(context: Context) {
        val disabled = mutableSetOf<Int>()
        val abbreviations = mutableMapOf<Int, String>()
        allVerses().asSequence()
            .filter { it.type == "Gezang" }
            .map { it.number }
            .distinct()
            .forEach { number ->
                val meta = ScoreBundleRenderer.readHymnMeta(context, number)
                if (meta.disabled) disabled.add(number)
                meta.abbreviation?.let { abbreviations[number] = it }
            }
        disabledHymnNumbers = disabled
        hymnAbbreviations = abbreviations
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

    fun verseByFileName(fileName: String): Verse? {
        allVerses()
        return verseByFileNameCache[fileName]
    }

    fun versesFor(type: String, number: Int): List<Verse> {
        allVerses()
        return versesByTypeNumberCache["$type-$number"].orEmpty()
    }

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
        val currentIndex = indexByFileNameCache[currentFileName] ?: return null
        // Sla verzen van uitgeschakelde liederen (bv. Gezang 22) over, zodat je er
        // via vorige/volgende niet alsnog belandt.
        var targetIndex = Math.floorMod(currentIndex + offset, verses.size)
        while (targetIndex != currentIndex) {
            val candidate = verses[targetIndex]
            if (!isHymnDisabled(candidate.type, candidate.number)) return candidate
            targetIndex = Math.floorMod(targetIndex + offset, verses.size)
        }
        return null
    }

    private fun allVerses(): List<Verse> {
        allVersesCache?.let { return it }
        val built = bundledVerses
            .distinctBy { "${it.type}-${it.number}-${it.verse}" }
            .sortedWith(compareBy<Verse> { bookOrder(it.type) }.thenBy { it.number }.thenBy { it.verse })
        // built is al gesorteerd op nummer+vers, dus groupBy levert per lied gesorteerde lijsten.
        verseByFileNameCache = built.associateBy { it.fileName }
        versesByTypeNumberCache = built.groupBy { "${it.type}-${it.number}" }
        indexByFileNameCache = built.withIndex().associate { (index, verse) -> verse.fileName to index }
        allVersesCache = built
        return built
    }

    private fun bookOrder(type: String): Int = when (type) {
        "Psalm" -> 0
        "Gezang" -> 1
        else -> 2
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
                    "psalters", "psalter" -> "Psalter"
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
        PSALTERS_TITLE -> "Psalter"
        else -> bookTitle.removeSuffix("en")
    }

    private const val BUNDLED_MANIFEST_FILE = "bundled-content-manifest.json"
}
