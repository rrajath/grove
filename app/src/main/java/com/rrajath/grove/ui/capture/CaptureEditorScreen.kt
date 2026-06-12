package com.rrajath.grove.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureContext
import com.rrajath.grove.capture.CaptureInserter
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.PlaceholderExpander
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
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
        CaptureContext(
            now = now,
            clipboard = clipboard.getText()?.text ?: "",
            promptValues = promptValues ?: emptyMap(),
        )
    }
    val expanded = remember(template, context) {
        PlaceholderExpander.expand(template.template, context)
    }
    var value by remember(expanded) {
        mutableStateOf(TextFieldValue(expanded.text, TextRange(expanded.cursorOffset)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClose)
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
                actions = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.accent)
                            .clickable(enabled = saveState !is SaveState.Saving) {
                                viewModel.save(template, value.text, context)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            if (saveState is SaveState.Saving) "Saving…" else "Save",
                            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = c.accentInk,
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
                .imePadding(),
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
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(
                    fontFamily = PlexMono, fontSize = 14.sp,
                    lineHeight = 1.9.em, color = c.ink,
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .focusRequester(focusRequester),
            )
            ToolbarRow(
                onInsert = { snippet ->
                    val sel = value.selection.start
                    val newText = value.text.substring(0, sel) + snippet + value.text.substring(sel)
                    value = TextFieldValue(newText, TextRange(sel + snippet.length))
                },
                now = now,
            )
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
        Text("inserts under", fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3)
        Spacer(Modifier.width(7.dp))
        Text(
            template.targetFile,
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp, color = c.accent,
        )
        Text(
            " › ${CaptureInserter.yearTitle(today)} › ${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} › ",
            fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink2,
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
            )
        }
        Spacer(Modifier.weight(1f))
        Pill("auto-created", fg = c.green, bg = c.greenSoft)
    }
}

@Composable
private fun ToolbarRow(onInsert: (String) -> Unit, now: LocalDateTime) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.line)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton("B", FontWeight.Bold, c.ink) { onInsert("*bold*") }
        ToolbarButton("I", FontWeight.Normal, c.ink, italic = true) { onInsert("/italic/") }
        ToolbarButton("◷", FontWeight.Normal, c.synTs) {
            onInsert(OrgTimestamp(now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0)).format())
        }
        Spacer(Modifier.weight(1f))
        Text(
            "%placeholders expanded",
            fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink2,
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    weight: FontWeight,
    color: androidx.compose.ui.graphics.Color,
    italic: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontFamily = PlexMono, fontWeight = weight, fontSize = 14.sp, color = color,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
        )
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
