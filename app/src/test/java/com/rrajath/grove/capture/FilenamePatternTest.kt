package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class FilenamePatternTest {

    // Wednesday 2025-06-11, 14:32:07
    private val now = LocalDateTime.of(2025, 6, 11, 14, 32, 7)

    @Test
    fun `strftime block expands recognized tokens`() {
        assertEquals(
            "20250611143207",
            FilenamePattern.expand("%<%Y%m%d%H%M%S>", now, slug = ""),
        )
        assertEquals(
            "2025-06-11",
            FilenamePattern.expand("%<%Y-%m-%d>", now, slug = ""),
        )
    }

    @Test
    fun `slug token is substituted`() {
        assertEquals(
            "2025-06-11-my_title",
            FilenamePattern.expand("%<%Y-%m-%d>-%(slug)", now, slug = "my_title"),
        )
    }

    @Test
    fun `slug from title collapses whitespace to underscores without case changes`() {
        assertEquals("My_Title", FilenamePattern.slugFromTitle("  My   Title  "))
        assertEquals("", FilenamePattern.slugFromTitle("   "))
        assertEquals("", FilenamePattern.slugFromTitle(""))
    }

    @Test
    fun `titleFromDraft reads the preamble title line`() {
        assertEquals(
            "My Note",
            FilenamePattern.titleFromDraft(":PROPERTIES:\n:ID: 1\n:END:\n#+title: My Note\nbody"),
        )
    }

    @Test
    fun `titleFromDraft is case-insensitive on the keyword`() {
        assertEquals("My Note", FilenamePattern.titleFromDraft("#+TITLE: My Note"))
    }

    @Test
    fun `titleFromDraft returns null for a blank title`() {
        assertNull(FilenamePattern.titleFromDraft("#+title:   \nbody"))
    }

    @Test
    fun `titleFromDraft returns null when title is missing`() {
        assertNull(FilenamePattern.titleFromDraft(":PROPERTIES:\n:ID: 1\n:END:\nbody"))
    }

    @Test
    fun `titleFromDraft ignores a title line after the first headline`() {
        assertNull(FilenamePattern.titleFromDraft("* Heading\n#+title: Too late"))
    }
}
