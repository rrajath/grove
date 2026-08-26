package com.rrajath.grove.ui.capture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.FilenameValidation
import com.rrajath.grove.capture.PlaceholderExpander
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.ui.components.ChangeIconColorDialog
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.MonogramTile
import com.rrajath.grove.ui.components.NotebookFileField
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.monogramLetter
import com.rrajath.grove.ui.components.nameHashPaletteKey
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

private val LOCATION_OPTIONS = listOf(
    "Top of file", "Bottom of file", "Under heading", "Datetree (date)", "Datetree (datetime)",
)

private fun locationIndex(location: TargetLocation): Int = when (location) {
    is TargetLocation.TopOfFile -> 0
    is TargetLocation.BottomOfFile -> 1
    is TargetLocation.UnderHeading -> 2
    is TargetLocation.DatetreeDate -> 3
    is TargetLocation.DatetreeDatetime -> 4
}

/** Template editor (design spec / PRD §7.6). templateId "new" creates one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TemplateEditScreen(
    templateId: String,
    onBack: () -> Unit,
    viewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val focusManager = LocalFocusManager.current
    // A focused OutlinedTextField's cursor/selection handle renders in its own Popup, which
    // isn't part of this screen's exit transition — without clearing focus first, it hangs in
    // place over the previous screen while this composable fades out from under it.
    val leave: () -> Unit = { focusManager.clearFocus(force = true); onBack() }
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val existing = templates.firstOrNull { it.id == templateId }
    // Fixed for this editor session so the monogram colour stays stable while the
    // name is still being typed, and so a new template's id matches what is saved.
    val editingId = remember(existing) { existing?.id ?: viewModel.newId() }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var colorKey by remember(existing) { mutableStateOf(existing?.color) }
    var showColorDialog by remember { mutableStateOf(false) }
    var targetFile by remember(existing) { mutableStateOf(existing?.targetFile ?: "inbox.org") }
    val targetFileError = FilenameValidation.errorFor(targetFile)
    var locationIdx by remember(existing) {
        mutableStateOf(existing?.location?.let { locationIndex(it) } ?: 1)
    }
    var headingTitle by remember(existing) {
        mutableStateOf((existing?.location as? TargetLocation.UnderHeading)?.title ?: "")
    }
    var customId by remember(existing) {
        mutableStateOf((existing?.location as? TargetLocation.UnderHeading)?.customId ?: "")
    }
    var templateText by remember(existing) { mutableStateOf(existing?.template ?: "* %^{Title}\n%cursor") }
    val invalidPlaceholders = remember(templateText) {
        PlaceholderExpander.findInvalid(templateText).map { it.token }.distinct()
    }
    var showPlaceholderHelp by remember { mutableStateOf(false) }

    fun buildLocation(): TargetLocation = when (locationIdx) {
        0 -> TargetLocation.TopOfFile
        1 -> TargetLocation.BottomOfFile
        2 -> TargetLocation.UnderHeading(
            title = headingTitle.takeIf { it.isNotBlank() },
            customId = customId.takeIf { it.isNotBlank() },
        )
        3 -> TargetLocation.DatetreeDate
        else -> TargetLocation.DatetreeDatetime
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = leave)
                            .padding(12.dp),
                    ) { Text("←", fontFamily = PlexMono, fontSize = 18.sp, color = c.ink) }
                },
                title = {
                    Text(
                        if (existing == null) "New template" else "Edit template",
                        style = MaterialTheme.typography.titleLarge, color = c.ink,
                    )
                },
                actions = {
                    val canSave = name.isNotBlank() && targetFileError == null
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (canSave) c.accent else c.line)
                            .clickable(enabled = canSave) {
                                viewModel.upsert(
                                    CaptureTemplate(
                                        id = editingId,
                                        name = name.trim(),
                                        color = colorKey,
                                        targetFile = targetFile.trim(),
                                        location = buildLocation(),
                                        template = templateText,
                                    )
                                )
                                leave()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            "Save",
                            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, color = if (canSave) c.accentInk else c.ink3,
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            FieldLabel("Name")
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonogramTile(
                    letter = monogramLetter(name),
                    colorKey = colorKey ?: nameHashPaletteKey(editingId),
                    size = 56.dp,
                    cornerRadius = 12.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = { showColorDialog = true },
                        onLongClick = { showColorDialog = true },
                    ),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontFamily = PlexSans),
                    placeholder = { Text("Meeting Note", fontFamily = PlexSans, color = c.ink3) },
                )
            }

            FieldLabel("Target file")
            NotebookFileField(
                value = targetFile,
                onValueChange = { targetFile = it },
                notebooks = notebooks,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Insert at")
            Column {
                LOCATION_OPTIONS.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (i == locationIdx) c.accentSoft else c.surface)
                            .clickable { locationIdx = i }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            label,
                            fontFamily = PlexSans,
                            fontWeight = if (i == locationIdx) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp,
                            color = if (i == locationIdx) c.accent else c.ink,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (locationIdx == 2) {
                FieldLabel("Heading: CUSTOM_ID (recommended) or exact name")
                Text(
                    "CUSTOM_ID keeps working if the heading is renamed; exact name is simpler but fragile.",
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = customId, onValueChange = { customId = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = PlexMono),
                    placeholder = { Text("custom-id (recommended)", fontFamily = PlexMono, color = c.ink3) },
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = headingTitle, onValueChange = { headingTitle = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = PlexSans),
                    placeholder = { Text("…or exact heading name", fontFamily = PlexSans, color = c.ink3) },
                )
            }

            FieldLabel("Template")
            OutlinedTextField(
                value = templateText, onValueChange = { templateText = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                isError = invalidPlaceholders.isNotEmpty(),
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.5.sp),
            )
            if (invalidPlaceholders.isNotEmpty()) {
                Text(
                    "Unsupported placeholder${if (invalidPlaceholders.size > 1) "s" else ""}: " +
                        invalidPlaceholders.joinToString(", "),
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.red,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                "placeholder help",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, color = c.accent,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { showPlaceholderHelp = true }
                    .padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showPlaceholderHelp) {
        PlaceholderInfoDialog(onDismiss = { showPlaceholderHelp = false })
    }

    if (showColorDialog) {
        ChangeIconColorDialog(
            name = name.ifBlank { "New template" },
            hint = "Letter follows the template name",
            letter = monogramLetter(name),
            currentColorKey = colorKey ?: nameHashPaletteKey(editingId),
            onPickColor = { colorKey = it },
            onDismiss = { showColorDialog = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, letterSpacing = 1.sp,
        color = MaterialTheme.grove.accent,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}
