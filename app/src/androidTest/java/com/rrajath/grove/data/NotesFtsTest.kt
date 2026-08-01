package com.rrajath.grove.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The FTS mirror against a real SQLite: that the virtual table is actually
 * created on this platform, and that indexing keeps it in lockstep with `notes`
 * , the failure mode the JVM suite structurally cannot see.
 */
@RunWith(AndroidJUnit4::class)
class NotesFtsTest {

    private lateinit var db: GroveDatabase
    private lateinit var index: RoomNoteIndex

    @Before
    fun setUp() {
        db = GroveDatabase.inMemory(ApplicationProvider.getApplicationContext())
        index = RoomNoteIndex(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fts5TrigramIsAvailableOnThisDevice() = runBlocking {
        // Forces the bootstrap callback, which sets the flag.
        db.indexDao().notebooks()
        assertTrue("FTS5 trigram should be available on minSdk 34", db.ftsAvailable)
    }

    @Test
    fun indexingPopulatesOneFtsRowPerNote() = runBlocking {
        indexFile("work.org", "* One\nbody one\n* Two\nbody two\n** Two point one\nnested body\n")
        assertEquals(3, noteCount())
        assertEquals(3, db.indexDao().ftsRowCount())
    }

    @Test
    fun reindexingAFileLeavesNoStaleRows() = runBlocking {
        indexFile("work.org", "* One\nbody one\n* Two\nbody two\n* Three\nbody three\n")
        assertEquals(3, db.indexDao().ftsRowCount())

        indexFile("work.org", "* One\nbody one edited\n")
        assertEquals(1, noteCount())
        assertEquals(1, db.indexDao().ftsRowCount())
        // The removed headings are gone from the index, not merely shadowed.
        assertEquals(emptyList<NoteKey>(), keysMatching("\"body three\""))
        assertEquals(1, keysMatching("\"body one edited\"").size)
    }

    @Test
    fun reindexingOneFileLeavesOtherFilesAlone() = runBlocking {
        indexFile("work.org", "* Work heading\nwork body\n")
        indexFile("home.org", "* Home heading\nhome body\n")
        assertEquals(2, db.indexDao().ftsRowCount())

        indexFile("work.org", "* Work heading\nwork body changed\n")
        assertEquals(2, db.indexDao().ftsRowCount())
        assertEquals(1, keysMatching("\"home body\"").size)
    }

    @Test
    fun removingANotebookRemovesItsFtsRows() = runBlocking {
        indexFile("work.org", "* One\nbody one\n")
        indexFile("home.org", "* Two\nbody two\n")

        db.indexDao().removeNotebook("work.org")

        assertEquals(1, noteCount())
        assertEquals(1, db.indexDao().ftsRowCount())
        assertEquals(emptyList<NoteKey>(), keysMatching("\"body one\""))
    }

    @Test
    fun clearingTheIndexClearsTheFtsTable() = runBlocking {
        indexFile("work.org", "* One\nbody one\n* Two\nbody two\n")

        db.indexDao().clearAll()

        assertEquals(0, noteCount())
        assertEquals(0, db.indexDao().ftsRowCount())
    }

    @Test
    fun bodiesPastTheOldFourThousandCharCapAreSearchable() = runBlocking {
        val filler = "lorem ipsum dolor sit amet ".repeat(300) // ~8100 chars
        indexFile("long.org", "* Long note\n$filler\nneedleatend\n")

        assertTrue(filler.length > 4000)
        assertEquals(1, keysMatching("\"needleatend\"").size)
    }

    @Test
    fun matchingIsCaseInsensitiveAndInfix() = runBlocking {
        indexFile("work.org", "* Committee Meeting\nAgenda for the QUARTERLY review\n")

        // Infix, mid-word: the substring semantics QueryMatcher documents.
        assertEquals(1, keysMatching("\"mitte\"").size)
        assertEquals(1, keysMatching("\"quarterly\"").size)
        assertEquals(1, keysMatching("\"COMMITTEE\"").size)
    }

    @Test
    fun titlesAndBodiesAreBothIndexed() = runBlocking {
        indexFile("work.org", "* Distinctive Title\nunrelated body words\n")

        assertEquals(1, keysMatching("\"Distinctive\"").size)
        assertEquals(1, keysMatching("\"unrelated\"").size)
    }

    private suspend fun indexFile(fileName: String, text: String) {
        index.indexNotebook(
            fileName = fileName,
            revision = "rev-${text.length}",
            text = text,
            lastModified = 1L,
            conflictFileName = null,
        )
    }

    private suspend fun noteCount(): Int =
        db.indexDao().notesMatching(rawQuery("SELECT * FROM notes")).size

    private suspend fun keysMatching(match: String): List<NoteKey> =
        db.indexDao().noteKeysMatching(
            rawQuery("SELECT fileName, lineIndex FROM notes_fts WHERE notes_fts MATCH ?", listOf(match))
        )
}
