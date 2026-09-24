package com.rrajath.grove.ui.dailies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.LinkedReferencesBar
import com.rrajath.grove.ui.components.LinkedReferencesSheet
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.editor.EditRegion
import com.rrajath.grove.ui.editor.EditorViewModel
import com.rrajath.grove.ui.editor.OrgSyntaxHighlight
import com.rrajath.grove.ui.editor.WholeFileEditorBody
import com.rrajath.grove.ui.screens.FileContent
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.screens.ReadModeBreadcrumb
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyNoteScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onNavigateDate: (LocalDate) -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onOpenOutline: (fileName: String) -> Unit,
    showBacklinks: Boolean,
    showPreface: Boolean,
    showPropertyDrawers: Boolean,
    readModeFontSize: FontSizePreference,
    editModeFontSize: FontSizePreference,
    dailiesViewModel: DailiesViewModel = viewModel(factory = DailiesViewModel.Factory),
    documentViewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
    editorViewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val haptic = LocalHapticFeedback.current
    val nav by dailiesViewModel.state.collectAsStateWithLifecycle()
    val vaultMissing by dailiesViewModel.vaultMissing.collectAsStateWithLifecycle()
    var mode by rememberSaveable(date) { mutableStateOf("read") }
    val editState by editorViewModel.state.collectAsStateWithLifecycle()
    val docState by documentViewModel.state.collectAsStateWithLifecycle()
    val linkedReferences by documentViewModel.linkedReferences.collectAsStateWithLifecycle()
    val isRoamFile = (docState as? DocumentUiState.Loaded)?.document?.fileId != null

    // Set when a Today-button tap / prev-next pill / Back needs to navigate away
    // from a dirty Edit-mode buffer; the leave-confirm dialog below routes there.
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var linkedRefsOpen by remember { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }

    fun runGuarded(action: () -> Unit) {
        if (mode == "edit" && editState.dirty) {
            pendingAction = action
            confirmLeave = true
        } else {
            action()
        }
    }
    fun leave() = runGuarded(onBack)
    androidx.activity.compose.BackHandler { leave() }

    // Shared by the empty-day tap-to-type affordance and the top-right Read/Edit
    // toggle: both are ways into Edit mode, and a brand-new (not-yet-existing)
    // day's buffer must be seeded exactly once, not clobbered on a later toggle.
    fun startEditing(n: DailiesNavState) {
        if (!n.exists && editorViewModel.state.value.fileName != n.fileName) {
            val expanded = dailiesViewModel.expandedHeaderFor(date)
            editorViewModel.loadNewWholeFile(n.fileName, expanded.text, expanded.cursorOffset)
        }
        mode = "edit"
    }

    LaunchedEffect(date) { dailiesViewModel.load(date) }
    LaunchedEffect(nav?.fileName, nav?.exists) {
        val n = nav ?: return@LaunchedEffect
        if (n.exists) {
            documentViewModel.load(n.fileName)
        }
    }
    // Linked References needs the document's real fileId/title, so it can only
    // fire once the document has actually loaded -- not eagerly alongside the
    // load() call above (which would race with a null fileId/empty title).
    LaunchedEffect(docState) {
        val loaded = docState as? DocumentUiState.Loaded ?: return@LaunchedEffect
        val doc = loaded.document
        val title = doc.preambleKeywords.firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
            ?.second ?: loaded.fileName.removeSuffix(".org")
        documentViewModel.loadLinkedReferences(loaded.fileName, com.rrajath.grove.org.INTRO_LINE_INDEX, doc.fileId, title)
    }
    // Refresh both the date-navigation state (so nav.exists stops being stale
    // for a brand-new file) and the Read-mode document after every save.
    LaunchedEffect(editState.lastSavedAt) {
        if (editState.lastSavedAt != null) {
            dailiesViewModel.load(date)
            documentViewModel.load(editState.fileName)
        }
    }

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GroveTopBar(
                leading = {
                    IconGlyph("←", onClick = ::leave)
                    if (mode == "edit" && (editState.dirty || editState.lastSavedAt != null)) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Save,
                            contentDescription = if (editState.dirty) "Unsaved changes, tap to save" else "Saved",
                            tint = if (editState.dirty) c.green else c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { if (editState.dirty) editorViewModel.save() }
                                .padding(10.dp),
                        )
                    }
                },
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                            fontFamily = PlexSans, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            fontSize = 17.sp, color = c.ink,
                        )
                        if (nav?.isToday == true) {
                            Box(Modifier.padding(start = 8.dp)) {
                                Pill(text = "TODAY", fg = c.accentInk, bg = c.accent)
                            }
                        }
                    }
                },
                subtitle = {
                    nav?.let { n ->
                        ReadModeBreadcrumb(
                            fileName = n.fileName,
                            path = emptyList(),
                            onOpenBreadcrumb = { onOpenOutline(n.fileName) },
                        )
                    }
                },
                actions = {
                    if (nav?.isToday == false) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { runGuarded { onNavigateDate(LocalDate.now()) } },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        datePickerOpen = true
                                    },
                                )
                                .padding(10.dp)
                                .testTag("dailies_today_button"),
                        ) {
                            androidx.compose.material3.Icon(Icons.Filled.CalendarToday, contentDescription = "Today", tint = c.ink2)
                        }
                    }
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        optionIcons = listOf(Icons.Outlined.Visibility, Icons.Outlined.Edit),
                        selectedIndex = if (mode == "edit") 1 else 0,
                        onSelect = { idx ->
                            if (idx == 0) {
                                if (editState.dirty) editorViewModel.save(onSaved = { mode = "read" }) else mode = "read"
                            } else {
                                nav?.let { startEditing(it) }
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp).width(IntrinsicSize.Min).testTag("read_edit_toggle"),
                    )
                },
            )
        },
        bottomBar = {
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (showBacklinks && isRoamFile && !imeVisible) {
                LinkedReferencesBar(
                    linkedCount = linkedReferences.linkedCount,
                    unlinkedCount = linkedReferences.unlinkedCount,
                    onClick = { linkedRefsOpen = true },
                )
            }
        },
    ) { padding ->
        val n = nav
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                .pointerInput(nav?.date) {
                    var totalDrag = 0f
                    val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f; velocityTracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            // Only consume the gesture once it's already past the
                            // distance threshold — a short drag (a tap, a cursor
                            // placement, the start of a text selection) is left
                            // completely alone by not calling change.consume().
                            if (kotlin.math.abs(totalDrag) >= size.width * 0.20f) change.consume()
                        },
                        onDragEnd = {
                            val navState = nav ?: return@detectHorizontalDragGestures
                            val velocity = velocityTracker.calculateVelocity().x
                            when (isDeliberateSwipe(totalDrag, velocity, size.width.toFloat())) {
                                SwipeDirection.PREVIOUS -> runGuarded { onNavigateDate(navState.previousDate) }
                                SwipeDirection.NEXT -> runGuarded { onNavigateDate(navState.nextDate) }
                                null -> {}
                            }
                        },
                    )
                },
        ) {
            when {
                n == null -> {
                    if (vaultMissing) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("No sync folder configured", fontFamily = PlexSans, color = c.ink2)
                        }
                    }
                    // else: still loading -- render nothing, a brief transient state.
                }
                !n.exists && mode == "read" -> DailyEmptyState(
                    fileName = n.fileName,
                    onStartTyping = { startEditing(n) },
                )
                mode == "read" -> {
                    when (val s = docState) {
                        is DocumentUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(s.message, fontFamily = PlexSans, color = c.ink2)
                        }
                        is DocumentUiState.Loaded -> {
                            ContentFontScale(readModeFontSize) {
                                FileContent(
                                    doc = s.document,
                                    fileName = n.fileName,
                                    listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                                    showPreface = showPreface,
                                    showPropertyDrawers = showPropertyDrawers,
                                    favorites = emptyList(),
                                    onEdit = { mode = "edit" },
                                    onOpenLink = { target -> documentViewModel.openOrgLink(target, n.fileName, onOpenNote, onOpenOutline) },
                                    onOpenDrawer = { _, _ -> },
                                    onOpenBlock = {},
                                    onOpenPreface = {},
                                    onOpenFileProperties = {},
                                    onToggleCheckbox = { line, longPress ->
                                        if (longPress) documentViewModel.toggleChecklistProgress(line)
                                        else documentViewModel.toggleChecklistDone(line)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        else -> {}
                    }
                }
                else -> {
                    val textState = rememberTextFieldState()
                    var fieldLoaded by remember(n.fileName) { mutableStateOf(false) }
                    var echoToSkip by remember { mutableStateOf<String?>(null) }
                    val focusRequester = remember { FocusRequester() }
                    val scrollState = rememberScrollState()
                    val highlight = remember(c, editState.keywords) {
                        OrgSyntaxHighlight(c, editState.keywords)
                    }

                    LaunchedEffect(n.fileName, n.exists) {
                        // A file that already exists but is being opened straight
                        // into Edit (toggle tapped from Read) loads normally; a
                        // brand-new file was already seeded by startEditing()
                        // above and must not be clobbered by a fresh load here.
                        if (n.exists && editorViewModel.state.value.fileName != n.fileName) {
                            editorViewModel.loadRegion(n.fileName, null, EditRegion.WHOLE_FILE)
                        }
                    }
                    LaunchedEffect(editState.loading, editState.bufferRevision) {
                        if (!editState.loading && editState.error == null) {
                            val cursor = if (!fieldLoaded) (editState.cursor ?: editState.buffer.length) else textState.selection.start
                            echoToSkip = editState.buffer
                            textState.edit {
                                replace(0, length, editState.buffer)
                                selection = TextRange(cursor.coerceIn(0, editState.buffer.length))
                            }
                            fieldLoaded = true
                        }
                    }
                    LaunchedEffect(Unit) {
                        snapshotFlow { textState.text.toString() }.collect { text ->
                            if (!fieldLoaded) return@collect
                            if (text == echoToSkip) { echoToSkip = null; return@collect }
                            editorViewModel.onBufferChange(text)
                        }
                    }

                    WholeFileEditorBody(
                        state = editState,
                        textState = textState,
                        scrollState = scrollState,
                        highlight = highlight,
                        focusRequester = focusRequester,
                        autoLinkSuggestions = emptyList(),
                        expandedChipKeys = emptySet(),
                        autoLinkTriggerRange = null,
                        onToggleExpandChip = {},
                        onClearAutoLinkTrigger = {},
                        onOverwriteStale = { editorViewModel.save(force = true) },
                        onReloadStale = {
                            editorViewModel.dismissStale()
                            editorViewModel.loadRegion(n.fileName, null, EditRegion.WHOLE_FILE)
                        },
                        editModeFontSize = editModeFontSize,
                        showBacklinks = false, // this screen's own bottom bar owns Linked References (Task 17)
                        isRoamFile = false,
                        linkedCount = 0,
                        unlinkedCount = 0,
                        onOpenLinkedRefs = {},
                        imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0,
                        onLink = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            nav?.let { n2 ->
                Box(
                    Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = if (showBacklinks && isRoamFile) 74.dp else 16.dp),
                ) { DateNavPill(label = shortLabel(n2.previousDate), leading = true) { runGuarded { onNavigateDate(n2.previousDate) } } }
                Box(
                    Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (showBacklinks && isRoamFile) 74.dp else 16.dp),
                ) { DateNavPill(label = shortLabel(n2.nextDate), leading = false) { runGuarded { onNavigateDate(n2.nextDate) } } }
            }
        }
    }

    if (datePickerOpen) {
        DailyDatePickerSheet(
            initialMonth = date,
            existingDates = nav?.existingDates.orEmpty(),
            onPick = { picked -> datePickerOpen = false; onNavigateDate(picked) },
            onDismiss = { datePickerOpen = false },
        )
    }

    if (confirmLeave) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Save changes?",
                    fontFamily = PlexSans, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "This daily note has unsaved changes.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    val action = pendingAction.also { pendingAction = null }
                    editorViewModel.save(onSaved = { action?.invoke() })
                }) { Text("Save", color = c.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    val action = pendingAction.also { pendingAction = null }
                    action?.invoke()
                }) { Text("Discard", color = c.red) }
            },
        )
    }

    if (linkedRefsOpen) {
        val title = (docState as? DocumentUiState.Loaded)?.document
            ?.preambleKeywords?.firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
            ?.second ?: nav?.fileName?.removeSuffix(".org") ?: ""
        LinkedReferencesSheet(
            title = title,
            result = linkedReferences,
            onOpenReference = { refFileName, lineIndex, id ->
                linkedRefsOpen = false
                onOpenNote(NoteRef(refFileName, lineIndex, id))
            },
            onDismiss = { linkedRefsOpen = false },
        )
    }
}

@Composable
private fun DailyEmptyState(fileName: String, onStartTyping: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onStartTyping)
            .padding(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            "Nothing here yet. Tap to start typing and create $fileName. " +
                "Long-press the calendar icon to create a note for another date.",
            fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun shortLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d"))

@Composable
private fun DateNavPill(label: String, leading: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            if (leading) "‹ $label" else "$label ›",
            fontFamily = PlexSans, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = 13.sp, color = c.ink,
        )
    }
}
