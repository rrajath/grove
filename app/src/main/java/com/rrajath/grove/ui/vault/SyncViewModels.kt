package com.rrajath.grove.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.data.SyncLogEntity
import com.rrajath.grove.sync.ConflictResolution
import com.rrajath.grove.sync.SyncConflicts
import com.rrajath.grove.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ConflictUiState {
    data object Loading : ConflictUiState()
    data object NoConflict : ConflictUiState()
    data class Loaded(
        val fileName: String,
        val currentText: String,
        val copyText: String,
        val copyLabel: String,
    ) : ConflictUiState()

    data object Resolved : ConflictUiState()
}

class ConflictViewModel(
    private val database: GroveDatabase,
    private val sync: SyncTrigger,
) : ViewModel() {

    private val _state = MutableStateFlow<ConflictUiState>(ConflictUiState.Loading)
    val state: StateFlow<ConflictUiState> = _state

    fun load(fileName: String) {
        viewModelScope.launch {
            val copyName = database.indexDao().notebooks()
                .firstOrNull { it.fileName == fileName }?.conflictFileName
            val texts = sync.conflictTexts(fileName)
            _state.value = if (texts == null || copyName == null) {
                ConflictUiState.NoConflict
            } else {
                ConflictUiState.Loaded(
                    fileName = fileName,
                    currentText = texts.first,
                    copyText = texts.second,
                    copyLabel = SyncConflicts.label(copyName),
                )
            }
        }
    }

    fun resolve(fileName: String, resolution: ConflictResolution) {
        viewModelScope.launch {
            val applied = sync.resolveConflict(fileName, resolution)
            if (applied) {
                _state.value = ConflictUiState.Resolved
            } else {
                // The conflict copy vanished from under us (e.g. a background sync
                // or Syncthing itself cleared it while the picker was open):
                // nothing was written. Reload instead of reporting success, so the
                // user sees the real state (no conflict, or a fresh copy to
                // resolve) rather than a silent no-op that looks like "kept
                // current".
                load(fileName)
            }
        }
    }

    companion object {
        val Factory = factory { ConflictViewModel(it.database, it.syncManager) }
    }
}

class SyncLogViewModel(database: GroveDatabase) : ViewModel() {
    private val limit = MutableStateFlow(PAGE_SIZE)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<SyncLogEntity>> = limit
        .flatMapLatest { database.syncLogDao().recent(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = database.syncLogDao().count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun loadMore() {
        limit.value += PAGE_SIZE
    }

    companion object {
        const val PAGE_SIZE = 50
        val Factory = factory { SyncLogViewModel(it.database) }
    }
}
