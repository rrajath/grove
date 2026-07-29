package com.rrajath.grove.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutesTest {

    @Test
    fun `outline route encodes notebook id`() {
        assertEquals("outline/travel.org", Routes.outline("travel.org"))
        // Spaces must encode as %20 (not +): androidx.navigation decodes via Uri.decode,
        // which leaves a literal + intact and would break exact-string matching.
        assertEquals("outline/my%20notes.org", Routes.outline("my notes.org"))
        assertEquals("outline/a%2Fb.org", Routes.outline("a/b.org"))
    }

    @Test
    fun `encode uses percent-20 for spaces and round-trips through Uri-style decode`() {
        // Regression for the reminder reschedule deep link (and multi-word note ids /
        // search queries): every real org heading has a space. Emulate Uri.decode
        // (only undoes %XX) to prove the encoded form survives NavDeepLink decoding.
        for (raw in listOf("Buy milk", "A/B path", "café résumé", "plus + and space")) {
            val encoded = Routes.encode(raw)
            assertFalse("space must not encode as '+': $encoded", encoded.contains('+'))
            assertEquals(raw, uriStyleDecode(encoded))
        }
    }

    /** Minimal stand-in for android.net.Uri.decode: undoes %XX (UTF-8), leaves + literal. */
    private fun uriStyleDecode(s: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                bytes.write(s.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                bytes.write(c.code)
                i += 1
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    @Test
    fun `note route defaults to read mode, isNew false`() {
        assertEquals("note/abc-123?mode=read&isNew=false", Routes.note("abc-123"))
        assertEquals("note/abc-123?mode=edit&isNew=false", Routes.note("abc-123", mode = "edit"))
        assertEquals(
            "note/abc-123?mode=edit&isNew=true",
            Routes.note("abc-123", mode = "edit", isNew = true),
        )
    }

    @Test
    fun `capture route with and without template`() {
        assertEquals("capture", Routes.capture())
        assertEquals("capture/journal", Routes.capture("journal"))
    }

    @Test
    fun `capture path matches launcher shortcut deep links`() {
        // ShortcutSyncer publishes grove://capture/<id> per template; the path
        // after the scheme must equal the route the NavHost matches via Routes.capture(id).
        assertEquals("capture/builtin-journal", Routes.capture("builtin-journal"))
        assertEquals("capture/builtin-quick-note", Routes.capture("builtin-quick-note"))
    }

    @Test
    fun `search route carries an optional pinned notebook`() {
        assertEquals("search?q=", Routes.search())
        assertEquals("search?q=ramen", Routes.search("ramen"))
        // The outline's search action pins the notebook with no query typed yet.
        assertEquals("search?q=&notebook=travel.org", Routes.search(notebook = "travel.org"))
        assertEquals(
            "search?q=ramen&notebook=my%20notes.org",
            Routes.search("ramen", notebook = "my notes.org"),
        )
        // Every built form must still match the pattern the NavHost registers.
        assertEquals(
            Routes.SEARCH.substringBefore("{"),
            Routes.search().substringBefore("&"),
        )
    }

    @Test
    fun `conflict route encodes notebook id`() {
        assertEquals("conflict/journal.org", Routes.conflict("journal.org"))
    }

    @Test
    fun `outline route omits narrowTo by default and appends it when set`() {
        assertEquals("outline/travel.org", Routes.outline("travel.org"))
        assertEquals("outline/travel.org?narrowTo=42", Routes.outline("travel.org", narrowTo = 42))
    }

    @Test
    fun `built routes match declared patterns`() {
        // outline/{notebookId}
        assertEquals(
            Routes.OUTLINE.substringBefore("{"),
            Routes.outline("x").substringBefore("x"),
        )
        // note/{noteId}?mode={mode}&isNew={isNew}
        assertEquals(
            Routes.NOTE.substringBefore("{"),
            Routes.note("x").substringBefore("x"),
        )
    }
}
