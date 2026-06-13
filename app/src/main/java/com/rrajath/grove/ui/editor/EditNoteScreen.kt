package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDateTime

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
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var metadataOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    fun leave() {
        if (state.dirty) confirmLeave = true else onBack()
    }
    androidx.activity.compose.BackHandler { leave() }

    LaunchedEffect(noteRef) { viewModel.load(noteRef) }
    LaunchedEffect(state.loading) {
        if (!state.loading && state.error == null) {
            value = TextFieldValue(state.buffer, TextRange(state.buffer.length.coerceAtMost(state.buffer.indexOf('\n').let { if (it == -1) state.buffer.length else it })))
        }
    }
    // Metadata-sheet mutations rewrite the buffer outside the text field.
    LaunchedEffect(state.buffer) {
        if (state.buffer != value.text) {
            value = TextFieldValue(state.buffer, TextRange(value.selection.start.coerceAtMost(state.buffer.length)))
        }
    }
    val transformation = remember(c, state.keywords) { OrgVisualTransformation(c, state.keywords) }

    fun applyEdit(newValue: TextFieldValue) {
        value = newValue
        viewModel.onBufferChange(newValue.text)
    }

    fun onTextChange(newValue: TextFieldValue) {
        val continued = LineEditing.continueListOnEnter(
            value.text, newValue.text, newValue.selection.start,
        )
        applyEdit(
            if (continued != null) TextFieldValue(continued.text, TextRange(continued.cursor))
            else newValue
        )
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = {
                    IconGlyph("←", onClick = ::leave)
                },
                title = {},
                actions = {
                    IconGlyph("☰", onClick = { metadataOpen = true })
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = 1,
                        onSelect = { if (it == 0) viewModel.save(onSaved = onSwitchToRead) },
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
                // Without consuming the Scaffold insets, the nav-bar padding and
                // imePadding stack up, leaving a gap between toolbar and keyboard.
                .consumeWindowInsets(padding)
                .imePadding(),
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
            BasicTextField(
                value = value,
                onValueChange = ::onTextChange,
                visualTransformation = transformation,
                textStyle = TextStyle(
                    fontFamily = PlexMono, fontSize = 13.5.sp,
                    lineHeight = 1.85.em, color = c.ink,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            )
            EditorToolbar(
                onWrap = { marker -> applyEdit(wrapSelection(value, marker)) },
                onInsert = { snippet -> applyEdit(insertAtCursor(value, snippet)) },
                onHeading = {
                    val edit = LineEditing.insertHeadingStar(value.text, value.selection.start)
                    applyEdit(TextFieldValue(edit.text, TextRange(edit.cursor)))
                },
                onIndent = { delta ->
                    LineEditing.changeListIndent(value.text, value.selection.start, delta)?.let {
                        applyEdit(TextFieldValue(it.text, TextRange(it.cursor)))
                    }
                },
                onDismissKeyboard = { keyboard?.hide() },
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

@Composable
private fun EditorToolbar(
    onWrap: (Char) -> Unit,
    onInsert: (String) -> Unit,
    onHeading: () -> Unit,
    onIndent: (Int) -> Unit,
    onDismissKeyboard: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.line)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("B", c.ink, bold = true) { onWrap('*') }
        ToolButton("I", c.ink, italic = true) { onWrap('/') }
        ToolButton("U", c.ink, underline = true) { onWrap('_') }
        ToolButton("</>", c.ink) { onWrap('~') }
        Box(Modifier.width(1.dp).padding(vertical = 4.dp).background(c.line))
        ToolButton("[[]]", c.synLink) { onInsert("[[][]]") }
        ToolButton("◷", c.synTs) {
            val now = LocalDateTime.now()
            onInsert(OrgTimestamp(now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0)).format())
        }
        ToolButton("*", c.synStar, bold = true) { onHeading() }
        // List indent: « promotes a sub-list item, » demotes into a sub-list.
        ToolButton("«", c.ink) { onIndent(-1) }
        ToolButton("»", c.ink) { onIndent(+1) }
        Spacer(Modifier.weight(1f))
        ToolButton("⌄", c.ink2) { onDismissKeyboard() }
    }
}

@Composable
private fun ToolButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontFamily = PlexMono,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
            fontSize = 17.sp,
            color = color,
        )
    }
}

/** Wrap the selection in emphasis markers, or insert a pair at the cursor. */
internal fun wrapSelection(value: TextFieldValue, marker: Char): TextFieldValue {
    val sel = value.selection
    val text = value.text
    return if (sel.collapsed) {
        val insert = "$marker$marker"
        TextFieldValue(
            text.substring(0, sel.start) + insert + text.substring(sel.start),
            TextRange(sel.start + 1),
        )
    } else {
        val selected = text.substring(sel.min, sel.max)
        TextFieldValue(
            text.substring(0, sel.min) + marker + selected + marker + text.substring(sel.max),
            TextRange(sel.max + 2),
        )
    }
}

internal fun insertAtCursor(value: TextFieldValue, snippet: String): TextFieldValue {
    val at = value.selection.start
    return TextFieldValue(
        value.text.substring(0, at) + snippet + value.text.substring(at),
        TextRange(at + snippet.length),
    )
}
