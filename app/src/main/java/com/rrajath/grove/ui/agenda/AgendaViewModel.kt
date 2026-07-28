package com.rrajath.grove.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.toNoteMeta
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.search.QueryMatcher
import com.rrajath.grove.search.Snippets
import com.rrajath.grove.ui.vault.factory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AgendaResult(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    val keyword: String?,
    val isDone: Boolean,
    val priority: String?,
    val snippet: Snippets.Snippet,
    val breadcrumb: String,
)

data class AgendaDay(val date: LocalDate, val results: List<AgendaResult>)

data class AgendaUiState(
    val overdueCount: Int = 0,
    val overdue: List<AgendaResult> = emptyList(),
    val days: List<AgendaDay> = emptyList(),
)

/** Upcoming/overdue day-grouped view (design spec §9 Agenda mode), now its own
 *  screen — distinct from Search, which is for finding a specific note. */
class AgendaViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(AgendaUiState())
    val state: StateFlow<AgendaUiState> = _state

    private var matched: List<NoteMeta> = emptyList()
    private var windowDays = INITIAL_WINDOW_DAYS

    @Volatile
    private var loadingMore = false

    init {
        viewModelScope.launch {
            // Only rows with a SCHEDULED or DEADLINE: QueryMatcher.agenda buckets
            // notes purely by those dates, so an undated note can never surface
            // here and there is no reason to hold one in memory.
            app.database.indexDao().plannedNotes()
                .map { rows -> rows.map { it.toNoteMeta() } }
                .flowOn(Dispatchers.Default)
                .collect { notes ->
                    matched = notes
                    recompute()
                }
        }
    }

    private fun recompute() {
        viewModelScope.launch(Dispatchers.Default) {
            val today = LocalDate.now()
            val result = QueryMatcher.agenda(matched, windowDays, today)
            _state.value = AgendaUiState(
                overdueCount = result.overdue.size,
                overdue = result.overdue.map { toResult(it) },
                days = result.days.map { entry -> AgendaDay(entry.date, entry.notes.map { toResult(it) }) },
            )
        }
    }

    /** Grows the future-day window on scroll; overdue never repages. */
    fun loadMoreDays() {
        if (loadingMore || windowDays >= MAX_WINDOW_DAYS) return
        loadingMore = true
        windowDays = (windowDays + PAGE_SIZE).coerceAtMost(MAX_WINDOW_DAYS)
        viewModelScope.launch(Dispatchers.Default) {
            val today = LocalDate.now()
            val result = QueryMatcher.agenda(matched, windowDays, today)
            _state.value = _state.value.copy(
                days = result.days.map { entry -> AgendaDay(entry.date, entry.notes.map { toResult(it) }) },
            )
            loadingMore = false
        }
    }

    private fun toResult(meta: NoteMeta) = AgendaResult(
        fileName = meta.fileName,
        lineIndex = meta.lineIndex,
        title = meta.title,
        keyword = meta.keyword,
        isDone = meta.isDoneKeyword,
        priority = meta.priority,
        snippet = Snippets.build(meta.searchText.substringAfter('\n', ""), emptyList()),
        breadcrumb = "${meta.fileName} › ${meta.title}",
    )

    companion object {
        val Factory = factory { AgendaViewModel(it) }

        private const val INITIAL_WINDOW_DAYS = 14
        private const val PAGE_SIZE = 14

        // Safety cap: stops loadMoreDays() from growing forever if the user
        // scrolls through a long stretch with nothing scheduled.
        private const val MAX_WINDOW_DAYS = 366
    }
}
