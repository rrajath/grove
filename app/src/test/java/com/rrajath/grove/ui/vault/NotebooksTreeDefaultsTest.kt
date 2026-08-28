package com.rrajath.grove.ui.vault

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * First-open folder-expansion heuristic (nested-folders plan §4): expand any
 * folder that recursively contains a SCHEDULED/DEADLINE note, collapse the rest.
 */
class NotebooksTreeDefaultsTest {

    @Test
    fun `expands the folder and every ancestor of a planned file`() {
        val expand = firstOpenExpandedDirs(listOf("projects/clients/acme.org"))
        assertEquals(setOf("projects", "projects/clients"), expand)
    }

    @Test
    fun `a planned file at the vault root expands nothing`() {
        assertEquals(emptySet<String>(), firstOpenExpandedDirs(listOf("inbox.org")))
    }

    @Test
    fun `no planned files means everything stays collapsed`() {
        assertEquals(emptySet<String>(), firstOpenExpandedDirs(emptyList()))
    }

    @Test
    fun `unions the ancestors of every planned file`() {
        val expand = firstOpenExpandedDirs(
            listOf("projects/grove.org", "areas/health/gym.org", "inbox.org"),
        )
        assertEquals(setOf("projects", "areas", "areas/health"), expand)
    }
}
