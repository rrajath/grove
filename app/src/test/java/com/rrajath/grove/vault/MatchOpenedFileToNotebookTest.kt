package com.rrajath.grove.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for opening a .org file from outside Grove (file
 * manager "Open with", or Grove set as the default handler for .org files;
 * see the file-open intent-filter on MainActivity and the LaunchedEffect in
 * GroveApp.kt that resolves the tapped file against the vault).
 */
class MatchOpenedFileToNotebookTest {

    private val notebooks = listOf(
        Notebook("notes.org", noteCount = 1, lastModified = 0L),
        Notebook("notes.2026.org", noteCount = 2, lastModified = 0L),
    )

    @Test
    fun `matches an exact file name already in the vault`() {
        val match = matchOpenedFileToNotebook("notes.org", notebooks)
        assertEquals("notes.org", match?.fileName)
    }

    @Test
    fun `matches a multi-dot file name`() {
        val match = matchOpenedFileToNotebook("notes.2026.org", notebooks)
        assertEquals("notes.2026.org", match?.fileName)
    }

    @Test
    fun `matches case-insensitively`() {
        val match = matchOpenedFileToNotebook("NOTES.ORG", notebooks)
        assertEquals("notes.org", match?.fileName)
    }

    @Test
    fun `returns null when the file is not in the vault`() {
        assertNull(matchOpenedFileToNotebook("elsewhere.org", notebooks))
    }
}
