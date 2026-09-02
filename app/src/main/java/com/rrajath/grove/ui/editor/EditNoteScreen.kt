package com.rrajath.grove.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.screens.RefileSheet
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.RefileUiState
import com.rrajath.grove.ui.vault.headlineAtLine
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Raw org editor (design spec §6): syntax-highlighted subtree editing with
 * formatting toolbar and metadata sheet. Leaving with unsaved changes asks
 * to save or discard.
 */
@Composable
fun EditNoteScreen(
    noteRef: NoteRef,
    onBack: () -> Unit,
    onSwitchToRead: () -> Unit,
    /** True when the note was just created (e.g. via the outline + button). */
    isNewNote: Boolean = false,
    /**
     * Absolute doc line of a subheading double-tapped in read mode, if any.
     * The cursor and scroll position land there instead of the default
     * "end of the first line" placement.
     */
    initialCursorLine: Int? = null,
    /** Settings § Notes: font-size lever for the editor field. App chrome is unaffected. */
    editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    val textState = rememberTextFieldState()
    var metadataOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDiscardBlankHeading by remember { mutableStateOf(false) }
    var showEmptyHeadingAlert by remember { mutableStateOf(false) }
    var confirmRefile by remember { mutableStateOf(false) }
    // Timestamp of the most recent save (auto or manual), shown as a tappable
    // save (floppy) icon in the top bar: green + tap-to-save-now while dirty,
    // grey + tap-for-last-saved-toast once clean.
    var lastAutoSavedAt by remember { mutableStateOf<LocalTime?>(null) }
    val focusRequester = remember { FocusRequester() }
    // False until the note has been loaded into the text field: the field's
    // pre-load contents are not the user's edits and must not be reported.
    var fieldLoaded by remember { mutableStateOf(false) }
    // Text last written into the field programmatically (initial load, metadata
    // sheet rewrite). The snapshotFlow below echoes every write straight back;
    // that echo must not be reported as a user edit, which would wrongly mark a
    // freshly opened note dirty. Cleared once consumed, so a later real edit
    // that happens to restore the same text is still reported.
    var echoToSkip by remember { mutableStateOf<String?>(null) }

    /** Replace the field's contents without it counting as a user edit. */
    fun setText(text: String, cursor: TextRange) {
        echoToSkip = text
        textState.edit {
            replace(0, length, text)
            selection = cursor
        }
    }

    // --- toolbar link picker (long-press the [[]] button) ---
    // The selection at the moment the picker was opened: its text is the link
    // description, and the picked link is spliced back over this range once the
    // sheet / dialog closes (by which time the field's own selection has
    // collapsed).
    var pendingLinkSel by remember { mutableStateOf<TextRange?>(null) }
    var pendingLinkDesc by remember { mutableStateOf<String?>(null) }
    // Set when a picked file or heading carries an ID and the user must choose
    // between the resilient id link and the plain one. Holds both, pre-formatted.
    var linkIdChoice by remember { mutableStateOf<LinkIdChoice?>(null) }
    val linkPicker by viewModel.linkPicker.collectAsStateWithLifecycle()

    fun captureLinkSelection() {
        val sel = textState.selection
        pendingLinkSel = sel
        pendingLinkDesc = if (sel.collapsed) null else textState.text.substring(sel.min, sel.max)
    }

    fun spliceLink(linkText: String) {
        val sel = pendingLinkSel ?: textState.selection
        val lo = sel.min.coerceIn(0, textState.text.length)
        val hi = sel.max.coerceIn(lo, textState.text.length)
        textState.edit {
            replace(lo, hi, linkText)
            selection = TextRange(lo + linkText.length)
        }
        pendingLinkSel = null
        pendingLinkDesc = null
    }

    /** Turn the picked file (top level) or heading into a link, asking about an ID first when there is one. */
    fun confirmLinkPick(r: RefileUiState) {
        val doc = r.pickedDoc ?: return
        val file = r.pickedFile ?: return
        viewModel.linkPickerCancel()

        val heading = r.path.lastOrNull()?.let { doc.headlineAtLine(it) }
        // With text selected the selection is the description; with nothing
        // selected the link falls back to naming its target (the heading title,
        // or the file's #+TITLE: / base name).
        val desc = pendingLinkDesc ?: if (heading != null) {
            heading.title
        } else {
            doc.preambleKeywords
                .firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
                ?.second?.takeIf { it.isNotBlank() }
                ?: file.substringAfterLast('/').removeSuffix(".org")
        }
        if (heading == null) {
            // File-level link. A file:-link is always relative to the editing
            // file's directory (just the name for a link into the same file).
            val relPath = relativeOrgPath(state.fileName, file)
            val fileId = doc.filePropertyDrawer
                .firstOrNull { it.first.equals(":ID:", ignoreCase = true) }?.second
            if (fileId != null) {
                linkIdChoice = LinkIdChoice(
                    withId = formatHeadingLink(HeadingLinkTarget.ById(fileId), desc),
                    withPlain = formatFileLink(relPath, desc),
                    subject = "file",
                )
            } else {
                spliceLink(formatFileLink(relPath, desc))
            }
            return
        }

        // Heading link. Drop the file: qualifier when the heading is in this note.
        val relPath = if (file == state.fileName) null else relativeOrgPath(state.fileName, file)
        val hasId = heading.id != null || heading.customId != null
        if (!hasId) {
            spliceLink(formatHeadingLink(HeadingLinkTarget.ByName(heading.title, relPath), desc))
        } else {
            linkIdChoice = LinkIdChoice(
                withId = formatHeadingLink(resilientHeadingTarget(heading.id, heading.customId, relPath), desc),
                withPlain = formatHeadingLink(HeadingLinkTarget.ByName(heading.title, relPath), desc),
                subject = "heading",
            )
        }
    }

    /** Validate heading before saving; shows alert if blank, otherwise saves. */
    fun trySave(onSaved: () -> Unit) {
        if (viewModel.isCurrentHeadingBlank()) {
            showEmptyHeadingAlert = true
        } else {
            viewModel.save(onSaved = onSaved)
        }
    }

    fun leave() {
        when {
            // A blank heading can't be saved, so leaving always means discarding
            // the note (heading, any body text, all of it); always confirm
            // first rather than silently dropping whatever was typed.
            isNewNote && viewModel.isCurrentHeadingBlank() -> confirmDiscardBlankHeading = true
            state.dirty -> confirmLeave = true
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler { leave() }

    // Refile is a disk-level move-between-files operation; the editor only holds an in-memory
    // buffer until Save. A dedicated DocumentViewModel drives the refile picker itself (that
    // state machine works against a loaded on-disk document, not this screen's buffer) once the
    // buffer has been flushed to disk.
    val refileViewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory)
    val refileDocState by refileViewModel.state.collectAsStateWithLifecycle()
    val refileState by refileViewModel.refile.collectAsStateWithLifecycle()
    val refileSnack by refileViewModel.snack.collectAsStateWithLifecycle()
    var refileTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(refileDocState, refileTarget) {
        val target = refileTarget ?: return@LaunchedEffect
        val loaded = refileDocState as? DocumentUiState.Loaded ?: return@LaunchedEffect
        if (loaded.fileName != target.first) return@LaunchedEffect
        refileTarget = null
        loaded.document.headlineAtLine(target.second)?.let(refileViewModel::startRefile)
    }
    // Set on a completed move (refileConfirm/refileToArchive/refileToLastUsed), not a plain
    // cancel/back-out. The move itself (file write + the "Refiled to X" snack) runs async in
    // refileViewModel.viewModelScope *after* `refile` is already nulled out to close the sheet,
    // so this can't just watch `refile`: leaving immediately would pop this screen's back-stack
    // entry — and with it refileViewModel's scope — out from under that still-in-flight
    // coroutine. Instead it waits for the snack this move ends with to actually appear and then
    // clear, which both guarantees the write has landed and gives the user the undo window this
    // screen closing shouldn't cut short. A tap on Undo restores this note at its original
    // fileName/lineIndex, which the buffer this screen is editing still targets, so it clears
    // the flag instead of leaving.
    var refileAwaitingLeave by remember { mutableStateOf(false) }
    var refileSnackSeen by remember { mutableStateOf(false) }
    LaunchedEffect(refileSnack) {
        if (!refileAwaitingLeave) return@LaunchedEffect
        if (refileSnack != null) {
            refileSnackSeen = true
        } else if (refileSnackSeen) {
            refileAwaitingLeave = false
            refileSnackSeen = false
            leave()
        }
    }

    val scrollState = rememberScrollState()
    val editorLineHeightPx = with(LocalDensity.current) { (13.5f * 1.85f).sp.toPx() }

    LaunchedEffect(noteRef) { viewModel.load(noteRef) }
    LaunchedEffect(state.loading) {
        if (!state.loading && state.error == null) {
            if (isNewNote) {
                // FAB-created heading has no body yet (just the "* " line,
                // plus an optional :PROPERTIES: drawer): append a blank body
                // line and park the cursor there so the keyboard opens ready
                // for content, instead of on the heading line.
                val bodyText = state.buffer + "\n"
                setText(bodyText, TextRange(bodyText.length))
            } else {
                val targetOffset = charOffsetForLine(state.buffer, state.lineIndex, initialCursorLine)
                val cursor = targetOffset ?: state.buffer.length.coerceAtMost(
                    state.buffer.indexOf('\n').let { if (it == -1) state.buffer.length else it },
                )
                setText(state.buffer, TextRange(cursor))
            }
            fieldLoaded = true
            if (isNewNote) focusRequester.requestFocus()
            // Scroll the tapped subheading into view. The buffer/cursor were
            // just set above, so scrollState's layout (and thus maxValue) is
            // still stale for this frame; give it two frames to catch up
            // before reading/clamping against it.
            val relativeLine = initialCursorLine?.let { it - state.lineIndex }
            if (relativeLine != null && relativeLine > 0) {
                withFrameNanos {}
                withFrameNanos {}
                val targetPx = ((relativeLine - 2).coerceAtLeast(0) * editorLineHeightPx).toInt()
                scrollState.scrollTo(targetPx.coerceIn(0, scrollState.maxValue))
            }
        }
    }
    // Metadata-sheet mutations rewrite the buffer outside the text field. Keyed
    // on the view model's explicit revision counter, never on the buffer text:
    // the field reports its edits one frame behind, so a text comparison could
    // see the *previous* buffer alongside the newest field contents and push
    // the older text back in, eating the characters typed in between.
    LaunchedEffect(state.bufferRevision) {
        if (!fieldLoaded || state.bufferRevision == 0L) return@LaunchedEffect
        val buffer = state.buffer
        if (buffer != textState.text.toString()) {
            setText(buffer, TextRange(textState.selection.start.coerceAtMost(buffer.length)))
        }
    }
    // Report the user's own edits back to the view model. Programmatic writes
    // (setText above) are filtered out, so only real typing marks the note dirty.
    LaunchedEffect(Unit) {
        snapshotFlow { textState.text.toString() }.collect { text ->
            if (!fieldLoaded) return@collect
            if (text == echoToSkip) {
                echoToSkip = null
                return@collect
            }
            viewModel.onBufferChange(text)
        }
    }
    val highlight = remember(c, state.keywords) { OrgSyntaxHighlight(c, state.keywords) }

    // Idle auto-save: wait for a 5s pause in typing, then save if the buffer
    // still has unsaved changes. Re-keying on the buffer text resets the
    // debounce timer on every edit; an unchanged buffer (or one already saved
    // by another path, e.g. save-on-exit) is a no-op via the `dirty` check.
    LaunchedEffect(state.buffer) {
        delay(5_000)
        if (state.dirty) {
            viewModel.save {
                lastAutoSavedAt = LocalTime.now()
            }
        }
    }

    // Five lines of editor text (13.5sp font * 1.85 line height), so the jump
    // buttons don't flash on every keystroke as typing nudges the view.
    val scrollButtonThresholdPx = with(LocalDensity.current) { (13.5f * 1.85f * 5).sp.toPx() }

    Scaffold(
        containerColor = c.bg,
        // Hand off all bottom-inset responsibility to the Column below so that
        // navigationBarsPadding + windowInsetsPadding(ime) work without the
        // Scaffold's own bottom insets creating a double-stacking gap.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GroveTopBar(
                leading = {
                    IconGlyph("←", onClick = ::leave)
                    // Shown once the note has either been edited or saved at least
                    // once; a freshly opened, never-touched note shows nothing.
                    if (state.dirty || lastAutoSavedAt != null) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = if (state.dirty) "Unsaved changes, tap to save" else "Saved",
                            // Green means there are unsaved changes and a tap saves
                            // immediately; grey means the buffer matches what's on
                            // disk (and blinks right after a save); a tap then just
                            // reports when that save happened.
                            tint = if (state.dirty) c.green else c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (state.dirty) {
                                        trySave { lastAutoSavedAt = LocalTime.now() }
                                    } else {
                                        val message = lastAutoSavedAt?.let {
                                            "The note was last saved at: ${AutoSaveTimestamp.format(it)}"
                                        } ?: "This note hasn't been saved yet"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(10.dp),
                        )
                    }
                },
                title = {},
                actions = {
                    IconGlyph("☰", onClick = { metadataOpen = true })
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = 1,
                        onSelect = { if (it == 0) trySave(onSaved = onSwitchToRead) },
                        modifier = Modifier.width(140.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Use safeDrawing bottom inset: gives max(nav-bar, keyboard) so the
                // toolbar always sits flush against whichever is visible.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            state.error?.let { error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, fontFamily = PlexSans, color = c.ink2)
                }
                return@Column
            }
            if (state.staleFile) {
                StaleFileBanner(
                    onOverwrite = { viewModel.save(force = true) },
                    onReload = { viewModel.dismissStale(); viewModel.load(noteRef) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The field owns its own vertical scrolling (rather than being
                // wrapped in Modifier.verticalScroll): that is what lets Compose
                // auto-scroll while a selection handle is dragged past the top or
                // bottom edge, and keeps the cursor visible when the keyboard
                // shrinks the viewport.
                ContentFontScale(editModeFontSize) {
                    BasicTextField(
                        state = textState,
                        inputTransformation = remember(state.keywords) { orgInputTransformation(state.keywords) },
                        outputTransformation = highlight,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        textStyle = TextStyle(
                            fontFamily = PlexMono, fontSize = 13.5.sp,
                            lineHeight = 1.85.em, color = c.ink,
                        ),
                        cursorBrush = SolidColor(c.accent),
                        scrollState = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .focusRequester(focusRequester),
                    )
                }
                ScrollJumpButtons(
                    scrollState = scrollState,
                    minScrollDeltaPx = scrollButtonThresholdPx,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
                GroveUndoSnackbar(
                    snack = snack,
                    onUndo = viewModel::undo,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
                GroveUndoSnackbar(
                    snack = refileSnack,
                    onUndo = {
                        // Restores this note at its original fileName/lineIndex, which the
                        // buffer this screen is editing still targets, so the pending
                        // auto-leave from the move this snack belongs to must not fire.
                        refileAwaitingLeave = false
                        refileSnackSeen = false
                        refileViewModel.undo()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            EditorToolbar(
                onWrap = { marker -> textState.applyEdit { wrapSelection(it, marker) } },
                onInsert = { snippet -> textState.applyEdit { insertAtCursor(it, snippet) } },
                onLink = { textState.applyEdit(::insertLinkFromToolbar) },
                onHeading = {
                    textState.applyEdit {
                        val edit = LineEditing.insertHeadingStar(it.text, it.selection.start)
                        TextFieldValue(edit.text, TextRange(edit.cursor))
                    }
                },
                onIndent = { delta ->
                    textState.applyEdit {
                        LineEditing.changeListIndent(it.text, it.selection.start, delta)
                            ?.let { edit -> TextFieldValue(edit.text, TextRange(edit.cursor)) }
                    }
                },
                onLinkLongPress = {
                    captureLinkSelection()
                    viewModel.startLinkPicker()
                },
            )
        }
    }

    linkPicker?.let { r ->
        RefileSheet(
            state = r,
            currentFileName = state.fileName,
            currentDoc = null,
            onPickNotebook = viewModel::linkPickerPickNotebook,
            onDrillInto = viewModel::linkPickerDrillInto,
            onBack = viewModel::linkPickerBack,
            onCancel = viewModel::linkPickerCancel,
            onConfirm = { confirmLinkPick(r) },
            onArchive = {},
            onPickLastUsed = {},
            headerTitle = "Insert a link",
            confirmLabel = "Link to this heading",
            topLevelConfirmLabel = "Link to this file",
        )
    }

    linkIdChoice?.let { choice ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { linkIdChoice = null },
            containerColor = c.surface,
            title = {
                Text(
                    "Use the ${choice.subject}'s ID?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "This ${choice.subject} has an ID. An ID link keeps working if it is later " +
                        "renamed or moved to another file.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    linkIdChoice = null
                    spliceLink(choice.withId)
                }) { Text("Use ID", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    linkIdChoice = null
                    spliceLink(choice.withPlain)
                }) {
                    Text(
                        if (choice.subject == "file") "Use file link" else "Use heading name",
                        color = c.ink2,
                    )
                }
            },
        )
    }

    if (metadataOpen) {
        val headline = remember(state.buffer, state.keywords) { viewModel.currentHeadline }
        MetadataSheet(
            headline = headline,
            keywords = state.keywords,
            allTags = state.allTags,
            onChangeKeyword = viewModel::changeKeyword,
            onSetPriority = viewModel::setPriority,
            onSetTags = viewModel::setTags,
            onSetPlanningDates = viewModel::setPlanningDates,
            onAddNote = viewModel::addNote,
            onRefile = {
                metadataOpen = false
                confirmRefile = true
            },
            onDismiss = { metadataOpen = false },
        )
    }

    if (confirmRefile) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRefile = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Refile this note?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "The note will be saved in its current state and refiled to the location you choose.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmRefile = false
                    trySave {
                        val s = viewModel.state.value
                        refileTarget = s.fileName to s.lineIndex
                        refileViewModel.load(s.fileName)
                    }
                }) { Text("Continue", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRefile = false }) {
                    Text("Cancel", color = c.ink2)
                }
            },
        )
    }

    refileState?.let { refile ->
        val doc = (refileDocState as? DocumentUiState.Loaded)?.document
        RefileSheet(
            state = refile,
            currentFileName = state.fileName,
            currentDoc = doc,
            onPickNotebook = refileViewModel::refilePickNotebook,
            onDrillInto = refileViewModel::refileDrillInto,
            onBack = refileViewModel::refileBack,
            onCancel = refileViewModel::refileCancel,
            onConfirm = { refileAwaitingLeave = true; refileViewModel.refileConfirm() },
            onArchive = { refileAwaitingLeave = true; refileViewModel.refileToArchive() },
            onPickLastUsed = { refileAwaitingLeave = true; refileViewModel.refileToLastUsed() },
        )
    }

    if (confirmLeave) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Save changes?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "This note has unsaved changes.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    trySave(onSaved = onBack)
                }) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    onBack()
                }) { Text("Discard", color = c.red) }
            },
        )
    }

    if (confirmDiscardBlankHeading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscardBlankHeading = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Discard note?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "This note needs a heading before it can be saved. Leaving now will discard it, including any text you've added.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDiscardBlankHeading = false
                    viewModel.deleteSubtree(onDeleted = onBack)
                }) { Text("Discard", color = c.red) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscardBlankHeading = false }) {
                    Text("Keep Editing", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showEmptyHeadingAlert) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEmptyHeadingAlert = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Add a heading",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "Please give this note a heading before saving.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showEmptyHeadingAlert = false }) {
                    Text("OK", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

/**
 * A picked link target that carries an ID: the "Use the …'s ID?" dialog lets the
 * user commit either [withId] (resilient) or [withPlain]. [subject] is `"file"`
 * or `"heading"` and drives the dialog copy.
 */
private data class LinkIdChoice(val withId: String, val withPlain: String, val subject: String)

/**
 * Char offset of the start of absolute doc line [targetLineIndex] within
 * [buffer], whose own line 0 is [bufferStartLine] in the full document (see
 * `OrgMutations.subtreeText`). Returns null for no target, the buffer's own
 * first line (the root heading), or an out-of-range line — all of which fall
 * back to the caller's default cursor placement.
 */
private fun charOffsetForLine(buffer: String, bufferStartLine: Int, targetLineIndex: Int?): Int? {
    val relativeLine = (targetLineIndex ?: return null) - bufferStartLine
    if (relativeLine <= 0) return null
    val lines = buffer.split('\n')
    if (relativeLine >= lines.size) return null
    var offset = 0
    for (i in 0 until relativeLine) offset += lines[i].length + 1
    return offset
}

@Composable
private fun StaleFileBanner(onOverwrite: () -> Unit, onReload: () -> Unit) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.amberSoft)
            .border(1.dp, c.amber, RoundedCornerShape(11.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "File changed on disk while editing",
            fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Overwrite",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            color = c.red,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onOverwrite)
                .padding(6.dp),
        )
        Text(
            "Reload",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            color = c.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onReload)
                .padding(6.dp),
        )
    }
}

