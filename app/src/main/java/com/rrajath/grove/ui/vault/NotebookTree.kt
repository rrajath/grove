package com.rrajath.grove.ui.vault

import com.rrajath.grove.settings.PinKind
import com.rrajath.grove.settings.PinnedItem
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
    /**
     * Effective monogram palette key: the user's [colorOverride] if set, otherwise
     * one derived from [dir] exactly as a file's is from its name.
     */
    val colorKey: String,
    /** `.org` files anywhere beneath [dir], recursively. */
    val recursiveOrgCount: Int,
    /** Immediate child folders of [dir]. */
    val directFolderCount: Int,
    /** True when any file beneath [dir] has an unresolved sync conflict. */
    val hasConflictDescendant: Boolean,
    /** User-chosen palette key for this folder's tile, or null to use the derived one. */
    val colorOverride: String? = null,
    /** Position in the pinned-folders list (0 = topmost); -1 when not pinned. */
    val pinnedIndex: Int = -1,
) {
    val isPinned: Boolean get() = pinnedIndex >= 0
}

/**
 * A folder whose recursive `.org` count is over this becomes a drill target on
 * the inline tree (variant 1b): tapping its row pushes the drill-down view
 * instead of expanding it in place (nested-folders plan §5).
 */
const val FOLDER_DRILL_THRESHOLD = 20

/** Duration and easing shared by the tree's expand/collapse height animation and its chevron flip. */
const val TREE_EXPAND_MILLIS = 240

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

/**
 * Build a [FolderNode] for every directory reachable from [items]. [folderColors]
 * maps a vault-relative dir to a user-chosen palette key; [pinnedFolders] is the
 * ordered pin list. Both default to empty for call sites that don't care (tests,
 * the first-open heuristic).
 */
fun buildFolderNodes(
    items: List<NotebookItem>,
    folderColors: Map<String, String> = emptyMap(),
    pinnedFolders: List<String> = emptyList(),
): Map<String, FolderNode> {
    val dirs = allFolderDirs(items)
    return dirs.associateWith { dir ->
        val prefix = "$dir/"
        val filesBeneath = items.filter { it.dir == dir || it.dir.startsWith(prefix) }
        val override = folderColors[dir]
        FolderNode(
            dir = dir,
            name = dir.substringAfterLast('/'),
            depth = dir.split('/').size,
            colorKey = override ?: nameHashPaletteKey(dir),
            recursiveOrgCount = filesBeneath.size,
            directFolderCount = dirs.count { parentOf(it) == dir },
            hasConflictDescendant = filesBeneath.any { it.hasConflict },
            colorOverride = override,
            pinnedIndex = pinnedFolders.indexOf(dir),
        )
    }
}

/**
 * The [FolderNode]s for [pinnedFolders], in pin order, skipping any pinned dir
 * that no longer holds a file (folders are path-derived, so a pin to an
 * emptied-out folder simply drops off the strip).
 */
fun pinnedFolderNodes(
    items: List<NotebookItem>,
    folderColors: Map<String, String> = emptyMap(),
    pinnedFolders: List<String> = emptyList(),
): List<FolderNode> {
    val nodes = buildFolderNodes(items, folderColors, pinnedFolders)
    return pinnedFolders.mapNotNull { nodes[it] }
}

/**
 * Flatten [items] into display rows. Within every level folders come first, then
 * files, each alphabetical by lowercased display name (matching the flat-list
 * sort). A folder's children are emitted only when its `dir` is in [expanded].
 *
 * A folder whose `dir` is in [pinnedFolders] is omitted entirely (its whole
 * subtree with it): a pinned folder lives only in the Pinned strip, exactly as a
 * pinned file is dropped from its in-tree position. A pinned *descendant* folder
 * doesn't hide its unpinned ancestor — `projects` still shows when only
 * `projects/clients` is pinned, just without the `clients` row.
 */
fun buildNotebookTree(
    items: List<NotebookItem>,
    expanded: Set<String>,
    folderColors: Map<String, String> = emptyMap(),
    pinnedFolders: List<String> = emptyList(),
): List<NotebookTreeRow> {
    val nodes = buildFolderNodes(items, folderColors, pinnedFolders)
    val pinnedDirs = pinnedFolders.toSet()
    val rows = mutableListOf<NotebookTreeRow>()

    fun emitLevel(dir: String) {
        nodes.values
            .filter { parentOf(it.dir) == dir }
            .filterNot { it.dir in pinnedDirs }
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
 * The display rows shown *under* an expanded pinned folder in the Pinned strip.
 * Same folders-first-then-files ordering and [expanded] gating as
 * [buildNotebookTree], but rooted at [rootDir]: no row is emitted for [rootDir]
 * itself (the strip already shows it), and every depth is shifted down so a
 * direct child sits exactly one indent step in from the flush strip row. A
 * pinned *descendant* folder is skipped here too — it carries its own strip row.
 */
fun pinnedFolderSubtreeRows(
    items: List<NotebookItem>,
    rootDir: String,
    expanded: Set<String>,
    folderColors: Map<String, String> = emptyMap(),
    pinnedFolders: List<String> = emptyList(),
): List<NotebookTreeRow> {
    if (rootDir.isEmpty()) return emptyList()
    val nodes = buildFolderNodes(items, folderColors, pinnedFolders)
    val pinnedDirs = pinnedFolders.toSet()
    // rootDir renders flush (depth 0) in the strip; a direct child folder has
    // absolute depth rootDepth + 1 and should land at effective depth 2 (one
    // indent step once FolderRow subtracts its own 1), so shift by rootDepth - 1.
    val shift = rootDir.split('/').size - 1
    val rows = mutableListOf<NotebookTreeRow>()

    fun emitLevel(dir: String) {
        nodes.values
            .filter { parentOf(it.dir) == dir }
            .filterNot { it.dir in pinnedDirs }
            .sortedBy { it.name.lowercase() }
            .forEach { node ->
                val isOpen = node.dir in expanded
                rows += NotebookTreeRow.Folder(node.copy(depth = node.depth - shift), isOpen)
                if (isOpen) emitLevel(node.dir)
            }
        val fileDepth = dir.split('/').size - shift
        items
            .filter { it.dir == dir }
            .sortedBy { it.displayName.lowercase() }
            .forEach { rows += NotebookTreeRow.File(it, fileDepth) }
    }

    emitLevel(rootDir)
    return rows
}

/**
 * A run of display rows the Notebooks list renders as one animating unit.
 *
 * [buildNotebookTree] returns a flat, expansion-gated row list; toggling a folder
 * inserts or removes a contiguous block of rows. Rendering each row as its own
 * `LazyColumn` item made every toggle fan out into N independent per-item
 * fade/slide animations that fought each other and shoved the rows below in one
 * jump. Grouping instead keeps every top-level folder together with its
 * currently-visible descendants so the whole subtree grows and shrinks its
 * height as a single block, and leaves root-level files as their own rows.
 */
sealed interface NotebookTreeRun {
    /** A top-level folder header plus the descendant rows currently visible under it. */
    data class Subtree(
        val header: NotebookTreeRow.Folder,
        val descendants: List<NotebookTreeRow>,
    ) : NotebookTreeRun

    /** A single row that stands on its own (a root-level file). */
    data class Loose(val row: NotebookTreeRow) : NotebookTreeRun
}

/**
 * Fold the flat rows from [buildNotebookTree] into [NotebookTreeRun]s: a
 * [NotebookTreeRun.Subtree] for every top-level folder (depth 1) carrying the
 * rows beneath it up to the next top-level folder or root-level file, and a
 * [NotebookTreeRun.Loose] for every root-level file. Order is preserved.
 */
fun groupNotebookTreeRuns(rows: List<NotebookTreeRow>): List<NotebookTreeRun> {
    val runs = mutableListOf<NotebookTreeRun>()
    var i = 0
    while (i < rows.size) {
        val row = rows[i]
        if (row is NotebookTreeRow.Folder && row.depth <= 1) {
            var j = i + 1
            while (j < rows.size) {
                val r = rows[j]
                if (r is NotebookTreeRow.Folder && r.depth <= 1) break
                if (r is NotebookTreeRow.File && r.depth <= 0) break
                j++
            }
            runs += NotebookTreeRun.Subtree(row, rows.subList(i + 1, j).toList())
            i = j
        } else {
            runs += NotebookTreeRun.Loose(row)
            i++
        }
    }
    return runs
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
fun drillLevel(
    items: List<NotebookItem>,
    dir: String,
    folderColors: Map<String, String> = emptyMap(),
    pinnedFolders: List<String> = emptyList(),
): DrillLevel {
    val nodes = buildFolderNodes(items, folderColors, pinnedFolders)
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

// --- flat mode (Settings § Look and Feel → "Flatten folders") ---

/** Path-grouped file order: parent directory first, then display name; both case-insensitive. */
private val flatRowOrder: Comparator<NotebookItem> =
    compareBy({ it.dir.lowercase() }, { it.displayName.lowercase() })

/**
 * The main file list for flat mode: every `.org` file as its own row, no folder
 * rows, grouped by parent directory then sorted by display name (so files in the
 * same folder cluster and root-level files come first). Files that live in the
 * Pinned strip — pinned files, and anything beneath a pinned folder — are pulled
 * out here, exactly as [buildNotebookTree] pulls a pinned subtree out of the tree.
 */
fun flatNotebookRows(
    items: List<NotebookItem>,
    pinnedItems: List<PinnedItem> = emptyList(),
): List<NotebookItem> {
    val pinnedFiles = pinnedItems.filter { it.kind == PinKind.FILE }.mapTo(mutableSetOf()) { it.path }
    val pinnedFolders = pinnedItems.filter { it.kind == PinKind.FOLDER }.map { it.path }
    fun underPinnedFolder(dir: String) =
        pinnedFolders.any { dir == it || dir.startsWith("$it/") }
    return items
        .filterNot { it.fileName in pinnedFiles || underPinnedFolder(it.dir) }
        .sortedWith(flatRowOrder)
}

/**
 * The Pinned strip contents for flat mode: each pin resolved in pin order to file
 * rows. A pinned file yields itself; a pinned folder yields every `.org` file
 * beneath it (path-grouped), with no folder row of its own. A file is emitted
 * once: a pinned file nested under a pinned folder, or a file under a more
 * specific pinned sub-folder, belongs to that more specific pin.
 */
fun flatPinnedRows(
    items: List<NotebookItem>,
    pinnedItems: List<PinnedItem>,
): List<NotebookItem> {
    val pinnedFiles = pinnedItems.filter { it.kind == PinKind.FILE }.mapTo(mutableSetOf()) { it.path }
    val pinnedFolders = pinnedItems.filter { it.kind == PinKind.FOLDER }.map { it.path }
    return pinnedItems.flatMap { pi ->
        when (pi.kind) {
            PinKind.FILE -> items.filter { it.fileName == pi.path }
            PinKind.FOLDER -> {
                val prefix = "${pi.path}/"
                val nestedPins = pinnedFolders.filter { it.startsWith(prefix) }
                fun underNestedPin(dir: String) =
                    nestedPins.any { dir == it || dir.startsWith("$it/") }
                items
                    .filter { f ->
                        (f.dir == pi.path || f.dir.startsWith(prefix)) &&
                            f.fileName !in pinnedFiles &&
                            !underNestedPin(f.dir)
                    }
                    .sortedWith(flatRowOrder)
            }
        }
    }
}
