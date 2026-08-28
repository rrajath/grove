package com.rrajath.grove.ui.vault

import com.rrajath.grove.ui.components.nameHashPaletteKey

/**
 * Pure tree model for the Notebooks screen (nested-folders plan §4, variant 1a).
 *
 * A flat list of [NotebookItem]s (each carrying a vault-relative path as its
 * `fileName`) is grouped by directory into [FolderNode]s and flattened into
 * [NotebookTreeRow]s honouring a per-folder expansion set. No Android or Compose
 * dependency, so every rule here is JVM-testable.
 */

/**
 * One directory in the tree. Every folder that appears here has at least one
 * `.org` file somewhere beneath it by construction (folders are derived from the
 * paths of real files), so the plan's "hide folders with no `.org` file" rule
 * needs no explicit pruning step.
 */
data class FolderNode(
    /** Vault-relative directory path, `/`-separated, e.g. `projects/clients`. */
    val dir: String,
    /** Last path segment, e.g. `clients`. */
    val name: String,
    /** 1 for a top-level folder, 2 one level in, ... Drives the indent cap. */
    val depth: Int,
    /** Monogram palette key, derived from [dir] exactly as a file's is from its name. */
    val colorKey: String,
    /** `.org` files anywhere beneath [dir], recursively. */
    val recursiveOrgCount: Int,
    /** Immediate child folders of [dir]. */
    val directFolderCount: Int,
    /** True when any file beneath [dir] has an unresolved sync conflict. */
    val hasConflictDescendant: Boolean,
)

/**
 * A folder whose recursive `.org` count is over this becomes a drill target on
 * the inline tree (variant 1b): tapping its row pushes the drill-down view
 * instead of expanding it in place (nested-folders plan §5).
 */
const val FOLDER_DRILL_THRESHOLD = 20

/** A single flattened display row: either a folder header or a file. */
sealed interface NotebookTreeRow {
    /** 0 for a root file, 1 for a top-level folder or a file one level deep, ... */
    val depth: Int

    data class Folder(val node: FolderNode, val expanded: Boolean) : NotebookTreeRow {
        override val depth: Int get() = node.depth
    }

    data class File(val item: NotebookItem, override val depth: Int) : NotebookTreeRow
}

/** `projects/clients` -> `[projects, projects/clients]`; `""` -> `[]`. */
private fun ancestorDirs(dir: String): List<String> {
    if (dir.isEmpty()) return emptyList()
    val segments = dir.split('/')
    return (1..segments.size).map { segments.subList(0, it).joinToString("/") }
}

/** The parent directory of [dir], or `""` for a top-level directory. */
private fun parentOf(dir: String): String = dir.substringBeforeLast('/', "")

/** Every directory that appears in [items], including intermediate ancestors. */
fun allFolderDirs(items: List<NotebookItem>): Set<String> =
    items.flatMapTo(mutableSetOf()) { ancestorDirs(it.dir) }

/** Build a [FolderNode] for every directory reachable from [items]. */
fun buildFolderNodes(items: List<NotebookItem>): Map<String, FolderNode> {
    val dirs = allFolderDirs(items)
    return dirs.associateWith { dir ->
        val prefix = "$dir/"
        val filesBeneath = items.filter { it.dir == dir || it.dir.startsWith(prefix) }
        FolderNode(
            dir = dir,
            name = dir.substringAfterLast('/'),
            depth = dir.split('/').size,
            colorKey = nameHashPaletteKey(dir),
            recursiveOrgCount = filesBeneath.size,
            directFolderCount = dirs.count { parentOf(it) == dir },
            hasConflictDescendant = filesBeneath.any { it.hasConflict },
        )
    }
}

/**
 * Flatten [items] into display rows. Within every level folders come first, then
 * files, each alphabetical by lowercased display name (matching the flat-list
 * sort). A folder's children are emitted only when its `dir` is in [expanded].
 */
fun buildNotebookTree(items: List<NotebookItem>, expanded: Set<String>): List<NotebookTreeRow> {
    val nodes = buildFolderNodes(items)
    val rows = mutableListOf<NotebookTreeRow>()

    fun emitLevel(dir: String) {
        nodes.values
            .filter { parentOf(it.dir) == dir }
            .sortedBy { it.name.lowercase() }
            .forEach { node ->
                val isOpen = node.dir in expanded
                rows += NotebookTreeRow.Folder(node, isOpen)
                if (isOpen) emitLevel(node.dir)
            }
        val fileDepth = if (dir.isEmpty()) 0 else dir.split('/').size
        items
            .filter { it.dir == dir }
            .sortedBy { it.displayName.lowercase() }
            .forEach { rows += NotebookTreeRow.File(it, fileDepth) }
    }

    emitLevel("")
    return rows
}

/**
 * One directory's contents for the drill-down view (variant 1b): its immediate
 * child folders and the `.org` files that live directly in it, each sorted
 * folders-first-then-files exactly as the inline tree sorts a level. Rows never
 * indent in 1b, so no depth is carried.
 */
data class DrillLevel(
    val dir: String,
    val childFolders: List<FolderNode>,
    val files: List<NotebookItem>,
)

/** Build the [DrillLevel] for [dir] (`""` = the vault root) from the flat [items]. */
fun drillLevel(items: List<NotebookItem>, dir: String): DrillLevel {
    val nodes = buildFolderNodes(items)
    return DrillLevel(
        dir = dir,
        childFolders = nodes.values
            .filter { parentOf(it.dir) == dir }
            .sortedBy { it.name.lowercase() },
        files = items
            .filter { it.dir == dir }
            .sortedBy { it.displayName.lowercase() },
    )
}

/**
 * Folders to auto-expand on the very first open of a vault: any folder that
 * recursively contains a note with a SCHEDULED or DEADLINE date. [plannedFileNames]
 * are the vault-relative paths of files that hold at least one such note (from
 * `IndexDao.plannedNotes()`). A vault with a persisted expansion state never
 * calls this.
 */
fun firstOpenExpandedDirs(plannedFileNames: Collection<String>): Set<String> =
    plannedFileNames
        .map { it.substringBeforeLast('/', "") }
        .filter { it.isNotEmpty() }
        .flatMapTo(mutableSetOf()) { ancestorDirs(it) }
