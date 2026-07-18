package com.rrajath.grove.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.ui.components.CollapsibleKvSection
import com.rrajath.grove.ui.components.FavoriteStar
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.GroveToast
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.SwipeAction
import com.rrajath.grove.ui.components.SwipeRevealRow
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.starColor
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.headlineAtLine
import java.time.Instant
import java.time.ZoneOffset

data class OutlineDisplayFlags(
    val tags: Boolean = true,
    val timestamps: Boolean = true,
    val keywords: Boolean = true,
)

/** Persist the collapsed line-index set across navigation (Set isn't saveable by default). */
private val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

/** Outline view per design spec §4 — collapsible heading tree with node ops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    notebookId: String,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onCreateNote: (NoteRef) -> Unit,
    onFavorite: (fileName: String, lineIndex: Int, title: String) -> Unit = { _, _, _ -> },
    /** Line indices of favorited headlines in this notebook — marked with a ★. */
    favoriteLines: Set<Int> = emptySet(),
    displayFlags: OutlineDisplayFlags = OutlineDisplayFlags(),
    onToggleDisplay: (OutlineToggle, Boolean) -> Unit = { _, _ -> },
    /** Settings toggle: show a collapsible section for file-level `#+` keywords, pinned at the top. */
    showHeaderTags: Boolean = true,
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusedLine by viewModel.focusedLine.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    val refileState by viewModel.refile.collectAsStateWithLifecycle()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }

    // Collapsed line-indices and scroll survive navigating into a note and back
    // (rememberSaveable persists across the destination leaving composition).
    var collapsed by rememberSaveable(notebookId, stateSaver = IntSetSaver) {
        mutableStateOf(setOf<Int>())
    }
    var headerTagsExpanded by rememberSaveable(notebookId) { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Only one swipe panel open at a time; any mutation snaps it shut.
    var openRowLine by remember { mutableStateOf<Int?>(null) }
    // Which headline the date-picker dialog targets: lineIndex to "scheduled"/"deadline".
    var datePickerFor by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // The command bar takes over the top bar in focus mode; back exits it.
    BackHandler(enabled = focusedLine != null) { viewModel.setFocus(null) }

    // A freshly opened notebook starts fully collapsed. Applied once per open
    // (the flag is saved alongside `collapsed`), so the user's later expanding
    // and collapsing is preserved across navigating into a note and back.
    var defaultCollapseApplied by rememberSaveable(notebookId) { mutableStateOf(false) }
    // Keyed on the loaded *transition*, not the state object itself — every
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
                    leading = { IconGlyph("←", onClick = onBack) },
                    title = {
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(
                                notebookId,
                                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp, color = c.ink,
                            )
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
                    },
                    actions = {
                        (state as? DocumentUiState.Loaded)?.let { loaded ->
                            val foldable = remember(loaded.document) {
                                loaded.document.headlines
                                    .filter { loaded.document.hasDescendants(it) }
                                    .map { it.lineIndex }
                                    .toSet()
                            }
                            if (foldable.isNotEmpty()) {
                                // Mirrors the per-row carets: ▸▸ = all folded (tap to
                                // expand all), ▾▾ = expanded (tap to collapse all).
                                val allCollapsed = collapsed.containsAll(foldable)
                                IconGlyph(if (allCollapsed) "▸▸" else "▾▾", onClick = {
                                    collapsed = if (allCollapsed) emptySet() else foldable
                                })
                            }
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
            // PRD §5.3: FAB adds a new top-level note to this notebook.
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
                // Every mutation produces a new document — snap any open panel shut.
                LaunchedEffect(doc) { openRowLine = null }
                val visible = remember(doc, collapsed) { visibleHeadlines(doc, collapsed) }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (showHeaderTags && doc.preambleKeywords.isNotEmpty() && doc.headlines.isEmpty()) {
                        CollapsibleKvSection(
                            label = "#+ header tags",
                            entries = doc.preambleKeywords,
                            expanded = headerTagsExpanded,
                            onToggle = { headerTagsExpanded = !headerTagsExpanded },
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
                        )
                    }
                    Box(Modifier.fillMaxSize().weight(1f)) {
                    if (doc.headlines.isEmpty()) {
                        // Empty state still needs the overlays below — undoing a
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
                    ) {
                        // Scrolls away with the rest of the outline instead of
                        // staying pinned above the list.
                        if (showHeaderTags && doc.preambleKeywords.isNotEmpty()) {
                            item(key = "header_tags") {
                                CollapsibleKvSection(
                                    label = "#+ header tags",
                                    entries = doc.preambleKeywords,
                                    expanded = headerTagsExpanded,
                                    onToggle = { headerTagsExpanded = !headerTagsExpanded },
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
                                // Right-swipe panel: state / schedule / deadline / favorite.
                                leftActions = listOf(
                                    SwipeAction("⟳", "State", c.amber, c.amberSoft) {
                                        viewModel.cycleState(h)
                                    },
                                    SwipeAction("◷", "Sched", c.blue, c.blueSoft) {
                                        datePickerFor = h.lineIndex to "scheduled"
                                    },
                                    SwipeAction("⚑", "Deadl", c.red, c.redSoft) {
                                        datePickerFor = h.lineIndex to "deadline"
                                    },
                                    SwipeAction("★", "Fav", c.accent, c.accentSoft, toggleFavorite),
                                ),
                                // Left-swipe panel: insert above / below / sub-note / refile.
                                rightActions = listOf(
                                    SwipeAction("↑+", "Above", c.blue, c.blueSoft) {
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

                datePickerFor?.let { (line, target) ->
                    val headline = doc.headlineAtLine(line)
                    val existing = if (target == "scheduled") headline?.planning?.scheduled
                    else headline?.planning?.deadline
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = existing?.date
                            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { datePickerFor = null },
                        confirmButton = {
                            TextButton(onClick = {
                                val millis = pickerState.selectedDateMillis
                                if (millis != null && headline != null) {
                                    val date = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneOffset.UTC).toLocalDate()
                                    val ts = OrgTimestamp(date)
                                    if (target == "scheduled") viewModel.setScheduled(headline, ts)
                                    else viewModel.setDeadline(headline, ts)
                                }
                                datePickerFor = null
                            }) { Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { datePickerFor = null }) {
                                Text("Cancel", color = c.ink2)
                            }
                        },
                    ) {
                        DatePicker(state = pickerState)
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

/**
 * "Move & indent" bar that replaces the top bar in focus mode (design spec
 * Gestures screen): 56dp, accentSoft bg. Every handler re-resolves the focused
 * headline at click time — headlines are stale after each mutation.
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

private fun visibleHeadlines(doc: OrgDocument, collapsed: Set<Int>): List<OrgHeadline> {
    val result = mutableListOf<OrgHeadline>()
    var hideDeeperThan: Int? = null
    for (h in doc.headlines) {
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
        // Caret (tap target toggles fold)
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = hasChildren, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    !hasChildren -> "○"
                    isCollapsed -> "▸"
                    else -> "▾"
                },
                fontFamily = PlexMono, fontSize = if (hasChildren) 12.sp else 8.sp,
                color = c.ink3,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "*".repeat(headline.level),
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = c.starColor(headline.level),
        )
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
                        fontSize = 11.sp, color = c.red,
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
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.blueSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "SCHEDULED: ${ts.format()}",
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.blue,
                    )
                }
            }
            headline.planning.deadline?.takeIf { flags.timestamps }?.let { ts ->
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.redSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "DEADLINE: ${ts.format()}",
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.red,
                    )
                }
            }
        }
    }
}
