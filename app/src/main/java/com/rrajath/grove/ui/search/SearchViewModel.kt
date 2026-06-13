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
import kotlinx.coroutines.flow.debounce
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
    val snippet: Snippets.Snippet,
    val breadcrumb: String,
)

data class AgendaDay(val date: LocalDate, val results: List<SearchResult>)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val agenda: List<AgendaDay>? = null,
    val notebookCount: Int = 0,
    val history: List<String> = emptyList(),
)

class SearchViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    val savedSearches: StateFlow<List<SavedSearch>> = app.searchRepository.savedSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            app.searchRepository.history.collect { history ->
                _state.value = _state.value.copy(history = history)
            }
        }
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queryFlow.debounce(300).collect { runSearch(it) }
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
            runSearch(query)
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

    private suspend fun runSearch(raw: String) {
        if (raw.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), agenda = null, notebookCount = 0)
            return
        }
        // Room runs the query off-main, but the mapping/filtering/snippet work is
        // pure CPU and would otherwise block the UI thread on every keystroke.
        val notes = app.database.indexDao().allNotes()
        withContext(Dispatchers.Default) {
            val query = QueryParser.parse(raw)
            val today = LocalDate.now()
            val matched = QueryMatcher.filter(notes.map { it.toMeta() }, query, today)
            val terms = query.textTerms

            fun toResult(meta: NoteMeta) = SearchResult(
                fileName = meta.fileName,
                lineIndex = meta.lineIndex,
                title = meta.title,
                keyword = meta.keyword,
                isDone = meta.isDoneKeyword,
                snippet = Snippets.build(meta.searchText.substringAfter('\n', ""), terms),
                breadcrumb = "${meta.fileName} › ${meta.title}",
            )

            val agenda = query.agendaDays?.let { days ->
                QueryMatcher.agenda(matched, days, today).map { entry ->
                    AgendaDay(entry.date, entry.notes.map(::toResult))
                }
            }
            _state.value = _state.value.copy(
                results = matched.map(::toResult),
                agenda = agenda,
                notebookCount = matched.map { it.fileName }.distinct().size,
            )
        }
    }

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
    }
}
