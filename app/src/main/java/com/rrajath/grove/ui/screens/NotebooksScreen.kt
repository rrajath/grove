package com.rrajath.grove.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NotebooksUiState
import com.rrajath.grove.ui.vault.NotebooksViewModel
import com.rrajath.grove.vault.Notebook

/** Notebook list home screen (design spec §2), backed by the real vault. */
@Composable
fun NotebooksScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenNotebook: (String) -> Unit,
    viewModel: NotebooksViewModel = viewModel(factory = NotebooksViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }

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
                    if (state is NotebooksUiState.Loaded) {
                        IconGlyph("＋", onClick = { showCreateDialog = true })
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
            SyncStatusStrip(state)
            when (val s = state) {
                is NotebooksUiState.NoVault -> NoVaultState(onChooseFolder = { folderPicker.launch(null) })
                is NotebooksUiState.Loading -> {}
                is NotebooksUiState.Error -> CenterMessage("⚠", s.message)
                is NotebooksUiState.Loaded ->
                    if (s.notebooks.isEmpty()) {
                        CenterMessage("✦", "No .org files here yet", "Capture a note or create a notebook with ＋")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.notebooks, key = { it.fileName }) { nb ->
                                NotebookRow(nb, onClick = { onOpenNotebook(nb.fileName) })
                            }
                        }
                    }
            }
        }
    }

    if (showCreateDialog) {
        CreateNotebookDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createNotebook(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun SyncStatusStrip(state: NotebooksUiState) {
    val c = MaterialTheme.grove
    val (glyph, glyphColor, text) = when (state) {
        is NotebooksUiState.Loaded -> Triple("✓", c.green, "Local folder · ${state.notebooks.size} notebooks")
        is NotebooksUiState.NoVault -> Triple("○", c.ink3, "Sync not set up yet")
        is NotebooksUiState.Loading -> Triple("↻", c.blue, "Reading folder…")
        is NotebooksUiState.Error -> Triple("✗", c.red, "Folder error")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(11.dp))
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontFamily = PlexMono, fontSize = 13.sp, color = glyphColor)
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2)
    }
}

// Notebook icon tiles cycle through the design-spec glyph/color pairs by name hash.
private val GLYPHS = listOf("✦", "✶", "✸", "✺", "❋", "✷")

private fun notebookStyle(c: GroveColors, name: String): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val palettes = listOf(
        c.green to c.greenSoft,
        c.accent to c.accentSoft,
        c.blue to c.blueSoft,
        c.red to c.redSoft,
    )
    val hash = name.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    val (fg, bg) = palettes[hash % palettes.size]
    return Triple(GLYPHS[hash % GLYPHS.size], fg, bg)
}

@Composable
private fun NotebookRow(notebook: Notebook, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val (glyph, fg, bg) = remember(notebook.fileName, c) { notebookStyle(c, notebook.fileName) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
                notebook.fileName,
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = c.ink,
            )
            val ago = DateUtils.getRelativeTimeSpanString(notebook.lastModified)
            Text(
                "${notebook.noteCount} notes · $ago",
                fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
            )
        }
        Text("›", fontFamily = PlexMono, fontSize = 16.sp, color = c.ink3)
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
private fun CreateNotebookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val c = MaterialTheme.grove
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("New notebook", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink) },
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
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("Create", color = c.accent, fontWeight = FontWeight.SemiBold) }
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
            fontSize = 18.sp,
            color = MaterialTheme.grove.ink,
        )
    }
}
