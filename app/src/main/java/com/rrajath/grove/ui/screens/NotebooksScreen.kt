package com.rrajath.grove.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.painterResource
import com.rrajath.grove.R
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.sync.SyncState
import com.rrajath.grove.ui.components.ChangeIconColorDialog
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.MonogramTile
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.ReminderPermissionBanner
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.monogramLetter
import com.rrajath.grove.ui.components.monogramPalette
import com.rrajath.grove.ui.components.nameHashPaletteKey
import com.rrajath.grove.ui.components.searchIcon
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.FOLDER_DRILL_THRESHOLD
import com.rrajath.grove.ui.vault.FolderNode
import com.rrajath.grove.ui.vault.NotebookItem
import com.rrajath.grove.ui.vault.NotebookTreeRow
import com.rrajath.grove.ui.vault.drillLevel
import com.rrajath.grove.ui.vault.NotebooksUiState
import com.rrajath.grove.ui.vault.NotebooksViewModel
import com.rrajath.grove.vault.vaultPath

/** Notebook list home screen (design spec §2), driven by the sync index. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenConflict: (String) -> Unit,
    viewModel: NotebooksViewModel = viewModel(factory = NotebooksViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Non-null while the "New notebook" dialog is open; the value is the target
    // directory ("" = vault root, or the folder being browsed in the drill view).
    var createInDir by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var styleTarget by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf<String?>(null) }
    // Folder long-press menu targets (variant 1a follow-up); the value is the folder's dir path.
    // Stored as the path (not the node) so each dialog reads the folder's live state on recompose.
    var folderRenameTarget by remember { mutableStateOf<String?>(null) }
    var folderColorTarget by remember { mutableStateOf<String?>(null) }
    var folderDeleteTarget by remember { mutableStateOf<String?>(null) }
    // Drill-down view (variant 1b): the folder currently being browsed as a full
    // screen, or null for the inline tree. A folder with more than
    // FOLDER_DRILL_THRESHOLD files opens here instead of expanding in place.
    var drillDir by rememberSaveable { mutableStateOf<String?>(null) }
    fun drillUp() {
        drillDir = drillDir?.substringBeforeLast('/', "")?.ifEmpty { null }
    }

    BackHandler(enabled = drillDir != null) { drillUp() }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.saveVaultUri(uri.toString())
        }
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            val loadedState = state as? NotebooksUiState.Loaded
            val currentDrill = drillDir
            if (loadedState != null && currentDrill != null) {
                GroveTopBar(
                    leading = { IconGlyph("←", onClick = { drillUp() }) },
                    title = {
                        Text(
                            currentDrill.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleLarge,
                            color = c.ink,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    },
                    subtitle = {
                        Breadcrumb(
                            vaultName = loadedState.vaultDisplayName,
                            dir = currentDrill,
                            onExit = { drillDir = null },
                            onNavigate = { drillDir = it },
                        )
                    },
                    actions = {
                        IconGlyph("＋", onClick = { createInDir = currentDrill })
                    },
                )
            } else {
                GroveTopBar(
                    leading = { IconGlyph("☰", onClick = onOpenDrawer) },
                    title = {
                        Text(
                            "Notebooks",
                            style = MaterialTheme.typography.titleLarge,
                            color = c.ink,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    },
                    actions = {
                        if (loadedState != null) {
                            IconGlyph("＋", onClick = { createInDir = "" })
                            if (loadedState.hasFolders) {
                                IconGlyph(
                                    if (loadedState.allFoldersCollapsed) Icons.Default.UnfoldMore
                                    else Icons.Default.UnfoldLess,
                                    contentDescription = if (loadedState.allFoldersCollapsed) {
                                        "Expand all folders"
                                    } else {
                                        "Collapse all folders"
                                    },
                                    onClick = {
                                        viewModel.setAllFoldersExpanded(loadedState.allFoldersCollapsed)
                                    },
                                )
                            }
                            SyncStatusIcon(loadedState, context)
                        }
                        IconGlyph(searchIcon(), contentDescription = "Search", onClick = onOpenSearch)
                    },
                )
            }
        },
        floatingActionButton = {
            // The Capture FAB belongs to the tree view; the drill-down is a
            // navigation surface, so it hides while browsing a folder.
            if (drillDir == null) {
                Row(
                    Modifier
                        .height(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(c.accent)
                        .clickable(onClick = onOpenCapture)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", fontFamily = PlexSans, fontSize = 21.sp, color = c.accentInk)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Capture",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, color = c.accentInk,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
        ) {
            when (val s = state) {
                is NotebooksUiState.NoVault ->
                    NoVaultState(onChooseFolder = { folderPicker.launch(null) })

                is NotebooksUiState.Loaded -> {
                    ReminderPermissionBanner(pendingCount = s.remindersPendingPermission)

                    @Composable
                    fun fileRow(nb: NotebookItem, depth: Int, showPath: Boolean, flat: Boolean = false) {
                        FileRow(
                            notebook = nb,
                            showFileIcon = s.showFileIcons,
                            depth = depth,
                            showPathSubtitle = showPath,
                            flat = flat,
                            onClick = { onOpenNotebook(nb.fileName) },
                            onOpenConflict = { onOpenConflict(nb.fileName) },
                            onRename = { renameTarget = nb.fileName },
                            onMove = { moveTarget = nb.fileName },
                            onChangeIcon = { styleTarget = nb.fileName },
                            onDelete = { viewModel.trashNotebook(nb.fileName) },
                            onForceReload = { viewModel.forceReload(nb.fileName) },
                            onPin = { viewModel.pinNotebook(nb.fileName) },
                            onUnpin = { viewModel.unpinNotebook(nb.fileName) },
                        )
                    }

                    @Composable
                    fun folderRow(
                        node: FolderNode,
                        expanded: Boolean = false,
                        flat: Boolean = false,
                        pinnedStrip: Boolean = false,
                        onClick: () -> Unit,
                    ) {
                        FolderRow(
                            node = node,
                            expanded = expanded,
                            chevron = !flat && node.recursiveOrgCount > FOLDER_DRILL_THRESHOLD,
                            flat = flat,
                            pinnedStrip = pinnedStrip,
                            onClick = onClick,
                            onPin = { viewModel.pinFolder(node.dir) },
                            onUnpin = { viewModel.unpinFolder(node.dir) },
                            onRename = { folderRenameTarget = node.dir },
                            onChangeColor = { folderColorTarget = node.dir },
                            onDelete = { folderDeleteTarget = node.dir },
                        )
                    }

                    // Tree/pinned-strip tap: drill into a big folder, else toggle it in place.
                    fun openFolder(node: FolderNode) {
                        if (node.recursiveOrgCount > FOLDER_DRILL_THRESHOLD) drillDir = node.dir
                        else viewModel.toggleFolder(node.dir)
                    }

                    val currentDrill = drillDir
                    if (currentDrill != null) {
                        // Variant 1b: one folder as a full screen, rows never indent.
                        val level = remember(s.notebooks, currentDrill, s.pinnedFolders, s.folderColors) {
                            drillLevel(
                                s.notebooks, currentDrill,
                                folderColors = s.folderColors,
                                pinnedFolders = s.pinnedFolders.map { it.dir },
                            )
                        }
                        if (level.childFolders.isEmpty() && level.files.isEmpty()) {
                            CenterMessage("✦", "Nothing in this folder yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().testTag("notebooks_drill_list"),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                            ) {
                                items(level.childFolders, key = { "dir:${it.dir}" }) { node ->
                                    folderRow(
                                        node = node,
                                        flat = true,
                                        onClick = { drillDir = node.dir },
                                    )
                                }
                                items(level.files, key = { "file:${it.fileName}" }) { nb ->
                                    fileRow(nb, depth = 0, showPath = false, flat = true)
                                }
                            }
                        }
                    } else {
                        val isRefreshing = s.syncState is SyncState.Checking || s.syncState is SyncState.Pulling
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.requestSync() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (s.notebooks.isEmpty()) {
                                CenterMessage("✦", "No .org files here yet", "Capture a note or create a notebook with ＋")
                            } else {
                                val listState = rememberLazyListState()
                                Box(Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize().testTag("notebooks_list"),
                                        // Bottom inset so the last row scrolls clear of
                                        // the FAB instead of sitting underneath it.
                                        contentPadding = PaddingValues(bottom = 86.dp),
                                    ) {
                                        if (s.pinned.isNotEmpty() || s.pinnedFolders.isNotEmpty()) {
                                            item(key = "strip:pinned") { StripLabel("Pinned") }
                                            // Folders first, then files — matching the tree's per-level sort.
                                            items(s.pinnedFolders, key = { "pindir:${it.dir}" }) { node ->
                                                folderRow(
                                                    node = node,
                                                    expanded = node.dir in s.expandedFolders,
                                                    pinnedStrip = true,
                                                    onClick = { openFolder(node) },
                                                )
                                            }
                                            items(s.pinned, key = { "pin:${it.fileName}" }) { nb ->
                                                fileRow(nb, depth = 0, showPath = true)
                                            }
                                        }
                                        items(
                                            s.rows,
                                            key = { row ->
                                                when (row) {
                                                    is NotebookTreeRow.Folder -> "dir:${row.node.dir}"
                                                    is NotebookTreeRow.File -> "file:${row.item.fileName}"
                                                }
                                            },
                                        ) { row ->
                                            when (row) {
                                                is NotebookTreeRow.Folder ->
                                                    folderRow(
                                                        node = row.node,
                                                        expanded = row.expanded,
                                                        onClick = { openFolder(row.node) },
                                                    )
                                                is NotebookTreeRow.File ->
                                                    fileRow(row.item, row.depth, showPath = false)
                                            }
                                        }
                                    }
                                    ScrollJumpButtons(
                                        listState = listState,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 86.dp, end = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    createInDir?.let { dir ->
        NameDialog(
            title = "New notebook",
            initial = "",
            confirmLabel = "Create",
            contextLabel = if (dir.isNotEmpty()) "in $dir/" else null,
            onDismiss = { createInDir = null },
            onConfirm = { name ->
                viewModel.createNotebook(name, dir)
                createInDir = null
            },
        )
    }
    moveTarget?.let { target ->
        val loaded = state as? NotebooksUiState.Loaded
        val nb = loaded?.notebooks?.firstOrNull { it.fileName == target }
        if (loaded == null || nb == null) {
            moveTarget = null
        } else {
            MoveToFolderSheet(
                displayName = nb.displayName,
                currentDir = nb.dir,
                notebooks = loaded.notebooks,
                vaultName = loaded.vaultDisplayName,
                onConfirm = { newDir ->
                    viewModel.moveNotebook(target, newDir)
                    moveTarget = null
                },
                onDismiss = { moveTarget = null },
            )
        }
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "Rename $target",
            initial = target,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameNotebook(target, name)
                renameTarget = null
            },
        )
    }
    styleTarget?.let { target ->
        val notebook = (state as? NotebooksUiState.Loaded)?.notebooks
            ?.firstOrNull { it.fileName == target }
        if (notebook == null) {
            styleTarget = null
        } else {
            ChangeIconColorDialog(
                name = notebook.displayName,
                hint = if (notebook.displayName == notebook.fileName.substringAfterLast('/')) {
                    "Letter follows the file name"
                } else {
                    "Letter follows the title"
                },
                letter = monogramLetter(notebook.displayName),
                currentColorKey = notebook.color ?: nameHashPaletteKey(notebook.fileName),
                onPickColor = { key -> viewModel.setNotebookColor(target, key) },
                onDismiss = { styleTarget = null },
            )
        }
    }
    folderRenameTarget?.let { dir ->
        NameDialog(
            title = "Rename ${dir.substringAfterLast('/')}",
            initial = dir.substringAfterLast('/'),
            confirmLabel = "Rename",
            placeholder = "folder name",
            onDismiss = { folderRenameTarget = null },
            onConfirm = { name ->
                viewModel.renameFolder(dir, name)
                folderRenameTarget = null
            },
        )
    }
    folderColorTarget?.let { dir ->
        val loaded = state as? NotebooksUiState.Loaded
        ChangeIconColorDialog(
            name = dir.substringAfterLast('/'),
            hint = "Color follows the folder name",
            letter = "▪",
            glyph = "▪",
            currentColorKey = loaded?.folderColors?.get(dir) ?: nameHashPaletteKey(dir),
            onPickColor = { key -> viewModel.setFolderColor(dir, key) },
            onDismiss = { folderColorTarget = null },
        )
    }
    folderDeleteTarget?.let { dir ->
        val c = MaterialTheme.grove
        val loaded = state as? NotebooksUiState.Loaded
        val node = loaded?.let { l ->
            l.rows.firstNotNullOfOrNull { (it as? NotebookTreeRow.Folder)?.node?.takeIf { n -> n.dir == dir } }
                ?: l.pinnedFolders.firstOrNull { it.dir == dir }
        }
        if (node == null) {
            folderDeleteTarget = null
        } else {
            val count = node.recursiveOrgCount
            AlertDialog(
                onDismissRequest = { folderDeleteTarget = null },
                containerColor = c.surface,
                title = {
                    Text(
                        "Delete ${node.name}?",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink,
                    )
                },
                text = {
                    Text(
                        "This moves $count ${if (count == 1) "note" else "notes"} to the trash. " +
                            "You can restore them from the synced folder.",
                        fontFamily = PlexSans, fontSize = 13.sp, color = c.ink2,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFolder(dir)
                            folderDeleteTarget = null
                        },
                    ) { Text("Delete", color = c.red, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { folderDeleteTarget = null }) {
                        Text("Cancel", color = c.ink2)
                    }
                },
            )
        }
    }
}

/**
 * Sync status indicator in the top app bar. Shows a green check when the last sync
 * completed successfully with no conflicts, a warning glyph when there is an active
 * sync error or any notebook has an unresolved conflict, or a spinner mid-sync.
 * Tapping any of them shows a toast with the sync status detail.
 */
@Composable
private fun SyncStatusIcon(state: NotebooksUiState.Loaded, context: android.content.Context) {
    val c = MaterialTheme.grove
    val conflictCount = state.notebooks.count { it.hasConflict }
    when (val sync = state.syncState) {
        is SyncState.Error -> {
            IconButton(
                onClick = {
                    Toast.makeText(context, "Sync issue: ${sync.message}", Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Sync issue", tint = c.amber)
            }
        }
        is SyncState.Checking -> {
            IconButton(
                onClick = { Toast.makeText(context, "Checking for changes…", Toast.LENGTH_SHORT).show() },
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Checking sync", tint = c.ink2)
            }
        }
        is SyncState.Pulling -> {
            IconButton(
                onClick = {
                    Toast.makeText(
                        context,
                        "Syncing ${sync.fileName.substringAfterLast('/')}…",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Syncing", tint = c.ink2)
            }
        }
        else -> {
            val minutes = state.lastSyncAt
                ?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0) }
            val syncedMessage = if (minutes != null) "Synced $minutes minutes ago" else "Not synced yet"
            if (conflictCount > 0) {
                IconButton(
                    onClick = {
                        val conflictMessage = if (conflictCount == 1) {
                            "1 file has a conflict"
                        } else {
                            "$conflictCount files have conflicts"
                        }
                        Toast.makeText(context, "$syncedMessage · $conflictMessage", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Sync conflicts", tint = c.amber)
                }
            } else {
                IconButton(
                    onClick = { Toast.makeText(context, syncedMessage, Toast.LENGTH_SHORT).show() },
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Synced", tint = c.green)
                }
            }
        }
    }
}

/**
 * Vertical indent rail + left inset shared by every tree row. Mirrors the
 * prototype math: indent caps at 2 levels (`min(depth, 2) * 20dp`), with a 1.5dp
 * `line2` guide two dp left of the content for any row below the root.
 */
@Composable
private fun TreeRowContainer(depth: Int, content: @Composable () -> Unit) {
    val c = MaterialTheme.grove
    val indent: Dp = (minOf(depth, 2) * 20).dp
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (depth > 0) {
            Box(
                Modifier
                    .offset(x = indent - 2.dp)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.line2),
            )
        }
        Box(Modifier.padding(start = indent)) { content() }
    }
}

/** Small caps section label above the pinned strip. */
@Composable
private fun StripLabel(text: String) {
    val c = MaterialTheme.grove
    Text(
        text.uppercase(),
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 1.sp,
        color = c.ink3,
        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Drill-down breadcrumb (variant 1b): the vault folder's name, then one crumb
 * per path segment. Tapping the root name returns to the inline tree; tapping a
 * segment jumps to that folder. The last crumb is the current folder, highlighted.
 */
@Composable
private fun Breadcrumb(
    vaultName: String,
    dir: String,
    onExit: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    val segments = dir.split('/').filter { it.isNotEmpty() }

    @Composable
    fun crumb(label: String, current: Boolean, onClick: () -> Unit) {
        Text(
            label,
            fontFamily = PlexMono,
            fontSize = 12.sp,
            color = if (current) c.ink else c.ink3,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(if (current) Modifier.background(c.surface2) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumb(vaultName, current = segments.isEmpty(), onClick = onExit)
        segments.forEachIndexed { i, seg ->
            Text(
                "/",
                fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3,
                modifier = Modifier.padding(horizontal = 1.dp),
            )
            val path = segments.subList(0, i + 1).joinToString("/")
            crumb(seg, current = i == segments.lastIndex, onClick = { onNavigate(path) })
        }
    }
}

/**
 * A folder row. In the inline tree (variant 1a) it carries a rotating caret and
 * indents under its parent; a folder over [FOLDER_DRILL_THRESHOLD] files shows a
 * static `›` ([chevron]) instead, since tapping it opens the drill-down view. In
 * that drill-down view ([flat]) rows never indent and the `›` sits on the right.
 *
 * The tile is a 42dp rounded square (matching the file monogram tiles) with the
 * folder's palette colour: soft-tint fill, a tinted border, and a `▪` glyph. A
 * descendant sync conflict shows a non-interactive amber warning glyph.
 *
 * The top-level folder row sits flush with the root files (no rail): its indent
 * rail belongs to its *children*, so the container depth is one less than the
 * folder's own nesting level. [pinnedStrip] forces that flush depth for a copy
 * shown in the Pinned strip.
 *
 * Long-pressing the row opens a menu mirroring the file row's: pin/unpin,
 * rename, change icon colour, and delete (moves every descendant file to trash).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    node: FolderNode,
    expanded: Boolean,
    chevron: Boolean = false,
    flat: Boolean = false,
    pinnedStrip: Boolean = false,
    onClick: () -> Unit,
    onPin: () -> Unit = {},
    onUnpin: () -> Unit = {},
    onRename: () -> Unit = {},
    onChangeColor: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val c = MaterialTheme.grove
    val (fg, bg) = monogramPalette(c, node.colorKey)
    var menuOpen by remember { mutableStateOf(false) }
    TreeRowContainer(depth = if (flat || pinnedStrip) 0 else node.depth - 1) {
      Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!flat) {
                Text(
                    if (chevron) "›" else "▸",
                    fontFamily = PlexMono,
                    fontSize = if (chevron) 15.sp else 11.sp,
                    color = c.ink3,
                    modifier = Modifier
                        .width(12.dp)
                        .rotate(if (!chevron && expanded) 90f else 0f),
                )
                Spacer(Modifier.width(9.dp))
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▪", fontFamily = PlexMono, fontSize = 15.sp, color = fg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    node.name,
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp, color = c.ink,
                )
                Text(
                    buildString {
                        append(node.recursiveOrgCount)
                        append(if (node.recursiveOrgCount == 1) " file" else " files")
                        if (node.directFolderCount > 0) {
                            append(" / ")
                            append(node.directFolderCount)
                            append(if (node.directFolderCount == 1) " folder" else " folders")
                        }
                    },
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (node.isPinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.padding(end = 6.dp).size(14.dp),
                )
            }
            if (node.hasConflictDescendant) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Contains a sync conflict",
                    tint = c.amber,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (flat) {
                Spacer(Modifier.width(8.dp))
                Text("›", fontFamily = PlexMono, fontSize = 17.sp, color = c.ink3)
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = c.surface,
        ) {
            if (node.isPinned) {
                DropdownMenuItem(
                    text = { Text("Unpin", fontFamily = PlexSans, color = c.ink) },
                    onClick = { menuOpen = false; onUnpin() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Pin to top", fontFamily = PlexSans, color = c.ink) },
                    onClick = { menuOpen = false; onPin() },
                )
            }
            DropdownMenuItem(
                text = { Text("Rename", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Change icon color", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onChangeColor() },
            )
            DropdownMenuItem(
                text = { Text("Delete (to trash)", fontFamily = PlexSans, color = c.red) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    notebook: NotebookItem,
    showFileIcon: Boolean,
    depth: Int,
    showPathSubtitle: Boolean,
    flat: Boolean = false,
    onClick: () -> Unit,
    onOpenConflict: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onChangeIcon: () -> Unit,
    onDelete: () -> Unit,
    onForceReload: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    val c = MaterialTheme.grove
    val letter = monogramLetter(notebook.displayName)
    val colorKey = notebook.color ?: nameHashPaletteKey(notebook.fileName)
    var menuOpen by remember { mutableStateOf(false) }
    // Files nested inside an expanded folder sit one caret-width in so they line
    // up under folder names. Top-level files (depth 0), the flat pinned strip and
    // the drill-down view have no caret column, so they sit flush.
    val leadingInset = if (showPathSubtitle || flat || depth == 0) 0.dp else 21.dp

    TreeRowContainer(depth = if (flat) 0 else depth) {
      Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingInset > 0.dp) Spacer(Modifier.width(leadingInset))
            if (showFileIcon) {
                MonogramTile(letter = letter, colorKey = colorKey, size = 42.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    notebook.displayName,
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = c.ink,
                )
                if (showPathSubtitle && notebook.dir.isNotEmpty()) {
                    Text(
                        "${notebook.dir}/",
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                val ago = DateUtils.getRelativeTimeSpanString(notebook.lastModified)
                // Stub rows show only the timestamp until the background parse
                // fills in the count; avoids a "0 notes" flash before the jump.
                Text(
                    if (notebook.isIndexed) "${notebook.noteCount} notes · $ago" else "$ago",
                    fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                )
            }
            if (notebook.isPinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.padding(end = 6.dp).size(14.dp),
                )
            }
            if (notebook.hasConflict) {
                Pill("Conflict", fg = c.amber, bg = c.amberSoft, onClick = onOpenConflict)
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = c.surface,
        ) {
            if (notebook.isPinned) {
                DropdownMenuItem(
                    text = { Text("Unpin", fontFamily = PlexSans, color = c.ink) },
                    onClick = { menuOpen = false; onUnpin() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Pin to top", fontFamily = PlexSans, color = c.ink) },
                    onClick = { menuOpen = false; onPin() },
                )
            }
            DropdownMenuItem(
                text = { Text("Rename", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Move to folder…", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Change icon color", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onChangeIcon() },
            )
            DropdownMenuItem(
                text = { Text("Force reload", fontFamily = PlexSans, color = c.ink) },
                onClick = { menuOpen = false; onForceReload() },
            )
            if (notebook.hasConflict) {
                DropdownMenuItem(
                    text = { Text("Resolve conflict", fontFamily = PlexSans, color = c.amber) },
                    onClick = { menuOpen = false; onOpenConflict() },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete (to trash)", fontFamily = PlexSans, color = c.red) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
      }
    }
}

@Composable
private fun NoVaultState(onChooseFolder: () -> Unit) {
    val c = MaterialTheme.grove
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✦", fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
            Spacer(Modifier.height(10.dp))
            Text(
                "Choose your org folder",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = c.ink2,
            )
            Text(
                "Pick the folder Syncthing shares with your laptop",
                fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.accent)
                    .clickable(onClick = onChooseFolder)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            ) {
                Text(
                    "Choose folder",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = c.accentInk,
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(glyph: String, title: String, subtitle: String? = null) {
    val c = MaterialTheme.grove
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(glyph, fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = c.ink2,
            )
            if (subtitle != null) {
                Text(subtitle, fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3)
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    contextLabel: String? = null,
    placeholder: String = "notebook.org",
) {
    val c = MaterialTheme.grove
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink) },
        text = {
            Column {
                if (contextLabel != null) {
                    Text(
                        contextLabel,
                        fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(placeholder, fontFamily = PlexMono, color = c.ink3) },
                    textStyle = TextStyle(fontFamily = PlexMono, color = c.ink),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel, color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) }
        },
    )
}

/**
 * "Move to folder…" destination picker (nested-folders plan §6). A bottom sheet
 * that drills the vault folder tree one level at a time: tapping a folder row
 * navigates into it, the breadcrumb jumps back out, and "Move here" targets the
 * folder currently shown. "New folder here" types a fresh sub-path that
 * [com.rrajath.grove.vault.Vault.moveNotebook] creates on the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToFolderSheet(
    displayName: String,
    currentDir: String,
    notebooks: List<NotebookItem>,
    vaultName: String,
    onConfirm: (newDir: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    var pickerDir by remember { mutableStateOf("") }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    val childFolders = remember(notebooks, pickerDir) {
        drillLevel(notebooks, pickerDir).childFolders
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(
                "Move $displayName",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, color = c.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Currently in ${currentDir.ifEmpty { vaultName }}",
                fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
            )
            Spacer(Modifier.height(10.dp))
            Breadcrumb(
                vaultName = vaultName,
                dir = pickerDir,
                onExit = { pickerDir = "" },
                onNavigate = { pickerDir = it },
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (childFolders.isEmpty()) {
                    item {
                        Text(
                            "No sub-folders here",
                            fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(childFolders, key = { it.dir }) { node ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, c.line, RoundedCornerShape(12.dp))
                            .clickable { pickerDir = node.dir }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▪", fontFamily = PlexMono, fontSize = 15.sp, color = c.ink3)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            node.name,
                            fontFamily = PlexMono, fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp, color = c.ink, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${node.recursiveOrgCount}",
                            fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink3,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("›", fontFamily = PlexMono, fontSize = 15.sp, color = c.ink3)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (creatingFolder) {
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    singleLine = true,
                    placeholder = { Text("folder name", fontFamily = PlexMono, color = c.ink3) },
                    textStyle = TextStyle(fontFamily = PlexMono, color = c.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val newDir = vaultPath(pickerDir, newFolder.trim().trim('/'))
                MoveConfirmButton(
                    label = "Create and move here",
                    enabled = newFolder.isNotBlank() && newDir != currentDir,
                    onClick = { onConfirm(newDir) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { creatingFolder = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋", fontFamily = PlexMono, fontSize = 15.sp, color = c.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "New folder here",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = c.accent,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = c.line)
            Row(
                Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Cancel",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = c.ink2,
                    )
                }
                MoveConfirmButton(
                    label = "Move to ${pickerDir.ifEmpty { vaultName }}",
                    enabled = pickerDir != currentDir && !creatingFolder,
                    onClick = { onConfirm(pickerDir) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MoveConfirmButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.accent else c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, color = if (enabled) c.accentInk else c.ink3,
            maxLines = 1,
        )
    }
}

@Composable
internal fun IconGlyph(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontFamily = PlexMono,
            fontSize = 22.sp,
            color = MaterialTheme.grove.ink,
        )
    }
}

@Composable
internal fun IconGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.grove.ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
