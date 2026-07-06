package com.rrajath.grove.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [orgInlineLinks] must report link ranges in the *rendered* string (labels
 * shown, not raw `[[target][label]]` syntax) since that's the coordinate
 * space callers hit-test against ([TextLayoutResult.getOffsetForPosition]).
 */
class OrgInlineLinksTest {

    @Test
    fun `no links in plain text`() {
        assertTrue(orgInlineLinks("just some text").isEmpty())
    }

    @Test
    fun `link range covers the rendered label, not the raw markup`() {
        val text = "see [[https://example.com][docs]] for more"
        val rendered = "see docs for more" // what annotateOrgInline actually renders
        val link = orgInlineLinks(text).single()
        assertEquals("https://example.com", link.target)
        assertEquals("docs", rendered.substring(link.range.first, link.range.last + 1))
    }

    @Test
    fun `link without a label uses the target as both text and range content`() {
        val links = orgInlineLinks("[[https://example.com]]")
        assertEquals(1, links.size)
        assertEquals("https://example.com", links.first().target)
        assertEquals(0.."https://example.com".length - 1, links.first().range)
    }

    @Test
    fun `bare url is a link spanning its own text`() {
        val text = "go to https://example.com now"
        val links = orgInlineLinks(text)
        assertEquals(1, links.size)
        assertEquals("https://example.com", links.first().target)
        assertEquals("go to ".length, links.first().range.first)
    }

    @Test
    fun `multiple links get non-overlapping ranges in encounter order`() {
        val text = "[[a][one]] and [[b][two]]"
        val links = orgInlineLinks(text)
        assertEquals(2, links.size)
        assertEquals("a", links[0].target)
        assertEquals("b", links[1].target)
        assertTrue(links[0].range.last < links[1].range.first)
    }

    @Test
    fun `offset within a link range resolves to that link`() {
        val text = "prefix [[https://x.io][click here]] suffix"
        val links = orgInlineLinks(text)
        val link = links.single()
        val rendered = "prefix click here suffix"
        val midOffset = rendered.indexOf("here")
        assertTrue(midOffset in link.range)
    }
}
