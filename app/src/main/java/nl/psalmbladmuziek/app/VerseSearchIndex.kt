package nl.psalmbladmuziek.app

import android.content.Context

/** Eén zoekresultaat: het vers plus de regel die getoond wordt (de matchende regel). */
data class SearchResult(val verse: Verse, val displayLine: String)

/**
 * In-memory zoekindex over alle verzen (per regel). Wordt eenmalig lui opgebouwd
 * bij de eerste zoekactie. Toont bij een treffer de regel waarin het woord voorkomt.
 */
object VerseSearchIndex {
    private data class Entry(val verse: Verse, val lines: List<String>, val haystack: String)

    @Volatile
    private var entries: List<Entry>? = null

    fun search(context: Context, query: String): List<SearchResult> {
        val index = ensureIndex(context)
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return index.map { SearchResult(it.verse, it.verse.firstLine) }
        }
        return index.filter { it.haystack.contains(q) }.map { entry ->
            val matchingLine = entry.lines.firstOrNull { it.lowercase().contains(q) }
                ?: entry.verse.firstLine
            SearchResult(entry.verse, matchingLine)
        }
    }

    private fun ensureIndex(context: Context): List<Entry> {
        entries?.let { return it }
        val built = build(context)
        entries = built
        return built
    }

    private fun build(context: Context): List<Entry> {
        val result = ArrayList<Entry>()
        HymnRepository.bookTitles.forEach { book ->
            HymnRepository.groupsForBook(book).forEach { group ->
                val first = group.verses.firstOrNull() ?: return@forEach
                val linesByVerse = ScoreBundleRenderer.readVerseLines(context, first.type, first.number)
                group.verses.forEach { verse ->
                    val lines = linesByVerse[verse.verse] ?: listOf(verse.firstLine)
                    val haystack = (verse.firstLine + " " + lines.joinToString(" ")).lowercase()
                    result.add(Entry(verse, lines, haystack))
                }
            }
        }
        return result
    }
}

