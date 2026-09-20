package com.rrajath.grove.ui.capture

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureContext
import com.rrajath.grove.capture.CaptureInserter
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.FilenamePattern
import com.rrajath.grove.capture.PlaceholderExpander
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.capture.TemplateKind
import com.rrajath.grove.capture.templateSlug
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.newOrgId
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.editor.AutoLinkSuggestionStrip
import com.rrajath.grove.ui.editor.AutoSaveTimestamp
import com.rrajath.grove.ui.editor.EditorToolbar
import com.rrajath.grove.ui.editor.MetadataSheet
import com.rrajath.grove.ui.editor.WordAtCursor
import com.rrajath.grove.ui.editor.filterAutoLinkSuggestions
import com.rrajath.grove.ui.editor.formatAutoLinkInsertion
import com.rrajath.grove.ui.editor.wordAtCursor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import com.rrajath.grove.ui.editor.orgInputTransformation
import com.rrajath.grove.ui.editor.OrgSyntaxHighlight
import com.rrajath.grove.ui.editor.applyEdit
import com.rrajath.grove.ui.editor.applyToolbarLink
import com.rrajath.grove.ui.editor.insertAtCursor
import com.rrajath.grove.ui.editor.wrapSelection
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.PlexSerif
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.priorityColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Whether a Roam capture is a brand-new node or a continuation of a file that
 * already exists (e.g. a second capture into today's daily note). Resolved
 * once from the template's fresh expansion, before the user types anything,
 * and held fixed for the rest of the capture session -- it must not flip
 * back and forth as a title-driven filename changes while typing.
 */
private sealed class RoamAppendState {
    data object Checking : RoamAppendState()
    data object New : RoamAppendState()
    data class ExistingFile(val path: String, val content: String) : RoamAppendState()
}

/**
 * Capture editor (design spec §8): prompts for `%^{…}` values, then a
 * pre-expanded mono editor with the cursor at `%cursor`. Save inserts into
 * the template's target file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureEditorScreen(
    templateId: String,
    onClose: () -> Unit,
    /**
     * Fired once the draft is persisted. [savedRoamFilePath] is the vault path
     * of the file just written, but only for a [TemplateKind.ROAM_NODE]
     * capture (null for a PLAIN entry) -- that's what the caller uses to
     * offer a "Visit file" snackbar action; a PLAIN capture inserts into an
     * existing notebook the user is presumably already looking at, so no
     * snackbar is warranted there.
     */
    onSaved: (savedRoamFilePath: String?) -> Unit,
    /** Settings § Notes: font-size lever for the editor field. App chrome is unaffected. */
    editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    /**
     * Settings § Roam Features (experimental): show file/heading link
     * suggestions while typing. Only ever shows for a [TemplateKind.ROAM_NODE]
     * capture, same as the whole-file editor.
     */
    showSuggestions: Boolean = false,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    // Deep links (launcher shortcuts, widget, or a hand-typed grove://capture/x
    // URL) pass the template's opaque id, but a manually-typed link is more
    // likely to use the visible name (e.g. "TODO") or the dash-separated slug
    // shown on the template editor's capture-link section, so fall back to a
    // case-insensitive name match, then a slug match, before giving up.
    val template = templates.firstOrNull { it.id == templateId }
        ?: templates.firstOrNull { it.name.equals(templateId, ignoreCase = true) }
        ?: templates.firstOrNull { templateSlug(it.name) == templateId.lowercase() }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.rrajath.grove.GroveApplication
    val keywords by app.keywords.collectAsStateWithLifecycle()

    LaunchedEffect(saveState) {
        val saved = saveState as? SaveState.Saved
        if (saved != null) {
            viewModel.resetSaveState()
            onSaved(saved.filePath.takeIf { template?.kind == TemplateKind.ROAM_NODE })
        }
    }

    // Only once templates have actually loaded (they start as an empty list
    // while the DataStore read is in flight) is a still-missing match a real
    // "no such template" rather than a load-in-progress false negative.
    val notFoundContext = LocalContext.current
    LaunchedEffect(template, templates) {
        if (template == null && templates.isNotEmpty()) {
            Toast.makeText(
                notFoundContext,
                "Couldn't find a capture template named \"$templateId\".",
                Toast.LENGTH_LONG,
            ).show()
            onClose()
        }
    }

    if (template == null) return

    val now = remember { LocalDateTime.now() }
    val prompts = remember(template) {
        val body = if (template.kind == TemplateKind.ROAM_NODE) template.newFileTemplate else template.template
        PlaceholderExpander.prompts(body)
    }
    var promptValues by remember(template) { mutableStateOf<Map<String, String>?>(null) }

    if (prompts.isNotEmpty() && promptValues == null) {
        PromptDialog(
            prompts = prompts,
            onCancel = onClose,
            onDone = { promptValues = it },
        )
        return
    }

    val context = remember(template, promptValues) {
        val share = app.pendingShare.value
        val body = if (template.kind == TemplateKind.ROAM_NODE) template.newFileTemplate else template.template
        // Only read the clipboard when the template actually uses %clipboard.
        // Android 13+ shows a system toast on every clipboard read, so reading
        // unconditionally would confuse users whose templates don't need it.
        val clipboardText =
            if (body.contains("%clipboard")) {
                // getClipEntry() has no real suspension point on Android (it's a
                // synchronous Binder call under the hood), so runBlocking here
                // just reads it inline instead of introducing an async gap that
                // could race with textState below reading a stale, clipboard-less
                // CaptureContext.
                runBlocking { clipboard.getClipEntry() }?.clipData?.let { data ->
                    if (data.itemCount > 0) data.getItemAt(0)?.text?.toString() else null
                } ?: ""
            } else {
                ""
            }
        CaptureContext(
            now = now,
            clipboard = clipboardText,
            sharedText = share?.text ?: "",
            sharedUrl = share?.url ?: "",
            promptValues = promptValues ?: emptyMap(),
            dateOnly = template.location is TargetLocation.DatetreeDate,
            // Generated once per capture, not regenerated on every expand() call.
            id = newOrgId(),
        )
    }
    // The share payload is one-shot: consumed by this capture.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { app.pendingShare.value = null }
    }
    val expanded = remember(template, context) {
        if (template.kind == TemplateKind.ROAM_NODE) {
            // The title is never prompted for: it expands blank with the
            // cursor on it, and the user types it straight into the draft.
            PlaceholderExpander.expand(template.newFileTemplate, context)
        } else {
            CaptureInserter.withHeadingStars(
                PlaceholderExpander.expand(template.template, context),
                template.location,
            )
        }
    }

    /** [text]'s properties-drawer + preamble head vs. everything after it --
     *  the same boundary CaptureViewModel.roamBody splits on when a Roam
     *  capture lands on an already-existing file. */
    fun roamBodySplit(text: String): Pair<String, String> {
        val doc = OrgParser.parse(text, keywords)
        val drawerEnd = OrgMutations.fileDrawerRange(doc)?.last ?: -1
        val prefaceEnd = OrgMutations.prefaceRange(doc)?.last ?: -1
        val headLineCount = maxOf(drawerEnd, prefaceEnd) + 1
        val lines = doc.lines
        return lines.take(headLineCount).joinToString("\n") to lines.drop(headLineCount).joinToString("\n")
    }

    fun resolvedRoamPathFor(text: String): String? {
        val title = FilenamePattern.titleFromDraft(text) ?: return null
        val slug = FilenamePattern.slugFromTitle(title)
        val stem = FilenamePattern.expand(template.filenamePattern, now, slug)
        return if (template.roamDirectory.isBlank()) "$stem.org" else "${template.roamDirectory}/$stem.org"
    }

    var roamAppendState by remember(expanded) {
        mutableStateOf<RoamAppendState>(
            if (template.kind == TemplateKind.ROAM_NODE) RoamAppendState.Checking else RoamAppendState.New,
        )
    }
    LaunchedEffect(expanded) {
        if (template.kind != TemplateKind.ROAM_NODE) return@LaunchedEffect
        val path = resolvedRoamPathFor(expanded.text)
        roamAppendState = if (path == null) {
            RoamAppendState.New
        } else {
            viewModel.loadExistingRoamContent(path)?.let { RoamAppendState.ExistingFile(path, it) } ?: RoamAppendState.New
        }
    }

    // For a continuation of an existing file, only the new body is editable
    // (the existing content renders read-only above it), so the field starts
    // out holding just the body half of the fresh expansion, cursor re-based
    // into it.
    val initialText = remember(expanded, roamAppendState) {
        (roamAppendState as? RoamAppendState.ExistingFile)?.let { roamBodySplit(expanded.text).second } ?: expanded.text
    }
    val textState = remember(expanded, roamAppendState) {
        val appendState = roamAppendState
        if (appendState is RoamAppendState.ExistingFile) {
            val (head, body) = roamBodySplit(expanded.text)
            val headOffset = if (head.isEmpty()) 0 else head.length + 1
            TextFieldState(body, TextRange((expanded.cursorOffset - headOffset).coerceIn(0, body.length)))
        } else {
            TextFieldState(expanded.text, TextRange(expanded.cursorOffset))
        }
    }
    // Snapshot-backed, so every keystroke recomposes the draft-dependent UI
    // (auto-save indicator, discard prompt) just as the old TextFieldValue did.
    // derivedStateOf so an unrelated recomposition (readMode, metadataOpen, …)
    // doesn't re-copy the whole buffer; only an actual text change does. Keyed
    // on textState itself: a Roam capture's Checking -> New/ExistingFile
    // transition recreates textState with a fresh TextFieldState (see its own
    // remember above), and an unkeyed derivedStateOf here would permanently
    // close over the original Checking-era instance -- silently orphaning
    // every keystroke into the field the user actually sees and edits.
    val draftText by remember(textState) { derivedStateOf { textState.text.toString() } }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(roamAppendState) {
        if (roamAppendState !is RoamAppendState.Checking) focusRequester.requestFocus()
    }
    val scrollState = rememberScrollState()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showEmptyHeadingAlert by remember { mutableStateOf(false) }
    var showEmptyTitleAlert by remember { mutableStateOf(false) }
    var metadataOpen by remember { mutableStateOf(false) }
    var readMode by remember { mutableStateOf(false) }
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()

    // Inline auto-link suggestion strip -- same mechanism as EditNoteScreen,
    // gated on the Roam capture flow only.
    val suggestionsActive = showSuggestions && template.kind == TemplateKind.ROAM_NODE
    LaunchedEffect(Unit) { if (suggestionsActive) viewModel.loadAutoLinkIndex() }
    val autoLinkIndex by viewModel.autoLinkIndex.collectAsStateWithLifecycle()
    var autoLinkTrigger by remember { mutableStateOf<WordAtCursor?>(null) }
    var expandedChipKeys by remember(autoLinkTrigger?.range) { mutableStateOf(emptySet<String>()) }
    val autoLinkSuggestions = remember(autoLinkTrigger?.text, autoLinkIndex) {
        val idx = autoLinkIndex
        val word = autoLinkTrigger?.text
        if (idx == null || word == null) emptyList() else filterAutoLinkSuggestions(idx, word)
    }
    // Keyed on textState too: a Roam capture's Checking -> New/ExistingFile
    // transition (below) recreates textState with a fresh TextFieldState, same
    // as draftText's derivedStateOf above. Keying on suggestionsActive alone
    // left this permanently watching the abandoned Checking-era instance,
    // so real keystrokes never reached it and the strip never had anything
    // to show.
    LaunchedEffect(suggestionsActive, textState) {
        if (!suggestionsActive) {
            autoLinkTrigger = null
            return@LaunchedEffect
        }
        snapshotFlow { textState.text.toString() to textState.selection }.collect { (text, selection) ->
            autoLinkTrigger = wordAtCursor(text, selection)?.takeIf { it.text.length >= 3 }
        }
    }

    // The draft is always a single heading (withHeadingStars above guarantees
    // it starts with a "* " line), so this is what the metadata sheet and the
    // read-mode preview both edit/render.
    val draftHeadline = remember(draftText, keywords) { OrgParser.parse(draftText, keywords).headlines.firstOrNull() }

    /** Metadata-sheet edits: parse the draft, apply an [OrgMutations] transform,
     *  write the result back into the field. No auto-archive path (unlike
     *  EditorViewModel.changeKeyword): the entry isn't saved anywhere yet, so
     *  marking it done has nothing to archive. */
    fun mutateDraft(block: (OrgDocument, OrgHeadline) -> String) {
        textState.applyEdit { tfv ->
            val doc = OrgParser.parse(tfv.text, keywords)
            val headline = doc.headlines.firstOrNull() ?: return@applyEdit null
            val newText = block(doc, headline)
            if (newText == tfv.text) null else TextFieldValue(newText, TextRange(tfv.selection.start.coerceAtMost(newText.length)))
        }
    }

    // Mirrors the note editor's auto-save indicator: a tappable save (floppy)
    // icon in the top bar, shown once the draft has been edited or saved at
    // least once. Green + tap-to-save-now while dirty; grey + tap-for-last-
    // saved-toast once clean, exactly like EditNoteScreen/EditRegionScreen.
    var lastAutoSavedAt by remember { mutableStateOf<LocalTime?>(null) }
    // Text as of the last auto-save, so dirty can be computed by comparison.
    var lastAutoSavedText by remember(expanded, roamAppendState) { mutableStateOf(initialText) }
    val dirty = draftText != lastAutoSavedText
    val toastContext = LocalContext.current

    /**
     * The Roam node's target file. A continuation of an already-existing file
     * keeps the path resolved once at capture start ([RoamAppendState]); a
     * brand-new node keeps recomputing from the draft's live `#+title:` line
     * (same `now` the rest of the draft's timestamps use, so a `%<...>`
     * filename pattern doesn't drift while the user is still typing). Null
     * when the title is blank/missing, or the existence check hasn't landed yet.
     */
    fun resolvedRoamPath(): String? = when (val appendState = roamAppendState) {
        is RoamAppendState.ExistingFile -> appendState.path
        RoamAppendState.Checking -> null
        RoamAppendState.New -> resolvedRoamPathFor(draftText)
    }

    /** Immediate autosave, used by the idle timer and by tapping the dirty save icon. */
    fun saveNow() {
        if (template.kind == TemplateKind.ROAM_NODE) {
            resolvedRoamPath()?.let { path ->
                viewModel.autosaveRoam(path, draftText, context)
                lastAutoSavedAt = LocalTime.now()
                lastAutoSavedText = draftText
            }
        } else if (!CaptureInserter.hasBlankHeading(draftText)) {
            viewModel.autosave(template, draftText, context)
            lastAutoSavedAt = LocalTime.now()
            lastAutoSavedText = draftText
        }
    }

    fun tryClose() {
        if (draftText != initialText) showDiscardDialog = true else onClose()
    }

    fun discard() {
        if (template.kind == TemplateKind.ROAM_NODE) {
            resolvedRoamPath()?.let { viewModel.discardRoamDraft(it) }
        } else {
            viewModel.discardDraft(template)
        }
        onClose()
    }

    // Idle auto-save: wait for a 5s pause in typing before persisting the
    // draft. A note with no heading yet (just the auto-inserted "* ") is
    // skipped rather than saved, the same blank-heading state that blocks
    // the explicit Save button in trySave() below, so autosave never writes
    // a heading-less entry the user hasn't confirmed.
    LaunchedEffect(draftText) {
        delay(5_000)
        if (dirty) saveNow()
    }

    fun trySave() {
        if (template.kind == TemplateKind.ROAM_NODE) {
            val resolvedPath = resolvedRoamPath()
            if (resolvedPath == null) {
                showEmptyTitleAlert = true
            } else {
                viewModel.saveRoam(resolvedPath, draftText, context)
            }
        } else if (CaptureInserter.hasBlankHeading(draftText)) {
            showEmptyHeadingAlert = true
        } else {
            viewModel.save(template, draftText, context)
        }
    }

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GroveTopBar(
                leading = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = ::tryClose)
                            .padding(12.dp),
                    ) {
                        Text("×", fontFamily = PlexSans, fontSize = 22.sp, color = c.ink)
                    }
                    if (dirty || lastAutoSavedAt != null) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = if (dirty) "Unsaved changes, tap to save" else "Saved",
                            tint = if (dirty) c.green else c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (dirty) {
                                        saveNow()
                                    } else {
                                        val formatted = lastAutoSavedAt?.let(AutoSaveTimestamp::format)
                                        Toast.makeText(
                                            toastContext,
                                            "The note auto saved at: $formatted",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                .padding(10.dp),
                        )
                    }
                },
                title = {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                    )
                },
                actions = {
                    IconGlyph("☰", onClick = { metadataOpen = true })
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = if (readMode) 0 else 1,
                        onSelect = { readMode = it == 0 },
                        modifier = Modifier.width(140.dp),
                    )
                },
            )
        },
    ) { padding ->
        // ime.getBottom > 0 tracks the live keyboard height, unlike isImeVisible's
        // visibility flag, which can get stuck true after a gesture-dismiss that
        // leaves the field focused.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // union (not sum) of nav-bar and ime bottom insets: the ime inset
                // already spans down to the screen edge when the keyboard is up,
                // so adding navigationBarsPadding on top double-counted it and
                // left a gap above the keyboard.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)),
        ) {
            if (template.kind == TemplateKind.PLAIN && template.location.isDatetree) {
                DatetreeBreadcrumb(template, now.toLocalDate())
            }
            (saveState as? SaveState.Failed)?.let { failed ->
                Text(
                    failed.message,
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.red,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (readMode) {
                    if (draftHeadline != null) {
                        DraftPreview(
                            doc = remember(draftText, keywords) { OrgParser.parse(draftText, keywords) },
                            headline = draftHeadline,
                            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                        )
                    }
                } else if (roamAppendState !is RoamAppendState.Checking) {
                    val existingContent = (roamAppendState as? RoamAppendState.ExistingFile)?.content
                    ContentFontScale(editModeFontSize) {
                        if (existingContent != null) {
                            // Continuing an already-existing file: its prior content
                            // renders read-only (selectable, greyed) above a divider,
                            // and only the new body below it is editable. Both scroll
                            // together as one column, unlike the plain field below.
                            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                                SelectionContainer {
                                    Text(
                                        existingContent,
                                        fontFamily = PlexMono, fontSize = 14.sp,
                                        lineHeight = 1.9.em, color = c.ink3,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, top = 20.dp, end = 20.dp),
                                    )
                                }
                                HorizontalDivider(
                                    color = c.line,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                                BasicTextField(
                                    state = textState,
                                    inputTransformation = remember(keywords) { orgInputTransformation(keywords) },
                                    outputTransformation = remember(c, keywords) { OrgSyntaxHighlight(c, keywords) },
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    lineLimits = TextFieldLineLimits.MultiLine(),
                                    textStyle = TextStyle(
                                        fontFamily = PlexMono, fontSize = 14.sp,
                                        lineHeight = 1.9.em, color = c.ink,
                                    ),
                                    cursorBrush = SolidColor(c.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, bottom = 80.dp)
                                        .testTag("capture_body_field")
                                        .focusRequester(focusRequester),
                                )
                            }
                        } else {
                            // The field owns its own vertical scrolling (rather than
                            // being wrapped in Modifier.verticalScroll): that is what
                            // lets Compose auto-scroll while a selection handle is
                            // dragged past the top or bottom edge, and keeps the cursor
                            // visible when the keyboard shrinks the viewport.
                            BasicTextField(
                                state = textState,
                                inputTransformation = remember(keywords) { orgInputTransformation(keywords) },
                                outputTransformation = remember(c, keywords) { OrgSyntaxHighlight(c, keywords) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                lineLimits = TextFieldLineLimits.MultiLine(),
                                textStyle = TextStyle(
                                    fontFamily = PlexMono, fontSize = 14.sp,
                                    lineHeight = 1.9.em, color = c.ink,
                                ),
                                cursorBrush = SolidColor(c.accent),
                                scrollState = scrollState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 80.dp)
                                    .testTag("capture_body_field")
                                    .focusRequester(focusRequester),
                            )
                        }
                    }
                    if (autoLinkSuggestions.isNotEmpty()) {
                        AutoLinkSuggestionStrip(
                            suggestions = autoLinkSuggestions,
                            expandedKeys = expandedChipKeys,
                            onToggleExpand = { key -> expandedChipKeys = expandedChipKeys + key },
                            onPick = { suggestion ->
                                val range = autoLinkTrigger?.range ?: return@AutoLinkSuggestionStrip
                                val linkText = formatAutoLinkInsertion(suggestion)
                                textState.edit {
                                    replace(range.start, range.end, linkText)
                                    selection = TextRange(range.start + linkText.length)
                                }
                                autoLinkTrigger = null
                            },
                            // End-padded clear of the Save pill's own 16dp gutter +
                            // its widest ("Saving…") width, so the scrollable strip
                            // stops short of the pill instead of running chips
                            // behind it.
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 100.dp, bottom = 16.dp),
                        )
                    }
                }
                // Save floats bottom-right: above the keyboard while it's up
                // (the column is ime-padded), at the screen's bottom otherwise.
                // Stays available in Read mode too, so a metadata-only capture
                // (state/dates/tags set from the sheet, no further typing) can be
                // saved without switching back to Edit.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 14.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.accent)
                        .clickable(enabled = saveState !is SaveState.Saving && roamAppendState !is RoamAppendState.Checking) {
                            trySave()
                        }
                        .testTag("capture_save")
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        if (saveState is SaveState.Saving) "Saving…" else "Save",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = c.accentInk,
                    )
                }
            }
            if (!readMode && imeVisible) EditorToolbar(
                onWrap = { marker -> textState.applyEdit { wrapSelection(it, marker) } },
                onInsert = { snippet -> textState.applyEdit { insertAtCursor(it, snippet) } },
                onLink = { textState.applyToolbarLink(clipboard) },
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
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
                    "Your changes will be lost.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    trySave()
                }) {
                    Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false; discard() }) {
                    Text("Discard", color = c.red)
                }
            },
        )
    }

    if (showEmptyHeadingAlert) {
        AlertDialog(
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
                TextButton(onClick = { showEmptyHeadingAlert = false }) {
                    Text("OK", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showEmptyTitleAlert) {
        AlertDialog(
            onDismissRequest = { showEmptyTitleAlert = false },
            containerColor = c.surface,
            title = {
                Text(
                    "Add a title",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, color = c.ink,
                )
            },
            text = {
                Text(
                    "Please give this note a title before saving.",
                    fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyTitleAlert = false }) {
                    Text("OK", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (metadataOpen) {
        MetadataSheet(
            headline = draftHeadline,
            keywords = keywords,
            allTags = allTags,
            onChangeKeyword = { kw ->
                mutateDraft { d, h -> OrgMutations.changeKeyword(d, h, kw, d.keywords, LocalDateTime.now()) }
            },
            onSetPriority = { p -> mutateDraft { d, h -> OrgMutations.setPriority(d, h, p) } },
            onSetTags = { tags -> mutateDraft { d, h -> OrgMutations.setTags(d, h, tags) } },
            onSetPlanningDates = { sched, dead, active ->
                mutateDraft { d, h -> OrgMutations.setPlanningAndActiveTimestamps(d, h, sched, dead, active) }
            },
            onAddNote = { note ->
                val stamp = LocalDateTime.now().let {
                    OrgTimestamp(it.toLocalDate(), time = it.toLocalTime().withSecond(0).withNano(0), active = false)
                }
                mutateDraft { d, h -> OrgMutations.appendLogbookNote(d, h, note.trim(), stamp) }
            },
            onGenerateId = {
                mutateDraft { d, h -> OrgMutations.upsertProperty(d, h, "ID", newOrgId()) }
            },
            onRefile = {},
            showRefile = false,
            onDismiss = { metadataOpen = false },
        )
    }
}

/**
 * Read mode's inline preview (design spec §8 hamburger/read-edit toggle): a
 * lighter render of the draft than [com.rrajath.grove.ui.screens.ReadNoteScreen]'s
 * NoteContent, since a capture draft has no file/vault identity yet to
 * navigate from (links, checkbox-toggle-writes-to-disk, refile all assume a
 * saved note). Body lines get inline org markup via [annotateOrgInline]; block
 * structure (lists, tables, code blocks) renders as plain lines.
 */
@Composable
private fun DraftPreview(doc: OrgDocument, headline: OrgHeadline, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val tags = doc.inheritedTags(headline)
        if (tags.isNotEmpty()) {
            Row {
                tags.forEach { tag ->
                    Pill(tag, fg = c.accent, bg = c.accentSoft, outline = true)
                    Spacer(Modifier.width(7.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            headline.keyword?.let { kw ->
                val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft else c.amber to c.amberSoft
                Pill(kw, fg = fg, bg = bg)
                Spacer(Modifier.width(8.dp))
            }
            headline.priority?.let { p ->
                Text(
                    "[#$p]", fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = c.priorityColor(p),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            annotateOrgInline(headline.title.ifBlank { "(no heading yet)" }, c),
            fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, color = if (headline.title.isBlank()) c.ink3 else c.ink, lineHeight = 1.3.em,
        )
        if (headline.planning.scheduled != null || headline.planning.deadline != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                headline.planning.scheduled?.let {
                    Pill("SCHEDULED " + it.formatHuman(), fg = c.blue, bg = c.blueSoft)
                }
                headline.planning.deadline?.let {
                    Pill("DEADLINE " + it.formatHuman(), fg = c.red, bg = c.redSoft)
                }
            }
        }
        val body = doc.bodyOf(headline)
        if (body.any { it.isNotBlank() }) {
            Spacer(Modifier.height(16.dp))
            body.forEach { line ->
                if (line.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        annotateOrgInline(line, c),
                        fontFamily = PlexSans, fontSize = 14.sp, color = c.ink, lineHeight = 1.5.em,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DatetreeBreadcrumb(template: CaptureTemplate, today: LocalDate) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.line)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The  t breadcrumb shares the row with the trailing pill; only the file
        // name may shrink (ellipsized), so nothing ever wraps vertically.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                template.targetFile,
                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp, color = c.accent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                " › ${CaptureInserter.yearTitle(today)} › ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} › ",
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink2,
                maxLines = 1,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.accentSoft)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    "${OrgTimestamp.dayAbbrev(today)} ${today.dayOfMonth}",
                    fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Pill("auto-created", fg = c.green, bg = c.greenSoft)
    }
}

@Composable
private fun PromptDialog(
    prompts: List<String>,
    onCancel: () -> Unit,
    onDone: (Map<String, String>) -> Unit,
) {
    val c = MaterialTheme.grove
    var values by remember { mutableStateOf(prompts.associateWith { "" }) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.surface,
        title = {
            Text(
                prompts.singleOrNull() ?: "Fill in",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink,
            )
        },
        text = {
            Column {
                prompts.forEach { prompt ->
                    if (prompts.size > 1) {
                        Text(prompt, fontFamily = PlexSans, fontSize = 13.sp, color = c.ink2)
                    }
                    OutlinedTextField(
                        value = values[prompt].orEmpty(),
                        onValueChange = { values = values + (prompt to it) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexSans, color = c.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(values) }) {
                Text("Continue", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = c.ink2) }
        },
    )
}
