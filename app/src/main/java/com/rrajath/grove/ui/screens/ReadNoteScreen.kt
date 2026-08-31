package com.rrajath.grove.ui.screens

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.net.toUri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.data.FavoriteNote
import com.rrajath.grove.data.matches
import com.rrajath.grove.org.BlockParser
import com.rrajath.grove.org.OrgBlock
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.settings.ChecklistStates
import com.rrajath.grove.ui.components.CollapsibleKvSection
import com.rrajath.grove.ui.components.CollapsibleLogSection
import com.rrajath.grove.ui.components.FavoriteStar
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.editor.MetadataSheet
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.components.doubleTapToEdit
import com.rrajath.grove.ui.components.linkPressHandler
import com.rrajath.grove.ui.components.orgInlineLinks
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.PlexSerif
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.priorityColor
import com.rrajath.grove.ui.util.IntSetSaver
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.breadcrumbFileLabel
import com.rrajath.grove.ui.vault.headlineFor

/**
 * Read mode per design spec §5: rendered note (own body + subtree).
 * Edit mode is M5; the toggle shows a hint until then.
 */
@Composable
fun ReadNoteScreen(
    noteRef: NoteRef,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    /**
     * Double-tap anywhere switches to edit mode. Double-tapping a specific
     * subheading's title/body passes that heading's line index so the editor
     * can land the cursor there instead of at the top; `null` for taps on
     * the note's own heading/blank space or the Read/Edit toggle.
     */
    onEdit: (Int?) -> Unit,
    /**
     * A breadcrumb segment was tapped: `null` for the file/notebook segment
     * (opens the full outline), or a heading's line index (narrows the
     * outline to that heading's subtree).
     */
    onOpenBreadcrumb: (Int?) -> Unit = {},
    /** Settings toggle: show collapsible sections for `:PROPERTIES:`/`:LOGBOOK:` drawers. */
    showPropertyDrawers: Boolean = true,
    /** Settings: how many states tapping a checklist item cycles through. */
    checklistStates: ChecklistStates = ChecklistStates.TWO,
    /** Favorited headlines in this file, matched per-heading by customId, marked with a ★. */
    favorites: List<FavoriteNote> = emptyList(),
    /**
     * The preface (heading-less content) was just given a blank heading because a
     * metadata action needed one; the arg is that heading's line. The host re-opens
     * the note at that line so it continues as an ordinary note. Preface refs only.
     */
    onPromotedToHeading: (Int) -> Unit = {},
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val refileState by viewModel.refile.collectAsStateWithLifecycle()
    val prefacePromotedLine by viewModel.prefacePromotedLine.collectAsStateWithLifecycle()
    LaunchedEffect(prefacePromotedLine) {
        prefacePromotedLine?.let { line ->
            viewModel.clearPrefacePromoted()
            onPromotedToHeading(line)
        }
    }
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    var metadataOpen by remember { mutableStateOf(false) }
    // Set on a completed move (refileConfirm/refileToArchive/refileToLastUsed), not a plain
    // cancel/back-out. The move itself (file write + the "Refiled to X" snack) runs async in
    // viewModel.viewModelScope *after* `refile` is already nulled out to close the sheet, so
    // this can't just watch `refile`: leaving immediately would pop this screen's back-stack
    // entry — and with it viewModel's scope — out from under that still-in-flight coroutine.
    // Instead it waits for the snack this move ends with to actually appear and then clear,
    // which both guarantees the write has landed and gives the user the undo window this
    // screen closing shouldn't cut short. A tap on Undo restores this exact note in place
    // (same fileName/lineIndex), so it clears the flag instead of leaving.
    var refileAwaitingLeave by remember { mutableStateOf(false) }
    var refileSnackSeen by remember { mutableStateOf(false) }
    LaunchedEffect(snack) {
        if (!refileAwaitingLeave) return@LaunchedEffect
        if (snack != null) {
            refileSnackSeen = true
        } else if (refileSnackSeen) {
            refileAwaitingLeave = false
            refileSnackSeen = false
            onBack()
        }
    }
    val currentHeadline = (state as? DocumentUiState.Loaded)?.document?.headlineFor(noteRef)
    // Reload whenever the screen comes back to the foreground (e.g. returning
    // from the editor) so saved edits show immediately.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, noteRef.fileName) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.load(noteRef.fileName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(noteRef.fileName) { viewModel.load(noteRef.fileName) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                actions = {
                    IconGlyph("☰", onClick = { metadataOpen = true })
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = 0,
                        onSelect = { if (it == 1) onEdit(null) },
                        modifier = Modifier.width(140.dp),
                    )
                },
                subtitle = {
                    (state as? DocumentUiState.Loaded)?.document?.let { doc ->
                        if (noteRef.isPreface) {
                            // No heading segment: the preface sits above every heading.
                            ReadModeBreadcrumb(noteRef.fileName, emptyList(), onOpenBreadcrumb)
                        } else doc.headlineFor(noteRef)?.let { h ->
                            val path = remember(doc, h) {
                                val chain = mutableListOf(h)
                                var p = doc.parent(h)
                                while (p != null) {
                                    chain.add(0, p)
                                    p = doc.parent(p)
                                }
                                chain.toList()
                            }
                            ReadModeBreadcrumb(noteRef.fileName, path, onOpenBreadcrumb)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is DocumentUiState.Loading -> {}
            is DocumentUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.message, fontFamily = PlexSans, color = c.ink2)
            }

            is DocumentUiState.Loaded -> {
                val doc = s.document
                val headline = doc.headlineFor(noteRef)
                if (noteRef.isPreface && doc.hasPrefaceContent) {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        PrefaceContent(
                            doc = doc,
                            fileName = noteRef.fileName,
                            listState = listState,
                            onOpenNote = onOpenNote,
                            onEdit = { onEdit(null) },
                            onToggleCheckbox = { line -> viewModel.toggleChecklistItem(line, checklistStates.marks) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        ScrollJumpButtons(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    }
                } else if (headline == null) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) { Text("Note not found", fontFamily = PlexSans, color = c.ink2) }
                } else {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        NoteContent(
                            doc = doc,
                            headline = headline,
                            listState = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                // Fallback: double-tap on blank space (not over any
                                // rendered text run) still switches to edit mode. Taps
                                // that land on actual text are handled per-run below
                                // (doubleTapToEdit on each OrgText/line), which wins the
                                // gesture race against SelectionContainer's own
                                // double-tap-select-word, so in practice this outer
                                // catch-all only ever fires for empty margins.
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { onEdit(null) })
                                },
                            onOpenNote = onOpenNote,
                            fileName = noteRef.fileName,
                            onEditAt = onEdit,
                            onToggleCheckbox = { line -> viewModel.toggleChecklistItem(line, checklistStates.marks) },
                            showPropertyDrawers = showPropertyDrawers,
                            favorites = favorites,
                        )
                        ScrollJumpButtons(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
        GroveUndoSnackbar(
            snack = snack,
            onUndo = {
                // Restores this exact note (same fileName/lineIndex) in place, so the
                // pending auto-leave from the move this snack belongs to must not fire.
                refileAwaitingLeave = false
                refileSnackSeen = false
                viewModel.undo()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(padding)
                .padding(bottom = 16.dp),
        )
        }
    }

    if (metadataOpen) {
        val headline = currentHeadline
        val doc = (state as? DocumentUiState.Loaded)?.document
        if (noteRef.isPreface && doc != null && doc.hasPrefaceContent) {
            // No heading yet: every chip first inserts a blank top-level heading
            // above the content (one atomic edit), then applies its change; the
            // screen then re-opens the note at that heading. Refile is hidden —
            // there's nothing to move until the content has a heading.
            val now = { java.time.LocalDateTime.now() }
            MetadataSheet(
                headline = null,
                keywords = doc.keywords,
                allTags = allTags,
                onChangeKeyword = { kw ->
                    metadataOpen = false
                    viewModel.withPrefaceHeading("State → ${kw ?: "none"}") { d, h ->
                        OrgMutations.changeKeyword(d, h, kw, d.keywords, now())
                    }
                },
                onSetPriority = { p ->
                    metadataOpen = false
                    viewModel.withPrefaceHeading("Priority → ${p?.let { "#$it" } ?: "none"}") { d, h ->
                        OrgMutations.setPriority(d, h, p)
                    }
                },
                onSetTags = { tags ->
                    metadataOpen = false
                    viewModel.withPrefaceHeading("") { d, h -> OrgMutations.setTags(d, h, tags) }
                },
                onSetPlanningDates = { sched, dead ->
                    metadataOpen = false
                    viewModel.withPrefaceHeading("Planning updated") { d, h ->
                        OrgMutations.setPlanningDates(d, h, sched, dead)
                    }
                },
                onAddNote = { note ->
                    metadataOpen = false
                    viewModel.withPrefaceHeading("Note added") { d, h ->
                        OrgMutations.appendLogbookNote(
                            d, h, note.trim(),
                            OrgTimestamp(
                                now().toLocalDate(),
                                time = now().toLocalTime().withSecond(0).withNano(0),
                                active = false,
                            ),
                        )
                    }
                },
                onRefile = {},
                showRefile = false,
                onDismiss = { metadataOpen = false },
            )
        } else if (headline != null && doc != null) {
            MetadataSheet(
                headline = headline,
                keywords = doc.keywords,
                allTags = allTags,
                onChangeKeyword = { kw -> viewModel.setState(headline, kw) },
                onSetPriority = { p -> viewModel.setPriority(headline, p) },
                onSetTags = { tags -> viewModel.setTags(headline, tags) },
                onSetPlanningDates = { sched, dead -> viewModel.setPlanningDates(headline, sched, dead) },
                onAddNote = { note -> viewModel.addNote(headline, note) },
                onRefile = { metadataOpen = false; viewModel.startRefile(headline) },
                onDismiss = { metadataOpen = false },
            )
        }
    }

    refileState?.let { refile ->
        val doc = (state as? DocumentUiState.Loaded)?.document
        RefileSheet(
            state = refile,
            currentFileName = noteRef.fileName,
            currentDoc = doc,
            onPickNotebook = viewModel::refilePickNotebook,
            onDrillInto = viewModel::refileDrillInto,
            onBack = viewModel::refileBack,
            onCancel = viewModel::refileCancel,
            onConfirm = { refileAwaitingLeave = true; viewModel.refileConfirm() },
            onArchive = { refileAwaitingLeave = true; viewModel.refileToArchive() },
            onPickLastUsed = { refileAwaitingLeave = true; viewModel.refileToLastUsed() },
        )
    }
}

/**
 * Path to the current heading, e.g. "shopping.org › Errands › Groceries".
 * Every segment is tappable: the file segment opens the full outline, each
 * heading segment narrows the outline to that heading's subtree.
 */
@Composable
private fun ReadModeBreadcrumb(
    fileName: String,
    path: List<OrgHeadline>,
    onOpenBreadcrumb: (Int?) -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            breadcrumbFileLabel(fileName),
            fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
            maxLines = 1,
            modifier = Modifier.clickable { onOpenBreadcrumb(null) },
        )
        path.forEach { h ->
            Text(" › ", fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3)
            Text(
                h.title,
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                maxLines = 1,
                modifier = Modifier.clickable { onOpenBreadcrumb(h.lineIndex) },
            )
        }
    }
}

/**
 * Read view for a file's heading-less content (everything before the first `*`).
 * Renders only that content, as org body blocks — no title, no heading, no
 * subtree. Double-tap switches to the preface editor; links and checkboxes work
 * as in [NoteContent].
 */
@Composable
private fun PrefaceContent(
    doc: OrgDocument,
    fileName: String,
    listState: LazyListState,
    onOpenNote: (NoteRef) -> Unit,
    onEdit: () -> Unit,
    onToggleCheckbox: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var linkMenuState by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    val openLink: (String) -> Unit = remember(doc, fileName, context, onOpenNote) {
        { openOrgTarget(it, doc, fileName, context, onOpenNote) }
    }
    val onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit = remember {
        { target, textLocalPos, textCoords ->
            boxCoords?.let {
                val boxLocalPos = it.localPositionOf(textCoords, textLocalPos)
                linkMenuState = target to IntOffset(boxLocalPos.x.toInt(), boxLocalPos.y.toInt())
            }
        }
    }
    val body = remember(doc) { doc.prefaceBody.toList() }

    Box(Modifier.onGloballyPositioned { boxCoords = it }) {
        LazyColumn(
            state = listState,
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onEdit() })
            },
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 48.dp),
        ) {
            item(key = "preface-body") {
                SelectionContainer {
                    Column {
                        BodyBlocks(body, doc.prefaceBodyStart, onToggleCheckbox, openLink, onLinkLongPress, onEdit)
                    }
                }
            }
            item(key = "bottom-spacer") { Spacer(Modifier.height(40.dp)) }
        }

        val (target, anchorOffset) = linkMenuState ?: (null to IntOffset.Zero)
        if (target != null && anchorOffset != IntOffset.Zero) {
            Box(Modifier.offset { anchorOffset }) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { linkMenuState = null },
                    containerColor = c.surface,
                ) {
                    LinkActionMenuItems(target, onDismiss = { linkMenuState = null })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteContent(
    doc: OrgDocument,
    headline: OrgHeadline,
    fileName: String,
    onOpenNote: (NoteRef) -> Unit,
    onEditAt: (Int?) -> Unit,
    onToggleCheckbox: (Int) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showPropertyDrawers: Boolean = true,
    favorites: List<FavoriteNote> = emptyList(),
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    // Per-section collapse state, reset when the viewed note changes; all
    // sections start collapsed (design/Grove.dc.html lines 499-552).
    val collapsibleExpanded = remember(fileName, headline.lineIndex) { mutableStateMapOf<String, Boolean>() }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var linkMenuState by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    // Remembered so rows keep stable callbacks and can skip recomposition:
    // e.g. opening the link menu (state change below) must not re-render rows.
    val openLink: (String) -> Unit = remember(doc, fileName, context, onOpenNote) {
        { openOrgTarget(it, doc, fileName, context, onOpenNote) }
    }
    val onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit = remember {
        { target, textLocalPos, textCoords ->
            // Convert text-local position to outer-Box-local position
            boxCoords?.let {
                val boxLocalPos = it.localPositionOf(textCoords, textLocalPos)
                linkMenuState = target to IntOffset(boxLocalPos.x.toInt(), boxLocalPos.y.toInt())
            }
        }
    }

    // O(document) traversals, computed once per document instead of per
    // recomposition. Body lines are resolved per-row inside the LazyColumn
    // items below, so only on-screen headings pay that cost.
    val tags = remember(doc, headline) { doc.inheritedTags(headline) }
    val ownBody = remember(doc, headline) { doc.bodyOf(headline) }
    val subtree = remember(doc, headline) { doc.subtree(headline) }

    // A heading whose subtree is huge or very deep (e.g. a 2000-heading "note")
    // used to render every descendant heading + body eagerly in one scrolling
    // Column, which janked the open, the scroll and the back navigation. Such
    // notes now open with their inner headings folded, so only the note body +
    // a one-level section list mount; small notes still open fully expanded.
    var collapsed by rememberSaveable(fileName, headline.lineIndex, stateSaver = IntSetSaver) {
        mutableStateOf(emptySet<Int>())
    }
    var defaultCollapseApplied by rememberSaveable(fileName, headline.lineIndex) { mutableStateOf(false) }
    LaunchedEffect(fileName, headline.lineIndex) {
        if (!defaultCollapseApplied) {
            defaultReadCollapse(doc, subtree).takeIf { it.isNotEmpty() }?.let { collapsed = it }
            defaultCollapseApplied = true
        }
    }
    val visibleRows = remember(subtree, collapsed) { visibleReadRows(subtree, collapsed) }

    Box(
        Modifier.onGloballyPositioned { boxCoords = it }
    ) {
        // One SelectionContainer per section (the note's own body, then each
        // visible heading). Cross-section selection was given up so the subtree
        // could stop being one eager Column and virtualize: a drag-select now
        // stays within a section.
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
        ) {
            item(key = "own") {
                SelectionContainer {
                    Column {
                        // Tag chips
                        if (tags.isNotEmpty()) {
                            Row {
                                tags.forEach { tag ->
                                    Pill(tag, fg = c.accent, bg = c.accentSoft, outline = true)
                                    Spacer(Modifier.width(7.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            headline.keyword?.let { kw ->
                                val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft
                                else c.amber to c.amberSoft
                                Pill(kw, fg = fg, bg = bg)
                                Spacer(Modifier.width(8.dp))
                            }
                            headline.priority?.let { p ->
                                Text(
                                    "[#$p]",
                                    fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = c.priorityColor(p),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            OrgText(
                                headline.title, onOpenLink = openLink, onLinkLongPress = onLinkLongPress,
                                onDoubleTapAt = { onEditAt(null) },
                                style = TextStyle(
                                    fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
                                    fontSize = 25.sp, color = c.ink, lineHeight = 1.3.em,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (favorites.any { it.matches(headline) }) {
                                Spacer(Modifier.width(8.dp))
                                FavoriteStar(modifier = Modifier.padding(top = 6.dp), size = 24.dp)
                            }
                        }

                        // Planning line (SCHEDULED/DEADLINE) is immediately after the
                        // heading in the raw org file, before any drawers, so it
                        // renders first here too, matching edit mode's line order.
                        // Both chips share a row and wrap to a second line only if
                        // they don't fit side by side.
                        if (headline.planning.scheduled != null || headline.planning.deadline != null) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                headline.planning.scheduled?.let {
                                    PlanningChip(it.formatHuman(), icon = Icons.Outlined.CalendarMonth, fg = c.blue, bg = c.blueSoft)
                                }
                                headline.planning.deadline?.let {
                                    PlanningChip(it.formatHuman(), icon = Icons.Filled.Flag, fg = c.red, bg = c.redSoft)
                                }
                            }
                        }

                        // Note's own :PROPERTIES: and :LOGBOOK: drawers (CREATED lives
                        // in :PROPERTIES:, shown there only, not as a separate line).
                        if (showPropertyDrawers && (headline.properties.isNotEmpty() || headline.logbook.isNotEmpty())) {
                            Spacer(Modifier.height(10.dp))
                            if (headline.properties.isNotEmpty()) {
                                CollapsibleKvSection(
                                    label = ":PROPERTIES:",
                                    entries = headline.properties.map { (k, v) -> ":$k:" to v },
                                    expanded = collapsibleExpanded["own"] == true,
                                    onToggle = {
                                        collapsibleExpanded["own"] = collapsibleExpanded["own"] != true
                                    },
                                )
                            }
                            if (headline.logbook.isNotEmpty()) {
                                if (headline.properties.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                CollapsibleLogSection(
                                    label = ":LOGBOOK:",
                                    lines = headline.logbook,
                                    expanded = collapsibleExpanded["own-logbook"] == true,
                                    onToggle = {
                                        collapsibleExpanded["own-logbook"] = collapsibleExpanded["own-logbook"] != true
                                    },
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                        Spacer(Modifier.height(16.dp))

                        // Own body
                        BodyBlocks(ownBody, headline.bodyStart, onToggleCheckbox, openLink, onLinkLongPress) { onEditAt(null) }
                    }
                }
            }

            // Subtree, one item per visible heading, sized by relative depth.
            // Headings nested under a folded ancestor aren't in `visibleRows`
            // at all, so they never compose.
            items(visibleRows, key = { it.lineIndex }) { child ->
                val body = remember(doc, child) { doc.bodyOf(child) }
                val foldable = remember(doc, child) { doc.hasDescendants(child) }
                val childCollapsed = child.lineIndex in collapsed
                val rel = (child.level - headline.level).coerceAtLeast(1)
                Column {
                    Spacer(Modifier.height(20.dp))
                    SelectionContainer {
                        Column {
                            // Top-aligned so the keyword pill stays on the first line
                            // when the title wraps.
                            Row(verticalAlignment = Alignment.Top) {
                                // Disclosure control: a tap folds/unfolds this
                                // heading's subtree. Drawn like the outline's caret.
                                // Only foldable headings reserve the space, so leaf
                                // headings stay flush with the body like before.
                                if (foldable) {
                                    Box(
                                        Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                collapsed = if (childCollapsed) collapsed - child.lineIndex
                                                else collapsed + child.lineIndex
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Canvas(Modifier.size(9.dp)) {
                                            val path = if (childCollapsed) {
                                                Path().apply {
                                                    moveTo(0f, 0f); lineTo(0f, size.height)
                                                    lineTo(size.width, size.height / 2f); close()
                                                }
                                            } else {
                                                Path().apply {
                                                    moveTo(0f, 0f); lineTo(size.width, 0f)
                                                    lineTo(size.width / 2f, size.height); close()
                                                }
                                            }
                                            drawPath(path, color = c.ink3)
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                child.keyword?.let { kw ->
                                    val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft
                                    else c.amber to c.amberSoft
                                    Pill(kw, fg = fg, bg = bg)
                                    Spacer(Modifier.width(8.dp))
                                }
                                child.priority?.let { p ->
                                    Text(
                                        "[#$p]",
                                        fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp, color = c.priorityColor(p),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                OrgText(
                                    child.title, onOpenLink = openLink, onLinkLongPress = onLinkLongPress,
                                    onDoubleTapAt = { onEditAt(child.lineIndex) },
                                    style = TextStyle(
                                        fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
                                        fontSize = when (rel) {
                                            1 -> 19.sp
                                            2 -> 17.sp
                                            else -> 16.sp
                                        },
                                        color = c.ink,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                if (childCollapsed && foldable) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "… ${doc.directChildren(child).size}",
                                        fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                                if (favorites.any { it.matches(child) }) {
                                    Spacer(Modifier.width(8.dp))
                                    FavoriteStar(modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            if (showPropertyDrawers && (child.properties.isNotEmpty() || child.logbook.isNotEmpty())) {
                                Spacer(Modifier.height(10.dp))
                                if (child.properties.isNotEmpty()) {
                                    CollapsibleKvSection(
                                        label = ":PROPERTIES:",
                                        entries = child.properties.map { (k, v) -> ":$k:" to v },
                                        expanded = collapsibleExpanded["child:${child.lineIndex}"] == true,
                                        onToggle = {
                                            val key = "child:${child.lineIndex}"
                                            collapsibleExpanded[key] = collapsibleExpanded[key] != true
                                        },
                                    )
                                }
                                if (child.logbook.isNotEmpty()) {
                                    if (child.properties.isNotEmpty()) Spacer(Modifier.height(6.dp))
                                    CollapsibleLogSection(
                                        label = ":LOGBOOK:",
                                        lines = child.logbook,
                                        expanded = collapsibleExpanded["child-logbook:${child.lineIndex}"] == true,
                                        onToggle = {
                                            val key = "child-logbook:${child.lineIndex}"
                                            collapsibleExpanded[key] = collapsibleExpanded[key] != true
                                        },
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }
                            // A folded heading shows only its title + "… N"; its
                            // body and descendants stay unmounted.
                            if (!childCollapsed) {
                                BodyBlocks(body, child.bodyStart, onToggleCheckbox, openLink, onLinkLongPress) { onEditAt(child.lineIndex) }
                            }
                        }
                    }
                }
            }

            item(key = "bottom-spacer") { Spacer(Modifier.height(40.dp)) }
        }

        // Zero-size anchor Box at the press location; DropdownMenu anchors to it
        val (target, anchorOffset) = linkMenuState ?: (null to IntOffset.Zero)
        if (target != null && anchorOffset != IntOffset.Zero) {
            Box(
                Modifier.offset { anchorOffset }
            ) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { linkMenuState = null },
                    containerColor = c.surface,
                ) {
                    LinkActionMenuItems(target, onDismiss = { linkMenuState = null })
                }
            }
        }
    }
}

/**
 * A note whose subtree has more than this many headings opens with its inner
 * headings folded, so only the note body + a one-level section list mount.
 * Below it, the read view opens fully expanded (no behavior change).
 */
internal const val LARGE_SUBTREE_THRESHOLD = 60

/**
 * Line indices to fold when the note first opens: for a large subtree, every
 * descendant heading that itself has descendants, so the reader lands on the
 * note body + a one-level section list. Empty for a small subtree (opens fully
 * expanded, unchanged behavior).
 */
internal fun defaultReadCollapse(doc: OrgDocument, subtree: List<OrgHeadline>): Set<Int> =
    if (subtree.size > LARGE_SUBTREE_THRESHOLD) {
        subtree.asSequence().filter { doc.hasDescendants(it) }.map { it.lineIndex }.toSet()
    } else {
        emptySet()
    }

/**
 * The subset of [subtree] to actually render: every heading except those nested
 * under a folded ancestor. Same document-order walk as the outline's
 * `visibleHeadlines`.
 */
internal fun visibleReadRows(subtree: List<OrgHeadline>, collapsed: Set<Int>): List<OrgHeadline> {
    val result = ArrayList<OrgHeadline>(subtree.size)
    var hideDeeperThan: Int? = null
    for (h in subtree) {
        val hide = hideDeeperThan
        if (hide != null) {
            if (h.level > hide) continue
            hideDeeperThan = null
        }
        result.add(h)
        if (h.lineIndex in collapsed) hideDeeperThan = h.level
    }
    return result
}

/** Text that renders org inline markup and hands link taps/long-presses to [onOpenLink]/[onLinkLongPress]. */
@Composable
private fun OrgText(
    text: String,
    onOpenLink: (String) -> Unit,
    onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    /** Double-tap anywhere in this run switches to edit mode. Word-selection
     * via long-press, and link taps, are unaffected (see [doubleTapToEdit]). */
    onDoubleTapAt: (() -> Unit)? = null,
) {
    val c = MaterialTheme.grove
    val annotated = remember(text, c, onOpenLink) { annotateOrgInline(text, c, onOpenLink) }
    val links = remember(text) { orgInlineLinks(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Wrapper to pass text's coordinates to the long-press handler
    val wrappedOnLongPress: (String, Offset) -> Unit = { target, textLocalPos ->
        textCoords?.let { onLinkLongPress(target, textLocalPos, it) }
    }

    Text(
        annotated,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { textCoords = it }
            .doubleTapToEdit(
                layoutResult = { layout },
                links = links,
                enabled = onDoubleTapAt != null,
                onDoubleTap = { onDoubleTapAt?.invoke() },
            )
            .linkPressHandler(
                links = links,
                layoutResult = { layout },
                onTap = onOpenLink,
                onLongPress = wrappedOnLongPress,
            ),
    )
}

/** Copy link / Share link: the actions offered when long-pressing a link in read mode. */
@Composable
private fun LinkActionMenuItems(target: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.grove
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("Copy link", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("link", target))) }
            onDismiss()
        },
    )
    DropdownMenuItem(
        text = { Text("Share link", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, target)
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        },
    )
}

/**
 * A planning date shown as a soft-tinted chip (SCHEDULED blue calendar,
 * DEADLINE red flag). [text] is the human form (`OrgTimestamp.formatHuman`),
 * not the raw org stamp: Read mode is prose, Edit mode is where the literal
 * `<2026-07-30 Thu>` belongs.
 */
@Composable
private fun PlanningChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fg: Color, bg: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Text(text, fontFamily = PlexMono, fontSize = 12.5.sp, color = fg)
    }
}

@Composable
private fun BodyBlocks(
    bodyLines: List<String>,
    /** Absolute doc line of `bodyLines[0]`, to resolve a [OrgBlock.ListItem]'s body-relative `line`. */
    lineOffset: Int,
    onToggleCheckbox: (Int) -> Unit,
    openTarget: (String) -> Unit,
    onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit,
    onEditAt: () -> Unit,
) {
    val c = MaterialTheme.grove
    val blocks = remember(bodyLines) { BlockParser.parse(bodyLines) }

    blocks.forEach { block ->
        when (block) {
            is OrgBlock.Paragraph -> {
                // A standalone timestamp line (e.g. a journal entry's inactive
                // timestamp) starts its own line rather than running into the
                // text that follows it with just a joining space.
                val firstLine = block.lines.first().trim()
                if (block.lines.size > 1 && isStandaloneTimestamp(firstLine)) {
                    OrgText(
                        firstLine,
                        onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                        onDoubleTapAt = onEditAt,
                        style = TextStyle(fontFamily = PlexSerif, fontSize = 16.sp, lineHeight = 1.65.em, color = c.ink),
                    )
                    OrgText(
                        block.lines.drop(1).joinToString(" ") { it.trim() },
                        onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                        onDoubleTapAt = onEditAt,
                        style = TextStyle(fontFamily = PlexSerif, fontSize = 16.sp, lineHeight = 1.65.em, color = c.ink),
                    )
                } else {
                    OrgText(
                        block.lines.joinToString(" ") { it.trim() },
                        onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                        onDoubleTapAt = onEditAt,
                        style = TextStyle(fontFamily = PlexSerif, fontSize = 16.sp, lineHeight = 1.65.em, color = c.ink),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.ListBlock -> {
                Column(Modifier.padding(start = 8.dp)) {
                    block.items.forEachIndexed { i, item ->
                        val done = item.checkbox == 'X' || item.checkbox == 'x'
                        Row(
                            Modifier.padding(vertical = 2.dp).padding(start = (item.indent * 20).dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Sized to the first line's height (16sp * 1.55 line-height
                            // below) so the glyph centers against just that line, not
                            // the full (possibly multi-line) height of the item text.
                            // Bullets/checkboxes are drawn on a Canvas rather than as
                            // Unicode glyphs: PlexSerif has no •/☐/☑/◧ glyphs, so those
                            // fall back to a different font whose vertical metrics don't
                            // match PlexSerif's, throwing off the centering above.
                            Box(
                                Modifier.width(20.dp).heightIn(min = 25.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val markColor = if (done) c.ink3 else c.ink2
                                when {
                                    item.checkbox != null -> {
                                        Canvas(
                                            Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { onToggleCheckbox(lineOffset + item.line) }
                                                .padding(1.dp),
                                        ) {
                                            val stroke = 1.5.dp.toPx()
                                            val corner = CornerRadius(3.dp.toPx())
                                            when (item.checkbox) {
                                                'X', 'x' -> {
                                                    drawRoundRect(color = markColor, cornerRadius = corner)
                                                    val check = Path().apply {
                                                        moveTo(size.width * 0.22f, size.height * 0.52f)
                                                        lineTo(size.width * 0.42f, size.height * 0.72f)
                                                        lineTo(size.width * 0.78f, size.height * 0.28f)
                                                    }
                                                    drawPath(
                                                        check,
                                                        color = c.bg,
                                                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                                    )
                                                }
                                                '-' -> {
                                                    drawRoundRect(color = markColor, cornerRadius = corner, style = Stroke(width = stroke))
                                                    drawLine(
                                                        color = markColor,
                                                        start = Offset(size.width * 0.25f, size.height / 2f),
                                                        end = Offset(size.width * 0.75f, size.height / 2f),
                                                        strokeWidth = stroke,
                                                        cap = StrokeCap.Round,
                                                    )
                                                }
                                                else -> {
                                                    drawRoundRect(color = markColor, cornerRadius = corner, style = Stroke(width = stroke))
                                                }
                                            }
                                        }
                                    }
                                    item.ordered -> {
                                        Text("${i + 1}.", fontFamily = PlexSerif, fontSize = 16.sp, color = markColor)
                                    }
                                    else -> {
                                        Canvas(Modifier.size(6.dp)) { drawCircle(color = markColor) }
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            OrgText(
                                item.text,
                                onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                                onDoubleTapAt = onEditAt,
                                style = TextStyle(
                                    fontFamily = PlexSerif, fontSize = 16.sp,
                                    lineHeight = 1.55.em, color = if (done) c.ink3 else c.ink,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.CodeBlock -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface2)
                        .padding(12.dp),
                ) {
                    block.lines.forEach { line ->
                        PlainTappableLine(
                            line, fontFamily = PlexMono, fontSize = 13.sp, color = c.ink,
                            onDoubleTapAt = onEditAt,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.Table -> {
                // v1 decision: tables as monospace plain text
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .padding(10.dp),
                ) {
                    block.lines.forEach { line ->
                        PlainTappableLine(
                            line, fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink,
                            onDoubleTapAt = onEditAt,
                        )
                    }
                    Text(
                        "table rendering coming in v2",
                        fontFamily = PlexMono, fontSize = 10.5.sp, color = c.ink3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** True when [line] (already trimmed) is nothing but a single org timestamp. */
private fun isStandaloneTimestamp(line: String): Boolean {
    val (_, range) = OrgTimestamp.parseWithRange(line) ?: return false
    return range.first == 0 && range.last == line.length - 1
}

/** A single plain (non-org-markup) line (code/table content) that maps a
 * double-tap to edit mode at the tapped character. */
@Composable
private fun PlainTappableLine(
    line: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    onDoubleTapAt: () -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        line,
        fontFamily = fontFamily, fontSize = fontSize, color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.doubleTapToEdit(
            layoutResult = { layout },
            onDoubleTap = onDoubleTapAt,
        ),
    )
}

/** Resolve an org link target: internal id/custom-id jumps to the note, else opens externally. */
private fun openOrgTarget(
    target: String,
    doc: OrgDocument,
    fileName: String,
    context: android.content.Context,
    onOpenNote: (NoteRef) -> Unit,
) {
    when {
        target.startsWith("id:") ->
            doc.findById(target.removePrefix("id:"))
                ?.let { onOpenNote(NoteRef(fileName, it.lineIndex)) }

        target.startsWith("#") ->
            doc.findByCustomId(target.removePrefix("#"))
                ?.let { onOpenNote(NoteRef(fileName, it.lineIndex)) }

        else -> runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
        }
    }
}
