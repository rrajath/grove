package com.rrajath.grove.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.SyncLogEntity
import com.rrajath.grove.sync.ConflictResolution
import com.rrajath.grove.sync.SyncConflicts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

class ConflictViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow<ConflictUiState>(ConflictUiState.Loading)
    val state: StateFlow<ConflictUiState> = _state

    fun load(fileName: String) {
        viewModelScope.launch {
            val copyName = app.database.indexDao().notebooks()
                .firstOrNull { it.fileName == fileName }?.conflictFileName
            val texts = app.syncManager.conflictTexts(fileName)
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
            app.syncManager.resolveConflict(fileName, resolution)
            _state.value = ConflictUiState.Resolved
        }
    }

    companion object {
        val Factory = factory { ConflictViewModel(it) }
    }
}

class SyncLogViewModel(app: GroveApplication) : ViewModel() {
    val entries: StateFlow<List<SyncLogEntity>> = app.database.syncLogDao().recent()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        val Factory = factory { SyncLogViewModel(it) }
    }
}
