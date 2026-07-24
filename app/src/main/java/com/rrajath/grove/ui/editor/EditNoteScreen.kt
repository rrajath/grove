package com.rrajath.grove.ui.editor

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
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
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef
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
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var metadataOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showEmptyHeadingAlert by remember { mutableStateOf(false) }
    // Timestamp of the most recent auto-save, shown as a tappable check mark
    // in the top bar once the note has been saved at least once.
    var lastAutoSavedAt by remember { mutableStateOf<LocalTime?>(null) }
    // Blinks the check mark twice on each auto-save instead of a toast; tapping
    // the check mark still shows the "saved at" toast on demand.
    val checkAlpha = remember { Animatable(1f) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val focusRequester = remember { FocusRequester() }
    // Tracks the buffer the text field's `value` already reflects, so the
    // external-mutation sync effect below doesn't clobber the deliberate
    // divergence (appended blank body line) set for a fresh FAB-created note.
    var syncedBuffer by remember { mutableStateOf<String?>(null) }

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
            state.dirty -> confirmLeave = true
            // New note with still-blank heading: silently remove it from file.
            isNewNote && viewModel.isCurrentHeadingBlank() ->
                viewModel.deleteSubtree(onDeleted = onBack)
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler { leave() }

    LaunchedEffect(noteRef) { viewModel.load(noteRef) }
    LaunchedEffect(state.loading) {
        if (!state.loading && state.error == null) {
            if (isNewNote) {
                // FAB-created heading has no body yet (just the "* " line,
                // plus an optional :PROPERTIES: drawer): append a blank body
                // line and park the cursor there so the keyboard opens ready
                // for content, instead of on the heading line.
                val bodyText = state.buffer + "\n"
                value = TextFieldValue(bodyText, TextRange(bodyText.length))
            } else {
                val cursor = state.buffer.length.coerceAtMost(
                    state.buffer.indexOf('\n').let { if (it == -1) state.buffer.length else it },
                )
                value = TextFieldValue(state.buffer, TextRange(cursor))
            }
            syncedBuffer = state.buffer
            if (isNewNote) focusRequester.requestFocus()
        }
    }
    // Metadata-sheet mutations rewrite the buffer outside the text field.
    LaunchedEffect(state.buffer) {
        if (state.buffer != syncedBuffer && state.buffer != value.text) {
            value = TextFieldValue(state.buffer, TextRange(value.selection.start.coerceAtMost(state.buffer.length)))
        }
        syncedBuffer = state.buffer
    }
    val transformation = remember(c, state.keywords) { OrgVisualTransformation(c, state.keywords) }

    fun applyEdit(newValue: TextFieldValue) {
        value = newValue
        viewModel.onBufferChange(newValue.text)
    }

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

    // Blink the check mark twice in quick succession on each auto-save.
    LaunchedEffect(lastAutoSavedAt) {
        if (lastAutoSavedAt == null) return@LaunchedEffect
        repeat(2) {
            checkAlpha.animateTo(0.15f, tween(120))
            checkAlpha.animateTo(1f, tween(120))
        }
    }

    fun onTextChange(newValue: TextFieldValue) {
        val continued = LineEditing.continueListOnEnter(
            value.text, newValue.text, newValue.selection.start,
        )
        val effective =
            if (continued != null) TextFieldValue(continued.text, TextRange(continued.cursor))
            else newValue
        val capitalized = LineEditing.capitalizeHeadingOnType(
            value.text, effective.text, effective.selection.start,
        )
        applyEdit(
            if (capitalized != null) TextFieldValue(capitalized.text, TextRange(capitalized.cursor))
            else effective
        )
    }

    val scrollState = rememberScrollState()
    // Two lines of editor text (13.5sp font * 1.85 line height), so the jump
    // buttons don't flash on every keystroke as typing nudges the view.
    val scrollButtonThresholdPx = with(LocalDensity.current) { (13.5f * 1.85f * 2).sp.toPx() }

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
                    lastAutoSavedAt?.let { savedAt ->
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Auto saved",
                            // Green while the buffer matches what's on disk (and
                            // blinks right after a save); grey again the moment a
                            // keystroke makes it dirty, until the next auto-save.
                            tint = if (state.dirty) c.ink3 else c.green,
                            modifier = Modifier
                                .alpha(checkAlpha.value)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val formatted = AutoSaveTimestamp.format(savedAt)
                                    Toast.makeText(
                                        context,
                                        "The note was auto saved at: $formatted",
                                        Toast.LENGTH_SHORT,
                                    ).show()
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
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val viewportHeightPx = remember(maxHeight, density) {
                        with(density) { maxHeight.toPx() }.toInt()
                    }
                    val editorPaddingPx = remember(density) {
                        with(density) { 18.dp.toPx() }.toInt()
                    }

                    // Keep the cursor in view when it's near the bottom edge and
                    // the keyboard is covering it — the keyboard shrinks the
                    // viewport but doesn't itself trigger a scroll.
                    LaunchedEffect(value.selection, textLayoutResult, viewportHeightPx) {
                        val layout = textLayoutResult ?: return@LaunchedEffect
                        if (value.text.isEmpty()) return@LaunchedEffect
                        // textLayoutResult can lag one frame behind `value` (e.g. rapid
                        // programmatic input, or the FAB new-note cursor jump) — clamp to
                        // what the layout was actually computed against, not the live text.
                        val offset = value.selection.end.coerceIn(0, layout.layoutInput.text.length)
                        val rect = layout.getCursorRect(offset)
                        val cursorTop = editorPaddingPx + rect.top.toInt()
                        val cursorBottom = editorPaddingPx + rect.bottom.toInt()
                        val buffer = 56
                        when {
                            cursorBottom > scrollState.value + viewportHeightPx - buffer ->
                                scrollState.animateScrollTo(cursorBottom - viewportHeightPx + buffer)
                            cursorTop < scrollState.value + buffer ->
                                scrollState.animateScrollTo(maxOf(0, cursorTop - buffer))
                        }
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = ::onTextChange,
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        textStyle = TextStyle(
                            fontFamily = PlexMono, fontSize = 13.5.sp,
                            lineHeight = 1.85.em, color = c.ink,
                        ),
                        cursorBrush = SolidColor(c.accent),
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
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
            }
            EditorToolbar(
                onWrap = { marker -> applyEdit(wrapSelection(value, marker)) },
                onInsert = { snippet -> applyEdit(insertAtCursor(value, snippet)) },
                onLink = { applyEdit(insertLinkTemplate(value)) },
                onHeading = {
                    val edit = LineEditing.insertHeadingStar(value.text, value.selection.start)
                    applyEdit(TextFieldValue(edit.text, TextRange(edit.cursor)))
                },
                onIndent = { delta ->
                    LineEditing.changeListIndent(value.text, value.selection.start, delta)?.let {
                        applyEdit(TextFieldValue(it.text, TextRange(it.cursor)))
                    }
                },
            )
        }
    }

    if (metadataOpen) {
        MetadataSheet(
            viewModel = viewModel,
            onDismiss = { metadataOpen = false },
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
                    if (isNewNote && viewModel.isCurrentHeadingBlank()) {
                        viewModel.deleteSubtree(onDeleted = onBack)
                    } else {
                        onBack()
                    }
                }) { Text("Discard", color = c.red) }
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

