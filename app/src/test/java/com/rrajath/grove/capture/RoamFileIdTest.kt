package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoamFileIdTest {

    @Test
    fun `withFileId prepends a drawer when there is none`() {
        assertEquals(
            ":PROPERTIES:\n:ID: X1\n:END:\n#+title: Foo",
            RoamFileId.withFileId("#+title: Foo", "X1"),
        )
    }

    @Test
    fun `withFileId replaces a template ID and keeps other properties`() {
        val text = ":PROPERTIES:\n:ROAM_ALIASES: foo\n:ID: old\n:END:\n#+title: Foo"
        assertEquals(
            ":PROPERTIES:\n:ID: X1\n:ROAM_ALIASES: foo\n:END:\n#+title: Foo",
            RoamFileId.withFileId(text, "X1"),
        )
    }

    @Test
    fun `ensureFileId keeps an existing ID`() {
        val text = ":PROPERTIES:\n:ID: keep\n:END:\n#+title: Foo\nbody"
        assertEquals(text, RoamFileId.ensureFileId(text, "X1"))
    }

    @Test
    fun `ensureFileId adds an ID to a drawer without one`() {
        assertEquals(
            ":PROPERTIES:\n:ID: X1\n:CATEGORY: c\n:END:\nbody",
            RoamFileId.ensureFileId(":PROPERTIES:\n:CATEGORY: c\n:END:\nbody", "X1"),
        )
    }

    @Test
    fun `splitDrawer hides an ID-only drawer and rebases the cursor`() {
        val text = ":PROPERTIES:\n:ID: X1\n:END:\n#+title: "
        val (drawer, rest, cursor) = RoamFileId.splitDrawer(text, text.length)
        assertEquals("", drawer)
        assertEquals("#+title: ", rest)
        assertEquals(rest.length, cursor)
    }

    @Test
    fun `splitDrawer keeps non-ID properties aside`() {
        val text = ":PROPERTIES:\n:ID: X1\n:ROAM_ALIASES: foo\n:END:\n#+title: "
        val (drawer, rest, _) = RoamFileId.splitDrawer(text, 0)
        assertEquals(":PROPERTIES:\n:ROAM_ALIASES: foo\n:END:", drawer)
        assertEquals("#+title: ", rest)
        assertEquals(text.replace(":ID: X1\n", ""), RoamFileId.joinDrawer(drawer, rest))
    }

    @Test
    fun `splitDrawer leaves drawer-less text alone`() {
        assertEquals(Triple("", "#+title: a", 3), RoamFileId.splitDrawer("#+title: a", 3))
    }

    @Test
    fun `templateDefinesId detects only a file-level ID`() {
        assertTrue(RoamFileId.templateDefinesId(":PROPERTIES:\n:ID: %(id)\n:END:\n#+title: %?"))
        assertFalse(RoamFileId.templateDefinesId("#+title: %?"))
        assertFalse(RoamFileId.templateDefinesId(":PROPERTIES:\n:ROAM_ALIASES: a\n:END:\n#+title: %?"))
    }
}
