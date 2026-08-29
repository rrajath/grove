package com.rrajath.grove.ui.vault

import com.rrajath.grove.ui.components.nameHashPaletteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tree-builder rules for the Notebooks screen (nested-folders plan §4): grouping,
 * counts, conflict-descendant propagation, folder-first alphabetical sort, and
 * expansion-gated flattening.
 */
class NotebookTreeTest {

    private fun item(
        path: String,
        conflict: Boolean = false,
        title: String? = null,
    ) = NotebookItem(
        fileName = path,
        noteCount = 0,
        lastModified = 0L,
        hasConflict = conflict,
        displayName = title ?: path.substringAfterLast('/'),
    )

    // The prototype's sample vault.
    private val vault = listOf(
        item("inbox.org"),
        item("journal.org"),
        item("projects/grove.org"),
        item("projects/website.org"),
        item("projects/clients/acme.org", conflict = true),
        item("projects/clients/northwind.org"),
        item("areas/health.org"),
        item("archive/2024.org"),
        item("recipes.org"),
    )

    @Test
    fun `allFolderDirs includes intermediate ancestors`() {
        assertEquals(
            setOf("projects", "projects/clients", "areas", "archive"),
            allFolderDirs(vault),
        )
    }

    @Test
    fun `every derived folder has at least one org file (free pruning)`() {
        val nodes = buildFolderNodes(vault)
        assertTrue(nodes.values.all { it.recursiveOrgCount >= 1 })
    }

    @Test
    fun `folder counts are recursive files and direct sub-folders`() {
        val nodes = buildFolderNodes(vault)
        val projects = nodes.getValue("projects")
        assertEquals(4, projects.recursiveOrgCount) // grove, website, acme, northwind
        assertEquals(1, projects.directFolderCount) // clients
        val clients = nodes.getValue("projects/clients")
        assertEquals(2, clients.recursiveOrgCount)
        assertEquals(0, clients.directFolderCount)
    }

    @Test
    fun `conflict on a deep file propagates to every ancestor folder`() {
        val nodes = buildFolderNodes(vault)
        assertTrue(nodes.getValue("projects").hasConflictDescendant)
        assertTrue(nodes.getValue("projects/clients").hasConflictDescendant)
        assertFalse(nodes.getValue("areas").hasConflictDescendant)
    }

    @Test
    fun `folder depth drives the indent cap input`() {
        val nodes = buildFolderNodes(vault)
        assertEquals(1, nodes.getValue("projects").depth)
        assertEquals(2, nodes.getValue("projects/clients").depth)
    }

    @Test
    fun `collapsed tree shows top-level folders then root files, alphabetical`() {
        val rows = buildNotebookTree(vault, expanded = emptySet())
        val labels = rows.map {
            when (it) {
                is NotebookTreeRow.Folder -> "d:${it.node.name}"
                is NotebookTreeRow.File -> "f:${it.item.fileName}"
            }
        }
        assertEquals(
            listOf(
                "d:archive", "d:areas", "d:projects",
                "f:inbox.org", "f:journal.org", "f:recipes.org",
            ),
            labels,
        )
    }

    @Test
    fun `expanding a folder inlines its children between folders and files of that level`() {
        val rows = buildNotebookTree(vault, expanded = setOf("projects"))
        val labels = rows.map {
            when (it) {
                is NotebookTreeRow.Folder -> "d:${it.node.dir}"
                is NotebookTreeRow.File -> "f:${it.item.fileName}"
            }
        }
        assertEquals(
            listOf(
                "d:archive", "d:areas", "d:projects",
                // projects' own contents: sub-folder first, then files
                "d:projects/clients",
                "f:projects/grove.org", "f:projects/website.org",
                // clients stays collapsed
                "f:inbox.org", "f:journal.org", "f:recipes.org",
            ),
            labels,
        )
    }

    @Test
    fun `a nested folder only opens when every ancestor is also expanded`() {
        val rows = buildNotebookTree(vault, expanded = setOf("projects", "projects/clients"))
        val paths = rows.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        assertTrue("projects/clients/acme.org" in paths)
        assertTrue("projects/clients/northwind.org" in paths)

        val closedParent = buildNotebookTree(vault, expanded = setOf("projects/clients"))
        val closedPaths = closedParent.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        assertFalse("projects/clients/acme.org" in closedPaths)
    }

    @Test
    fun `file rows carry the depth of their directory`() {
        val rows = buildNotebookTree(vault, expanded = setOf("projects", "projects/clients"))
        val byPath = rows.filterIsInstance<NotebookTreeRow.File>().associate { it.item.fileName to it.depth }
        assertEquals(0, byPath["inbox.org"])
        assertEquals(1, byPath["projects/grove.org"])
        assertEquals(2, byPath["projects/clients/acme.org"])
    }

    @Test
    fun `within a level files sort by display name, not raw path`() {
        val items = listOf(
            item("projects/zeta.org", title = "Alpha"),
            item("projects/alpha.org", title = "Zeta"),
        )
        val rows = buildNotebookTree(items, expanded = setOf("projects"))
        val order = rows.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        assertEquals(listOf("projects/zeta.org", "projects/alpha.org"), order)
    }

    @Test
    fun `drillLevel returns a directory's own sub-folders and files, folders-first alphabetical`() {
        val root = drillLevel(vault, "")
        assertEquals(listOf("archive", "areas", "projects"), root.childFolders.map { it.name })
        assertEquals(listOf("inbox.org", "journal.org", "recipes.org"), root.files.map { it.fileName })

        val projects = drillLevel(vault, "projects")
        assertEquals(listOf("clients"), projects.childFolders.map { it.name })
        assertEquals(
            listOf("projects/grove.org", "projects/website.org"),
            projects.files.map { it.fileName },
        )

        val clients = drillLevel(vault, "projects/clients")
        assertTrue(clients.childFolders.isEmpty())
        assertEquals(
            listOf("projects/clients/acme.org", "projects/clients/northwind.org"),
            clients.files.map { it.fileName },
        )
    }

    @Test
    fun `drillLevel sub-folder nodes keep their recursive counts for the drill affordance`() {
        val projects = drillLevel(vault, "projects").childFolders.single()
        assertEquals("projects/clients", projects.dir)
        assertEquals(2, projects.recursiveOrgCount)
    }

    @Test
    fun `only a folder over the drill threshold is a drill target`() {
        val big = (1..FOLDER_DRILL_THRESHOLD + 1).map { item("archive/note$it.org") }
        val node = buildFolderNodes(big).getValue("archive")
        assertTrue(node.recursiveOrgCount > FOLDER_DRILL_THRESHOLD)

        val small = (1..FOLDER_DRILL_THRESHOLD).map { item("areas/note$it.org") }
        assertFalse(buildFolderNodes(small).getValue("areas").recursiveOrgCount > FOLDER_DRILL_THRESHOLD)
    }

    @Test
    fun `buildFolderNodes applies a folder colour override and leaves others derived`() {
        val nodes = buildFolderNodes(vault, folderColors = mapOf("projects" to "cobalt"))
        val projects = nodes.getValue("projects")
        assertEquals("cobalt", projects.colorKey)
        assertEquals("cobalt", projects.colorOverride)
        val areas = nodes.getValue("areas")
        assertEquals(null, areas.colorOverride)
        assertEquals(nameHashPaletteKey("areas"), areas.colorKey)
    }

    @Test
    fun `buildFolderNodes carries the pin index in list order`() {
        val nodes = buildFolderNodes(
            vault, pinnedFolders = listOf("areas", "projects/clients"),
        )
        assertEquals(0, nodes.getValue("areas").pinnedIndex)
        assertTrue(nodes.getValue("areas").isPinned)
        assertEquals(1, nodes.getValue("projects/clients").pinnedIndex)
        assertEquals(-1, nodes.getValue("projects").pinnedIndex)
        assertFalse(nodes.getValue("projects").isPinned)
    }

    @Test
    fun `a pinned folder still appears as a row in the tree`() {
        val rows = buildNotebookTree(vault, expanded = emptySet(), pinnedFolders = listOf("projects"))
        val dirs = rows.filterIsInstance<NotebookTreeRow.Folder>().map { it.node.dir }
        assertTrue("projects" in dirs)
    }

    @Test
    fun `pinnedFolderNodes returns nodes in pin order and skips folders with no files`() {
        val nodes = pinnedFolderNodes(
            vault,
            folderColors = mapOf("projects/clients" to "rose"),
            pinnedFolders = listOf("projects/clients", "archive", "ghost/gone"),
        )
        assertEquals(listOf("projects/clients", "archive"), nodes.map { it.dir })
        assertEquals("rose", nodes.first().colorKey)
    }

    @Test
    fun `a flat vault with no folders produces only file rows`() {
        val flat = listOf(item("a.org"), item("b.org"))
        val rows = buildNotebookTree(flat, expanded = emptySet())
        assertTrue(rows.all { it is NotebookTreeRow.File })
        assertTrue(allFolderDirs(flat).isEmpty())
    }
}
