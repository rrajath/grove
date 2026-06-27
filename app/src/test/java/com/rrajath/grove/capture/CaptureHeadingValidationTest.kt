package com.rrajath.grove.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CaptureInserter.hasBlankHeading(), which guards the capture editor
 * save button against notes with an empty heading.
 */
class CaptureHeadingValidationTest {

    private fun blank(text: String) = CaptureInserter.hasBlankHeading(text)

    @Test
    fun `star with no title is blank`() {
        assertTrue(blank("* \n"))
    }

    @Test
    fun `star with only spaces after it is blank`() {
        assertTrue(blank("*   \n"))
    }

    @Test
    fun `star with a title is not blank`() {
        assertFalse(blank("* My capture note\n"))
    }

    @Test
    fun `keyword only no title is blank`() {
        assertTrue(blank("* TODO \n"))
    }

    @Test
    fun `keyword with title is not blank`() {
        assertFalse(blank("* TODO Buy milk\n"))
    }

    @Test
    fun `priority only no title is blank`() {
        assertTrue(blank("* [#A] \n"))
    }

    @Test
    fun `priority with title is not blank`() {
        assertFalse(blank("* [#B] Important\n"))
    }

    @Test
    fun `four stars for datetree with no title is blank`() {
        assertTrue(blank("**** \n"))
    }

    @Test
    fun `four stars for datetree with title is not blank`() {
        assertTrue(blank("**** \n"))
        assertFalse(blank("**** My journal entry\n"))
    }

    @Test
    fun `empty text has no headline and counts as blank`() {
        assertTrue(blank(""))
    }

    @Test
    fun `plain text with no heading stars counts as blank`() {
        assertTrue(blank("just some body text\n"))
    }

    @Test
    fun `heading with body lines is not blank`() {
        assertFalse(blank("* Meeting notes\n\nSome body text\n"))
    }

    @Test
    fun `only whitespace after stars on first line is blank`() {
        assertTrue(blank("*  \t  \n"))
    }
}
