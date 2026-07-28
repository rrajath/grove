package com.rrajath.grove.search

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.data.NoteEntity
import com.rrajath.grove.data.RoomNoteIndex
import com.rrajath.grove.data.rawQuery
import com.rrajath.grove.data.toNoteMeta
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A/B measurement of one search over a large synthetic vault: the old path
 * (materialise every row, then scan in Kotlin) against the new one (let SQLite
 * and FTS5 return only candidates).
 *
 * This is a measurement harness, not a pass/fail gate — timings are logged, not
 * asserted, because thresholds would just be flaky. What it *does* assert is
 * that both paths return identical results at vault scale, which is the parity
 * guarantee `FtsParityTest` makes on a small fixture.
 *
 * Read the numbers with `adb logcat -s SearchLatency`. Treat emulator runs as
 * directional only; the go/no-go figure has to come from a physical device (see
 * docs/benchmarking.md).
 */
@RunWith(AndroidJUnit4::class)
class SearchLatencyBenchmark {

    private lateinit var db: GroveDatabase

    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() = runBlocking {
        db = GroveDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val index = RoomNoteIndex(db)
        val seeded = System.nanoTime()
        repeat(FILES) { file -> index.indexNotebook("vault$file.org", "rev", notebookText(file), 1L, null) }
        Log.i(TAG, "seeded ${FILES * NOTES_PER_FILE} notes in ${msSince(seeded)} ms")
        assertTrue("FTS should be available for a meaningful comparison", db.ftsAvailable)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun compareFullScanAgainstNarrowedSearch() = runBlocking {
        val queries = listOf(
            "zebrafish",          // rare term: a handful of hits
            "meeting",            // common term: many hits
            "meeting quarterly",  // two common terms ANDed
            "i.TODO zebrafish",   // facet + rare text
            "t.alpha",            // facet only, no text at all
        )

        Log.i(TAG, "--- ${FILES * NOTES_PER_FILE} notes across $FILES files ---")
        Log.i(TAG, String.format("%-22s %10s %10s %10s %8s", "query", "fullScan", "narrowed", "rowsRead", "hits"))

        for (input in queries) {
            val query = QueryParser.parse(input)

            // Warm both paths so neither pays first-run statement compilation.
            fullScan(query)
            narrowed(query)

            val fullStart = System.nanoTime()
            val expected = fullScan(query)
            val fullMs = msSince(fullStart)

            val narrowStart = System.nanoTime()
            val actual = narrowed(query)
            val narrowMs = msSince(narrowStart)

            val rowsRead = rowsFor(query).size
            Log.i(
                TAG,
                String.format(
                    "%-22s %9s %9s %10d %8d",
                    input, "$fullMs ms", "$narrowMs ms", rowsRead, expected.size,
                ),
            )

            // The point of the whole migration: same answers, less work.
            assertEquals("results for \"$input\"", expected, actual)
        }
    }

    private suspend fun fullScan(query: SearchQuery): List<String> {
        val all = db.indexDao().notesMatching(rawQuery(FULL_TABLE)).map { it.toNoteMeta() }
        return QueryMatcher.filter(all, query, today).map { "${it.fileName}:${it.lineIndex}" }
    }

    private suspend fun narrowed(query: SearchQuery): List<String> =
        QueryMatcher.filter(rowsFor(query).map { it.toNoteMeta() }, query, today)
            .map { "${it.fileName}:${it.lineIndex}" }

    private suspend fun rowsFor(query: SearchQuery): List<NoteEntity> {
        val candidate = NoteCandidateQuery.build(FtsQuery.matchExpression(query), query)
        return db.indexDao().notesMatching(rawQuery(candidate.sql, candidate.args))
    }

    private fun msSince(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

    /**
     * Bodies are long enough to be realistic (and to have been truncated by the
     * old 4000-char cap). "zebrafish" is planted in a tiny fraction of notes so
     * a rare-term search has something selective to find.
     */
    private fun notebookText(file: Int): String = buildString {
        repeat(NOTES_PER_FILE) { n ->
            val id = file * NOTES_PER_FILE + n
            val keyword = if (id % 3 == 0) "TODO " else ""
            val tag = if (id % 2 == 0) ":alpha:" else ":beta:"
            appendLine("* $keyword Note $id about the quarterly meeting $tag")
            if (id % 250 == 0) appendLine("Only a few notes mention zebrafish anywhere in the vault.")
            repeat(12) { line ->
                appendLine(
                    "Line $line of note $id: agenda, roadmap and meeting notes with " +
                        "enough prose to make the body realistically long for search."
                )
            }
        }
    }

    private companion object {
        const val TAG = "SearchLatency"
        const val FILES = 40
        const val NOTES_PER_FILE = 100
        const val FULL_TABLE = "SELECT * FROM notes ORDER BY fileName, lineIndex"
    }
}
