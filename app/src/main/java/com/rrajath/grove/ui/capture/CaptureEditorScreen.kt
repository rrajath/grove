package com.rrajath.grove.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.rrajath.grove.capture.CaptureContext
import com.rrajath.grove.capture.CaptureInserter
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.PlaceholderExpander
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.autoScrollWhileDragging
import com.rrajath.grove.ui.editor.EditorToolbar
import kotlinx.coroutines.delay
import com.rrajath.grove.ui.editor.OrgVisualTransformation
import com.rrajath.grove.ui.editor.insertAtCursor
import com.rrajath.grove.ui.editor.insertLinkTemplate
import com.rrajath.grove.ui.editor.wrapSelection
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Capture editor (design spec §8): prompts for `%^{…}` values, then a
 * pre-expanded mono editor with the cursor at `%cursor`. Save inserts into
 * the template's target file.
 */
@Composable
fun CaptureEditorScreen(
    templateId: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val templates by viewModel.templates.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val template = templates.firstOrNull { it.id == templateId }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.rrajath.grove.GroveApplication
    val keywords by app.keywords.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            viewModel.resetSaveState()
            onSaved()
        }
    }

    if (template == null) return

    val now = remember { LocalDateTime.now() }
    val prompts = remember(template) { PlaceholderExpander.prompts(template.template) }
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
        // Only read the clipboard when the template actually uses %clipboard.
        // Android 13+ shows a system toast on every clipboard read, so reading
        // unconditionally would confuse users whose templates don't need it.
        val clipboardText =
            if (template.template.contains("%clipboard")) clipboard.getText()?.text ?: "" else ""
        CaptureContext(
            now = now,
            clipboard = clipboardText,
            sharedText = share?.text ?: "",
            sharedUrl = share?.url ?: "",
            promptValues = promptValues ?: emptyMap(),
            dateOnly = template.location is TargetLocation.DatetreeDate,
        )
    }
    // The share payload is one-shot: consumed by this capture.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { app.pendingShare.value = null }
    }
    val expanded = remember(template, context) {
        CaptureInserter.withHeadingStars(
            PlaceholderExpander.expand(template.template, context),
            template.location,
        )
    }
    val initialText = remember(expanded) { expanded.text }
    var value by remember(expanded) {
        mutableStateOf(TextFieldValue(expanded.text, TextRange(expanded.cursorOffset)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showEmptyHeadingAlert by remember { mutableStateOf(false) }

    fun tryClose() {
        if (value.text != initialText) showDiscardDialog = true else onClose()
    }

    fun discard() {
        viewModel.discardDraft(template)
        onClose()
    }

    fun applyEdit(newValue: TextFieldValue) {
        value = newValue
    }

    // Idle auto-save: wait for a 5s pause in typing before persisting the
    // draft. A note with no heading yet (just the auto-inserted "* ") is
    // skipped rather than saved — the same blank-heading state that blocks
    // the explicit Save button in trySave() below, so autosave never writes
    // a heading-less entry the user hasn't confirmed.
    LaunchedEffect(value.text) {
        delay(5_000)
        if (value.text != initialText && !CaptureInserter.hasBlankHeading(value.text)) {
            viewModel.autosave(template, value.text, context)
        }
    }

    fun trySave() {
        if (CaptureInserter.hasBlankHeading(value.text)) {
            showEmptyHeadingAlert = true
        } else {
            viewModel.save(template, value.text, context)
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
                },
                title = {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                    )
                },
                actions = {},
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            if (template.location.isDatetree) {
                DatetreeBreadcrumb(template, now.toLocalDate())
            }
            (saveState as? SaveState.Failed)?.let { failed ->
                Text(
                    failed.message,
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.red,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().autoScrollWhileDragging(scrollState)) {
                val density = LocalDensity.current
                val viewportHeightPx = remember(maxHeight, density) {
                    with(density) { maxHeight.toPx() }.toInt()
                }
                val editorPaddingPx = remember(density) {
                    with(density) { 20.dp.toPx() }.toInt()
                }

                LaunchedEffect(value.selection, textLayoutResult, viewportHeightPx) {
                    val layout = textLayoutResult ?: return@LaunchedEffect
                    if (value.text.isEmpty()) return@LaunchedEffect
                    // Track selection.end so dragging a selection also scrolls.
                    val offset = value.selection.end.coerceIn(0, value.text.length)
                    val rect = layout.getCursorRect(offset)
                    val cursorTop = editorPaddingPx + rect.top.toInt()
                    val cursorBottom = editorPaddingPx + rect.bottom.toInt()
                    val buffer = 56 // px breathing room above/below cursor

                    when {
                        cursorBottom > scrollState.value + viewportHeightPx - buffer ->
                            scrollState.animateScrollTo(cursorBottom - viewportHeightPx + buffer)
                        cursorTop < scrollState.value + buffer ->
                            scrollState.animateScrollTo(maxOf(0, cursorTop - buffer))
                    }
                }

                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
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
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = TextStyle(
                        fontFamily = PlexMono, fontSize = 14.sp,
                        lineHeight = 1.9.em, color = c.ink,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = remember(c, keywords) { OrgVisualTransformation(c, keywords) },
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 80.dp)
                        .focusRequester(focusRequester),
                )
                // Save floats bottom-right: above the keyboard while it's up
                // (the column is ime-padded), at the screen's bottom otherwise.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 14.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.accent)
                        .clickable(enabled = saveState !is SaveState.Saving) {
                            trySave()
                        }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        if (saveState is SaveState.Saving) "Saving…" else "Save",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = c.accentInk,
                    )
                }
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
        // The breadcrumb shares the row with the trailing pill; only the file
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
