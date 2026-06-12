package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineTokenizerTest {

    private fun types(line: String) = InlineTokenizer.tokenize(line).map { it.type }

    @Test
    fun `plain text is one token`() {
        val tokens = InlineTokenizer.tokenize("just some text")
        assertEquals(listOf(InlineType.TEXT), tokens.map { it.type })
        assertEquals("just some text", tokens[0].text)
    }

    @Test
    fun `tokens cover the whole line`() {
        val line = "a *b* c [[https://x.io][link]] d <2025-01-01 Wed> e"
        val tokens = InlineTokenizer.tokenize(line)
        assertEquals(line.length, tokens.sumOf { it.range.last - it.range.first + 1 })
        assertEquals(0, tokens.first().range.first)
        assertEquals(line.length - 1, tokens.last().range.last)
    }

    @Test
    fun `emphasis variants`() {
        assertEquals(listOf(InlineType.BOLD), types("*bold*"))
        assertEquals(listOf(InlineType.ITALIC), types("/italic/"))
        assertEquals(listOf(InlineType.UNDERLINE), types("_under_"))
        assertEquals(listOf(InlineType.CODE), types("~code~"))
        assertEquals(listOf(InlineType.CODE), types("`code`"))
        assertEquals(listOf(InlineType.VERBATIM), types("=verb="))
    }

    @Test
    fun `emphasis strips markers from display text`() {
        val token = InlineTokenizer.tokenize("say *hello* now")[1]
        assertEquals(InlineType.BOLD, token.type)
        assertEquals("hello", token.text)
    }

    @Test
    fun `mid-word asterisks are not emphasis`() {
        assertEquals(listOf(InlineType.TEXT), types("2*3*4 equals 24"))
        assertEquals(listOf(InlineType.TEXT), types("a*b"))
    }

    @Test
    fun `bracket link with label`() {
        val token = InlineTokenizer.tokenize("see [[https://example.com][the site]] ok")[1]
        assertEquals(InlineType.LINK, token.type)
        assertEquals("the site", token.text)
        assertEquals("https://example.com", token.target)
    }

    @Test
    fun `bracket link without label shows target`() {
        val token = InlineTokenizer.tokenize("[[id:abc-123]]")[0]
        assertEquals(InlineType.LINK, token.type)
        assertEquals("id:abc-123", token.text)
        assertEquals("id:abc-123", token.target)
    }

    @Test
    fun `custom id link`() {
        val token = InlineTokenizer.tokenize("see [[#kyoto-day-2]]")[1]
        assertEquals("#kyoto-day-2", token.target)
    }

    @Test
    fun `bare url`() {
        val tokens = InlineTokenizer.tokenize("go to https://example.com/x?y=1 now")
        val link = tokens.first { it.type == InlineType.LINK }
        assertEquals("https://example.com/x?y=1", link.target)
    }

    @Test
    fun `scheme links`() {
        for (target in listOf("mailto:a@b.c", "tel:+15551234", "geo:35.0,135.7")) {
            val tokens = InlineTokenizer.tokenize("contact $target here")
            assertEquals(target, tokens.first { it.type == InlineType.LINK }.target)
        }
    }

    @Test
    fun `timestamps tokenize`() {
        val tokens = InlineTokenizer.tokenize("due <2025-06-27 Fri> and noted [2025-01-04 Sat]")
        assertEquals(2, tokens.count { it.type == InlineType.TIMESTAMP })
    }

    @Test
    fun `emphasis inside link is not split`() {
        val tokens = InlineTokenizer.tokenize("[[https://x.io/a*b*c][label]]")
        assertEquals(1, tokens.size)
        assertEquals(InlineType.LINK, tokens[0].type)
    }

    @Test
    fun `italic does not trigger inside urls`() {
        val tokens = InlineTokenizer.tokenize("https://example.com/path/to/thing and more")
        val link = tokens.first { it.type == InlineType.LINK }
        assertEquals("https://example.com/path/to/thing", link.target)
        assertTrue(tokens.none { it.type == InlineType.ITALIC })
    }
}
