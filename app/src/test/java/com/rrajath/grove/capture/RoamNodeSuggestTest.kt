package com.rrajath.grove.capture

import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSyncTrigger
import com.rrajath.grove.ui.editor.AutoLinkFileSuggestion
import com.rrajath.grove.ui.editor.AutoLinkHeadingSuggestion
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RoamNodeSuggestTest {

    private val now = LocalDateTime.of(2026, 9, 20, 10, 0)

    private fun roamTemplate(
        newFileTemplate: String = ":PROPERTIES:\n:ID: %(id)\n:END:\n#+title: %?",
        roamDirectory: String = "roam",
        filenamePattern: String = "%<%Y%m%d%H%M%S>-%(slug)",
    ) = CaptureTemplate(
        id = "roam1",
        name = "Default",
        targetFile = "unused.org",
        location = TargetLocation.BottomOfFile,
        template = "unused",
        kind = TemplateKind.ROAM_NODE,
        roamDirectory = roamDirectory,
        filenamePattern = filenamePattern,
        newFileTemplate = newFileTemplate,
    )

    // --- hasUserDefinedTitle ---

    @Test
    fun `bare cursor placeholder title is eligible`() {
        assertTrue(roamTemplate(newFileTemplate = "#+title: %?").hasUserDefinedTitle())
    }

    @Test
    fun `cursor placeholder mixed with prefix text is eligible`() {
        assertTrue(roamTemplate(newFileTemplate = "#+title: Re: %?").hasUserDefinedTitle())
    }

    @Test
    fun `legacy percent-cursor placeholder is eligible`() {
        assertTrue(roamTemplate(newFileTemplate = "#+title: %cursor").hasUserDefinedTitle())
    }

    @Test
    fun `derived date title is not eligible`() {
        assertFalse(roamTemplate(newFileTemplate = "#+title: %date").hasUserDefinedTitle())
    }

    @Test
    fun `missing title line is not eligible`() {
        assertFalse(roamTemplate(newFileTemplate = ":PROPERTIES:\n:ID: %(id)\n:END:\n").hasUserDefinedTitle())
    }

    // --- RoamNodeCreator.createOrLink ---

    @Test
    fun `matches an existing title case-insensitively without writing anything`() = runTest {
        val store = FakeFileStore()
        val vault = Vault(store)
        val sync = FakeSyncTrigger()
        val index = listOf(AutoLinkFileSuggestion("meeting-notes.org", "Meeting Notes", "EXISTING-ID"))

        val result = RoamNodeCreator.createOrLink(
            vault, sync, roamTemplate(), "meeting notes", index, now,
        )

        assertEquals(RoamNodeResult.Linked("EXISTING-ID", "Meeting Notes"), result)
        assertTrue(store.snapshot().isEmpty())
        assertTrue(sync.reindexCalls.isEmpty())
    }

    @Test
    fun `matches an existing heading title too`() = runTest {
        val store = FakeFileStore()
        val vault = Vault(store)
        val sync = FakeSyncTrigger()
        val index = listOf(AutoLinkHeadingSuggestion("projects.org", 4, "Acme Launch", "HEADING-ID"))

        val result = RoamNodeCreator.createOrLink(
            vault, sync, roamTemplate(), "Acme Launch", index, now,
        )

        assertEquals(RoamNodeResult.Linked("HEADING-ID", "Acme Launch"), result)
    }

    @Test
    fun `creates a brand-new node when no title matches`() = runTest {
        val store = FakeFileStore()
        val vault = Vault(store)
        val sync = FakeSyncTrigger()

        val result = RoamNodeCreator.createOrLink(
            vault, sync, roamTemplate(), "My New Idea", emptyList(), now,
        ) as RoamNodeResult.Created

        assertEquals("My New Idea", result.title)
        assertTrue(result.path.startsWith("roam/"))
        assertTrue(result.path.endsWith(".org"))
        val text = store.read(result.path)
        assertTrue(text.contains(":ID: ${result.id}"))
        assertTrue(text.contains("#+title: My New Idea"))
        assertEquals(listOf("roam node created from selection"), sync.reindexCalls.map { it.reason })
    }

    @Test
    fun `a Re prefix template keeps the prefix and extracts the real title separately`() = runTest {
        val store = FakeFileStore()
        val vault = Vault(store)
        val sync = FakeSyncTrigger()

        val result = RoamNodeCreator.createOrLink(
            vault, sync, roamTemplate(newFileTemplate = "#+title: Re: %?"), "Budget", emptyList(), now,
        ) as RoamNodeResult.Created

        assertEquals("Re: Budget", result.title)
        assertTrue(store.read(result.path).contains("#+title: Re: Budget"))
    }

    @Test
    fun `existing-path collision falls back to appending the body instead of clobbering`() = runTest {
        val store = FakeFileStore(mapOf("roam/fixed-name.org" to "#+title: Fixed Name\n\n* Prior heading\nold\n"))
        val vault = Vault(store)
        val sync = FakeSyncTrigger()
        // A body line after the title so the appended fallback (head stripped,
        // same as CaptureViewModel.upsertRoamEntry's ExistingFile branch) has
        // something to assert on -- the fresh drawer/title themselves are head,
        // not body, and are never written into an already-existing file.
        val template = roamTemplate(
            newFileTemplate = ":PROPERTIES:\n:ID: %(id)\n:END:\n#+title: %?\nFresh node body\n",
            roamDirectory = "roam",
            filenamePattern = "fixed-name",
        )

        RoamNodeCreator.createOrLink(vault, sync, template, "New Title", emptyList(), now)

        val text = store.read("roam/fixed-name.org")
        assertEquals("the pre-existing head is untouched, not duplicated", 1, Regex("#\\+title:").findAll(text).count())
        assertTrue("prior content survives", text.contains("Prior heading"))
        assertTrue("the fresh node's own body was appended", text.contains("Fresh node body"))
        assertFalse("the typed title never appears: it belongs to the dropped head", text.contains("New Title"))
    }
}
