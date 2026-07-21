package com.rrajath.grove.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import com.rrajath.grove.R
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.sync.SyncState
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NotebookItem
import com.rrajath.grove.ui.vault.NotebooksUiState
import com.rrajath.grove.ui.vault.NotebooksViewModel

/** Notebook list home screen (design spec §2), driven by the sync index. */
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
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var styleTarget by remember { mutableStateOf<String?>(null) }

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
                    val loadedState = state as? NotebooksUiState.Loaded
                    if (loadedState != null) {
                        IconGlyph("＋", onClick = { showCreateDialog = true })
                        IconGlyph("↻", onClick = { viewModel.requestSync() })
                        SyncStatusIcon(loadedState, context)
                    }
                    IconGlyph("⌕", onClick = onOpenSearch)
                },
            )
        },
        floatingActionButton = {
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
                                items(s.notebooks, key = { it.fileName }) { nb ->
                                    NotebookRow(
                                        notebook = nb,
                                        onClick = { onOpenNotebook(nb.fileName) },
                                        onOpenConflict = { onOpenConflict(nb.fileName) },
                                        onRename = { renameTarget = nb.fileName },
                                        onChangeIcon = { styleTarget = nb.fileName },
                                        onDelete = { viewModel.trashNotebook(nb.fileName) },
                                        onForceReload = { viewModel.forceReload(nb.fileName) },
                                        onPin = { viewModel.pinNotebook(nb.fileName) },
                                        onUnpin = { viewModel.unpinNotebook(nb.fileName) },
                                    )
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

    if (showCreateDialog) {
        NameDialog(
            title = "New notebook",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createNotebook(name)
                showCreateDialog = false
            },
        )
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
            IconStyleDialog(
                notebook = notebook,
                onDismiss = { styleTarget = null },
                onPickIcon = { glyph -> viewModel.setNotebookIcon(target, glyph) },
                onPickColor = { key -> viewModel.setNotebookColor(target, key) },
            )
        }
    }
}

@Composable
private fun IconStyleDialog(
    notebook: NotebookItem,
    onDismiss: () -> Unit,
    onPickIcon: (String) -> Unit,
    onPickColor: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    val hash = nameHash(notebook.fileName)
    val currentGlyph = notebook.icon ?: GLYPHS[hash % GLYPHS.size]
    val currentColor = notebook.color ?: PALETTE_KEYS[hash % PALETTE_KEYS.size]
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                "Icon for ${notebook.fileName}",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, color = c.ink,
            )
        },
        text = {
            Column {
                Row {
                    GLYPHS.forEach { glyph ->
                        val selected = glyph == currentGlyph
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) c.accentSoft else c.surface2)
                                .border(
                                    1.dp,
                                    if (selected) c.accent else c.line,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onPickIcon(glyph) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                glyph,
                                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp, color = if (selected) c.accent else c.ink2,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    PALETTE_KEYS.forEach { key ->
                        val (fg, bg) = palette(c, key)
                        val selected = key == currentColor
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) fg else c.line,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onPickColor(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (selected) currentGlyph else "",
                                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp, color = fg,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
    )
}

/**
 * Sync status indicator in the top app bar. Shows a green check when the last sync
 * completed successfully, or a warning glyph when there is an active sync error.
 * Tapping either shows a toast with the sync status detail.
 */
@Composable
private fun SyncStatusIcon(state: NotebooksUiState.Loaded, context: android.content.Context) {
    val c = MaterialTheme.grove
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
                    Toast.makeText(context, "Syncing ${sync.fileName}…", Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Syncing", tint = c.ink2)
            }
        }
        else -> {
            IconButton(
                onClick = {
                    val minutes = state.lastSyncAt
                        ?.let { ((System.currentTimeMillis() - it) / 60_000L).coerceAtLeast(0) }
                    val message = if (minutes != null) "Synced $minutes minutes ago" else "Not synced yet"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(Icons.Default.Check, contentDescription = "Synced", tint = c.green)
            }
        }
    }
}

private val GLYPHS = listOf("✦", "✶", "✸", "✺", "❋", "✷")

/** Palette keys persisted in settings; resolved against the current theme. */
private val PALETTE_KEYS = listOf("green", "accent", "blue", "red")

private fun palette(
    c: GroveColors,
    key: String,
): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (key) {
    "green" -> c.green to c.greenSoft
    "blue" -> c.blue to c.blueSoft
    "red" -> c.red to c.redSoft
    else -> c.accent to c.accentSoft
}

private fun nameHash(name: String): Int =
    name.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

private fun notebookStyle(
    c: GroveColors,
    name: String,
    iconOverride: String? = null,
    colorOverride: String? = null,
): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val hash = nameHash(name)
    val (fg, bg) = palette(c, colorOverride ?: PALETTE_KEYS[hash % PALETTE_KEYS.size])
    return Triple(iconOverride ?: GLYPHS[hash % GLYPHS.size], fg, bg)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookRow(
    notebook: NotebookItem,
    onClick: () -> Unit,
    onOpenConflict: () -> Unit,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onDelete: () -> Unit,
    onForceReload: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    val c = MaterialTheme.grove
    val (glyph, fg, bg) = remember(notebook.fileName, notebook.icon, notebook.color, c) {
        notebookStyle(c, notebook.fileName, notebook.icon, notebook.color)
    }
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = fg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notebook.displayName,
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = c.ink,
                )
                val ago = DateUtils.getRelativeTimeSpanString(notebook.lastModified)
                Text(
                    "${notebook.noteCount} notes · $ago",
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
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Synced",
                    tint = c.green,
                    modifier = Modifier.size(14.dp),
                )
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
                text = { Text("Icon & color", fontFamily = PlexSans, color = c.ink) },
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
) {
    val c = MaterialTheme.grove
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("notebook.org", fontFamily = PlexMono, color = c.ink3) },
            )
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
