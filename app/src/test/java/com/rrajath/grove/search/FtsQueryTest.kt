package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    private fun expressionFor(input: String) = FtsQuery.matchExpression(QueryParser.parse(input))

    @Test
    fun `single term becomes a quoted probe`() {
        assertEquals("(\"meeting\")", expressionFor("meeting"))
    }

    @Test
    fun `terms in a group are ANDed`() {
        assertEquals("(\"meet\" AND \"notes\")", expressionFor("meet notes"))
    }

    @Test
    fun `OR groups are ORed`() {
        assertEquals("(\"meet\" AND \"notes\") OR (\"standup\")", expressionFor("meet notes OR standup"))
    }

    @Test
    fun `an empty query does not narrow`() {
        assertNull(expressionFor(""))
        assertNull(FtsQuery.matchExpression(SearchQuery(groups = emptyList())))
    }

    // --- the narrowing precondition ---

    @Test
    fun `a group with no text term does not narrow`() {
        // i.TODO can match a note containing none of the query's text, so
        // narrowing on the text alone would drop valid results.
        assertNull(expressionFor("i.TODO"))
        assertNull(expressionFor("t.work p.A"))
    }

    @Test
    fun `one facet-only group disqualifies the whole query`() {
        assertNull(expressionFor("meeting OR i.TODO"))
    }

    @Test
    fun `facets alongside a usable text term still narrow`() {
        assertEquals("(\"meeting\")", expressionFor("i.TODO meeting t.work"))
    }

    // --- the trigram minimum ---

    @Test
    fun `terms shorter than three characters are dropped`() {
        assertEquals("(\"meeting\")", expressionFor("hi meeting"))
    }

    @Test
    fun `a group of only short terms does not narrow`() {
        assertNull(expressionFor("hi go"))
    }

    @Test
    fun `three characters is long enough`() {
        assertEquals("(\"cat\")", expressionFor("cat"))
    }

    @Test
    fun `CJK terms count code points, not bytes`() {
        assertEquals("(\"会議録\")", expressionFor("会議録"))
        assertNull(expressionFor("会議"))
    }

    @Test
    fun `surrogate pairs count as one code point each`() {
        // Three emoji = three code points = one trigram.
        assertEquals("(\"🌲🌲🌲\")", expressionFor("🌲🌲🌲"))
        assertNull(expressionFor("🌲🌲"))
    }

    // --- negation ---

    @Test
    fun `negated text terms are excluded from the expression`() {
        // .draft only ever removes rows, so leaving it out keeps a superset.
        assertEquals("(\"meeting\")", expressionFor("meeting .draft"))
    }

    @Test
    fun `a group whose only text term is negated does not narrow`() {
        assertNull(expressionFor(".draft"))
    }

    // --- escaping ---

    @Test
    fun `embedded quotes are doubled`() {
        assertEquals("(\"say \"\"hi\"\" now\")", FtsQuery.matchExpression(textQuery("say \"hi\" now")))
    }

    @Test
    fun `FTS operators in a term are matched as text`() {
        assertEquals("(\"a*b\")", expressionFor("a*b"))
        assertEquals("(\"NOT\")", expressionFor("NOT"))
        assertEquals("(\"(paren)\")", expressionFor("(paren)"))
    }

    @Test
    fun `duplicate terms collapse`() {
        assertEquals("(\"meeting\")", expressionFor("meeting meeting"))
    }

    private fun textQuery(term: String) =
        SearchQuery(groups = listOf(listOf(Term(Condition.Text(term), negated = false))))
}
