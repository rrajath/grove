package com.rrajath.grove.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.vault.Notebook
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class NotebooksUiState {
    data object NoVault : NotebooksUiState()
    data object Loading : NotebooksUiState()
    data class Loaded(val notebooks: List<Notebook>) : NotebooksUiState()
    data class Error(val message: String) : NotebooksUiState()
}

class NotebooksViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow<NotebooksUiState>(NotebooksUiState.Loading)
    val state: StateFlow<NotebooksUiState> = _state

    init {
        viewModelScope.launch {
            app.vault.collect { refresh(it) }
        }
    }

    fun refresh() = refresh(app.vault.value)

    private fun refresh(vault: Vault?) {
        if (vault == null) {
            _state.value = NotebooksUiState.NoVault
            return
        }
        viewModelScope.launch {
            _state.value = NotebooksUiState.Loading
            _state.value = try {
                NotebooksUiState.Loaded(vault.notebooks().sortedBy { it.fileName.lowercase() })
            } catch (e: Exception) {
                NotebooksUiState.Error(e.message ?: "Could not read folder")
            }
        }
    }

    fun saveVaultUri(uri: String) {
        viewModelScope.launch {
            app.settingsRepository.setVaultTreeUri(uri)
            app.settingsRepository.setOnboardingDone(true)
        }
    }

    fun createNotebook(name: String) {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            vault.createNotebook(name.trim())
            refresh()
        }
    }

    companion object {
        val Factory = factory { NotebooksViewModel(it) }
    }
}

sealed class DocumentUiState {
    data object Loading : DocumentUiState()
    data class Loaded(val fileName: String, val document: OrgDocument) : DocumentUiState()
    data class Error(val message: String) : DocumentUiState()
}

class DocumentViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow<DocumentUiState>(DocumentUiState.Loading)
    val state: StateFlow<DocumentUiState> = _state

    fun load(fileName: String) {
        viewModelScope.launch {
            val vault = app.vault.value
            if (vault == null) {
                _state.value = DocumentUiState.Error("No sync folder configured")
                return@launch
            }
            _state.value = try {
                val doc = vault.open(fileName)
                if (doc == null) DocumentUiState.Error("$fileName not found")
                else DocumentUiState.Loaded(fileName, doc)
            } catch (e: Exception) {
                DocumentUiState.Error(e.message ?: "Could not open $fileName")
            }
        }
    }

    companion object {
        val Factory = factory { DocumentViewModel(it) }
    }
}

/** Note identity until the Room index lands in M4: "fileName@headlineLineIndex". */
data class NoteRef(val fileName: String, val lineIndex: Int) {
    fun encode(): String = "$fileName@$lineIndex"

    companion object {
        fun decode(noteId: String): NoteRef? {
            val at = noteId.lastIndexOf('@')
            if (at <= 0) return null
            val line = noteId.substring(at + 1).toIntOrNull() ?: return null
            return NoteRef(noteId.substring(0, at), line)
        }
    }
}

fun OrgDocument.headlineAtLine(lineIndex: Int): OrgHeadline? =
    headlines.firstOrNull { it.lineIndex == lineIndex }

private fun <T : ViewModel> factory(create: (GroveApplication) -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GroveApplication
            return create(app) as V
        }
    }
