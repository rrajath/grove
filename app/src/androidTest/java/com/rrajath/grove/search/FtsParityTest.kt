package com.rrajath.grove.search

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
 * The safety net for the whole migration: for every query shape, the results
 * produced from the SQL-narrowed candidate set must be **identical** to the
 * results produced by scanning the entire vault.
 *
 * Narrowing is only ever allowed to be a superset filter, so any divergence
 * here is a user-visible search regression, a note that silently stops being
 * findable. The cases below are the ones where a superset is easiest to get
 * wrong: short terms the trigram tokenizer cannot index, groups with no text at
 * all, negation, non-ASCII text, and bodies past the old 4000-char cap.
 */
@RunWith(AndroidJUnit4::class)
class FtsParityTest {

    private lateinit var db: GroveDatabase

    private val today: LocalDate = LocalDate.of(2026, 7, 27)

    @Before
    fun setUp() = runBlocking {
        db = GroveDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val index = RoomNoteIndex(db)
        FIXTURE.forEach { (name, text) -> index.indexNotebook(name, "rev", text, 1L, null) }
        assertTrue("fixture should have been indexed", db.ftsAvailable)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- plain text ---

    @Test fun singleTerm() = assertParity("meeting", expectHits = true)
    @Test fun differentCase() = assertParity("MEETING", expectHits = true)
    @Test fun infixMidWord() = assertParity("mitte", expectHits = true)
    @Test fun twoTermsAnded() = assertParity("meeting roadmap", expectHits = true)
    @Test fun termMatchingNothing() = assertParity("nonexistentterm")
    @Test fun punctuationInTerm() = assertParity("e.g", expectHits = true)

    /** Past the old MAX_BODY_CHARS truncation: unsearchable before this migration. */
    @Test fun deepInsideALongBody() = assertParity("needleatend", expectHits = true)

    // --- terms too short for the trigram tokenizer ---

    @Test fun oneCharTerm() = assertParity("a", expectHits = true)
    @Test fun twoCharTerm() = assertParity("of", expectHits = true)
    @Test fun shortTermBesideALongOne() = assertParity("of meeting", expectHits = true)

    // --- OR groups and facet-only groups ---

    @Test fun orOfTwoTextGroups() = assertParity("meeting OR plumber", expectHits = true)
    @Test fun facetOnlyGroup() = assertParity("i.TODO", expectHits = true)
    @Test fun textOredWithAFacetOnlyGroup() = assertParity("meeting OR i.TODO", expectHits = true)
    @Test fun textOredWithAShortTerm() = assertParity("meeting OR of", expectHits = true)
    @Test fun facetAndTextInOneGroup() = assertParity("i.TODO meeting", expectHits = true)

    // --- negation ---

    @Test fun negatedText() = assertParity("meeting .draft", expectHits = true)
    @Test fun onlyNegatedText() = assertParity(".draft", expectHits = true)
    @Test fun negatedFacet() = assertParity("meeting .i.DONE", expectHits = true)

    // --- structured tokens, which NoteCandidateQuery also pushes down ---

    @Test fun stateToken() = assertParity("i.IN-PROGRESS", expectHits = true)
    @Test fun noStateToken() = assertParity("i.none", expectHits = true)
    @Test fun inheritedTagToken() = assertParity("t.work", expectHits = true)
    @Test fun ownTagToken() = assertParity("tn.archive", expectHits = true)
    @Test fun priorityToken() = assertParity("p.A", expectHits = true)
    @Test fun notebookToken() = assertParity("b.work", expectHits = true)
    @Test fun notebookTokenWithSuffix() = assertParity("b.work.org", expectHits = true)
    @Test fun scheduledWindow() = assertParity("s.3d", expectHits = true)
    @Test fun deadlineWindow() = assertParity("d.1w", expectHits = true)
    @Test fun closedWindow() = assertParity("c.1w", expectHits = true)
    @Test fun createdWindow() = assertParity("cr.1m", expectHits = true)
    @Test fun everythingAtOnce() = assertParity("meeting i.TODO t.work p.A")
    @Test fun explicitSortOrder() = assertParity("o.priority meeting", expectHits = true)

    // --- non-ASCII ---

    @Test fun cjkTerm() = assertParity("会議録", expectHits = true)
    @Test fun cjkInfix() = assertParity("録の内", expectHits = true)
    @Test fun cjkTermTooShortForTrigram() = assertParity("会議", expectHits = true)
    @Test fun accentedTerm() = assertParity("café", expectHits = true)
    @Test fun accentedTermInDifferentCase() = assertParity("CAFÉ", expectHits = true)
    @Test fun cyrillicTerm() = assertParity("задача", expectHits = true)
    @Test fun cyrillicTermInDifferentCase() = assertParity("ЗАДАЧА", expectHits = true)
    // Matches nothing by construction (OrgParser's tag syntax is ASCII-only)
    // but it must still take the no-pushdown path without error.
    @Test fun nonAsciiTagToken() = assertParity("t.café")

    // --- characters that are FTS syntax ---

    @Test fun quotedFragment() = assertParity("\"hello", expectHits = true)
    @Test fun asteriskInTerm() = assertParity("wild*card", expectHits = true)
    @Test fun bareOperatorWord() = assertParity("NEAR", expectHits = true)
    @Test fun hyphenPrefixedTerm() = assertParity("well-known", expectHits = true)

    /**
     * The filter-chip half of the pushdown. The chips are applied in Kotlin
     * either way, so the check is that the SQL never withholds a row the Kotlin
     * predicate would have kept.
     */
    @Test
    fun facetChipsNarrowWithoutLosingRows() = runBlocking {
        val chipSets = listOf(
            FacetNarrowing(notebook = "work.org"),
            FacetNarrowing(states = setOf("TODO")),
            FacetNarrowing(states = setOf("TODO", "IN-PROGRESS")),
            FacetNarrowing(includeNoState = true),
            FacetNarrowing(states = setOf("TODO"), includeNoState = true),
            FacetNarrowing(priorities = setOf("A")),
            FacetNarrowing(tags = setOf("work")),
            FacetNarrowing(tags = setOf("work", "home")),
            // "work" must not match the "network" tag: the chips are whole-tag.
            FacetNarrowing(tags = setOf("net")),
            FacetNarrowing(tags = setOf("café")),
            FacetNarrowing(scheduled = DatePresence.PRESENT),
            FacetNarrowing(scheduled = DatePresence.ABSENT),
            FacetNarrowing(deadline = DatePresence.PRESENT),
            FacetNarrowing(deadline = DatePresence.ABSENT),
            FacetNarrowing(states = setOf("TODO"), tags = setOf("work"), scheduled = DatePresence.PRESENT),
        )
        val everything = allRows().map { it.toNoteMeta() }
        for (chips in chipSets) {
            val expected = everything.filter { it.matchesChips(chips) }
            val narrowed = rowsFor(NoteCandidateQuery.build(null, null, chips))
                .map { it.toNoteMeta() }
                .filter { it.matchesChips(chips) }
            assertEquals("chips $chips", expected.map { it.key() }, narrowed.map { it.key() })
        }
    }

    @Test
    fun narrowingActuallyNarrows() = runBlocking {
        // Guards against the whole suite passing because everything silently
        // fell back to the full scan.
        val query = QueryParser.parse("needleatend")
        val candidate = NoteCandidateQuery.build(FtsQuery.matchExpression(query), query)
        assertEquals(1, rowsFor(candidate).size)
        assertTrue("FTS should return far fewer rows than the table", allRows().size > 5)
    }

    // --- harness ---

    private fun assertParity(input: String, expectHits: Boolean = false) = runBlocking {
        val query = QueryParser.parse(input)
        val expected = QueryMatcher.filter(allRows().map { it.toNoteMeta() }, query, today)

        val candidate = NoteCandidateQuery.build(FtsQuery.matchExpression(query), query)
        val actual = QueryMatcher.filter(rowsFor(candidate).map { it.toNoteMeta() }, query, today)

        assertEquals("results for \"$input\"", expected.map { it.key() }, actual.map { it.key() })
        if (expectHits) {
            assertTrue("\"$input\" should match something in the fixture", expected.isNotEmpty())
        }
    }

    private suspend fun allRows(): List<NoteEntity> =
        rowsFor(CandidateSql("SELECT * FROM notes ORDER BY fileName, lineIndex", emptyList()))

    private suspend fun rowsFor(candidate: CandidateSql): List<NoteEntity> =
        db.indexDao().notesMatching(rawQuery(candidate.sql, candidate.args))

    private fun NoteMeta.key() = "$fileName:$lineIndex"

    /** Mirrors SearchViewModel.matchesFilters for the facets SQL can narrow on. */
    private fun NoteMeta.matchesChips(f: FacetNarrowing): Boolean {
        if (f.notebook != null && fileName != f.notebook) return false
        if (f.states.isNotEmpty() || f.includeNoState) {
            val ok = (keyword != null && keyword in f.states) || (f.includeNoState && keyword == null)
            if (!ok) return false
        }
        if (f.priorities.isNotEmpty() && priority !in f.priorities) return false
        if (f.tags.isNotEmpty() && f.tags.none { inheritedTags.contains(it) }) return false
        if (f.scheduled == DatePresence.PRESENT && scheduledDate == null) return false
        if (f.scheduled == DatePresence.ABSENT && scheduledDate != null) return false
        if (f.deadline == DatePresence.PRESENT && deadlineDate == null) return false
        if (f.deadline == DatePresence.ABSENT && deadlineDate != null) return false
        return true
    }

    private companion object {
        /** ~8 KB, so the note's marker sits well past the old 4000-char cap. */
        private val FILLER = "lorem ipsum dolor sit amet consectetur ".repeat(200)

        val FIXTURE = listOf(
            "work.org" to """
                * TODO Weekly meeting notes :work:standup:
                SCHEDULED: <2026-07-28 Tue>
                Notes about the committee and the roadmap draft, e.g. the well-known parts.
                ** DONE Old task :archive:
                CLOSED: [2026-07-24 Fri]
                Nothing much of note here.
                * TODO [#A] Ship the release :work:urgent:
                DEADLINE: <2026-07-29 Wed>
                :PROPERTIES:
                :CREATED: [2026-07-10 Fri]
                :END:
                Release checklist and meeting logistics, part of the plan.
                * Someday idea :work:network:
                No keyword on this one, just a wild*card and a "hello there" aside.
            """.trimIndent(),

            "personal.org" to """
                * TODO Call plumber :home:urgent:
                SCHEDULED: <2026-07-25 Sat>
                Pipe leaking under the sink.
                * Groceries :home:café:
                Buy milk, eggs and 会議録の内容 for the trip to the café.
                * IN-PROGRESS Read book :home:
                A draft chapter about meetings, NEAR the end.
                * Заметка :home:
                Это задача на завтра.
            """.trimIndent(),

            "long.org" to """
                * Long research note :research:
                $FILLER
                needleatend
            """.trimIndent(),
        )
    }
}
