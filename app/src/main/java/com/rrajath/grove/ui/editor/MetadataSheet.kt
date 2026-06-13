package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Note metadata sheet (PRD §5.2): state, priority, tags, SCHEDULED, DEADLINE. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    // Derive the headline from the observed buffer so the sheet recomposes when
    // a chip mutates state — reading state.buffer here (not the off-band
    // viewModel.currentHeadline) is what subscribes this scope to the change.
    val headline = remember(state.buffer, state.keywords) { viewModel.currentHeadline }
    var datePickerFor by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            SheetLabel("State")
            Row {
                val current = headline?.keyword
                StateChip(
                    label = "none",
                    active = current == null,
                    fg = c.ink2, bg = c.surface2,
                ) { viewModel.setKeyword(null) }
                state.keywords.all.forEach { kw ->
                    Spacer(Modifier.width(6.dp))
                    val done = state.keywords.isDone(kw)
                    StateChip(
                        label = kw,
                        active = current == kw,
                        fg = if (done) c.green else c.amber,
                        bg = if (done) c.greenSoft else c.amberSoft,
                    ) {
                        if (done) viewModel.markDone(kw) else viewModel.setKeyword(kw)
                    }
                }
            }

            SheetLabel("Priority")
            Row {
                listOf<Char?>(null, 'A', 'B', 'C').forEach { p ->
                    StateChip(
                        label = p?.let { "#$it" } ?: "none",
                        active = headline?.priority == p,
                        fg = if (p == 'A') c.red else c.ink2,
                        bg = if (p == 'A') c.redSoft else c.surface2,
                    ) { viewModel.setPriority(p) }
                    Spacer(Modifier.width(6.dp))
                }
            }

            SheetLabel("Tags")
            var tagsText by remember(headline?.tags) {
                mutableStateOf(headline?.tags?.joinToString(" ") ?: "")
            }
            OutlinedTextField(
                value = tagsText,
                onValueChange = {
                    tagsText = it
                    viewModel.setTags(it.split(Regex("[\\s:]+")).filter { t -> t.isNotEmpty() })
                },
                singleLine = true,
                placeholder = { Text("tag1 tag2", fontFamily = PlexMono, color = c.ink3) },
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = state.allTags.filter { tag ->
                val last = tagsText.substringAfterLast(' ').trim()
                last.isNotEmpty() && tag.startsWith(last, ignoreCase = true) &&
                        !tagsText.split(Regex("[\\s:]+")).contains(tag)
            }.take(8)
            if (suggestions.isNotEmpty()) {
                LazyRow(Modifier.padding(top = 6.dp)) {
                    items(suggestions) { tag ->
                        Pill(tag, fg = c.accent, bg = c.accentSoft, onClick = {
                            val parts = tagsText.trim().split(Regex("\\s+")).dropLast(1) + tag
                            tagsText = parts.joinToString(" ")
                            viewModel.setTags(parts.filter { it.isNotEmpty() })
                        })
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            SheetLabel("Planning")
            PlanningRow(
                label = "SCHEDULED",
                value = headline?.planning?.scheduled,
                color = c.blue,
                onPick = { datePickerFor = "scheduled" },
                onClear = { viewModel.setScheduled(null) },
            )
            Spacer(Modifier.height(8.dp))
            PlanningRow(
                label = "DEADLINE",
                value = headline?.planning?.deadline,
                color = c.red,
                onPick = { datePickerFor = "deadline" },
                onClear = { viewModel.setDeadline(null) },
            )
        }
    }

    datePickerFor?.let { target ->
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val ts = OrgTimestamp(date)
                        if (target == "scheduled") viewModel.setScheduled(ts)
                        else viewModel.setDeadline(ts)
                    }
                    datePickerFor = null
                }) { Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerFor = null }) { Text("Cancel", color = c.ink2) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, letterSpacing = 1.sp,
        color = MaterialTheme.grove.accent,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun StateChip(
    label: String,
    active: Boolean,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) bg else c.surface2.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontFamily = PlexMono,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            color = if (active) fg else c.ink2,
        )
    }
}

@Composable
private fun PlanningRow(
    label: String,
    value: OrgTimestamp?,
    color: androidx.compose.ui.graphics.Color,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, color = color,
            modifier = Modifier.width(96.dp),
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.surface2)
                .clickable(onClick = onPick)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .wrapContentWidth(),
        ) {
            Text(
                value?.format() ?: "set date…",
                fontFamily = PlexMono, fontSize = 12.5.sp,
                color = if (value != null) c.ink else c.ink3,
            )
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "✕",
                fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClear)
                    .padding(6.dp),
            )
        }
    }
}
