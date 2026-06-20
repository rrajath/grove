package com.rrajath.grove.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `outline route encodes notebook id`() {
        assertEquals("outline/travel.org", Routes.outline("travel.org"))
        assertEquals("outline/my+notes.org", Routes.outline("my notes.org"))
        assertEquals("outline/a%2Fb.org", Routes.outline("a/b.org"))
    }

    @Test
    fun `note route defaults to read mode and isNew false`() {
        assertEquals("note/abc-123?mode=read&isNew=false", Routes.note("abc-123"))
        assertEquals("note/abc-123?mode=edit&isNew=false", Routes.note("abc-123", mode = "edit"))
        assertEquals("note/abc-123?mode=edit&isNew=true", Routes.note("abc-123", mode = "edit", isNew = true))
    }

    @Test
    fun `capture route with and without template`() {
        assertEquals("capture", Routes.capture())
        assertEquals("capture/journal", Routes.capture("journal"))
    }

    @Test
    fun `capture path matches launcher shortcut deep links`() {
        // res/xml/shortcuts.xml fires grove://capture/<id>; the path after the
        // scheme must equal the route the NavHost matches via Routes.capture(id).
        assertEquals("capture/builtin-journal", Routes.capture("builtin-journal"))
        assertEquals("capture/builtin-quick-note", Routes.capture("builtin-quick-note"))
    }

    @Test
    fun `conflict route encodes notebook id`() {
        assertEquals("conflict/journal.org", Routes.conflict("journal.org"))
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
