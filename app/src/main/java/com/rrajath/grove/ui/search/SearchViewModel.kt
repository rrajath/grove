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
import com.rrajath.grove.ui.vault.factory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Sentinel bucket for notes with no TODO keyword, in both the states filter
 *  and the states catalog — distinct from any real keyword string. */
const val NO_STATE = "—"

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

data class SearchFilters(
    val tags: Set<String> = emptySet(),
    val states: Set<String> = emptySet(),
    val priorities: Set<String> = emptySet(),
    val scheduled: DatePreset = DatePreset.ANY,
    val scheduledRange: DateRange? = null,
    val deadline: DatePreset = DatePreset.ANY,
    val deadlineRange: DateRange? = null,
    val notebook: String? = null,
) {
    val activeCount: Int
        get() = (if (tags.isNotEmpty()) 1 else 0) +
            (if (states.isNotEmpty()) 1 else 0) +
            (if (priorities.isNotEmpty()) 1 else 0) +
            (if (scheduled != DatePreset.ANY) 1 else 0) +
            (if (deadline != DatePreset.ANY) 1 else 0) +
            (if (notebook != null) 1 else 0)
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
    /** Configured todo-type (non-done) keywords — backs the "Open tasks" quick card. */
    val activeStates: List<String> = emptyList(),
    /** Current query's plain-text terms — result rows highlight these after org-rendering. */
    val matchedTerms: List<String> = emptyList(),
)

/** Full-text + faceted search, results grouped by file (design spec §9 "Search
 *  B — panel"). Agenda's day-grouped/Overdue view now lives on its own screen. */
class SearchViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val savedSearches: StateFlow<List<SavedSearch>> = app.searchRepository.savedSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun toggleTag(tag: String) = filtersFlow.update { it.copy(tags = it.tags.toggled(tag)) }
    fun toggleState(state: String) = filtersFlow.update { it.copy(states = it.states.toggled(state)) }
    fun togglePriority(priority: String) = filtersFlow.update { it.copy(priorities = it.priorities.toggled(priority)) }

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

    fun setNotebookScope(name: String?) =
        filtersFlow.update { it.copy(notebook = if (it.notebook == name) null else name) }

    /**
     * Scope the search to one notebook without the toggle semantics of
     * [setNotebookScope] — the Outline's search action arrives with a file
     * already in mind, so re-entering the same notebook must keep it pinned
     * rather than clearing it.
     */
    fun pinNotebook(name: String) {
        filtersFlow.update { it.copy(notebook = name) }
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

    /** Human-readable preview of the composed query + active filters, for the
     *  Advanced panel — informational only, not re-parsed. */
    fun composedExpression(): String {
        val f = _state.value.filters
        val parts = mutableListOf<String>()
        _state.value.query.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        f.tags.sorted().forEach { parts += "t.$it" }
        f.states.sorted().forEach { parts += "i.${if (it == NO_STATE) "none" else it.lowercase()}" }
        f.priorities.sorted().forEach { parts += "p.$it" }
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
        f.notebook?.let { parts += "b.${it.removeSuffix(".org")}" }
        return if (parts.isEmpty()) "(everything)" else parts.joinToString(" ")
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
        notebook = notebook,
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
        if (f.states.isNotEmpty() && (note.keyword ?: NO_STATE) !in f.states) return false
        if (f.priorities.isNotEmpty() && note.priority !in f.priorities) return false
        if (f.scheduled != DatePreset.ANY &&
            !datePresetMatches(note.scheduledDate, f.scheduled, today, f.scheduledRange, note.isDoneKeyword)
        ) return false
        if (f.deadline != DatePreset.ANY &&
            !datePresetMatches(note.deadlineDate, f.deadline, today, f.deadlineRange, note.isDoneKeyword)
        ) return false
        if (f.notebook != null && note.fileName != f.notebook) return false
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
            scheduledLabel = scheduledLabel?.let { "SCHED $it" },
            scheduledOverdue = scheduledOverdue,
            deadlineLabel = deadlineLabel?.let { "DEADLINE $it" },
            deadlineOverdue = deadlineOverdue,
            tagLine = meta.tags.joinToString(" ") { ":$it:" },
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

private fun <T> Set<T>.toggled(value: T): Set<T> = if (contains(value)) this - value else this + value
