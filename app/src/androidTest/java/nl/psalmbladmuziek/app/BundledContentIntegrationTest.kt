package nl.psalmbladmuziek.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@RunWith(AndroidJUnit4::class)
class BundledContentIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun loadRepository() {
        HymnRepository.ensureLoaded(context)
    }

    @Test
    fun everyManifestVerseHasReadableText() {
        val failures = allVerses().filter { verse ->
            ScoreBundleRenderer.readVerseText(context, verse).isBlank()
        }

        assertTrue("Unreadable verse text: ${failures.take(10)}", failures.isEmpty())
    }

    @Test
    fun everyMusicalVerseProducesAValidScoreModel() {
        val failures = allVerses()
            .filterNot { it.fileName.endsWith(".txt", ignoreCase = true) }
            .filter { verse ->
                val model = JSONObject(ScoreBundleRenderer.readScoreModel(context, verse))
                model.optJSONArray("lines")?.length() ?: 0 == 0
            }

        assertTrue("Invalid score models: ${failures.take(10)}", failures.isEmpty())
    }

    @Test
    fun playbackEventsContainStableIdsAndRespectTransposition() {
        val verses = HymnRepository.versesFor("Psalm", 1).take(2)
        val original = MelodyPlaybackModel.buildEvents(context, verses, transposeSemitones = 0)
        val transposed = MelodyPlaybackModel.buildEvents(context, verses, transposeSemitones = 2)
        val soundingOriginal = original.filter { it.frequenciesHz.isNotEmpty() }
        val soundingTransposed = transposed.filter { it.frequenciesHz.isNotEmpty() }

        assertFalse(soundingOriginal.isEmpty())
        assertEquals(soundingOriginal.size, soundingTransposed.size)
        assertTrue(soundingOriginal.all { !it.playbackId.isNullOrBlank() })
        val expectedRatio = 2.0.pow(2.0 / 12.0)
        assertEquals(
            expectedRatio,
            soundingTransposed.first().frequenciesHz.first() / soundingOriginal.first().frequenciesHz.first(),
            0.000001
        )
        assertTrue(original.any { it.frequenciesHz.isEmpty() && it.durationBeats == 0.5 })
    }

    @Test
    fun everyTextLineMatchesItsVerseMelodySyllableCount() {
        val failures = allVerses().flatMap { verse ->
            val textPath = when (verse.type) {
                "Gezang" -> "content/gezangen/texts/Gezang${verse.number.toString().padStart(3, '0')}.json"
                "Psalm" -> "content/psalms/texts/Psalm${verse.number.toString().padStart(3, '0')}.json"
                else -> return@flatMap emptyList()
            }
            val text = JSONObject(ContentStorage.readBundledAsset(context, textPath))
            val verseObject = text.optJSONArray("verses")?.let { verses ->
                (0 until verses.length()).asSequence()
                    .mapNotNull { verses.optJSONObject(it) }
                    .firstOrNull { it.optInt("number") == verse.verse }
            } ?: return@flatMap emptyList()
            val melodyPath = verseObject.optString("melodyFile").ifBlank {
                text.optString("melodyFile").ifBlank {
                    "content/melodies/${verse.fileName.removeSuffix("_v${verse.verse}.json")}.json"
                }
            }
            val melodyLines = JSONObject(ContentStorage.readBundledAsset(context, melodyPath))
                .optJSONObject("melody")?.optJSONArray("lines") ?: return@flatMap emptyList()
            val textLines = verseObject.optJSONArray("lines") ?: return@flatMap emptyList()
            buildList {
                for (lineIndex in 0 until textLines.length()) {
                    val line = textLines.optJSONObject(lineIndex) ?: continue
                    val tokenCount = line.optJSONArray("tokens")?.length() ?: 0
                    val notes = melodyLines.optJSONArray(lineIndex % melodyLines.length()) ?: continue
                    val lyricSlots = (0 until notes.length()).count { noteIndex ->
                        val note = notes.optJSONObject(noteIndex) ?: return@count false
                        note.optBoolean("lyricSlot") && !note.optBoolean("hidden") && !note.optBoolean("rest")
                    }
                    if (tokenCount != lyricSlots) {
                        add("${verse.fileName} line ${lineIndex + 1}: tokens=$tokenCount slots=$lyricSlots")
                    }
                }
            }
        }

        assertTrue("Text/melody syllable mismatches: ${failures.take(10)}", failures.isEmpty())
    }

    private fun allVerses(): List<Verse> = HymnRepository.bookTitles(context)
        .flatMap(HymnRepository::groupsForBook)
        .flatMap { it.verses }
}
