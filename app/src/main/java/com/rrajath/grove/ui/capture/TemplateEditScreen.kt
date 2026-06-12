package com.rrajath.grove.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.SegmentedControl
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
@Composable
fun TemplateEditScreen(
    templateId: String,
    onBack: () -> Unit,
    viewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val templates by viewModel.templates.collectAsState()
    val existing = templates.firstOrNull { it.id == templateId }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var icon by remember(existing) { mutableStateOf(existing?.icon ?: "✶") }
    var targetFile by remember(existing) { mutableStateOf(existing?.targetFile ?: "inbox.org") }
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
                            .clickable(onClick = onBack)
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
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.accent)
                            .clickable(enabled = name.isNotBlank() && targetFile.isNotBlank()) {
                                viewModel.upsert(
                                    CaptureTemplate(
                                        id = existing?.id ?: viewModel.newId(),
                                        name = name.trim(),
                                        icon = icon.ifBlank { "✶" },
                                        targetFile = targetFile.trim(),
                                        location = buildLocation(),
                                        template = templateText,
                                    )
                                )
                                onBack()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(
                            "Save",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            FieldLabel("Name")
            Row {
                OutlinedTextField(
                    value = icon, onValueChange = { icon = it.take(2) },
                    singleLine = true, modifier = Modifier.width(72.dp),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("Meeting Note", color = c.ink3) },
                )
            }

            FieldLabel("Target file")
            OutlinedTextField(
                value = targetFile, onValueChange = { targetFile = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontFamily = PlexMono),
                placeholder = { Text("notebook.org", fontFamily = PlexMono, color = c.ink3) },
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
                FieldLabel("Heading — CUSTOM_ID (recommended) or exact name")
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
                    placeholder = { Text("…or exact heading name", color = c.ink3) },
                )
            }

            FieldLabel("Template")
            OutlinedTextField(
                value = templateText, onValueChange = { templateText = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.5.sp),
            )
            Text(
                "Placeholders: %t %T %u %U %date %time %day %year %month " +
                        "%cursor %? %^{prompt} %clipboard %shared_text %shared_url",
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
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
