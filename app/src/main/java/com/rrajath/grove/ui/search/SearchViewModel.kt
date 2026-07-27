package com.rrajath.grove.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.NoteEntity
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.search.QueryMatcher
import com.rrajath.grove.search.QueryParser
import com.rrajath.grove.search.SavedSearch
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class SearchResult(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    val keyword: String?,
    val isDone: Boolean,
    val priority: String?,
    val snippet: Snippets.Snippet,
    val breadcrumb: String,
)

data class AgendaDay(val date: LocalDate, val results: List<SearchResult>)

data class AgendaUiState(
    val overdueCount: Int,
    val overdue: List<SearchResult>,
    val days: List<AgendaDay>,
)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val agenda: AgendaUiState? = null,
    val notebookCount: Int = 0,
    val history: List<String> = emptyList(),
)

class SearchViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val savedSearches: StateFlow<List<SavedSearch>> = app.searchRepository.savedSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val queryFlow = MutableStateFlow("")

    // Mapped once per index change (Room invalidation), not per keystroke;
    // NoteMeta also caches its parsed dates, so repeated searches reuse both.
    private val metas = MutableStateFlow<List<NoteMeta>?>(null)

    // Agenda window state: `ad.N` seeds the initial window, loadMoreAgendaDays()
    // grows it as the user scrolls. Cached matched/terms let load-more re-run
    // QueryMatcher.agenda() without re-filtering the whole note set.
    private var lastAgendaQueryText: String? = null
    private var lastMatchedForAgenda: List<NoteMeta> = emptyList()
    private var lastAgendaTerms: List<String> = emptyList()
    private var agendaWindowDays = 0

    @Volatile
    private var loadingMoreAgenda = false

    init {
        viewModelScope.launch {
            app.searchRepository.history.collect { history ->
                _state.value = _state.value.copy(history = history)
            }
        }
        viewModelScope.launch {
            app.database.indexDao().allNotes()
                .map { rows -> rows.map { it.toMeta() } }
                .flowOn(Dispatchers.Default)
                .collect { metas.value = it }
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            // Re-runs on new keystrokes and when the index changes under an
            // active query (e.g. a sync finishing while the search screen is up).
            combine(queryFlow.debounce(300), metas.filterNotNull()) { q, m -> q to m }
                .collect { (q, m) -> runSearch(q, m) }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        queryFlow.value = query
    }

    /** Run immediately (drawer shortcuts, history taps). */
    fun submit(query: String) {
        _state.value = _state.value.copy(query = query)
        queryFlow.value = query
        viewModelScope.launch {
            runSearch(query, metas.filterNotNull().first())
            app.searchRepository.recordHistory(query)
        }
    }

    fun saveCurrentSearch(name: String) {
        val query = _state.value.query
        if (query.isBlank() || name.isBlank()) return
        viewModelScope.launch { app.searchRepository.saveSearch(name.trim(), query.trim()) }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch { app.searchRepository.deleteSearch(id) }
    }

    private suspend fun runSearch(raw: String, notes: List<NoteMeta>) {
        if (raw.isBlank()) {
            lastAgendaQueryText = null
            _state.value = _state.value.copy(results = emptyList(), agenda = null, notebookCount = 0)
            return
        }
        // The filtering/snippet work is pure CPU and would otherwise block the
        // UI thread on every keystroke.
        withContext(Dispatchers.Default) {
            val query = QueryParser.parse(raw)
            val today = LocalDate.now()
            val matched = QueryMatcher.filter(notes, query, today)
            val terms = query.textTerms

            val agendaUi = query.agendaDays?.let { requestedDays ->
                if (raw != lastAgendaQueryText) agendaWindowDays = requestedDays
                lastAgendaQueryText = raw
                lastMatchedForAgenda = matched
                lastAgendaTerms = terms
                val result = QueryMatcher.agenda(matched, agendaWindowDays, today)
                AgendaUiState(
                    overdueCount = result.overdue.size,
                    overdue = result.overdue.map { toResult(it, terms) },
                    days = result.days.map { entry ->
                        AgendaDay(entry.date, entry.notes.map { toResult(it, terms) })
                    },
                )
            }
            if (query.agendaDays == null) lastAgendaQueryText = null

            _state.value = _state.value.copy(
                results = matched.map { toResult(it, terms) },
                agenda = agendaUi,
                notebookCount = matched.map { it.fileName }.distinct().size,
            )
        }
    }

    /** Grows the agenda's future-day window on scroll; overdue never repages. */
    fun loadMoreAgendaDays() {
        if (loadingMoreAgenda || lastAgendaQueryText == null) return
        if (agendaWindowDays >= AGENDA_MAX_WINDOW_DAYS) return
        loadingMoreAgenda = true
        agendaWindowDays = (agendaWindowDays + AGENDA_PAGE_SIZE).coerceAtMost(AGENDA_MAX_WINDOW_DAYS)
        viewModelScope.launch(Dispatchers.Default) {
            val today = LocalDate.now()
            val result = QueryMatcher.agenda(lastMatchedForAgenda, agendaWindowDays, today)
            val days = result.days.map { entry ->
                AgendaDay(entry.date, entry.notes.map { toResult(it, lastAgendaTerms) })
            }
            _state.value = _state.value.copy(agenda = _state.value.agenda?.copy(days = days))
            loadingMoreAgenda = false
        }
    }

    private fun toResult(meta: NoteMeta, terms: List<String>) = SearchResult(
        fileName = meta.fileName,
        lineIndex = meta.lineIndex,
        title = meta.title,
        keyword = meta.keyword,
        isDone = meta.isDoneKeyword,
        priority = meta.priority,
        snippet = Snippets.build(meta.searchText.substringAfter('\n', ""), terms),
        breadcrumb = "${meta.fileName} › ${meta.title}",
    )

    private fun NoteEntity.toMeta() = NoteMeta(
        fileName = fileName,
        lineIndex = lineIndex,
        title = title,
        keyword = keyword,
        isDoneKeyword = isDone,
        priority = priority,
        tags = tags.split(':').filter { it.isNotEmpty() },
        inheritedTags = inheritedTags.split(':').filter { it.isNotEmpty() },
        scheduled = scheduled,
        deadline = deadline,
        closed = closed,
        createdAt = createdAt,
        lastModified = lastModified,
        searchText = title + "\n" + body,
    )

    companion object {
        val Factory = factory { SearchViewModel(it) }

        private const val AGENDA_PAGE_SIZE = 14

        // Safety cap: stops loadMoreAgendaDays() from growing forever if the
        // user scrolls through a long stretch with nothing scheduled.
        private const val AGENDA_MAX_WINDOW_DAYS = 366
    }
}
