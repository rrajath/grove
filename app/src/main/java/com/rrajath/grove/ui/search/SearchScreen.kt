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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.search.Snippets
import com.rrajath.grove.ui.components.CustomDateRangePicker
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.priorityColor
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OPERATOR_CHIPS = listOf("t.TAG", "i.STATE", "s.PERIOD", "b.NOTEBOOK", "p.PRIORITY")

/** Full-text + faceted search, results grouped by file (design spec §9
 *  "Search B — panel"). Finding a specific note; for upcoming/overdue browsing
 *  see the dedicated Agenda screen. */
@Composable
fun SearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()
    var advancedOpen by remember { mutableStateOf(false) }
    var filterPanelOpen by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val collapsedFiles = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.submit(initialQuery)
        else focusRequester.requestFocus()
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
                    Text("⌕", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
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
                    IconGlyph("☆", onClick = { saveDialogOpen = true })
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
                ExprPreview(viewModel.composedExpression())
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
                        onOpenTags = { filterPanelOpen = true },
                        onSavedTap = viewModel::submit,
                        onRenameSaved = viewModel::renameSavedSearch,
                        onDeleteSaved = viewModel::deleteSavedSearch,
                    )
                    state.groups.isEmpty() -> NoResultsState(onOpenFilters = { filterPanelOpen = true })
                    else -> GroupedResultsList(listState, state.groups, state.matchedTerms, collapsedFiles, onOpenNote)
                }
                ScrollJumpButtons(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
            onSetNotebook = viewModel::setNotebookScope,
            onClear = viewModel::clearFilters,
            onDismiss = { filterPanelOpen = false },
        )
    }

    if (saveDialogOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            containerColor = c.surface,
            title = {
                Text("Save search", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink)
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
                        viewModel.saveCurrentSearch(name)
                        saveDialogOpen = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) { Text("Cancel", color = c.ink2) }
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

@Composable
private fun ExprPreview(text: String) {
    val c = MaterialTheme.grove
    Text(
        text,
        fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    )
}

@Composable
private fun AdvancedPanel(onChipTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Text(
            "Operators — space = AND · OR · prefix . = NOT · o.PROP sorts",
            fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OPERATOR_CHIPS.take(3).forEach { OpChip(it, onChipTap); Spacer(Modifier.width(8.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            OPERATOR_CHIPS.drop(3).forEach { OpChip(it, onChipTap); Spacer(Modifier.width(8.dp)) }
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
                Text("⚙", fontSize = 13.sp, color = c.accentInk)
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
    val icon: String,
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
    onOpenTags: () -> Unit,
    onSavedTap: (String) -> Unit,
    onRenameSaved: (id: String, name: String) -> Unit,
    onDeleteSaved: (id: String) -> Unit,
) {
    val c = MaterialTheme.grove
    val cards = listOf(
        QuickCard("!", "Overdue", "${quickCounts.overdue} past their date", c.red, c.redSoft) {
            onQuick(SearchFilters(deadline = DatePreset.OVERDUE))
        },
        QuickCard("◷", "Today", "${quickCounts.today} scheduled or due", c.amber, c.amberSoft) {
            onQuick(SearchFilters(scheduled = DatePreset.TODAY))
        },
        QuickCard(
            "□", "Open tasks",
            "${quickCounts.openTasks} ${if (quickCounts.openTasks == 1) "TODO item" else "TODO items"}",
            c.blue, c.blueSoft,
        ) {
            onQuick(SearchFilters(states = activeStates.toSet()))
        },
        QuickCard("#", "Browse tags", "Tap to filter by tag", c.synTag, c.accentSoft, onOpenTags),
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
                        verticalAlignment = Alignment.CenterVertically,
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
            Text(card.icon, fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = card.fg)
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
) {
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
                    SearchResultRow(result, matchedTerms) { onOpenNote(NoteRef(group.fileName, result.lineIndex)) }
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

private enum class PillKind { SCHEDULED, DEADLINE }

@Composable
private fun DatePillText(label: String, overdue: Boolean, kind: PillKind) {
    val c = MaterialTheme.grove
    val (fg, bg) = when {
        overdue -> c.red to c.redSoft
        kind == PillKind.DEADLINE -> c.amber to c.amberSoft
        else -> c.blue to c.blueSoft
    }
    Text(
        label,
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
    )
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
private fun SearchResultRow(result: SearchResult, matchedTerms: List<String>, onOpenNote: () -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenNote)
            .padding(start = 16.dp, top = 9.dp, end = 11.dp, bottom = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            result.keyword?.let { kw ->
                Pill(kw, fg = if (result.isDone) c.green else c.amber, bg = if (result.isDone) c.greenSoft else c.amberSoft)
                Spacer(Modifier.width(7.dp))
            }
            result.priority?.let { p ->
                Text(
                    "[#$p]", fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = c.priorityColor(p[0]),
                )
                Spacer(Modifier.width(7.dp))
            }
            val titleText = remember(result.title, matchedTerms, c) {
                highlightedOrgText(result.title, matchedTerms, c)
            }
            Text(titleText, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.ink)
        }
        if (result.snippet.text.isNotEmpty()) {
            val highlighted = remember(result.snippet.text, matchedTerms, c) {
                highlightedOrgText(result.snippet.text, matchedTerms, c)
            }
            Text(
                highlighted, fontFamily = PlexSans, fontSize = 12.5.sp, lineHeight = 1.5.em, color = c.ink2,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        val hasMeta = result.scheduledLabel != null || result.deadlineLabel != null || result.tagLine.isNotEmpty()
        if (hasMeta) {
            FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                result.deadlineLabel?.let { DatePillText(it, overdue = result.deadlineOverdue, kind = PillKind.DEADLINE) }
                result.scheduledLabel?.let { DatePillText(it, overdue = result.scheduledOverdue, kind = PillKind.SCHEDULED) }
                if (result.tagLine.isNotEmpty()) {
                    Text(result.tagLine, fontFamily = PlexMono, fontSize = 11.sp, color = c.synTag)
                }
            }
        }
    }
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
    onSetNotebook: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    var rangeTarget by remember { mutableStateOf<PillKind?>(null) }
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
                            "Nothing filtered yet — everything shows."
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
                FilterSection("Notebook") {
                    PanelChip("All notebooks", filters.notebook == null) { onSetNotebook(null) }
                    catalog.notebooks.forEach { nb -> PanelChip(nb, filters.notebook == nb) { onSetNotebook(nb) } }
                }
                if (catalog.tags.isNotEmpty()) {
                    FilterSection("Tags") {
                        catalog.tags.forEach { tag -> PanelChip(":$tag:", filters.tags.contains(tag)) { onToggleTag(tag) } }
                    }
                }
                if (catalog.states.isNotEmpty()) {
                    FilterSection("TODO state") {
                        catalog.states.forEach { st ->
                            PanelChip(if (st == NO_STATE) "no state" else st, filters.states.contains(st)) { onToggleState(st) }
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
                FilterSection("Priority") {
                    listOf("A", "B", "C").forEach { p -> PanelChip("[#$p]", filters.priorities.contains(p)) { onTogglePriority(p) } }
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
        val current = if (target == PillKind.SCHEDULED) filters.scheduledRange else filters.deadlineRange
        CustomDateRangePicker(
            initialStart = current?.start,
            initialEnd = current?.end,
            onDismiss = { rangeTarget = null },
            onConfirm = { start, end ->
                if (target == PillKind.SCHEDULED) onSetScheduledRange(start, end) else onSetDeadlineRange(start, end)
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

@Composable
private fun FilterSection(label: String, content: @Composable FlowRowScope.() -> Unit) {
    val c = MaterialTheme.grove
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            label.uppercase(),
            fontFamily = PlexSans, fontSize = 11.sp, letterSpacing = 0.07.em, color = c.ink3,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            content()
        }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun PanelChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.accent else c.surface)
            .border(1.dp, if (active) Color.Transparent else c.line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
            color = if (active) c.accentInk else c.ink2,
        )
    }
}
