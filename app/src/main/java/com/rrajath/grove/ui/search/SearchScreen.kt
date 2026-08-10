package com.rrajath.grove.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.search.Snippets
import com.rrajath.grove.ui.components.CustomDateRangePicker
import com.rrajath.grove.ui.components.GroveUndoSnackbar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.components.StatePickerSheet
import com.rrajath.grove.ui.components.SwipeAction
import com.rrajath.grove.ui.components.SwipeCommitRow
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.components.ResultRowContent
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.searchIcon
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OPERATOR_CHIPS = listOf(
    "t.TAG", "i.STATE", "s.PERIOD", "d.PERIOD", "c.PERIOD", "cr.PERIOD", "b.NOTEBOOK", "p.PRIORITY",
)

/** Quick-start card labels (see [BlankState]), included in the star button's
 *  searchable dropdown alongside actual saved searches. */
private val QUICK_START_NAMES = listOf("Overdue", "Today", "Open tasks", "Unscheduled")

/** Full-text + faceted search, results grouped by file (design spec §9
 *  "Search B: panel"). Finding a specific note; for upcoming/overdue browsing
 *  see the dedicated Agenda screen. */
@Composable
fun SearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    /**
     * Notebook to pin the search to on entry (the Outline's search action passes
     * the file you were reading). The pin is an ordinary notebook filter from
     * there on: the Filters sheet can point it at another file or widen it back
     * to all notebooks, and the mirrored `b.` token in the search field follows.
     */
    initialNotebook: String? = null,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    var advancedOpen by remember { mutableStateOf(false) }
    var filterPanelOpen by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    var statePickerFor by remember { mutableStateOf<SearchResult?>(null) }
    var schedulePickerFor by remember { mutableStateOf<SearchResult?>(null) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val collapsedFiles = remember { mutableStateMapOf<String, Boolean>() }

    // TextFieldValue (not the plain-String overload) so a programmatic change
    // (an operator chip tap or a Filters-panel selection mirroring into the
    // field) can place the cursor at the end instead of Compose's default of
    // resetting it to the start. Typed input keeps whatever selection the IME
    // reports; only changes that didn't originate from this field's own
    // onValueChange move the cursor.
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.query)) }
    LaunchedEffect(state.query) {
        if (state.query != fieldValue.text) {
            fieldValue = TextFieldValue(state.query, selection = TextRange(state.query.length))
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.submit(initialQuery)
        else focusRequester.requestFocus()
    }
    LaunchedEffect(initialNotebook) {
        if (!initialNotebook.isNullOrBlank()) viewModel.pinNotebook(initialNotebook)
    }

    // First back press (system gesture or the in-screen arrow) clears back to the
    // blank quick-start view rather than leaving the screen; only a blank state
    // pops the nav stack straight to the previous screen.
    val handleBack = { if (!state.isBlank) viewModel.resetToBlank() else onBack() }
    BackHandler(enabled = !state.isBlank, onBack = { viewModel.resetToBlank() })

    Scaffold(containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search field row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconGlyph("←", onClick = handleBack)
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(13.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(searchIcon(), contentDescription = null, tint = c.ink3, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { new ->
                            fieldValue = new
                            viewModel.onQueryChange(new.text)
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    if (state.query.isNotEmpty()) {
                        Text(
                            "×", fontFamily = PlexMono, fontSize = 15.sp, color = c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.onQueryChange("") }
                                .padding(4.dp),
                        )
                    }
                }
                if (state.query.isNotBlank()) {
                    Box {
                        IconGlyph("☆", onClick = { saveDialogOpen = true })
                        if (saveDialogOpen) {
                            SaveSearchDropdown(
                                savedSearches = savedSearches,
                                onDismiss = { saveDialogOpen = false },
                                onSaveOrOverwrite = viewModel::saveOrOverwriteSearch,
                            )
                        }
                    }
                }
            }

            // Meta row: result count on the left (hidden until there's an active
            // query/filter to count), Advanced toggle on the right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.isBlank) {
                    Text(
                        "${state.resultCount} ${if (state.resultCount == 1) "result" else "results"} · " +
                            "${state.notebookCount} ${if (state.notebookCount == 1) "notebook" else "notebooks"}",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                AdvancedToggle(active = advancedOpen, onClick = { advancedOpen = !advancedOpen })
            }

            if (advancedOpen) {
                AdvancedPanel(onChipTap = { op ->
                    viewModel.onQueryChange((state.query.trim() + " " + op.substringBefore('.') + ".").trim())
                })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isBlank -> BlankState(
                        quickCounts = state.quickCounts,
                        activeStates = state.activeStates,
                        savedSearches = savedSearches,
                        onQuick = viewModel::applyQuickFilter,
                        onQuickQuery = viewModel::applyQuickQuery,
                        onSavedTap = viewModel::submit,
                        onRenameSaved = viewModel::renameSavedSearch,
                        onDeleteSaved = viewModel::deleteSavedSearch,
                    )
                    state.groups.isEmpty() -> NoResultsState(onOpenFilters = { filterPanelOpen = true })
                    else -> GroupedResultsList(
                        listState = listState,
                        groups = state.groups,
                        matchedTerms = state.matchedTerms,
                        collapsedFiles = collapsedFiles,
                        onOpenNote = onOpenNote,
                        onOpenStatePicker = { statePickerFor = it },
                        onOpenSchedulePicker = { schedulePickerFor = it },
                    )
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

            FiltersBar(activeCount = state.filters.activeCount, onClick = { filterPanelOpen = true })
        }
    }

    if (filterPanelOpen) {
        FilterPanel(
            filters = state.filters,
            catalog = state.catalog,
            resultCount = state.resultCount,
            onToggleTag = viewModel::toggleTag,
            onToggleState = viewModel::toggleState,
            onTogglePriority = viewModel::togglePriority,
            onSetScheduled = viewModel::setScheduledPreset,
            onSetScheduledRange = viewModel::setScheduledRange,
            onSetDeadline = viewModel::setDeadlinePreset,
            onSetDeadlineRange = viewModel::setDeadlineRange,
            onSetClosed = viewModel::setClosedPreset,
            onSetClosedRange = viewModel::setClosedRange,
            onSetCreated = viewModel::setCreatedPreset,
            onSetCreatedRange = viewModel::setCreatedRange,
            onToggleNotebook = viewModel::toggleNotebook,
            onClearNotebooks = viewModel::clearNotebooks,
            onClear = viewModel::clearFilters,
            onDismiss = { filterPanelOpen = false },
        )
    }

    statePickerFor?.let { result ->
        StatePickerSheet(
            title = result.title,
            keywords = keywords,
            current = result.keyword,
            onPick = { keyword ->
                viewModel.setState(result.fileName, result.lineIndex, keyword)
                statePickerFor = null
            },
            onDismiss = { statePickerFor = null },
        )
    }

    schedulePickerFor?.let { result ->
        PlanningDatesScreen(
            title = result.title,
            scheduled = result.scheduledTs,
            deadline = result.deadlineTs,
            focus = PlanningKind.SCHEDULED,
            onDismiss = { schedulePickerFor = null },
            onConfirm = { sched, dead ->
                viewModel.setPlanningDates(result.fileName, result.lineIndex, sched, dead)
                schedulePickerFor = null
            },
        )
    }
}

/**
 * Star button's searchable dropdown: types continuously filter the combined
 * list of saved searches and quick-start card labels; picking one asks to
 * overwrite it, typing a name not in the list saves it as new.
 */
@Composable
private fun SaveSearchDropdown(
    savedSearches: List<SavedSearch>,
    onDismiss: () -> Unit,
    onSaveOrOverwrite: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    var typed by remember { mutableStateOf("") }
    var confirmTarget by remember { mutableStateOf<String?>(null) }
    val allNames = remember(savedSearches) { (QUICK_START_NAMES + savedSearches.map { it.name }).distinct() }
    val filtered = remember(typed, allNames) {
        if (typed.isBlank()) allNames else allNames.filter { it.contains(typed, ignoreCase = true) }
    }
    val exactMatch = allNames.any { it.equals(typed, ignoreCase = true) }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(230.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                placeholder = { Text("Name this search", color = c.ink3, fontSize = 13.sp) },
                textStyle = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, color = c.ink),
                modifier = Modifier.fillMaxWidth(),
            )
            if (typed.isNotBlank() && !exactMatch) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Save as “$typed”",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSaveOrOverwrite(typed); onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                )
            }
            if (filtered.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = c.line)
                filtered.forEach { name ->
                    Text(
                        name,
                        fontFamily = PlexSans, fontSize = 13.sp, color = c.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { confirmTarget = name }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }

    confirmTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            containerColor = c.surface,
            title = { Text("Overwrite “$name”?", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink) },
            text = {
                Text(
                    "This replaces its saved query with the current search.",
                    fontFamily = PlexSans, fontSize = 13.sp, color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = { onSaveOrOverwrite(name); confirmTarget = null; onDismiss() }) {
                    Text("Overwrite", color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("Cancel", color = c.ink2) }
            },
        )
    }
}

@Composable
private fun AdvancedToggle(active: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) c.accent else c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            "⚑ Advanced",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (active) c.accentInk else c.ink2,
        )
    }
}

/** One line per operator, shown from the (i) button (see [AdvancedPanel]). */
private val OPERATOR_LEGEND = listOf(
    "space" to "AND: every term must match",
    "OR" to "starts a new AND-group: either side can match",
    ". prefix" to "NOT: excludes rather than requires",
    "o.PROP" to "sort by PROP (priority, scheduled, deadline, created, title, notebook)",
    "t.TAG / tn.TAG" to "tag anywhere in the heading / on this heading only",
    "i.STATE" to "TODO keyword (i.none = no keyword)",
    "b.NOTEBOOK" to "restrict to one notebook",
    "p.PRIORITY" to "priority letter (A/B/C)",
    "s./d." to "scheduled/deadline within a period (today, tomorrow, 3d, 1w, overdue, nodate…)",
    "c./cr." to "closed/created within a period (same period tokens as s./d.)",
)

@Composable
private fun AdvancedPanel(onChipTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    var legendOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Operators", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp, color = c.ink2, modifier = Modifier.weight(1f),
            )
            Box {
                Text(
                    "ⓘ", fontFamily = PlexMono, fontSize = 13.sp, color = c.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { legendOpen = true }
                        .padding(4.dp),
                )
                DropdownMenu(expanded = legendOpen, onDismissRequest = { legendOpen = false }) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        OPERATOR_LEGEND.forEach { (token, desc) ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, color = c.ink)) {
                                        append(token)
                                    }
                                    withStyle(SpanStyle(fontFamily = PlexSans, color = c.ink2)) {
                                        append("  $desc")
                                    }
                                },
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OPERATOR_CHIPS.forEach { OpChip(it, onChipTap) }
        }
    }
}

@Composable
private fun OpChip(label: String, onTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surface2)
            .clickable { onTap(label) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontFamily = PlexMono, fontSize = 11.sp, color = c.ink2)
    }
}

@Composable
private fun FiltersBar(activeCount: Int, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Column {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.accent)
                    .clickable(onClick = onClick)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Text("Filters", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.accentInk)
                if (activeCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accentInk)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$activeCount", fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoResultsState(onOpenFilters: () -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing matches", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
        Text(
            "Loosen a section in Filters.",
            fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp),
        )
        Box(
            Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface2)
                .clickable(onClick = onOpenFilters)
                .padding(horizontal = 17.dp, vertical = 10.dp),
        ) {
            Text("Open filters", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink)
        }
    }
}

private data class QuickCard(
    val icon: ImageVector,
    val label: String,
    val meta: String,
    val fg: Color,
    val bg: Color,
    val onClick: () -> Unit,
)

@Composable
private fun BlankState(
    quickCounts: QuickCounts,
    activeStates: List<String>,
    savedSearches: List<SavedSearch>,
    onQuick: (SearchFilters) -> Unit,
    onQuickQuery: (String) -> Unit,
    onSavedTap: (String) -> Unit,
    onRenameSaved: (id: String, name: String) -> Unit,
    onDeleteSaved: (id: String) -> Unit,
) {
    val c = MaterialTheme.grove
    val cards = listOf(
        // "Overdue" is open tasks whose scheduled OR deadline date has
        // passed: an OR across two different fields (and across every active
        // keyword) that SearchFilters can't express as one facet, so this
        // drives the query text directly. Equivalent to
        // "(i.KW1 OR i.KW2 OR …) AND (s.overdue OR d.overdue)", expanded into
        // the grammar's flat OR-of-AND-groups since it has no parens.
        QuickCard(Icons.Filled.PriorityHigh, "Overdue", "${quickCounts.overdue} past their date", c.red, c.redSoft) {
            val expr = if (activeStates.isEmpty()) {
                "s.overdue OR d.overdue"
            } else {
                activeStates.flatMap { kw -> listOf("i.$kw s.overdue", "i.$kw d.overdue") }.joinToString(" OR ")
            }
            onQuickQuery(expr)
        },
        QuickCard(Icons.Filled.Schedule, "Today", "${quickCounts.today} scheduled or due", c.amber, c.amberSoft) {
            onQuick(SearchFilters(scheduled = DatePreset.TODAY))
        },
        QuickCard(
            Icons.Filled.CheckBoxOutlineBlank, "Open tasks",
            "${quickCounts.openTasks} ${if (quickCounts.openTasks == 1) "TODO item" else "TODO items"}",
            c.blue, c.blueSoft,
        ) {
            onQuick(SearchFilters(states = activeStates.toSet()))
        },
        QuickCard(
            Icons.Filled.EventBusy, "Unscheduled",
            "${quickCounts.unscheduled} without a date",
            c.synTag, c.accentSoft,
        ) {
            onQuick(SearchFilters(states = activeStates.toSet(), scheduled = DatePreset.NO_DATE, deadline = DatePreset.NO_DATE))
        },
    )
    var menuTarget by remember { mutableStateOf<SavedSearch?>(null) }
    var renameTarget by remember { mutableStateOf<SavedSearch?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(
            "QUICK START",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.07.em, color = c.ink3,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickCardView(cards[0], Modifier.weight(1f))
            QuickCardView(cards[1], Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickCardView(cards[2], Modifier.weight(1f))
            QuickCardView(cards[3], Modifier.weight(1f))
        }

        if (savedSearches.isNotEmpty()) {
            Text(
                "SAVED SEARCHES",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.07.em, color = c.ink3,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            savedSearches.forEach { saved ->
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onSavedTap(saved.query) },
                                onLongClick = { menuTarget = saved },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        // Top, not CenterVertically: centering against the whole two-line
                        // Column (title + query) sinks the star between the lines instead of
                        // level with the title.
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("★", fontSize = 14.sp, color = c.accent)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(saved.name, fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = c.ink)
                            Text(
                                saved.query, fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuTarget?.id == saved.id,
                        onDismissRequest = { menuTarget = null },
                        containerColor = c.surface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", fontFamily = PlexSans, color = c.ink) },
                            onClick = { menuTarget = null; renameTarget = saved },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", fontFamily = PlexSans, color = c.red) },
                            onClick = { menuTarget = null; onDeleteSaved(saved.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = c.surface,
            title = {
                Text("Rename search", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink)
            },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Name", color = c.ink3) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameSaved(target.id, name)
                        renameTarget = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Rename", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = c.ink2) }
            },
        )
    }
}

@Composable
private fun QuickCardView(card: QuickCard, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = card.onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(card.bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(card.icon, contentDescription = null, tint = card.fg, modifier = Modifier.size(16.dp))
        }
        Text(
            card.label, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(card.meta, fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedResultsList(
    listState: LazyListState,
    groups: List<SearchFileGroup>,
    matchedTerms: List<String>,
    collapsedFiles: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onOpenNote: (NoteRef) -> Unit,
    onOpenStatePicker: (SearchResult) -> Unit,
    onOpenSchedulePicker: (SearchResult) -> Unit,
) {
    val c = MaterialTheme.grove
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        groups.forEach { group ->
            val collapsed = collapsedFiles[group.fileName] == true
            stickyHeader(key = "file-${group.fileName}") {
                FileGroupHeader(
                    fileName = group.fileName,
                    count = group.results.size,
                    collapsed = collapsed,
                    onToggle = { collapsedFiles[group.fileName] = !collapsed },
                )
            }
            if (!collapsed) {
                itemsIndexed(group.results, key = { _, r -> "${group.fileName}-${r.lineIndex}" }, contentType = { _, _ -> "result" }) { index, result ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.grove.line)
                    SwipeCommitRow(
                        // Swipe left-to-right: cycle the TODO state via a bottom sheet.
                        leftAction = SwipeAction("⟳", "State", c.amber, c.amberSoft) { onOpenStatePicker(result) },
                        // Swipe right-to-left: schedule this task.
                        rightAction = SwipeAction(
                            label = "Schedule",
                            fg = c.blue,
                            bg = c.blueSoft,
                            icon = Icons.Outlined.CalendarMonth,
                        ) { onOpenSchedulePicker(result) },
                        onTap = { onOpenNote(NoteRef(group.fileName, result.lineIndex)) },
                    ) {
                        SearchResultRow(result, matchedTerms)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileGroupHeader(fileName: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.grove
    val angle by animateFloatAsState(if (collapsed) -90f else 0f, label = "groupCaret")
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .clickable(onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▾", fontSize = 11.sp, color = c.ink3,
            modifier = Modifier.graphicsLayer { rotationZ = angle },
        )
        Spacer(Modifier.width(9.dp))
        Text(fileName, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = c.ink)
        Spacer(Modifier.width(9.dp))
        Text("$count " + if (count == 1) "match" else "matches", fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink3)
    }
}

private enum class PillKind { SCHEDULED, DEADLINE, CLOSED, CREATED }

@Composable
private fun DatePillText(label: String, overdue: Boolean, kind: PillKind) {
    val c = MaterialTheme.grove
    val (fg, bg) = when {
        overdue -> c.red to c.redSoft
        kind == PillKind.DEADLINE -> c.amber to c.amberSoft
        else -> c.blue to c.blueSoft
    }
    val icon = if (kind == PillKind.DEADLINE) Icons.Filled.Flag else Icons.Outlined.CalendarMonth
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(10.5.dp))
        Text(
            label,
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = fg,
        )
    }
}

/**
 * Renders org inline markup (links show their description, not `[[url][desc]]`; rows are
 * themselves clickable so links are styled-only, not independently tappable) and overlays
 * search-term highlighting on top, computed against the already-rendered text so positions
 * stay correct even when link syntax shortens it.
 */
private fun highlightedOrgText(text: String, terms: List<String>, c: com.rrajath.grove.ui.theme.GroveColors): AnnotatedString {
    val base = annotateOrgInline(text, c)
    val highlights = Snippets.highlightRanges(base.text, terms)
    if (highlights.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        highlights.forEach { range ->
            addStyle(
                SpanStyle(color = c.amber, background = c.amberSoft, fontWeight = FontWeight.SemiBold),
                range.first, (range.last + 1).coerceAtMost(base.text.length),
            )
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, matchedTerms: List<String>) {
    val c = MaterialTheme.grove
    val titleText = remember(result.title, matchedTerms, c) { highlightedOrgText(result.title, matchedTerms, c) }
    val snippetText = if (result.snippet.text.isNotEmpty()) {
        remember(result.snippet.text, matchedTerms, c) { highlightedOrgText(result.snippet.text, matchedTerms, c) }
    } else null
    // A done-type item's dates are no longer actionable, so they're not worth
    // surfacing in results (unlike the still-open items these pills exist for).
    val showDates = !result.isDone
    val hasMeta = (showDates && (result.scheduledLabel != null || result.deadlineLabel != null)) ||
        result.tagLine.isNotEmpty()
    ResultRowContent(
        keyword = result.keyword,
        isDone = result.isDone,
        priority = result.priority,
        titleText = titleText,
        snippetText = snippetText,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(start = 16.dp, top = 9.dp, end = 11.dp, bottom = 11.dp),
        metaContent = if (hasMeta) {
            {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showDates) {
                        result.deadlineLabel?.let { DatePillText(it, overdue = result.deadlineOverdue, kind = PillKind.DEADLINE) }
                        result.scheduledLabel?.let { DatePillText(it, overdue = result.scheduledOverdue, kind = PillKind.SCHEDULED) }
                    }
                    if (result.tagLine.isNotEmpty()) {
                        Text(result.tagLine, fontFamily = PlexMono, fontSize = 11.sp, color = c.synTag)
                    }
                }
            }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    filters: SearchFilters,
    catalog: SearchCatalog,
    resultCount: Int,
    onToggleTag: (String) -> Unit,
    onToggleState: (String) -> Unit,
    onTogglePriority: (String) -> Unit,
    onSetScheduled: (DatePreset) -> Unit,
    onSetScheduledRange: (LocalDate, LocalDate) -> Unit,
    onSetDeadline: (DatePreset) -> Unit,
    onSetDeadlineRange: (LocalDate, LocalDate) -> Unit,
    onSetClosed: (DatePreset) -> Unit,
    onSetClosedRange: (LocalDate, LocalDate) -> Unit,
    onSetCreated: (DatePreset) -> Unit,
    onSetCreatedRange: (LocalDate, LocalDate) -> Unit,
    onToggleNotebook: (String) -> Unit,
    onClearNotebooks: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    var rangeTarget by remember { mutableStateOf<PillKind?>(null) }
    // Hundreds of tags/notebooks would otherwise flood the sheet: each section
    // starts at 10 and grows by 10 per "Load more" tap; reset whenever the
    // panel is freshly opened since it's recomposed from scratch each time.
    var visibleNotebooks by remember(catalog.notebooks) { mutableStateOf(minOf(10, catalog.notebooks.size)) }
    var visibleTags by remember(catalog.tags) { mutableStateOf(minOf(10, catalog.tags.size)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Filters", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, color = c.ink)
                    Text(
                        if (filters.activeCount > 0) {
                            "${filters.activeCount} ${if (filters.activeCount == 1) "filter" else "filters"} active"
                        } else {
                            "Nothing filtered yet. Everything shows."
                        },
                        fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (filters.activeCount > 0) {
                    Text(
                        "Reset",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(onClick = onClear)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    )
                }
            }
            HorizontalDivider(color = c.line, modifier = Modifier.padding(top = 8.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                FilterSection("Notebook", negatable = true) {
                    PanelChip("All notebooks", filters.notebooks.isEmpty() && filters.excludedNotebooks.isEmpty()) {
                        onClearNotebooks()
                    }
                    catalog.notebooks.take(visibleNotebooks).forEach { nb ->
                        val st = facetState(nb, filters.notebooks, filters.excludedNotebooks)
                        PanelChip(nb, included = st == FacetState.INCLUDED, excluded = st == FacetState.EXCLUDED) {
                            onToggleNotebook(nb)
                        }
                    }
                    if (visibleNotebooks < catalog.notebooks.size) {
                        LoadMoreChip(catalog.notebooks.size - visibleNotebooks) {
                            visibleNotebooks = minOf(visibleNotebooks + 10, catalog.notebooks.size)
                        }
                    }
                }
                if (catalog.tags.isNotEmpty()) {
                    FilterSection("Tags", negatable = true) {
                        catalog.tags.take(visibleTags).forEach { tag ->
                            val st = facetState(tag, filters.tags, filters.excludedTags)
                            PanelChip(":$tag:", included = st == FacetState.INCLUDED, excluded = st == FacetState.EXCLUDED) {
                                onToggleTag(tag)
                            }
                        }
                        if (visibleTags < catalog.tags.size) {
                            LoadMoreChip(catalog.tags.size - visibleTags) {
                                visibleTags = minOf(visibleTags + 10, catalog.tags.size)
                            }
                        }
                    }
                }
                if (catalog.states.isNotEmpty()) {
                    FilterSection("TODO state", negatable = true) {
                        catalog.states.forEach { st ->
                            val label = if (st == NO_STATE) "no state" else st
                            val chipState = facetState(st, filters.states, filters.excludedStates)
                            PanelChip(label, included = chipState == FacetState.INCLUDED, excluded = chipState == FacetState.EXCLUDED) {
                                onToggleState(st)
                            }
                        }
                    }
                }
                FilterSection("Scheduled") {
                    DatePreset.entries.filter { it != DatePreset.ANY && it != DatePreset.CUSTOM }.forEach { preset ->
                        PanelChip(preset.label, filters.scheduled == preset) { onSetScheduled(preset) }
                    }
                    PanelChip(customRangeLabel(filters.scheduledRange), filters.scheduled == DatePreset.CUSTOM) {
                        if (filters.scheduled == DatePreset.CUSTOM) onSetScheduled(DatePreset.CUSTOM)
                        else rangeTarget = PillKind.SCHEDULED
                    }
                }
                FilterSection("Deadline") {
                    DatePreset.entries.filter { it != DatePreset.ANY && it != DatePreset.CUSTOM }.forEach { preset ->
                        PanelChip(preset.label, filters.deadline == preset) { onSetDeadline(preset) }
                    }
                    PanelChip(customRangeLabel(filters.deadlineRange), filters.deadline == DatePreset.CUSTOM) {
                        if (filters.deadline == DatePreset.CUSTOM) onSetDeadline(DatePreset.CUSTOM)
                        else rangeTarget = PillKind.DEADLINE
                    }
                }
                FilterSection("Closed") {
                    DatePreset.entries.filter { it != DatePreset.ANY && it != DatePreset.CUSTOM }.forEach { preset ->
                        PanelChip(preset.label, filters.closed == preset) { onSetClosed(preset) }
                    }
                    PanelChip(customRangeLabel(filters.closedRange), filters.closed == DatePreset.CUSTOM) {
                        if (filters.closed == DatePreset.CUSTOM) onSetClosed(DatePreset.CUSTOM)
                        else rangeTarget = PillKind.CLOSED
                    }
                }
                FilterSection("Created") {
                    DatePreset.entries.filter { it != DatePreset.ANY && it != DatePreset.CUSTOM }.forEach { preset ->
                        PanelChip(preset.label, filters.created == preset) { onSetCreated(preset) }
                    }
                    PanelChip(customRangeLabel(filters.createdRange), filters.created == DatePreset.CUSTOM) {
                        if (filters.created == DatePreset.CUSTOM) onSetCreated(DatePreset.CUSTOM)
                        else rangeTarget = PillKind.CREATED
                    }
                }
                FilterSection("Priority", negatable = true) {
                    listOf("A", "B", "C").forEach { p ->
                        val st = facetState(p, filters.priorities, filters.excludedPriorities)
                        PanelChip("[#$p]", included = st == FacetState.INCLUDED, excluded = st == FacetState.EXCLUDED) {
                            onTogglePriority(p)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.accent)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (resultCount > 0) "Show $resultCount ${if (resultCount == 1) "result" else "results"}" else "No matches",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.accentInk,
                    )
                }
            }
        }
    }

    rangeTarget?.let { target ->
        val current = when (target) {
            PillKind.SCHEDULED -> filters.scheduledRange
            PillKind.DEADLINE -> filters.deadlineRange
            PillKind.CLOSED -> filters.closedRange
            PillKind.CREATED -> filters.createdRange
        }
        CustomDateRangePicker(
            initialStart = current?.start,
            initialEnd = current?.end,
            onDismiss = { rangeTarget = null },
            onConfirm = { start, end ->
                when (target) {
                    PillKind.SCHEDULED -> onSetScheduledRange(start, end)
                    PillKind.DEADLINE -> onSetDeadlineRange(start, end)
                    PillKind.CLOSED -> onSetClosedRange(start, end)
                    PillKind.CREATED -> onSetCreatedRange(start, end)
                }
                rangeTarget = null
            },
        )
    }
}

private fun customRangeLabel(range: DateRange?): String {
    if (range == null) return "Custom range"
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    return "${range.start.format(fmt)} – ${range.end.format(fmt)}"
}

/** [negatable] sections show a hint next to the label that tapping a chip a
 *  second time excludes it, since those are the only facets whose chips cycle
 *  through [FacetState.EXCLUDED] (see [cycleFacet]) rather than just toggling. */
@Composable
private fun FilterSection(label: String, negatable: Boolean = false, content: @Composable FlowRowScope.() -> Unit) {
    val c = MaterialTheme.grove
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                fontFamily = PlexSans, fontSize = 11.sp, letterSpacing = 0.07.em, color = c.ink3,
            )
            if (negatable) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "tap again to exclude",
                    fontFamily = PlexSans, fontSize = 10.5.sp, color = c.ink3,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            content()
        }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(top = 14.dp))
    }
}

/** [excluded] renders a NOT-styled chip (see [FacetState.EXCLUDED]); date
 *  presets and other non-tri-state chips just pass [included]. */
@Composable
private fun PanelChip(label: String, included: Boolean, excluded: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val bg = when {
        included -> c.accent
        excluded -> c.redSoft
        else -> c.surface
    }
    val border = when {
        included -> Color.Transparent
        excluded -> c.red
        else -> c.line
    }
    val fg = when {
        included -> c.accentInk
        excluded -> c.red
        else -> c.ink2
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            if (excluded) "¬ $label" else label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            color = fg,
        )
    }
}

@Composable
private fun LoadMoreChip(remaining: Int, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            "Load ${minOf(10, remaining)} more",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = c.ink2,
        )
    }
}
