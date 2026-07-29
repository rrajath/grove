package com.rrajath.grove.ui.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.settings.AgendaSwipeAction
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.components.ResultRowContent
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.SwipeAction
import com.rrajath.grove.ui.components.SwipeCommitRow
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A pending swipe-left/swipe-right date pick, before the user confirms. The
 * Dates screen edits both planning dates at once, so the request carries both —
 * [target] only decides which section it opens focused on.
 */
private data class DatePickerRequest(
    val fileName: String,
    val lineIndex: Int,
    val target: PlanningKind,
    val title: String,
    val scheduled: OrgTimestamp?,
    val deadline: OrgTimestamp?,
)

/** Upcoming/overdue agenda — a dedicated screen from the drawer's Agenda item,
 *  separate from Search (design decision: agenda answers "what's next", search
 *  answers "find a specific note"). Rows support swipe gestures (Settings §
 *  Agenda: set scheduled date, set deadline, or mark done), each committing
 *  immediately on release past the swipe threshold. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    viewModel: AgendaViewModel = viewModel(factory = AgendaViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH) }
    val today = remember { LocalDate.now() }
    var datePickerRequest by remember { mutableStateOf<DatePickerRequest?>(null) }

    Scaffold(containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.height(56.dp).fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconGlyph("←", onClick = onBack)
                Spacer(Modifier.width(4.dp))
                Text("Agenda", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumnAgenda(
                    listState = listState,
                    state = state,
                    formatter = formatter,
                    today = today,
                    onOpenNote = onOpenNote,
                    onOpenDatePicker = { result, target ->
                        datePickerRequest = DatePickerRequest(
                            fileName = result.fileName,
                            lineIndex = result.lineIndex,
                            target = target,
                            title = result.title,
                            scheduled = result.scheduledTs,
                            deadline = result.deadlineTs,
                        )
                    },
                    onMarkDone = { result -> viewModel.markDone(result.fileName, result.lineIndex) },
                )

                val nearBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
                    }
                }
                LaunchedEffect(nearBottom, state.days.size) {
                    if (nearBottom) viewModel.loadMoreDays()
                }

                ScrollJumpButtons(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
                GroveUndoSnackbar(
                    snack = snack,
                    onUndo = viewModel::undo,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }
    }

    datePickerRequest?.let { req ->
        PlanningDatesScreen(
            title = req.title,
            scheduled = req.scheduled,
            deadline = req.deadline,
            focus = req.target,
            onDismiss = { datePickerRequest = null },
            onConfirm = { sched, dead ->
                viewModel.setPlanningDates(req.fileName, req.lineIndex, sched, dead)
                datePickerRequest = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyColumnAgenda(
    listState: LazyListState,
    state: AgendaUiState,
    formatter: DateTimeFormatter,
    today: LocalDate,
    onOpenNote: (NoteRef) -> Unit,
    onOpenDatePicker: (AgendaResult, PlanningKind) -> Unit,
    onMarkDone: (AgendaResult) -> Unit,
) {
    val c = MaterialTheme.grove
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        if (state.overdue.isEmpty() && state.days.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing scheduled", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
                    Text(
                        "Nothing overdue or due soon.",
                        fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }
        if (state.overdue.isNotEmpty()) {
            stickyHeader(key = "overdue-header") {
                AgendaSectionHeader("Overdue (${state.overdueCount})", color = c.red)
            }
            itemsIndexed(
                state.overdue,
                key = { _, it -> "overdue-${it.fileName}@${it.lineIndex}" },
                contentType = { _, _ -> "result" },
            ) { index, result ->
                if (index > 0) HorizontalDivider(color = c.line)
                AgendaResultRow(result, state.swipeLeftAction, state.swipeRightAction, onOpenNote, onOpenDatePicker, onMarkDone)
            }
        }
        state.days.forEach { day ->
            stickyHeader(key = day.date.toString()) {
                AgendaSectionHeader(dayHeaderLabel(day.date, today, formatter), color = c.accent)
            }
            itemsIndexed(
                day.results,
                key = { _, it -> "${day.date}-${it.fileName}@${it.lineIndex}" },
                contentType = { _, _ -> "result" },
            ) { index, result ->
                if (index > 0) HorizontalDivider(color = c.line)
                AgendaResultRow(result, state.swipeLeftAction, state.swipeRightAction, onOpenNote, onOpenDatePicker, onMarkDone)
            }
        }
    }
}

/** "Tuesday, Jul 28 · Today" / "Wednesday, Jul 29 · Tomorrow"; every other day is plain "EEEE, MMM d". */
private fun dayHeaderLabel(date: LocalDate, today: LocalDate, formatter: DateTimeFormatter): String {
    val base = date.format(formatter)
    return when (date) {
        today -> "$base · Today"
        today.plusDays(1) -> "$base · Tomorrow"
        else -> base
    }
}

@Composable
private fun AgendaSectionHeader(text: String, color: Color) {
    val c = MaterialTheme.grove
    Text(
        text,
        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, letterSpacing = 0.5.sp, color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

/** Maps a configured [AgendaSwipeAction] to the glyph/label/color and effect for one swipe direction. */
private fun swipeActionFor(
    kind: AgendaSwipeAction,
    result: AgendaResult,
    c: GroveColors,
    onOpenDatePicker: (AgendaResult, PlanningKind) -> Unit,
    onMarkDone: (AgendaResult) -> Unit,
): SwipeAction = when (kind) {
    AgendaSwipeAction.SET_SCHEDULED ->
        SwipeAction(label = "Sched", fg = c.blue, bg = c.blueSoft, icon = Icons.Outlined.CalendarMonth) {
            onOpenDatePicker(result, PlanningKind.SCHEDULED)
        }
    AgendaSwipeAction.SET_DEADLINE ->
        SwipeAction(label = "Deadl", fg = c.red, bg = c.redSoft, icon = Icons.Filled.Flag) {
            onOpenDatePicker(result, PlanningKind.DEADLINE)
        }
    AgendaSwipeAction.MARK_DONE ->
        SwipeAction(label = "Done", fg = c.green, bg = c.greenSoft, icon = Icons.Default.Check) { onMarkDone(result) }
}

@Composable
private fun AgendaResultRow(
    result: AgendaResult,
    swipeLeftAction: AgendaSwipeAction,
    swipeRightAction: AgendaSwipeAction,
    onOpenNote: (NoteRef) -> Unit,
    onOpenDatePicker: (AgendaResult, PlanningKind) -> Unit,
    onMarkDone: (AgendaResult) -> Unit,
) {
    val c = MaterialTheme.grove
    val titleText = remember(result.title, c) { annotateOrgInline(result.title, c) }
    val snippetText = if (result.snippet.text.isNotEmpty()) {
        remember(result.snippet.text, c) { annotateOrgInline(result.snippet.text, c) }
    } else null
    SwipeCommitRow(
        leftAction = swipeActionFor(swipeLeftAction, result, c, onOpenDatePicker, onMarkDone),
        rightAction = swipeActionFor(swipeRightAction, result, c, onOpenDatePicker, onMarkDone),
        onTap = { onOpenNote(NoteRef(result.fileName, result.lineIndex)) },
        shape = RoundedCornerShape(12.dp),
    ) {
        ResultRowContent(
            keyword = result.keyword,
            isDone = result.isDone,
            priority = result.priority,
            titleText = titleText,
            snippetText = snippetText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 9.dp, end = 11.dp, bottom = 11.dp),
        ) {
            Text(
                result.breadcrumb,
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
