package com.rrajath.grove.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.ui.components.CollapsibleKvSection
import com.rrajath.grove.ui.components.FavoriteStar
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.GroveToast
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.StatePickerSheet
import com.rrajath.grove.ui.components.SwipeAction
import com.rrajath.grove.ui.components.SwipeRevealRow
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.components.favoriteIcon
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.priorityColor
import com.rrajath.grove.ui.theme.starColor
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.headlineAtLine

data class OutlineDisplayFlags(
    val tags: Boolean = true,
    val timestamps: Boolean = true,
    val keywords: Boolean = true,
)

/** Persist the collapsed line-index set across navigation (Set isn't saveable by default). */
private val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

/** Outline view per design spec §4: collapsible heading tree with node ops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    notebookId: String,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onCreateNote: (NoteRef) -> Unit,
    /**
     * Line index of a heading to narrow the outline to (org-narrow-to-subtree):
     * only that heading and its descendants are shown, until [onWiden].
     */
    narrowLineIndex: Int? = null,
    /** Return to the full, unnarrowed outline. */
    onWiden: () -> Unit = {},
    /** Top-bar ⌕: opens Search with the notebook filter already pinned to this file. */
    onSearchInNotebook: () -> Unit = {},
    onFavorite: (fileName: String, lineIndex: Int, title: String) -> Unit = { _, _, _ -> },
    /** Line indices of favorited headlines in this notebook, marked with a ★. */
    favoriteLines: Set<Int> = emptySet(),
    displayFlags: OutlineDisplayFlags = OutlineDisplayFlags(),
    onToggleDisplay: (OutlineToggle, Boolean) -> Unit = { _, _ -> },
    /** Settings toggle: show a collapsible section for file-level `#+` keywords, pinned at the top. */
    showPreface: Boolean = true,
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusedLine by viewModel.focusedLine.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    val refileState by viewModel.refile.collectAsStateWithLifecycle()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }

    val loadedDoc = (state as? DocumentUiState.Loaded)?.document
    // Resolved once here (not separately in the top bar and the body) so both
    // stay in sync. Falls back to the full outline if the target heading no
    // longer exists (e.g. it was deleted since the breadcrumb was shown).
    val narrowTarget = remember(loadedDoc, narrowLineIndex) {
        narrowLineIndex?.let { idx -> loadedDoc?.headlineAtLine(idx) }
    }
    val scopedHeadlines = remember(loadedDoc, narrowTarget) {
        loadedDoc?.let { doc ->
            if (narrowTarget != null) listOf(narrowTarget) + doc.subtree(narrowTarget) else doc.headlines
        } ?: emptyList()
    }

    // Collapsed line-indices and scroll survive navigating into a note and back
    // (rememberSaveable persists across the destination leaving composition).
    var collapsed by rememberSaveable(notebookId, stateSaver = IntSetSaver) {
        mutableStateOf(setOf<Int>())
    }
    var prefaceExpanded by rememberSaveable(notebookId) { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Only one swipe panel open at a time; any mutation snaps it shut.
    var openRowLine by remember { mutableStateOf<Int?>(null) }
    // Which headline the date-picker dialog targets: lineIndex to "scheduled"/"deadline".
    var datePickerFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    // Line index whose TODO state the swipe panel's "State" action is picking.
    var statePickerFor by remember { mutableStateOf<Int?>(null) }
    // Line index whose swipe panel "Note" action opened the note-input dialog.
    var noteDialogFor by remember { mutableStateOf<Int?>(null) }

    // The command bar takes over the top bar in focus mode; back exits it.
    BackHandler(enabled = focusedLine != null) { viewModel.setFocus(null) }

    // A freshly opened notebook starts fully collapsed. Applied once per open
    // (the flag is saved alongside `collapsed`), so the user's later expanding
    // and collapsing is preserved across navigating into a note and back.
    var defaultCollapseApplied by rememberSaveable(notebookId) { mutableStateOf(false) }
    // Keyed on the loaded *transition*, not the state object itself: every
    // document emission is a new state instance and would relaunch this effect.
    LaunchedEffect(state is DocumentUiState.Loaded, defaultCollapseApplied) {
        if (!defaultCollapseApplied) {
            (state as? DocumentUiState.Loaded)?.let { loaded ->
                collapsed = loaded.document.headlines
                    .filter { loaded.document.hasDescendants(it) }
                    .map { it.lineIndex }
                    .toSet()
                defaultCollapseApplied = true
            }
        }
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            val focused = focusedLine
            if (focused != null) {
                StructureCommandBar(
                    onExit = { viewModel.setFocus(null) },
                    resolve = { (state as? DocumentUiState.Loaded)?.document?.headlineAtLine(focused) },
                    viewModel = viewModel,
                )
            } else {
                GroveTopBar(
                    leading = {
                        if (narrowTarget != null) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = onWiden)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("←", fontSize = 19.sp, color = c.ink)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "widen",
                                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = c.accent,
                                )
                            }
                        } else {
                            IconGlyph("←", onClick = onBack)
                        }
                    },
                    title = {
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(
                                notebookId,
                                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp, color = c.ink,
                            )
                            if (narrowTarget != null) {
                                Text(
                                    "narrowed to “${narrowTarget.title}”",
                                    fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            } else {
                                (state as? DocumentUiState.Loaded)?.let {
                                    val noteCount = remember(it.document) {
                                        it.document.headlines.count { h -> h.level == 1 }
                                    }
                                    Text(
                                        "$noteCount notes",
                                        fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        (state as? DocumentUiState.Loaded)?.let { loaded ->
                            val foldable = remember(loaded.document, scopedHeadlines) {
                                scopedHeadlines
                                    .filter { loaded.document.hasDescendants(it) }
                                    .map { it.lineIndex }
                                    .toSet()
                            }
                            if (foldable.isNotEmpty()) {
                                // Chevrons pointing apart = all folded (tap to expand
                                // all); chevrons pointing together = expanded (tap to
                                // collapse all).
                                val allCollapsed = collapsed.containsAll(foldable)
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            collapsed = if (allCollapsed) emptySet() else foldable
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                        contentDescription = if (allCollapsed) "Expand all headings" else "Collapse all headings",
                                        tint = c.ink,
                                    )
                                }
                            }
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onSearchInNotebook),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search in this notebook",
                                tint = c.ink,
                            )
                        }
                        var displayMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconGlyph("⋮", onClick = { displayMenuOpen = true })
                            androidx.compose.material3.DropdownMenu(
                                expanded = displayMenuOpen,
                                onDismissRequest = { displayMenuOpen = false },
                                containerColor = c.surface,
                            ) {
                                @Composable
                                fun toggleItem(label: String, value: Boolean, toggle: OutlineToggle) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Text(
                                                (if (value) "✓ " else "   ") + label,
                                                fontFamily = PlexSans, fontSize = 14.sp, color = c.ink,
                                            )
                                        },
                                        onClick = { onToggleDisplay(toggle, !value) },
                                    )
                                }
                                toggleItem("Show tags", displayFlags.tags, OutlineToggle.TAGS)
                                toggleItem("Show timestamps", displayFlags.timestamps, OutlineToggle.TIMESTAMPS)
                                toggleItem("Show keywords", displayFlags.keywords, OutlineToggle.KEYWORDS)
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // PRD §5.3: FAB adds a new top-level note to this notebook. Hidden while
            // narrowed: adding a top-level note doesn't belong to the focused subtree.
            if (narrowTarget == null) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.accent)
                        .clickable(onClick = {
                            viewModel.newTopLevelNote { line -> onCreateNote(NoteRef(notebookId, line)) }
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", fontFamily = PlexSans, fontSize = 26.sp, color = c.accentInk)
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            is DocumentUiState.Loading -> {}
            is DocumentUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.message, fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2)
            }

            is DocumentUiState.Loaded -> {
                val doc = s.document
                // Every mutation produces a new document; snap any open panel shut.
                LaunchedEffect(doc) { openRowLine = null }
                val visible = remember(scopedHeadlines, collapsed) { visibleHeadlines(scopedHeadlines, collapsed) }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (showPreface && doc.preambleKeywords.isNotEmpty() && doc.headlines.isEmpty()) {
                        CollapsibleKvSection(
                            label = "PREFACE",
                            entries = doc.preambleKeywords,
                            expanded = prefaceExpanded,
                            onToggle = { prefaceExpanded = !prefaceExpanded },
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
                        )
                    }
                    Box(Modifier.fillMaxSize().weight(1f)) {
                    if (scopedHeadlines.isEmpty()) {
                        // Empty state still needs the overlays below: undoing a
                        // delete/refile of the last note happens from here.
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("✶", fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "This notebook is empty",
                                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, color = c.ink2,
                            )
                            Text(
                                "Tap + to write your first note",
                                fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                            )
                        }
                    } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .testTag("outline_list"),
                        // Bottom inset so the last row scrolls clear of the FAB
                        // (54.dp) instead of sitting underneath it.
                        contentPadding = PaddingValues(bottom = 86.dp),
                    ) {
                        // Scrolls away with the rest of the outline instead of
                        // staying pinned above the list.
                        if (showPreface && doc.preambleKeywords.isNotEmpty()) {
                            item(key = "preface") {
                                CollapsibleKvSection(
                                    label = "PREFACE",
                                    entries = doc.preambleKeywords,
                                    expanded = prefaceExpanded,
                                    onToggle = { prefaceExpanded = !prefaceExpanded },
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(visible, key = { it.lineIndex }) { h ->
                            val isFavorite = h.lineIndex in favoriteLines
                            val toggleFavorite = {
                                onFavorite(notebookId, h.lineIndex, h.title)
                                viewModel.showToast(
                                    if (isFavorite) "Removed favorite" else "★ Added to favorites"
                                )
                            }
                            SwipeRevealRow(
                                // Right-swipe panel: state / schedule / note / favorite.
                                leftActions = listOf(
                                    SwipeAction("⟳", "State", c.amber, c.amberSoft) {
                                        statePickerFor = h.lineIndex
                                    },
                                    SwipeAction(
                                        label = "Schedule",
                                        fg = c.blue,
                                        bg = c.blueSoft,
                                        icon = Icons.Outlined.CalendarMonth,
                                    ) {
                                        // Both SCHEDULED and DEADLINE live on one screen now, so a
                                        // single entry point opens it (defaulting to the SCHEDULED tab).
                                        datePickerFor = h.lineIndex to "scheduled"
                                    },
                                    SwipeAction(
                                        label = "Note",
                                        fg = c.green,
                                        bg = c.greenSoft,
                                        icon = Icons.Outlined.EditNote,
                                    ) {
                                        noteDialogFor = h.lineIndex
                                    },
                                    SwipeAction(
                                        label = "Fav",
                                        fg = c.accent,
                                        bg = c.accentSoft,
                                        icon = favoriteIcon(),
                                        onClick = toggleFavorite,
                                    ),
                                ),
                                // Left-swipe panel: insert above / below / sub-note / refile.
                                rightActions = listOf(
                                    SwipeAction("↑+", "Above", c.amber, c.amberSoft) {
                                        viewModel.insertSiblingAbove(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    SwipeAction("↓+", "Below", c.blue, c.blueSoft) {
                                        viewModel.insertSiblingBelow(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    SwipeAction("↳", "Sub", c.green, c.greenSoft) {
                                        viewModel.newChild(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    SwipeAction("➜", "Refile", c.accent, c.accentSoft) {
                                        viewModel.startRefile(h)
                                    },
                                ),
                                enabled = focusedLine == null,
                                forceClose = openRowLine != h.lineIndex,
                                onOpenChanged = { open ->
                                    if (open) openRowLine = h.lineIndex
                                    else if (openRowLine == h.lineIndex) openRowLine = null
                                },
                                onTap = { onOpenNote(NoteRef(notebookId, h.lineIndex)) },
                                onLongPress = { viewModel.setFocus(h.lineIndex) },
                                modifier = if (focusedLine == h.lineIndex) Modifier.zIndex(1f) else Modifier,
                            ) {
                                OutlineNode(
                                    doc = doc,
                                    headline = h,
                                    isCollapsed = h.lineIndex in collapsed,
                                    isFocused = focusedLine == h.lineIndex,
                                    onToggle = {
                                        collapsed = if (h.lineIndex in collapsed) collapsed - h.lineIndex
                                        else collapsed + h.lineIndex
                                    },
                                    isFavorite = isFavorite,
                                    flags = displayFlags,
                                )
                            }
                        }
                    }
                    ScrollJumpButtons(
                        listState = listState,
                        // Stacked above the FAB (54.dp + its own padding) so the two
                        // never overlap.
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 86.dp, end = 16.dp),
                    )
                    }
                    // Lifted above the FAB (54.dp + insets) so UNDO stays tappable.
                    GroveUndoSnackbar(
                        snack = snack,
                        onUndo = viewModel::undo,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp),
                    )
                    GroveToast(
                        toast = toast,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp),
                    )
                    }
                }

                statePickerFor?.let { line ->
                    doc.headlineAtLine(line)?.let { headline ->
                        StatePickerSheet(
                            title = headline.title,
                            keywords = doc.keywords,
                            current = headline.keyword,
                            onPick = { keyword ->
                                viewModel.setState(headline, keyword)
                                statePickerFor = null
                            },
                            onDismiss = { statePickerFor = null },
                        )
                    }
                }

                datePickerFor?.let { (line, target) ->
                    val headline = doc.headlineAtLine(line)
                    PlanningDatesScreen(
                        title = headline?.title.orEmpty(),
                        scheduled = headline?.planning?.scheduled,
                        deadline = headline?.planning?.deadline,
                        focus = if (target == "scheduled") PlanningKind.SCHEDULED else PlanningKind.DEADLINE,
                        onDismiss = { datePickerFor = null },
                        onConfirm = { sched, dead ->
                            if (headline != null) viewModel.setPlanningDates(headline, sched, dead)
                            datePickerFor = null
                        },
                    )
                }

                noteDialogFor?.let { line ->
                    val headline = doc.headlineAtLine(line)
                    if (headline != null) {
                        NoteDialog(
                            title = headline.title,
                            onDismiss = { noteDialogFor = null },
                            onConfirm = { note ->
                                viewModel.addNote(headline, note)
                                noteDialogFor = null
                            },
                        )
                    }
                }

                refileState?.let { refile ->
                    RefileSheet(
                        state = refile,
                        currentFileName = notebookId,
                        currentDoc = doc,
                        onPickNotebook = viewModel::refilePickNotebook,
                        onDrillInto = viewModel::refileDrillInto,
                        onBack = viewModel::refileBack,
                        onCancel = viewModel::refileCancel,
                        onConfirm = viewModel::refileConfirm,
                        onArchive = viewModel::refileToArchive,
                        onPickLastUsed = viewModel::refileToLastUsed,
                    )
                }
            }
        }
    }
}

/** Swipe panel's "Note" action: free text logged into the headline's LOGBOOK drawer. */
@Composable
internal fun NoteDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = MaterialTheme.grove
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Column {
                Text(
                    "Add note",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
                Text(
                    title,
                    fontFamily = PlexSans, fontSize = 13.5.sp, color = c.ink2,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Note contents", fontFamily = PlexSans, color = c.ink3) },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("Add", color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) }
        },
    )
}

/**
 * "Move & indent" bar that replaces the top bar in focus mode (design spec
 * Gestures screen): 56dp, accentSoft bg. Every handler re-resolves the focused
 * headline at click time: headlines are stale after each mutation.
 */
@Composable
private fun StructureCommandBar(
    onExit: () -> Unit,
    resolve: () -> OrgHeadline?,
    viewModel: DocumentViewModel,
) {
    val c = MaterialTheme.grove
    var confirmDelete by remember { mutableStateOf(false) }
    // Background first so the accentSoft wash extends behind the status bar,
    // then the inset padding (Scaffold does not pad the topBar slot).
    Column(Modifier.background(c.accentSoft).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontFamily = PlexSans, fontSize = 16.sp, color = c.accent)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Move & indent",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp, color = c.accent,
                modifier = Modifier.weight(1f),
            )
            CommandButton(Icons.Default.KeyboardArrowUp, "Move up", c.ink) { resolve()?.let(viewModel::moveUp) }
            CommandButton(Icons.Default.KeyboardArrowDown, "Move down", c.ink) { resolve()?.let(viewModel::moveDown) }
            CommandButton(Icons.Default.FormatIndentDecrease, "Promote", c.ink) { resolve()?.let(viewModel::promote) }
            CommandButton(Icons.Default.FormatIndentIncrease, "Demote", c.ink) { resolve()?.let(viewModel::demote) }
            CommandButton(Icons.Default.Delete, "Delete", c.red) { confirmDelete = true }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accent)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    tint = c.accentInk,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Delete this note?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "This will delete the heading and everything under it.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resolve()?.let(viewModel::deleteNote)
                    confirmDelete = false
                    onExit()
                }) { Text("Delete", color = c.red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.ink2) }
            },
        )
    }
}

@Composable
private fun CommandButton(icon: ImageVector, contentDescription: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.grove.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** [headlines] is either the whole document or, when narrowed, just a target heading + its subtree. */
private fun visibleHeadlines(headlines: List<OrgHeadline>, collapsed: Set<Int>): List<OrgHeadline> {
    val result = mutableListOf<OrgHeadline>()
    var hideDeeperThan: Int? = null
    for (h in headlines) {
        val hideLevel = hideDeeperThan
        if (hideLevel != null) {
            if (h.level > hideLevel) continue
            hideDeeperThan = null
        }
        result.add(h)
        if (h.lineIndex in collapsed) hideDeeperThan = h.level
    }
    return result
}

@Composable
private fun OutlineNode(
    doc: OrgDocument,
    headline: OrgHeadline,
    isCollapsed: Boolean,
    isFocused: Boolean,
    onToggle: () -> Unit,
    isFavorite: Boolean = false,
    flags: OutlineDisplayFlags = OutlineDisplayFlags(),
) {
    val c = MaterialTheme.grove
    val hasChildren = remember(doc, headline) { doc.hasDescendants(headline) }
    // Only needed for the "… N" collapsed indicator below.
    val childCount = remember(doc, headline) { doc.directChildren(headline).size }
    val isDone = headline.keyword != null && doc.keywords.isDone(headline.keyword)
    // Tokenizing the title allocates a new AnnotatedString; rows recompose on
    // scroll and swipe, so keep it across recompositions.
    val titleAnnotated = remember(headline.title, c) { annotateOrgInline(headline.title, c) }

    // Focused rows lift with a 2dp accent outline per the design spec.
    val focusModifier = if (isFocused) {
        Modifier
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(2.dp, c.accent, RoundedCornerShape(12.dp))
    } else {
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.bg)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .then(focusModifier)
            .padding(
                start = (22 * (headline.level - 1)).dp,
                top = 9.dp, bottom = 9.dp, end = 6.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // Caret + asterisk centered together so the caret sits on the same
        // line as the level markers, independent of the title's own height.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Drawn on a Canvas rather than as ○/▸/▾ glyphs: those characters'
            // ink sits at different offsets within their own line box depending
            // on glyph and font size, so Box's contentAlignment=Center couldn't
            // make all three states line up with each other or the asterisk.
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = hasChildren, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (hasChildren) {
                    Canvas(Modifier.size(9.dp)) {
                        val path = if (isCollapsed) {
                            Path().apply {
                                moveTo(0f, 0f)
                                lineTo(0f, size.height)
                                lineTo(size.width, size.height / 2f)
                                close()
                            }
                        } else {
                            Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                        }
                        drawPath(path, color = c.ink3)
                    }
                } else {
                    Canvas(Modifier.size(7.dp)) {
                        drawCircle(color = c.ink3, style = Stroke(width = 1.2.dp.toPx()))
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "*".repeat(headline.level),
                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = c.starColor(headline.level),
            )
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            // Baseline-aligned so the keyword/priority chips sit on the first
            // line when the title wraps, instead of centering on the block.
            Row {
                headline.keyword?.takeIf { flags.keywords }?.let { kw ->
                    val (fg, bg) = when {
                        doc.keywords.isDone(kw) -> c.green to c.greenSoft
                        kw == "IN-PROGRESS" -> c.blue to c.blueSoft
                        else -> c.amber to c.amberSoft
                    }
                    Text(
                        kw,
                        fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = fg,
                        modifier = Modifier
                            .alignByBaseline()
                            .clip(RoundedCornerShape(5.dp))
                            .background(bg)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                headline.priority?.let { p ->
                    Text(
                        "[#$p]",
                        fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = c.priorityColor(p),
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    titleAnnotated,
                    fontFamily = PlexSans,
                    fontWeight = if (headline.level == 1) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.5.sp,
                    color = if (isDone) c.ink3 else c.ink,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    // fill = true reserves the trailing tag/star width on every
                    // wrapped line so they land flush at the row's right edge,
                    // baseline-aligned to the title's first line.
                    modifier = Modifier.alignByBaseline().weight(1f, fill = true),
                )
                if (isCollapsed && childCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "… $childCount", fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                if (flags.tags && headline.tags.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        headline.tags.joinToString(":", prefix = ":", postfix = ":"),
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.synTag,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    FavoriteStar(modifier = Modifier.alignByBaseline())
                }
            }
            // Body preview: first two non-empty lines, keeping the line breaks
            // so multi-line content stays readable.
            val preview = remember(doc, headline) {
                doc.bodyOf(headline)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("\n")
                    .take(200)
            }
            if (preview.isNotEmpty()) {
                Text(
                    preview,
                    fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink3,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Scheduled / deadline chips
            headline.planning.scheduled?.takeIf { flags.timestamps }?.let { ts ->
                Row(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.blueSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = c.blue, modifier = Modifier.size(11.dp))
                    Text(
                        ts.formatHuman(),
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.blue,
                    )
                }
            }
            headline.planning.deadline?.takeIf { flags.timestamps }?.let { ts ->
                Row(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.redSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = c.red, modifier = Modifier.size(11.dp))
                    Text(
                        ts.formatHuman(),
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.red,
                    )
                }
            }
        }
    }
}
