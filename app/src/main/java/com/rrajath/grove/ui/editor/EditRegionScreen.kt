package com.rrajath.grove.ui.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Raw org editor scoped to the file's preface: its leading `#+KEY:` lines. Opened by
 * double-tapping the outline's PREFACE section. Covers only that keyword run: the
 * property drawer above it and the intro below it have their own editors.
 */
@Composable
fun EditPrefaceScreen(
    fileName: String,
    onBack: () -> Unit,
    editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) = EditRegionScreen(
    fileName, EditRegion.PREFACE, noteId = null, onBack = onBack,
    editModeFontSize = editModeFontSize, viewModel = viewModel,
)

/**
 * Raw org editor scoped to the file's intro: the heading-less content between the
 * preface and the first `*` heading. Opened by double-tapping that content in read
 * mode. Covers only the content itself, not the `#+KEY:` lines or the property
 * drawer above it.
 */
@Composable
fun EditIntroScreen(
    fileName: String,
    onBack: () -> Unit,
    editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) = EditRegionScreen(
    fileName, EditRegion.INTRO, noteId = null, onBack = onBack,
    editModeFontSize = editModeFontSize, viewModel = viewModel,
)

/** One-word label for [region], used in this screen's title bar, save toast and leave dialog. */
private fun regionLabel(region: EditRegion): String = when (region) {
    EditRegion.INTRO -> "Intro"
    EditRegion.PREFACE -> "Preface"
    EditRegion.FILE_PROPERTIES, EditRegion.HEADING_PROPERTIES -> "Properties"
    EditRegion.HEADING_LOGBOOK -> "Logbook"
    EditRegion.BLOCK -> "Block"
}

/** Title-cased block kind pulled from the buffer's `#+BEGIN_x` line (e.g. "Quote", "Src"), or "Block". */
private fun blockLabelFromBuffer(buffer: String): String {
    val begin = buffer.lineSequence().firstOrNull { Regex("""^\s*#\+(?i:BEGIN_)\S+""").containsMatchIn(it) }
        ?: return "Block"
    val kind = Regex("""^\s*#\+(?i:BEGIN_)(\S+)""").find(begin)?.groupValues?.get(1) ?: return "Block"
    return kind.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Raw org editor scoped to one region of a file: its preface, its intro, a file-level
 * or per-heading `:PROPERTIES:` drawer, or a `:LOGBOOK:` drawer. Opened by double-tapping
 * the matching section (see [doubleTapToEdit] usage in `CollapsibleKvSection` /
 * `CollapsibleLogSection`). Deliberately a smaller sibling of [EditNoteScreen]: no
 * Read/Edit toggle, metadata sheet, or blank-heading validation, just the same
 * syntax-highlighted text field, dirty/save affordance, idle auto-save, and stale-file
 * handling.
 */

@Composable
fun EditRegionScreen(
    fileName: String,
    region: EditRegion,
    noteId: String?,
    onBack: () -> Unit,
    /** Absolute doc line of the tapped `#+BEGIN` (or its first affiliated line); [EditRegion.BLOCK] only. */
    blockLine: Int = -1,
    /** Settings § Notes: font-size lever for the editor field. App chrome is unaffected. */
    editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val label = if (region == EditRegion.BLOCK) blockLabelFromBuffer(state.buffer) else regionLabel(region)
    val textState = rememberTextFieldState()
    var confirmLeave by remember { mutableStateOf(false) }
    var lastAutoSavedAt by remember { mutableStateOf<LocalTime?>(null) }
    val focusRequester = remember { FocusRequester() }
    // False until the region has been loaded into the text field: the field's
    // pre-load contents are not the user's edits and must not be reported.
    var fieldLoaded by remember { mutableStateOf(false) }
    // Text last written into the field programmatically (initial load only, here).
    // The snapshotFlow below echoes every write straight back; that echo must not
    // be reported as a user edit, which would wrongly mark a freshly opened
    // region dirty.
    var echoToSkip by remember { mutableStateOf<String?>(null) }

    fun setText(text: String, cursor: TextRange) {
        echoToSkip = text
        textState.edit {
            replace(0, length, text)
            selection = cursor
        }
    }

    fun leave() {
        if (state.dirty) confirmLeave = true else onBack()
    }
    androidx.activity.compose.BackHandler { leave() }

    LaunchedEffect(fileName, noteId, region) { viewModel.loadRegion(fileName, noteId, region, blockLine) }
    LaunchedEffect(state.loading) {
        if (!state.loading && state.error == null) {
            setText(state.buffer, TextRange(0))
            fieldLoaded = true
        }
    }
    // Report the user's own edits back to the view model. The programmatic write
    // above is filtered out, so only real typing marks the region dirty.
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
    // still has unsaved changes, matching EditNoteScreen's convention.
    LaunchedEffect(state.buffer) {
        delay(5_000)
        if (state.dirty) {
            viewModel.save {
                lastAutoSavedAt = LocalTime.now()
            }
        }
    }

    val scrollState = rememberScrollState()
    val scrollButtonThresholdPx = with(LocalDensity.current) { (13.5f * 1.85f * 5).sp.toPx() }

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GroveTopBar(
                leading = {
                    IconGlyph("←", onClick = ::leave)
                    if (state.dirty || lastAutoSavedAt != null) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = if (state.dirty) "Unsaved changes, tap to save" else "Saved",
                            tint = if (state.dirty) c.green else c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (state.dirty) {
                                        viewModel.save { lastAutoSavedAt = LocalTime.now() }
                                    } else {
                                        val message = lastAutoSavedAt?.let {
                                            "$label last saved at: ${AutoSaveTimestamp.format(it)}"
                                        } ?: "This ${label.lowercase()} hasn't been saved yet"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(10.dp),
                        )
                    }
                },
                title = {
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            label,
                            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, color = c.ink,
                        )
                        Text(
                            fileName,
                            fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
                    onReload = { viewModel.dismissStale(); viewModel.loadRegion(fileName, noteId, region, blockLine) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
            }
            EditorToolbar(
                onWrap = { marker -> textState.applyEdit { wrapSelection(it, marker) } },
                onInsert = { snippet -> textState.applyEdit { insertAtCursor(it, snippet) } },
                onLink = { textState.applyEdit(::insertLinkTemplate) },
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
            )
        }
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
                    "This ${label.lowercase()} has unsaved changes.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    viewModel.save(onSaved = onBack)
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
