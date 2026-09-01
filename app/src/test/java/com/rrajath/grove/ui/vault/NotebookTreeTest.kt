package com.rrajath.grove.ui.vault

import com.rrajath.grove.settings.NotebookSortKey
import com.rrajath.grove.settings.PinKind
import com.rrajath.grove.settings.PinnedItem
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
    fun `a descending sort reverses folders and files at every level`() {
        val rows = buildNotebookTree(
            vault, expanded = setOf("projects"),
            sort = NotebookSort(NotebookSortKey.ALPHABETICAL, ascending = false),
        )
        val folders = rows.filterIsInstance<NotebookTreeRow.Folder>()
            .filter { it.node.depth == 1 }.map { it.node.name }
        assertEquals(listOf("projects", "areas", "archive"), folders)
        val rootFiles = rows.filterIsInstance<NotebookTreeRow.File>()
            .filter { it.depth == 0 }.map { it.item.fileName }
        assertEquals(listOf("recipes.org", "journal.org", "inbox.org"), rootFiles)
    }

    @Test
    fun `last-modified sort orders folders by their newest descendant file`() {
        val items = listOf(
            item("a/old.org").copy(lastModified = 10),
            item("b/new.org").copy(lastModified = 90),
        )
        val newestFirst = NotebookSort(NotebookSortKey.LAST_MODIFIED, ascending = false)
        val folders = buildNotebookTree(items, expanded = emptySet(), sort = newestFirst)
            .filterIsInstance<NotebookTreeRow.Folder>().map { it.node.name }
        assertEquals(listOf("b", "a"), folders)
    }

    @Test
    fun `drillLevel honours the sort order`() {
        val desc = drillLevel(vault, "", sort = NotebookSort(NotebookSortKey.ALPHABETICAL, ascending = false))
        assertEquals(listOf("projects", "areas", "archive"), desc.childFolders.map { it.name })
        assertEquals(listOf("recipes.org", "journal.org", "inbox.org"), desc.files.map { it.fileName })
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
    fun `a pinned folder is omitted from the tree rows (strip-only)`() {
        val rows = buildNotebookTree(vault, expanded = setOf("projects"), pinnedFolders = listOf("projects"))
        val dirs = rows.filterIsInstance<NotebookTreeRow.Folder>().map { it.node.dir }
        assertFalse("projects" in dirs)
        // Its whole subtree goes with it — no orphaned child folder or file rows.
        assertFalse("projects/clients" in dirs)
        val paths = rows.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        assertFalse(paths.any { it.startsWith("projects/") })
    }

    @Test
    fun `a pinned nested folder is omitted while its unpinned parent stays`() {
        val rows = buildNotebookTree(
            vault, expanded = setOf("projects"), pinnedFolders = listOf("projects/clients"),
        )
        val dirs = rows.filterIsInstance<NotebookTreeRow.Folder>().map { it.node.dir }
        assertTrue("projects" in dirs)
        assertFalse("projects/clients" in dirs)
        val paths = rows.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        // projects' own files still render; the pinned sub-folder's don't.
        assertTrue("projects/grove.org" in paths)
        assertFalse("projects/clients/acme.org" in paths)
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
    fun `pinnedFolderSubtreeRows lists a pinned folder's contents with no row for the folder itself`() {
        val rows = pinnedFolderSubtreeRows(
            vault, rootDir = "projects", expanded = emptySet(),
        )
        val folders = rows.filterIsInstance<NotebookTreeRow.Folder>().map { it.node.dir }
        val files = rows.filterIsInstance<NotebookTreeRow.File>().map { it.item.fileName }
        // The collapsed child folder shows; its files stay hidden until expanded.
        assertEquals(listOf("projects/clients"), folders)
        assertEquals(listOf("projects/grove.org", "projects/website.org"), files)
    }

    @Test
    fun `pinnedFolderSubtreeRows expands a nested child and shifts depths to indent under the strip row`() {
        val rows = pinnedFolderSubtreeRows(
            vault, rootDir = "projects", expanded = setOf("projects/clients"),
        )
        val clients = rows.filterIsInstance<NotebookTreeRow.Folder>()
            .single { it.node.dir == "projects/clients" }
        // projects (depth 1) is flush; its direct child folder indents one step.
        assertEquals(2, clients.depth)
        val acme = rows.filterIsInstance<NotebookTreeRow.File>()
            .single { it.item.fileName == "projects/clients/acme.org" }
        assertEquals(2, acme.depth)
    }

    @Test
    fun `pinnedFolderSubtreeRows skips a pinned descendant folder`() {
        val rows = pinnedFolderSubtreeRows(
            vault, rootDir = "projects", expanded = emptySet(),
            pinnedFolders = listOf("projects", "projects/clients"),
        )
        assertFalse(
            rows.filterIsInstance<NotebookTreeRow.Folder>().any { it.node.dir == "projects/clients" },
        )
    }

    @Test
    fun `a flat vault with no folders produces only file rows`() {
        val flat = listOf(item("a.org"), item("b.org"))
        val rows = buildNotebookTree(flat, expanded = emptySet())
        assertTrue(rows.all { it is NotebookTreeRow.File })
        assertTrue(allFolderDirs(flat).isEmpty())
    }

    // --- groupNotebookTreeRuns (Notebooks list animation grouping) ---

    private fun runLabels(runs: List<NotebookTreeRun>) = runs.map { run ->
        when (run) {
            is NotebookTreeRun.Subtree ->
                "sub:${run.header.node.dir}(${run.descendants.size})"
            is NotebookTreeRun.Loose -> when (val r = run.row) {
                is NotebookTreeRow.Folder -> "loose-d:${r.node.dir}"
                is NotebookTreeRow.File -> "loose-f:${r.item.fileName}"
            }
        }
    }

    @Test
    fun `collapsed tree is one run per top-level folder plus one per root file`() {
        val runs = groupNotebookTreeRuns(buildNotebookTree(vault, expanded = emptySet()))
        assertEquals(
            listOf(
                "sub:archive(0)", "sub:areas(0)", "sub:projects(0)",
                "loose-f:inbox.org", "loose-f:journal.org", "loose-f:recipes.org",
            ),
            runLabels(runs),
        )
    }

    @Test
    fun `an expanded folder's descendants all land in its subtree run`() {
        val runs = groupNotebookTreeRuns(
            buildNotebookTree(vault, expanded = setOf("projects", "projects/clients")),
        )
        val projects = runs.filterIsInstance<NotebookTreeRun.Subtree>()
            .single { it.header.node.dir == "projects" }
        assertEquals(
            listOf(
                "projects/clients",
                "projects/clients/acme.org", "projects/clients/northwind.org",
                "projects/grove.org", "projects/website.org",
            ),
            projects.descendants.map {
                when (it) {
                    is NotebookTreeRow.Folder -> it.node.dir
                    is NotebookTreeRow.File -> it.item.fileName
                }
            },
        )
        // Sibling top-level folders stay separate runs, not swallowed by projects.
        assertTrue(runLabels(runs).contains("sub:archive(0)"))
    }

    @Test
    fun `a flat vault with no folders is all loose file runs`() {
        val runs = groupNotebookTreeRuns(
            buildNotebookTree(listOf(item("a.org"), item("b.org")), expanded = emptySet()),
        )
        assertEquals(listOf("loose-f:a.org", "loose-f:b.org"), runLabels(runs))
    }

    // --- flat mode (Settings § Look and Feel → "Flatten folders") ---

    @Test
    fun `flatNotebookRows lists every file by display name under the default sort`() {
        val rows = flatNotebookRows(vault).map { it.fileName }
        assertEquals(
            listOf(
                "archive/2024.org",
                "projects/clients/acme.org",
                "projects/grove.org",
                "areas/health.org",
                "inbox.org",
                "journal.org",
                "projects/clients/northwind.org",
                "recipes.org",
                "projects/website.org",
            ),
            rows,
        )
    }

    @Test
    fun `flatNotebookRows honours a descending alphabetical sort`() {
        val rows = flatNotebookRows(
            vault,
            sort = NotebookSort(NotebookSortKey.ALPHABETICAL, ascending = false),
        ).map { it.fileName }
        assertEquals("projects/website.org", rows.first())
        assertEquals("archive/2024.org", rows.last())
    }

    @Test
    fun `flatNotebookRows honours a last-modified sort`() {
        val items = listOf(
            item("old.org").copy(lastModified = 100),
            item("mid.org").copy(lastModified = 200),
            item("new.org").copy(lastModified = 300),
        )
        val asc = flatNotebookRows(items, sort = NotebookSort(NotebookSortKey.LAST_MODIFIED, ascending = true))
        assertEquals(listOf("old.org", "mid.org", "new.org"), asc.map { it.fileName })
        val desc = flatNotebookRows(items, sort = NotebookSort(NotebookSortKey.LAST_MODIFIED, ascending = false))
        assertEquals(listOf("new.org", "mid.org", "old.org"), desc.map { it.fileName })
    }

    @Test
    fun `flatNotebookRows pulls pinned files and pinned-folder subtrees out of the main list`() {
        val pins = listOf(
            PinnedItem(PinKind.FILE, "inbox.org"),
            PinnedItem(PinKind.FOLDER, "projects"),
        )
        val rows = flatNotebookRows(vault, pins).map { it.fileName }
        assertFalse("inbox.org" in rows)
        assertFalse(rows.any { it.startsWith("projects/") })
        assertTrue("journal.org" in rows)
        assertTrue("areas/health.org" in rows)
    }

    @Test
    fun `flatPinnedRows resolves each pin in order, expanding a folder to its files inline`() {
        val pins = listOf(
            PinnedItem(PinKind.FILE, "recipes.org"),
            PinnedItem(PinKind.FOLDER, "projects"),
        )
        // The pinned file stays first (pin order); the pinned folder's files
        // follow, ordered by the notebook sort (default: display name A→Z).
        assertEquals(
            listOf(
                "recipes.org",
                "projects/clients/acme.org", "projects/grove.org",
                "projects/clients/northwind.org", "projects/website.org",
            ),
            flatPinnedRows(vault, pins).map { it.fileName },
        )
    }

    @Test
    fun `flatPinnedRows emits a file once when both a folder and a nested folder are pinned`() {
        val pins = listOf(
            PinnedItem(PinKind.FOLDER, "projects"),
            PinnedItem(PinKind.FOLDER, "projects/clients"),
        )
        val rows = flatPinnedRows(vault, pins).map { it.fileName }
        assertEquals(rows, rows.distinct())
        // The nested pin owns its files; the parent pin emits only its own.
        assertEquals(
            listOf(
                "projects/grove.org", "projects/website.org",
                "projects/clients/acme.org", "projects/clients/northwind.org",
            ),
            rows,
        )
    }
}
