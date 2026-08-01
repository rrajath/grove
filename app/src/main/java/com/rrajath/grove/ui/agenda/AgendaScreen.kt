package com.rrajath.grove.ui.agenda

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.settings.AgendaGrouping
import com.rrajath.grove.settings.AgendaStateFilter
import com.rrajath.grove.settings.AgendaSwipeAction
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.components.SwipeAction
import com.rrajath.grove.ui.components.SwipeCommitRow
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.screens.NoteDialog
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef

/**
 * A pending swipe-left/swipe-right date pick, before the user confirms. The
 * Dates screen edits both planning dates at once, so the request carries both:
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

/**
 * The "Agenda A · focus" screen from `design/Grove.dc.html`: today's work
 * first, with an overdue card above it and a levers panel (⇅) that re-buckets
 * the list by date, priority, tag, or file.
 *
 * Rows additionally support the swipe gestures configured in Settings § Agenda
 * (set scheduled date, set deadline, or mark done), each committing on release
 * past the threshold. Overdue rows are deliberately not swipeable: they live on
 * the red card, whose own "Move to today" is the bulk equivalent, and the swipe
 * underlay would break the card's fill.
 */
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
    var datePickerRequest by remember { mutableStateOf<DatePickerRequest?>(null) }
    var noteDialogFor by remember { mutableStateOf<AgendaRow?>(null) }

    val openDatePicker: (AgendaRow, PlanningKind) -> Unit = { row, target ->
        datePickerRequest = DatePickerRequest(
            fileName = row.fileName,
            lineIndex = row.lineIndex,
            target = target,
            title = row.title,
            scheduled = row.scheduledTs,
            deadline = row.deadlineTs,
        )
    }

    Scaffold(containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AgendaHeader(
                day = state.headerDay,
                date = state.headerDate,
                todayCount = state.todayCount,
                onBack = onBack,
                onToggleLevers = viewModel::toggleLevers,
            )

            AgendaTabs(
                tab = state.tab,
                onSelect = viewModel::setTab,
                modifier = Modifier.padding(start = 14.dp, top = 11.dp, end = 14.dp),
            )

            if (state.leversOpen) {
                LeversPanel(
                    state = state,
                    onGrouping = viewModel::setGrouping,
                    onStateFilter = viewModel::setStateFilter,
                    onShowTags = viewModel::setShowTags,
                    onShowFile = viewModel::setShowFile,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AgendaList(
                    listState = listState,
                    state = state,
                    onOpenNote = onOpenNote,
                    onToggleDone = { viewModel.toggleDone(it.fileName, it.lineIndex) },
                    onToggleOverdue = viewModel::toggleOverdue,
                    onMoveOverdue = viewModel::moveOverdueToToday,
                    onOpenDatePicker = openDatePicker,
                    onOpenNoteDialog = { row -> noteDialogFor = row },
                )

                val nearBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
                    }
                }
                LaunchedEffect(nearBottom, state.groups.size) {
                    if (nearBottom) viewModel.loadMoreDays()
                }

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

    noteDialogFor?.let { row ->
        NoteDialog(
            title = row.title,
            onDismiss = { noteDialogFor = null },
            onConfirm = { note ->
                viewModel.addNote(row.fileName, row.lineIndex, note)
                noteDialogFor = null
            },
        )
    }
}

// ---------------------------------------------------------------- header

/** Back arrow, the day headline ("Wednesday" / "July 29 · 6 scheduled"), and the ⇅ levers toggle. */
@Composable
private fun AgendaHeader(
    day: String,
    date: String,
    todayCount: Int,
    onBack: () -> Unit,
    onToggleLevers: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", fontFamily = PlexSans, fontSize = 20.sp, color = c.ink)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                day,
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp, letterSpacing = (-0.21).sp, color = c.ink,
            )
            Text(
                "$date · $todayCount scheduled",
                fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink2,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface2)
                .clickable(onClick = onToggleLevers),
            contentAlignment = Alignment.Center,
        ) {
            Text("⇅", fontFamily = PlexSans, fontSize = 15.sp, color = c.ink2)
        }
    }
}

/**
 * Today/Upcoming switch. Distinct from the shared `SegmentedControl`: the
 * prototype's agenda tabs raise the active option on a `surface` card rather
 * than filling it with `accent`.
 *
 * The card alone is nearly invisible on the dark themes: an elevation shadow
 * has almost no contrast to cast against `surface2`, so the active option also
 * carries a 1dp `accent` border, and inactive labels drop to `ink3` to widen the
 * gap. That keeps the "raised card, not filled pill" character while still
 * reading as selected on every theme.
 */
@Composable
private fun AgendaTabs(
    tab: AgendaTab,
    onSelect: (AgendaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(AgendaTab.TODAY to "Today", AgendaTab.UPCOMING to "Upcoming").forEach { (value, label) ->
            val active = value == tab
            Box(
                Modifier
                    .weight(1f)
                    .then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)) else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.surface else Color.Transparent)
                    .then(
                        if (active) Modifier.border(1.dp, c.accent, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp, color = if (active) c.ink else c.ink3,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- levers

/** The ⇅ panel: "Group by" chips, "Show" chips, and the two row-density toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeversPanel(
    state: AgendaUiState,
    onGrouping: (AgendaGrouping) -> Unit,
    onStateFilter: (AgendaStateFilter) -> Unit,
    onShowTags: (Boolean) -> Unit,
    onShowFile: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(15.dp))
            .padding(start = 13.dp, top = 12.dp, end = 13.dp, bottom = 13.dp),
    ) {
        LeverLabel("Group by")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AgendaGrouping.entries.forEach { g ->
                LeverChip(g.label, active = g == state.grouping) { onGrouping(g) }
            }
        }

        LeverLabel("Show", modifier = Modifier.padding(top = 14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Open · <the vault's own todo-type keywords> · Everything
            LeverChip("Open", active = state.stateFilter == AgendaStateFilter.Open) {
                onStateFilter(AgendaStateFilter.Open)
            }
            state.activeKeywords.forEach { keyword ->
                val filter = AgendaStateFilter.Keyword(keyword)
                LeverChip(keyword, active = state.stateFilter == filter) { onStateFilter(filter) }
            }
            LeverChip("Everything", active = state.stateFilter == AgendaStateFilter.All) {
                onStateFilter(AgendaStateFilter.All)
            }
        }

        LeverToggle("Tags on rows", state.showTags, Modifier.padding(top = 14.dp)) { onShowTags(!state.showTags) }
        LeverToggle("Source file on rows", state.showFile, Modifier.padding(top = 11.dp)) { onShowFile(!state.showFile) }
    }
}

@Composable
private fun LeverLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontFamily = PlexSans, fontSize = 10.5.sp, letterSpacing = 0.74.sp,
        color = MaterialTheme.grove.ink3,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun LeverChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.accent else Color.Transparent)
            .then(if (active) Modifier else Modifier.border(1.dp, c.line, RoundedCornerShape(9.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (active) c.accentInk else c.ink2,
        )
    }
}

@Composable
private fun LeverToggle(label: String, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val c = MaterialTheme.grove
    val knobX by animateDpAsState(if (on) 17.5.dp else 2.5.dp, label = "knob")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = PlexSans, fontSize = 13.sp, color = c.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(width = 36.dp, height = 21.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (on) c.accent else c.surface3)
                .clickable(onClick = onToggle),
        ) {
            Box(
                Modifier
                    .offset(x = knobX, y = 2.5.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(c.surface),
            )
        }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun AgendaList(
    listState: LazyListState,
    state: AgendaUiState,
    onOpenNote: (NoteRef) -> Unit,
    onToggleDone: (AgendaRow) -> Unit,
    onToggleOverdue: () -> Unit,
    onMoveOverdue: () -> Unit,
    onOpenDatePicker: (AgendaRow, PlanningKind) -> Unit,
    onOpenNoteDialog: (AgendaRow) -> Unit,
) {
    val c = MaterialTheme.grove
    // At most one row's swipe panel stays open at a time (mirrors the Outline
    // screen's SwipeRevealRow coordination); a row's key matches its `items` key below.
    var openRowKey by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 20.dp),
    ) {
        if (state.isEmpty) {
            item("empty") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Nothing scheduled",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink,
                    )
                    Text(
                        if (state.tab == AgendaTab.TODAY) "Nothing on today's plate." else "Nothing coming up.",
                        fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }

        if (state.overdue.isNotEmpty()) {
            item("overdue") {
                OverdueCard(
                    state = state,
                    onToggle = onToggleOverdue,
                    onMoveAll = onMoveOverdue,
                    onOpenNote = onOpenNote,
                    onToggleDone = onToggleDone,
                )
            }
        }

        state.groups.forEach { group ->
            item("head-${group.key}") { GroupHeader(group) }
            items(group.rows, key = { "${group.key}-${it.fileName}@${it.lineIndex}" }) { row ->
                val rowKey = "${group.key}-${row.fileName}@${row.lineIndex}"
                SwipeCommitRow(
                    leftAction = swipeActionFor(state.swipeLeftAction, row, c, onOpenDatePicker, onToggleDone),
                    rightAction = swipeActionFor(state.swipeRightAction, row, c, onOpenDatePicker, onToggleDone),
                    // Add-note rides along beside whichever side is configured as
                    // Mark Done: partial swipe reveals both, full swipe still marks done.
                    leftSecondaryAction = addNoteAction(state.swipeLeftAction, row, c, onOpenNoteDialog),
                    rightSecondaryAction = addNoteAction(state.swipeRightAction, row, c, onOpenNoteDialog),
                    forceClose = openRowKey != rowKey,
                    onOpenChanged = { open ->
                        if (open) openRowKey = rowKey
                        else if (openRowKey == rowKey) openRowKey = null
                    },
                    onTap = { onOpenNote(NoteRef(row.fileName, row.lineIndex)) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    AgendaRowContent(row, onToggleDone = { onToggleDone(row) })
                }
            }
            item("gap-${group.key}") { Spacer(Modifier.height(14.dp)) }
        }
    }
}

/** Uppercase bucket key, its mono count, and a hairline running to the right edge. */
@Composable
private fun GroupHeader(group: AgendaGroup) {
    val c = MaterialTheme.grove
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.key.uppercase(),
            fontFamily = PlexSans, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, letterSpacing = 0.77.sp, color = c.ink2,
        )
        Spacer(Modifier.width(8.dp))
        Text(group.count.toString(), fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(c.line))
    }
}

/** The red collapsible card above the day's list, with its bulk reschedule. */
@Composable
private fun OverdueCard(
    state: AgendaUiState,
    onToggle: () -> Unit,
    onMoveAll: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onToggleDone: (AgendaRow) -> Unit,
) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(c.redSoft)
            .border(1.dp, c.red, RoundedCornerShape(15.dp))
            .padding(start = 12.dp, top = 11.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (state.overdueOpen) "▾" else "▸", fontFamily = PlexSans, fontSize = 12.sp, color = c.red)
            Spacer(Modifier.width(9.dp))
            Text(
                "${state.overdueCount} overdue",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.surface)
                    .clickable(onClick = onMoveAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Move to today",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = c.red,
                )
            }
        }
        if (state.overdueOpen) {
            Spacer(Modifier.height(4.dp))
            state.overdue.forEach { row ->
                Box(Modifier.clickable { onOpenNote(NoteRef(row.fileName, row.lineIndex)) }) {
                    AgendaRowContent(row, onToggleDone = { onToggleDone(row) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- row

/**
 * Circular priority-tinted checkbox, TODO-state chip + title, the mono meta
 * strip, and the right-hand priority badge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaRowContent(row: AgendaRow, onToggleDone: () -> Unit) {
    val c = MaterialTheme.grove
    val titleText = remember(row.title, c) { annotateOrgInline(row.title, c) }
    Row(
        Modifier
            .fillMaxWidth()
            // The prototype fades a completed row wholesale, checkbox and meta included.
            .alpha(if (row.isDone) 0.55f else 1f)
            .padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AgendaCheckbox(isDone = row.isDone, priority = row.priority, onClick = onToggleDone)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                row.keyword?.let { keyword ->
                    StateChip(keyword, row.isDone, Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    titleText,
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, lineHeight = 1.35.em,
                    color = if (row.isDone) c.ink3 else c.ink,
                    textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
                )
            }
            if (row.meta.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.meta.forEach { meta ->
                        Text(meta.text, fontFamily = PlexMono, fontSize = 11.5.sp, color = c.metaColor(meta.tone))
                    }
                }
            }
        }
        row.priority?.let { p ->
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(17.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p,
                    fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = c.agendaPriorityColor(p),
                )
            }
        }
    }
}

/** Ring tinted by priority, filled green with a ✓ once done. */
@Composable
private fun AgendaCheckbox(isDone: Boolean, priority: String?, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val ring = when {
        isDone -> c.green
        priority != null -> c.agendaPriorityColor(priority)
        else -> c.line2
    }
    Box(
        Modifier
            .padding(top = 1.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(if (isDone) c.green else Color.Transparent)
            .border(1.5.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isDone) {
            Icon(Icons.Default.Check, contentDescription = null, tint = c.surface, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun StateChip(keyword: String, isDone: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    val fg = when {
        isDone -> c.green
        keyword == "NEXT" -> c.green
        keyword == "WAITING" -> c.ink3
        else -> c.synTodo
    }
    val bg = when {
        isDone -> c.greenSoft
        keyword == "NEXT" -> c.greenSoft
        keyword == "WAITING" -> c.surface2
        else -> c.amberSoft
    }
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            keyword,
            fontFamily = PlexMono, fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp, letterSpacing = 0.48.sp, color = fg,
        )
    }
}

/**
 * Agenda priority scale from the prototype (`agPColor`). Deliberately not the
 * shared `GroveColors.priorityColor`, which paints C green; that reads as
 * "done" next to this screen's green checkboxes.
 */
private fun GroveColors.agendaPriorityColor(priority: String): Color = when (priority.uppercase()) {
    "A" -> red
    "B" -> amber
    "C" -> blue
    else -> ink3
}

private fun GroveColors.metaColor(tone: AgendaMetaTone): Color = when (tone) {
    AgendaMetaTone.NORMAL -> ink2
    AgendaMetaTone.MUTED -> ink3
    AgendaMetaTone.DANGER -> red
    AgendaMetaTone.TAG -> synTag
}

/** Maps a configured [AgendaSwipeAction] to the glyph/label/color and effect for one swipe direction. */
private fun swipeActionFor(
    kind: AgendaSwipeAction,
    row: AgendaRow,
    c: GroveColors,
    onOpenDatePicker: (AgendaRow, PlanningKind) -> Unit,
    onToggleDone: (AgendaRow) -> Unit,
): SwipeAction = when (kind) {
    AgendaSwipeAction.SET_SCHEDULED ->
        SwipeAction(label = "Sched", fg = c.blue, bg = c.blueSoft, icon = Icons.Outlined.CalendarMonth) {
            onOpenDatePicker(row, PlanningKind.SCHEDULED)
        }
    AgendaSwipeAction.SET_DEADLINE ->
        SwipeAction(label = "Deadl", fg = c.red, bg = c.redSoft, icon = Icons.Filled.Flag) {
            onOpenDatePicker(row, PlanningKind.DEADLINE)
        }
    AgendaSwipeAction.MARK_DONE ->
        SwipeAction(label = "Done", fg = c.green, bg = c.greenSoft, icon = Icons.Default.Check) { onToggleDone(row) }
}

/**
 * Rides alongside whichever side is configured as [AgendaSwipeAction.MARK_DONE]:
 * a partial swipe reveals this "Note" cell next to Done for a tap, while a full
 * swipe still commits Done directly. Any other configured action gets no
 * secondary (null), so its side keeps the plain swipe-to-commit behavior.
 */
private fun addNoteAction(
    kind: AgendaSwipeAction,
    row: AgendaRow,
    c: GroveColors,
    onOpenNoteDialog: (AgendaRow) -> Unit,
): SwipeAction? = when (kind) {
    AgendaSwipeAction.MARK_DONE ->
        SwipeAction(label = "Note", fg = c.blue, bg = c.blueSoft, icon = Icons.Outlined.EditNote) {
            onOpenNoteDialog(row)
        }
    else -> null
}
