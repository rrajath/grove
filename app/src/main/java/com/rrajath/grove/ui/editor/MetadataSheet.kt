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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.screens.NoteDialog
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.priorityColor
import com.rrajath.grove.ui.theme.prioritySoftColor

/**
 * Note metadata sheet (PRD §5.2): state, priority, tags, SCHEDULED, DEADLINE.
 * Stateless: shared by the editor (mutates the in-memory buffer) and read mode
 * (writes each change straight to disk), which each wire the callbacks to
 * their own view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    headline: OrgHeadline?,
    keywords: OrgKeywords,
    allTags: List<String>,
    onChangeKeyword: (String?) -> Unit,
    onSetPriority: (Char?) -> Unit,
    onSetTags: (List<String>) -> Unit,
    onSetPlanningDates: (OrgTimestamp?, OrgTimestamp?) -> Unit,
    onAddNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    var planningOpen by remember { mutableStateOf(false) }
    var noteDialogOpen by remember { mutableStateOf(false) }

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
                ) { onChangeKeyword(null) }
                keywords.all.forEach { kw ->
                    Spacer(Modifier.width(6.dp))
                    val done = keywords.isDone(kw)
                    StateChip(
                        label = kw,
                        active = current == kw,
                        fg = if (done) c.green else c.amber,
                        bg = if (done) c.greenSoft else c.amberSoft,
                    ) {
                        onChangeKeyword(kw)
                    }
                }
            }

            SheetLabel("Priority")
            Row {
                listOf<Char?>(null, 'A', 'B', 'C').forEach { p ->
                    StateChip(
                        label = p?.let { "#$it" } ?: "none",
                        active = headline?.priority == p,
                        fg = p?.let { c.priorityColor(it) } ?: c.ink2,
                        bg = p?.let { c.prioritySoftColor(it) } ?: c.surface2,
                    ) { onSetPriority(p) }
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
                    onSetTags(it.split(Regex("[\\s:]+")).filter { t -> t.isNotEmpty() })
                },
                singleLine = true,
                placeholder = { Text("tag1 tag2", fontFamily = PlexMono, color = c.ink3) },
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = allTags.filter { tag ->
                val last = tagsText.substringAfterLast(' ').trim()
                last.isNotEmpty() && tag.startsWith(last, ignoreCase = true) &&
                        !tagsText.split(Regex("[\\s:]+")).contains(tag)
            }.take(8)
            if (suggestions.isNotEmpty()) {
                LazyRow(Modifier.padding(top = 6.dp)) {
                    items(suggestions) { tag ->
                        Pill(tag, fg = c.accent, bg = c.accentSoft, outline = true, onClick = {
                            val parts = tagsText.trim().split(Regex("\\s+")).dropLast(1) + tag
                            tagsText = parts.joinToString(" ")
                            onSetTags(parts.filter { it.isNotEmpty() })
                        })
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            SheetLabel("Schedule/Deadline")
            CombinedPlanningRow(
                scheduled = headline?.planning?.scheduled,
                deadline = headline?.planning?.deadline,
                onPick = { planningOpen = true },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "+ Add note",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { noteDialogOpen = true }
                    .padding(vertical = 6.dp),
            )
        }
    }

    if (planningOpen) {
        val scheduled = headline?.planning?.scheduled
        val deadline = headline?.planning?.deadline
        PlanningDatesScreen(
            title = headline?.title.orEmpty(),
            scheduled = scheduled,
            deadline = deadline,
            // Opens on whichever field is more relevant: the unset one when only
            // one of the two is set, otherwise SCHEDULED.
            focus = if (scheduled == null && deadline != null) PlanningKind.DEADLINE else PlanningKind.SCHEDULED,
            onDismiss = { planningOpen = false },
            onConfirm = { sched, dead ->
                onSetPlanningDates(sched, dead)
                planningOpen = false
            },
        )
    }

    if (noteDialogOpen) {
        NoteDialog(
            title = headline?.title.orEmpty(),
            onDismiss = { noteDialogOpen = false },
            onConfirm = { note ->
                onAddNote(note)
                noteDialogOpen = false
            },
        )
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

/** Combined SCHEDULED/DEADLINE summary: both values (blue/red) in one tappable pill. */
@Composable
private fun CombinedPlanningRow(
    scheduled: OrgTimestamp?,
    deadline: OrgTimestamp?,
    onPick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface2)
            .clickable(onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        if (scheduled == null && deadline == null) {
            Text("set date…", fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink3)
        } else {
            Row {
                scheduled?.let {
                    Text(
                        "SCHED " + it.format(),
                        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, color = c.blue,
                    )
                }
                if (scheduled != null && deadline != null) {
                    Spacer(Modifier.width(12.dp))
                }
                deadline?.let {
                    Text(
                        "DUE " + it.format(),
                        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, color = c.red,
                    )
                }
            }
        }
    }
}
