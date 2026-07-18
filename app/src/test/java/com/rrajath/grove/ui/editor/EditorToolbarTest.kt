package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorToolbarTest {

    @Test
    fun `link button with no selection inserts placeholders and highlights link`() {
        val value = TextFieldValue("Hello ", TextRange(6))
        val result = insertLinkTemplate(value)

        assertEquals("Hello [[link][description]]", result.text)
        // "link" (between "Hello [[" and "][") is selected, ready to type the URL.
        assertEquals(TextRange(8, 12), result.selection)
    }

    @Test
    fun `link button with a selection replaces it with the description and highlights link`() {
        // "my note" spans indices 4..10 of "See my note here".
        val value = TextFieldValue("See my note here", TextRange(4, 11))
        val result = insertLinkTemplate(value)

        assertEquals("See [[link][my note]] here", result.text)
        assertEquals(TextRange(6, 10), result.selection)
    }

    @Test
    fun `link button with a reversed selection still uses the selected text`() {
        // TextFieldValue selections can run start > end when the drag goes right-to-left.
        val value = TextFieldValue("See my note here", TextRange(11, 4))
        val result = insertLinkTemplate(value)

        assertEquals("See [[link][my note]] here", result.text)
        assertEquals(TextRange(6, 10), result.selection)
    }
}
