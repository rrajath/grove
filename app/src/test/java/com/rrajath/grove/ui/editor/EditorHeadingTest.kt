package com.rrajath.grove.ui.editor

import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the empty-heading detection used by EditorViewModel.isCurrentHeadingBlank()
 * and the save-guard alert in EditNoteScreen.
 */
class EditorHeadingTest {

    private fun isHeadingBlank(buffer: String): Boolean {
        val doc = OrgParser.parse(buffer, OrgKeywords.DEFAULT)
        val headline = doc.headlines.firstOrNull() ?: return true
        return headline.title.isBlank()
    }

    @Test
    fun `headline with no title is blank`() {
        assertTrue(isHeadingBlank("* \n"))
    }

    @Test
    fun `headline with only spaces after stars is blank`() {
        assertTrue(isHeadingBlank("*   \n"))
    }

    @Test
    fun `headline with a title is not blank`() {
        assertFalse(isHeadingBlank("* My note\n"))
    }

    @Test
    fun `headline with keyword and no title is blank`() {
        assertTrue(isHeadingBlank("* TODO \n"))
    }

    @Test
    fun `headline with keyword and title is not blank`() {
        assertFalse(isHeadingBlank("* TODO Buy milk\n"))
    }

    @Test
    fun `headline with priority and no title is blank`() {
        assertTrue(isHeadingBlank("* [#A] \n"))
    }

    @Test
    fun `headline with priority and title is not blank`() {
        assertFalse(isHeadingBlank("* [#B] Important task\n"))
    }

    @Test
    fun `empty buffer has no headline so counts as blank`() {
        assertTrue(isHeadingBlank(""))
    }

    @Test
    fun `buffer with only body text and no headline is blank`() {
        assertTrue(isHeadingBlank("just some text without a heading\n"))
    }
}
