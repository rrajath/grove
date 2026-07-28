package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCandidateQueryTest {

    private fun build(input: String? = null, facets: FacetNarrowing = FacetNarrowing.NONE): CandidateSql {
        val query = input?.let { QueryParser.parse(it) }
        return NoteCandidateQuery.build(query?.let { FtsQuery.matchExpression(it) }, query, facets)
    }

    @Test
    fun `nothing to narrow selects the whole table in a stable order`() {
        val sql = build()
        assertEquals("SELECT * FROM notes ORDER BY fileName, lineIndex", sql.sql)
        assertTrue(sql.args.isEmpty())
        assertTrue(sql.isFullScan)
    }

    @Test
    fun `a text query joins through the FTS table`() {
        val sql = build("meeting")
        assertTrue(sql.sql.contains("(fileName, lineIndex) IN (SELECT fileName, lineIndex FROM notes_fts WHERE notes_fts MATCH ?)"))
        assertEquals(listOf("(\"meeting\")"), sql.args)
        assertFalse(sql.isFullScan)
    }

    @Test
    fun `a short text term narrows on nothing but still runs`() {
        val sql = build("hi")
        assertTrue(sql.isFullScan)
    }

    // --- structured query tokens ---

    @Test
    fun `state tokens become a keyword predicate`() {
        val sql = build("i.TODO")
        assertTrue(sql.sql.contains("(keyword = ? COLLATE NOCASE)"))
        assertEquals(listOf("TODO"), sql.args)
    }

    @Test
    fun `i none becomes a null check`() {
        assertTrue(build("i.none").sql.contains("keyword IS NULL"))
    }

    @Test
    fun `priority and tag tokens are ANDed within a group`() {
        val sql = build("p.A t.work")
        assertTrue(sql.sql.contains("priority = ? COLLATE NOCASE"))
        assertTrue(sql.sql.contains("inheritedTags LIKE ? ESCAPE '\\'"))
        assertEquals(listOf("A", "%work%"), sql.args)
    }

    @Test
    fun `tn uses the own-tags column`() {
        assertTrue(build("tn.work").sql.contains("tags LIKE ?"))
        assertFalse(build("tn.work").sql.contains("inheritedTags LIKE ?"))
    }

    @Test
    fun `notebook tokens accept the name with or without the org suffix`() {
        val sql = build("b.work")
        assertTrue(sql.sql.contains("(fileName = ? COLLATE NOCASE OR fileName = ? COLLATE NOCASE)"))
        assertEquals(listOf("work", "work.org"), sql.args)
    }

    @Test
    fun `date windows only require the timestamp to exist`() {
        assertTrue(build("s.3d").sql.contains("scheduled IS NOT NULL"))
        assertTrue(build("d.today").sql.contains("deadline IS NOT NULL"))
        assertTrue(build("c.1w").sql.contains("closed IS NOT NULL"))
        assertTrue(build("cr.1m").sql.contains("createdAt IS NOT NULL"))
    }

    @Test
    fun `OR groups become an ORed predicate`() {
        val sql = build("i.TODO OR p.A")
        assertTrue(sql.sql.contains("((keyword = ? COLLATE NOCASE) OR (priority = ? COLLATE NOCASE))"))
        assertEquals(listOf("TODO", "A"), sql.args)
    }

    @Test
    fun `a group with nothing pushable drops the whole query predicate`() {
        // The second group would match anything, so ORing it in narrows nothing —
        // and emitting only the first group's predicate would drop valid rows.
        val sql = build("i.TODO OR hi")
        assertFalse(sql.sql.contains("keyword"))
        assertTrue(sql.isFullScan)
    }

    @Test
    fun `negated terms are never pushed down`() {
        // .i.TODO excludes rows; leaving it out keeps the candidate set a superset.
        val sql = build(".i.TODO")
        assertTrue(sql.isFullScan)
    }

    @Test
    fun `negation alongside a positive term keeps only the positive one`() {
        val sql = build("i.TODO .p.A")
        assertTrue(sql.sql.contains("keyword = ?"))
        assertFalse(sql.sql.contains("priority"))
    }

    @Test
    fun `non-ASCII operands are left to the Kotlin matcher`() {
        // SQLite's NOCASE and LIKE only fold ASCII, so pushing these down could
        // exclude a row Kotlin's Unicode-aware ignoreCase would have matched.
        assertTrue(build("t.café").isFullScan)
        assertTrue(build("i.ЗАДАЧА").isFullScan)
    }

    @Test
    fun `LIKE wildcards inside a tag are escaped`() {
        assertEquals(listOf("%50\\%\\_done%"), build("t.50%_done").args)
    }

    // --- filter chips ---

    @Test
    fun `notebook chip is an exact file name match`() {
        val sql = build(facets = FacetNarrowing(notebook = "work.org"))
        assertTrue(sql.sql.contains("fileName = ?"))
        assertEquals(listOf("work.org"), sql.args)
    }

    @Test
    fun `state chips become an IN list`() {
        val sql = build(facets = FacetNarrowing(states = linkedSetOf("TODO", "NEXT")))
        assertTrue(sql.sql.contains("(keyword IN (?, ?))"))
        assertEquals(listOf("TODO", "NEXT"), sql.args)
    }

    @Test
    fun `the no-state chip ORs in a null check`() {
        val sql = build(facets = FacetNarrowing(states = setOf("TODO"), includeNoState = true))
        assertTrue(sql.sql.contains("(keyword IN (?) OR keyword IS NULL)"))
    }

    @Test
    fun `no-state on its own`() {
        val sql = build(facets = FacetNarrowing(includeNoState = true))
        assertTrue(sql.sql.contains("(keyword IS NULL)"))
        assertTrue(sql.args.isEmpty())
    }

    @Test
    fun `tag chips match a whole tag, not a substring`() {
        // The chips compare whole tags, so "work" must not also match "network".
        val sql = build(facets = FacetNarrowing(tags = setOf("work")))
        assertTrue(sql.sql.contains("instr(':' || inheritedTags || ':', ?) > 0"))
        assertEquals(listOf(":work:"), sql.args)
    }

    @Test
    fun `tag chips are ORed with each other`() {
        val sql = build(facets = FacetNarrowing(tags = linkedSetOf("work", "home")))
        assertTrue(sql.sql.contains("(instr(':' || inheritedTags || ':', ?) > 0 OR instr(':' || inheritedTags || ':', ?) > 0)"))
        assertEquals(listOf(":work:", ":home:"), sql.args)
    }

    @Test
    fun `date presence maps to null checks`() {
        assertTrue(build(facets = FacetNarrowing(scheduled = DatePresence.PRESENT)).sql.contains("scheduled IS NOT NULL"))
        assertTrue(build(facets = FacetNarrowing(scheduled = DatePresence.ABSENT)).sql.contains("scheduled IS NULL"))
        assertTrue(build(facets = FacetNarrowing(deadline = DatePresence.PRESENT)).sql.contains("deadline IS NOT NULL"))
        assertTrue(build(facets = FacetNarrowing(deadline = DatePresence.ANY)).isFullScan)
    }

    @Test
    fun `text, query tokens and chips all combine with AND`() {
        val sql = build("meeting i.TODO", FacetNarrowing(priorities = setOf("A")))
        assertTrue(sql.sql.contains("notes_fts MATCH ?"))
        assertTrue(sql.sql.contains("keyword = ? COLLATE NOCASE"))
        assertTrue(sql.sql.contains("priority IN (?)"))
        assertEquals(listOf("(\"meeting\")", "TODO", "A"), sql.args)
    }

    @Test
    fun `argument order follows placeholder order`() {
        val sql = build(
            "meeting t.work",
            FacetNarrowing(notebook = "n.org", states = setOf("TODO"), priorities = setOf("B"), tags = setOf("home")),
        )
        val placeholders = sql.sql.count { it == '?' }
        assertEquals(placeholders, sql.args.size)
    }
}
