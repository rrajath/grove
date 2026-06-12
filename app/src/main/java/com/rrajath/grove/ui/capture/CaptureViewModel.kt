package com.rrajath.grove.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.capture.CaptureContext
import com.rrajath.grove.capture.CaptureInserter
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.TemplatesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data object Saved : SaveState()
    data class Failed(val message: String) : SaveState()
}

class CaptureViewModel(private val app: GroveApplication) : ViewModel() {

    val templates: StateFlow<List<CaptureTemplate>> = app.templatesRepository.templates
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    fun template(id: String): CaptureTemplate? = templates.value.firstOrNull { it.id == id }

    /**
     * Insert [entryText] into the template's target file, creating the file
     * if it doesn't exist yet.
     */
    fun save(template: CaptureTemplate, entryText: String, context: CaptureContext) {
        val vault = app.vault.value
        if (vault == null) {
            _saveState.value = SaveState.Failed("No sync folder configured")
            return
        }
        if (entryText.isBlank()) {
            _saveState.value = SaveState.Failed("Nothing to save")
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                if (!vault.open(template.targetFile).let { it != null }) {
                    vault.createNotebook(template.targetFile)
                }
                val docText = vault.open(template.targetFile)?.text ?: ""
                val result = CaptureInserter.insert(
                    docText = docText,
                    location = template.location,
                    entry = entryText,
                    today = LocalDate.from(context.now),
                )
                vault.save(template.targetFile, result.newText)
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed(e.message ?: "Capture failed")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as GroveApplication
                return CaptureViewModel(app) as T
            }
        }
    }
}

class TemplatesViewModel(private val app: GroveApplication) : ViewModel() {

    val templates: StateFlow<List<CaptureTemplate>> = app.templatesRepository.templates
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upsert(template: CaptureTemplate) =
        viewModelScope.launch { app.templatesRepository.upsert(template) }

    fun delete(id: String) = viewModelScope.launch { app.templatesRepository.delete(id) }

    fun move(id: String, delta: Int) =
        viewModelScope.launch { app.templatesRepository.move(id, delta) }

    fun newId(): String = TemplatesRepository.newId()

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as GroveApplication
                return TemplatesViewModel(app) as T
            }
        }
    }
}
