package com.rrajath.grove.ui.search

import android.database.SQLException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.NoteEntity
import com.rrajath.grove.data.NoteFacetRow
import com.rrajath.grove.data.rawQuery
import com.rrajath.grove.data.toNoteMeta
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.search.DatePresence
import com.rrajath.grove.search.FacetNarrowing
import com.rrajath.grove.search.FtsQuery
import com.rrajath.grove.search.NoteCandidateQuery
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.search.QueryMatcher
import com.rrajath.grove.search.QueryParser
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.search.SearchQuery
import com.rrajath.grove.search.Snippets
import com.rrajath.grove.ui.vault.OutlineSnack
import com.rrajath.grove.ui.vault.factory
import com.rrajath.grove.ui.vault.headlineAtLine
import com.rrajath.grove.vault.AutoArchive
import com.rrajath.grove.vault.StateChangeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Sentinel bucket for notes with no TODO keyword, in both the states filter
 *  and the states catalog, distinct from any real keyword string. */
const val NO_STATE = "-"

enum class DatePreset(val label: String, val token: String) {
    ANY("Any", "any"),
    TODAY("Today", "today"),
    NEXT_7_DAYS("Next 7 days", "7d"),
    OVERDUE("Overdue", "overdue"),
    NO_DATE("No date", "none"),
    CUSTOM("Custom range", "custom"),
}

/** Inclusive start/end for [DatePreset.CUSTOM]. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Each facet is tri-state: a value is included, excluded (NOT), or absent from
 * both sets: [FilterPanel]'s chips cycle a tapped value through exactly those
 * three states. Included values OR together (e.g. tag=work OR tag=urgent);
 * excluded values each independently rule a note out.
 */
data class SearchFilters(
    val tags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val states: Set<String> = emptySet(),
    val excludedStates: Set<String> = emptySet(),
    val priorities: Set<String> = emptySet(),
    val excludedPriorities: Set<String> = emptySet(),
    val scheduled: DatePreset = DatePreset.ANY,
    val scheduledRange: DateRange? = null,
    val deadline: DatePreset = DatePreset.ANY,
    val deadlineRange: DateRange? = null,
    val notebooks: Set<String> = emptySet(),
    val excludedNotebooks: Set<String> = emptySet(),
) {
    val activeCount: Int
        get() = (if (tags.isNotEmpty() || excludedTags.isNotEmpty()) 1 else 0) +
            (if (states.isNotEmpty() || excludedStates.isNotEmpty()) 1 else 0) +
            (if (priorities.isNotEmpty() || excludedPriorities.isNotEmpty()) 1 else 0) +
            (if (scheduled != DatePreset.ANY) 1 else 0) +
            (if (deadline != DatePreset.ANY) 1 else 0) +
            (if (notebooks.isNotEmpty() || excludedNotebooks.isNotEmpty()) 1 else 0)
}

/** A facet chip's current tri-state, for [FilterPanel]'s chip styling. */
enum class FacetState { NONE, INCLUDED, EXCLUDED }

fun <T> facetState(value: T, included: Set<T>, excluded: Set<T>): FacetState = when {
    value in included -> FacetState.INCLUDED
    value in excluded -> FacetState.EXCLUDED
    else -> FacetState.NONE
}

/** Cycles [value] through include -> exclude -> none across the given pair of sets. */
private fun <T> cycleFacet(included: Set<T>, excluded: Set<T>, value: T): Pair<Set<T>, Set<T>> = when (value) {
    in included -> (included - value) to (excluded + value)
    in excluded -> included to (excluded - value)
    else -> (included + value) to excluded
}

data class SearchResult(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    val keyword: String?,
    val isDone: Boolean,
    val priority: String?,
    val snippet: Snippets.Snippet,
    val scheduledLabel: String?,
    val scheduledOverdue: Boolean,
    val deadlineLabel: String?,
    val deadlineOverdue: Boolean,
    val tagLine: String,
    /** Raw timestamps (vs. the display-only labels above) for the swipe-to-schedule action's date picker. */
    val scheduledTs: OrgTimestamp?,
    val deadlineTs: OrgTimestamp?,
)

data class SearchFileGroup(val fileName: String, val results: List<SearchResult>)

data class SearchCatalog(
    val tags: List<String> = emptyList(),
    val states: List<String> = emptyList(),
    val notebooks: List<String> = emptyList(),
)

data class QuickCounts(val overdue: Int = 0, val today: Int = 0, val openTasks: Int = 0)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val groups: List<SearchFileGroup> = emptyList(),
    val resultCount: Int = 0,
    val notebookCount: Int = 0,
    val isBlank: Boolean = true,
    val catalog: SearchCatalog = SearchCatalog(),
    val quickCounts: QuickCounts = QuickCounts(),
    /** Configured todo-type (non-done) keywords: backs the "Open tasks" quick card. */
    val activeStates: List<String> = emptyList(),
    /** Current query's plain-text terms: result rows highlight these after org-rendering. */
    val matchedTerms: List<String> = emptyList(),
)

/** Full-text + faceted search, results grouped by file (design spec §9 "Search
 *  B: panel"). Agenda's day-grouped/Overdue view now lives on its own screen. */
class SearchViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val _snack = MutableStateFlow<OutlineSnack?>(null)
    val snack: StateFlow<OutlineSnack?> = _snack
    private var eventId = 0L
    private var undoSnapshot: List<Pair<String, String>>? = null

    private fun showSnack(message: String) {
        val s = OutlineSnack(message, ++eventId)
        _snack.value = s
        viewModelScope.launch {
            delay(4200)
            if (_snack.value?.id == s.id) _snack.value = null
        }
    }

    /** Reverts the auto-archive move (state change + refile) from the most recent [setState]. */
    fun undo() {
        val snap = undoSnapshot ?: return
        undoSnapshot = null
        _snack.value = null
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            snap.forEach { (name, text) -> vault.save(name, text) }
            app.syncManager.requestSync("search undo")
        }
    }

    val savedSearches: StateFlow<List<SavedSearch>> = app.searchRepository.savedSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Configured TODO keywords, for the swipe-to-cycle state sheet. */
    val keywords: StateFlow<OrgKeywords> = app.keywords

    private val queryFlow = MutableStateFlow("")
    private val filtersFlow = MutableStateFlow(SearchFilters())

    // Whole-vault facet projection: no titles, no bodies. Backs the filter
    // catalog and the blank-state quick counts, and doubles as the
    // index-changed signal for re-running the active search. Result rows
    // themselves are loaded per search (see loadCandidates), so note text is
    // never resident for the whole vault.
    private val facets = MutableStateFlow<List<NoteFacets>?>(null)

    /** Whole-vault totals, for the Filters sheet's count while nothing is narrowed. */
    private var vaultNoteCount = 0
    private var vaultNotebookCount = 0

    /** The filter-derived tokens last spliced into the query text, so the next
     *  filter change can find-and-replace just that substring (see [runSearch]
     *  doc on filters staying authoritative; this splice is display-only). */
    private var lastFilterExpr: String = ""

    init {
        viewModelScope.launch {
            app.database.indexDao().noteFacets()
                .map { rows -> rows.map { it.toFacets() } }
                .flowOn(Dispatchers.Default)
                .collect { rows ->
                    facets.value = rows
                    updateCatalogAndCounts(rows)
                }
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            // Re-runs on new keystrokes, filter taps, and when the index
            // changes under an active query (e.g. a sync finishing while the
            // search screen is up).
            combine(queryFlow.debounce(300), facets.filterNotNull(), filtersFlow) { q, _, f -> q to f }
                .collect { (q, f) -> runSearch(q, f) }
        }
        viewModelScope.launch {
            // Mirrors Filters-panel selections into the editable query field as
            // t./i./b./p. expression tokens, so picking a facet is visible and
            // hand-editable right in the field instead of a separate read-only
            // preview. Matching itself still runs off the structured filters
            // (see matchesFilters), so hand-editing or deleting the mirrored
            // tokens only changes what's displayed, not what's excluded.
            filtersFlow.collect { f ->
                val newExpr = filterExpression(f)
                if (newExpr != lastFilterExpr) {
                    val spliced = spliceFilterExpr(_state.value.query, lastFilterExpr, newExpr)
                    lastFilterExpr = newExpr
                    if (spliced != _state.value.query) {
                        _state.value = _state.value.copy(query = spliced)
                        queryFlow.value = spliced
                    }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        queryFlow.value = query
    }

    /** Run immediately (drawer shortcuts, saved-search taps). */
    fun submit(query: String) {
        _state.value = _state.value.copy(query = query)
        queryFlow.value = query
    }

    fun saveCurrentSearch(name: String) {
        val query = _state.value.query
        if (query.isBlank() || name.isBlank()) return
        viewModelScope.launch { app.searchRepository.saveSearch(name.trim(), query.trim()) }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch { app.searchRepository.deleteSearch(id) }
    }

    fun renameSavedSearch(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { app.searchRepository.renameSearch(id, name.trim()) }
    }

    /**
     * Swipe-to-cycle-state action: set a result's TODO state to exactly the
     * keyword picked from the state sheet (null = clear it), via
     * [OrgMutations.changeKeyword], same "mark done" / "reopen and drop
     * CLOSED" semantics as the Outline's own state sheet. Results span many
     * files, so (unlike the Outline's single open document) the target file is
     * opened and saved on demand, the way Agenda's row swipes do.
     */
    fun setState(fileName: String, lineIndex: Int, keyword: String?) {
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val doc = vault.open(fileName) ?: return@launch
            val headline = doc.headlineAtLine(lineIndex) ?: return@launch
            if (headline.keyword == keyword) return@launch
            val settings = app.settingsRepository.settings.first()
            when (
                val result = AutoArchive.apply(vault, settings, doc, fileName, headline, keyword, LocalDateTime.now())
            ) {
                is StateChangeResult.Plain -> {
                    vault.save(fileName, result.text)
                    app.syncManager.requestSync("search state set")
                }
                is StateChangeResult.Archived -> {
                    undoSnapshot = if (result.sourceFile == result.destFile) {
                        listOf(result.sourceFile to doc.text)
                    } else {
                        listOf(fileName to doc.text, result.destFile to result.destTextBefore)
                    }
                    vault.save(fileName, result.sourceText)
                    if (result.destFile != fileName) vault.save(result.destFile, result.destText)
                    app.syncManager.requestSync("search state set")
                    showSnack("Marked done. Refiled to ${result.label}")
                }
            }
        }
    }

    /** Swipe-to-schedule action: both planning dates in one edit, as the Dates screen commits. */
    fun setPlanningDates(fileName: String, lineIndex: Int, scheduled: OrgTimestamp?, deadline: OrgTimestamp?) {
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val doc = vault.open(fileName) ?: return@launch
            val headline = doc.headlineAtLine(lineIndex) ?: return@launch
            val newText = withContext(Dispatchers.Default) {
                OrgMutations.setPlanningDates(doc, headline, scheduled, deadline)
            }
            vault.save(fileName, newText)
            app.syncManager.requestSync("search planning edit")
        }
    }

    fun toggleTag(tag: String) = filtersFlow.update {
        val (inc, exc) = cycleFacet(it.tags, it.excludedTags, tag)
        it.copy(tags = inc, excludedTags = exc)
    }

    fun toggleState(state: String) = filtersFlow.update {
        val (inc, exc) = cycleFacet(it.states, it.excludedStates, state)
        it.copy(states = inc, excludedStates = exc)
    }

    fun togglePriority(priority: String) = filtersFlow.update {
        val (inc, exc) = cycleFacet(it.priorities, it.excludedPriorities, priority)
        it.copy(priorities = inc, excludedPriorities = exc)
    }

    fun toggleNotebook(name: String) = filtersFlow.update {
        val (inc, exc) = cycleFacet(it.notebooks, it.excludedNotebooks, name)
        it.copy(notebooks = inc, excludedNotebooks = exc)
    }

    fun clearNotebooks() = filtersFlow.update { it.copy(notebooks = emptySet(), excludedNotebooks = emptySet()) }

    fun setScheduledPreset(preset: DatePreset) =
        filtersFlow.update {
            if (it.scheduled == preset) it.copy(scheduled = DatePreset.ANY, scheduledRange = null)
            else it.copy(scheduled = preset, scheduledRange = null)
        }

    fun setDeadlinePreset(preset: DatePreset) =
        filtersFlow.update {
            if (it.deadline == preset) it.copy(deadline = DatePreset.ANY, deadlineRange = null)
            else it.copy(deadline = preset, deadlineRange = null)
        }

    fun setScheduledRange(start: LocalDate, end: LocalDate) =
        filtersFlow.update { it.copy(scheduled = DatePreset.CUSTOM, scheduledRange = DateRange(start, end)) }

    fun setDeadlineRange(start: LocalDate, end: LocalDate) =
        filtersFlow.update { it.copy(deadline = DatePreset.CUSTOM, deadlineRange = DateRange(start, end)) }

    /**
     * Scope the search to exactly one notebook, replacing any existing
     * notebook selection: the Outline's search action arrives with a file
     * already in mind, so this pins that file rather than toggling it in
     * alongside whatever else was selected.
     */
    fun pinNotebook(name: String) {
        filtersFlow.update { it.copy(notebooks = setOf(name), excludedNotebooks = emptySet()) }
    }

    fun clearFilters() {
        filtersFlow.value = SearchFilters()
    }

    /** Quick-start shortcuts (design spec §9 blank-state cards): replace the
     *  free-text query and filters wholesale with the shortcut's own facet. */
    fun applyQuickFilter(filters: SearchFilters) {
        queryFlow.value = ""
        _state.value = _state.value.copy(query = "")
        filtersFlow.value = filters
    }

    /** Back button: clear back to the blank quick-start view instead of leaving
     *  the screen, when a query or filter is active (see SearchScreen's onBack). */
    fun resetToBlank() {
        queryFlow.value = ""
        _state.value = _state.value.copy(query = "")
        filtersFlow.value = SearchFilters()
    }

    /** The filter-panel selection expressed as query tokens, in the same
     *  t./i./b./p. syntax the field itself parses (see [lastFilterExpr]). */
    private fun filterExpression(f: SearchFilters): String {
        val parts = mutableListOf<String>()
        f.tags.sorted().forEach { parts += "t.$it" }
        f.excludedTags.sorted().forEach { parts += ".t.$it" }
        f.states.sorted().forEach { parts += "i.${stateToken(it)}" }
        f.excludedStates.sorted().forEach { parts += ".i.${stateToken(it)}" }
        f.priorities.sorted().forEach { parts += "p.$it" }
        f.excludedPriorities.sorted().forEach { parts += ".p.$it" }
        if (f.scheduled == DatePreset.CUSTOM && f.scheduledRange != null) {
            parts += "s.${f.scheduledRange.start}..${f.scheduledRange.end}"
        } else if (f.scheduled != DatePreset.ANY) {
            parts += "s.${f.scheduled.token}"
        }
        if (f.deadline == DatePreset.CUSTOM && f.deadlineRange != null) {
            parts += "d.${f.deadlineRange.start}..${f.deadlineRange.end}"
        } else if (f.deadline != DatePreset.ANY) {
            parts += "d.${f.deadline.token}"
        }
        f.notebooks.sorted().forEach { parts += "b.${it.removeSuffix(".org")}" }
        f.excludedNotebooks.sorted().forEach { parts += ".b.${it.removeSuffix(".org")}" }
        return parts.joinToString(" ")
    }

    private fun stateToken(state: String) = if (state == NO_STATE) "none" else state.lowercase()

    /** Replaces the previous filter-token substring with the new one inside
     *  [current], leaving whatever the user typed themselves untouched. */
    private fun spliceFilterExpr(current: String, old: String, new: String): String {
        val withoutOld = if (old.isNotEmpty() && current.contains(old)) {
            current.replace(old, " ").replace(Regex("\\s+"), " ").trim()
        } else {
            current.trim()
        }
        return when {
            new.isEmpty() -> withoutOld
            withoutOld.isEmpty() -> new
            else -> "$withoutOld $new"
        }
    }

    private suspend fun runSearch(raw: String, filters: SearchFilters) {
        val textQuery = if (raw.isBlank()) null else QueryParser.parse(raw)

        // Nothing typed and nothing filtered: the screen shows the quick-start
        // cards, so there are no results to build. The Filters sheet still wants
        // the whole-vault totals for its "Show N results" button.
        if (raw.isBlank() && filters.activeCount == 0) {
            _state.value = _state.value.copy(
                filters = filters,
                groups = emptyList(),
                resultCount = vaultNoteCount,
                notebookCount = vaultNotebookCount,
                isBlank = true,
                matchedTerms = emptyList(),
            )
            return
        }

        val notes = loadCandidates(textQuery, filters)

        withContext(Dispatchers.Default) {
            val today = LocalDate.now()
            val textMatched = textQuery?.let { QueryMatcher.filter(notes, it, today) } ?: notes
            val terms = textQuery?.textTerms ?: emptyList()
            val filtered = textMatched.filter { matchesFilters(it, filters, today) }

            val groups = mutableListOf<SearchFileGroup>()
            val indexOfFile = HashMap<String, Int>()
            filtered.forEach { note ->
                val result = toResult(note, terms, today)
                val idx = indexOfFile[note.fileName]
                if (idx == null) {
                    indexOfFile[note.fileName] = groups.size
                    groups.add(SearchFileGroup(note.fileName, listOf(result)))
                } else {
                    groups[idx] = groups[idx].copy(results = groups[idx].results + result)
                }
            }

            _state.value = _state.value.copy(
                filters = filters,
                groups = groups,
                resultCount = filtered.size,
                notebookCount = groups.size,
                isBlank = false,
                matchedTerms = terms,
            )
        }
    }

    /**
     * Loads only the rows this search could possibly match: the FTS5 index
     * supplies the text candidates and the facet chips / structured tokens
     * become SQL predicates. Both narrowings are supersets by construction, so
     * [QueryMatcher] and [matchesFilters] below still decide every result and
     * the visible behaviour is identical to scanning the whole vault.
     */
    private suspend fun loadCandidates(textQuery: SearchQuery?, filters: SearchFilters): List<NoteMeta> {
        val dao = app.database.indexDao()
        val match = textQuery
            ?.takeIf { app.database.ftsAvailable }
            ?.let { FtsQuery.matchExpression(it) }
        val candidate = NoteCandidateQuery.build(match, textQuery, filters.toNarrowing())
        val rows = try {
            dao.notesMatching(rawQuery(candidate.sql, candidate.args))
        } catch (e: SQLException) {
            // A search must never fail because of the index: fall back to the
            // whole table and let the Kotlin matcher do all the work, exactly as
            // it did before this table existed.
            Log.w(TAG, "candidate query failed, falling back to a full scan: ${candidate.sql}", e)
            dao.notesMatching(rawQuery("SELECT * FROM notes ORDER BY fileName, lineIndex"))
        }
        return withContext(Dispatchers.Default) { rows.map { it.toNoteMeta() } }
    }

    private fun SearchFilters.toNarrowing() = FacetNarrowing(
        // A superset-only optimization (see NoteCandidateQuery's doc comment):
        // excludes and multi-notebook selection aren't pushed to SQL, they're
        // just left for matchesFilters below to decide in Kotlin.
        notebook = notebooks.singleOrNull(),
        states = states - NO_STATE,
        includeNoState = NO_STATE in states,
        priorities = priorities,
        tags = tags,
        scheduled = scheduled.presence(),
        deadline = deadline.presence(),
    )

    /** Every date preset except "any" and "no date" needs a timestamp to exist. */
    private fun DatePreset.presence(): DatePresence = when (this) {
        DatePreset.ANY -> DatePresence.ANY
        DatePreset.NO_DATE -> DatePresence.ABSENT
        else -> DatePresence.PRESENT
    }

    private fun matchesFilters(note: NoteMeta, f: SearchFilters, today: LocalDate): Boolean {
        if (f.tags.isNotEmpty() && f.tags.none { note.inheritedTags.contains(it) }) return false
        if (f.excludedTags.isNotEmpty() && f.excludedTags.any { note.inheritedTags.contains(it) }) return false
        val state = note.keyword ?: NO_STATE
        if (f.states.isNotEmpty() && state !in f.states) return false
        if (f.excludedStates.isNotEmpty() && state in f.excludedStates) return false
        if (f.priorities.isNotEmpty() && note.priority !in f.priorities) return false
        if (f.excludedPriorities.isNotEmpty() && note.priority in f.excludedPriorities) return false
        if (f.scheduled != DatePreset.ANY &&
            !datePresetMatches(note.scheduledDate, f.scheduled, today, f.scheduledRange, note.isDoneKeyword)
        ) return false
        if (f.deadline != DatePreset.ANY &&
            !datePresetMatches(note.deadlineDate, f.deadline, today, f.deadlineRange, note.isDoneKeyword)
        ) return false
        if (f.notebooks.isNotEmpty() && note.fileName !in f.notebooks) return false
        if (f.excludedNotebooks.isNotEmpty() && note.fileName in f.excludedNotebooks) return false
        return true
    }

    private fun datePresetMatches(
        date: LocalDate?,
        preset: DatePreset,
        today: LocalDate,
        range: DateRange?,
        isDone: Boolean,
    ): Boolean = when (preset) {
        DatePreset.ANY -> true
        DatePreset.NO_DATE -> date == null
        DatePreset.TODAY -> date == today
        DatePreset.NEXT_7_DAYS -> date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(7))
        // Overdue is only meaningful for tasks that are still open.
        DatePreset.OVERDUE -> !isDone && date != null && date.isBefore(today)
        DatePreset.CUSTOM -> date != null && range != null && date in range
    }

    private fun toResult(meta: NoteMeta, terms: List<String>, today: LocalDate): SearchResult {
        val (scheduledLabel, scheduledOverdue) = dateLabel(meta.scheduledDate, today)
        val (deadlineLabel, deadlineOverdue) = dateLabel(meta.deadlineDate, today)
        return SearchResult(
            fileName = meta.fileName,
            lineIndex = meta.lineIndex,
            title = meta.title,
            keyword = meta.keyword,
            isDone = meta.isDoneKeyword,
            priority = meta.priority,
            snippet = Snippets.build(meta.searchText.substringAfter('\n', ""), terms),
            scheduledLabel = scheduledLabel,
            scheduledOverdue = scheduledOverdue,
            deadlineLabel = deadlineLabel,
            deadlineOverdue = deadlineOverdue,
            tagLine = meta.tags.joinToString(" ") { ":$it:" },
            scheduledTs = meta.scheduled?.let { OrgTimestamp.parse(it) },
            deadlineTs = meta.deadline?.let { OrgTimestamp.parse(it) },
        )
    }

    private fun dateLabel(date: LocalDate?, today: LocalDate): Pair<String?, Boolean> {
        if (date == null) return null to false
        val n = ChronoUnit.DAYS.between(today, date)
        val overdue = n < 0
        val text = when {
            n == 0L -> "today"
            n == 1L -> "tomorrow"
            overdue -> "${-n}d overdue"
            else -> date.format(DAY_FORMAT)
        }
        return text to overdue
    }

    /** Recomputed from the whole vault (not the active filters), so the
     *  Filters panel's chip catalog and the blank-state quick-start counts
     *  stay stable while the user is actively narrowing results. */
    private fun updateCatalogAndCounts(notes: List<NoteFacets>) {
        val today = LocalDate.now()
        val keywords = app.keywords.value
        val tags = notes.flatMap { it.inheritedTags }.distinct().sorted()
        // All configured states (not just ones currently in use), todo-type first
        // then done-type, "no state" last.
        val states = keywords.active + keywords.done + NO_STATE
        val notebooks = notes.map { it.fileName }.distinct().sorted()
        val overdue = notes.count {
            !it.isDone && ((it.scheduledDate?.isBefore(today) == true) || (it.deadlineDate?.isBefore(today) == true))
        }
        val dueToday = notes.count { it.scheduledDate == today || it.deadlineDate == today }
        val openTasks = notes.count { it.keyword != null && !it.isDone }
        vaultNoteCount = notes.size
        vaultNotebookCount = notebooks.size
        _state.value = _state.value.copy(
            catalog = SearchCatalog(tags, states, notebooks),
            quickCounts = QuickCounts(overdue, dueToday, openTasks),
            activeStates = keywords.active,
        )
    }

    /** Facet projection with its planning dates already parsed. */
    private data class NoteFacets(
        val fileName: String,
        val keyword: String?,
        val isDone: Boolean,
        val inheritedTags: List<String>,
        val scheduledDate: LocalDate?,
        val deadlineDate: LocalDate?,
    )

    private fun NoteFacetRow.toFacets() = NoteFacets(
        fileName = fileName,
        keyword = keyword,
        isDone = isDone,
        inheritedTags = inheritedTags.split(':').filter { it.isNotEmpty() },
        scheduledDate = scheduled?.let { OrgTimestamp.parse(it)?.date },
        deadlineDate = deadline?.let { OrgTimestamp.parse(it)?.date },
    )

    companion object {
        val Factory = factory { SearchViewModel(it) }

        private const val TAG = "SearchViewModel"

        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    }
}
