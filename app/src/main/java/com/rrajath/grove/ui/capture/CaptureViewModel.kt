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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

    // Tracks the currently autosaved draft (if any) so the next autosave or the
    // final Save replaces it in place instead of inserting a duplicate copy.
    private var draftInsertion: CaptureInserter.Insertion? = null

    /**
     * Insert [entryText] into the template's target file, creating the file
     * if it doesn't exist yet.
     */
    fun save(template: CaptureTemplate, entryText: String, context: CaptureContext) {
        if (entryText.isBlank()) {
            _saveState.value = SaveState.Failed("Nothing to save")
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                upsertEntry(template, entryText, context)
                app.syncManager.requestSync("capture saved")
                draftInsertion = null
                _saveState.value = SaveState.Saved
            } catch (e: Exception) {
                _saveState.value = SaveState.Failed(e.message ?: "Capture failed")
            }
        }
    }

    /**
     * Silently persist the in-progress capture so it survives the app being
     * killed mid-edit. Replaces the previous autosave in place (never
     * duplicates it) by stripping it out before re-inserting.
     */
    fun autosave(template: CaptureTemplate, entryText: String, context: CaptureContext) {
        if (entryText.isBlank()) return
        viewModelScope.launch {
            try {
                upsertEntry(template, entryText, context)
            } catch (_: Exception) {
                // Best-effort: a failed autosave just waits for the next tick
                // or the explicit Save tap, which surfaces errors to the user.
            }
        }
    }

    /** Remove a draft this session autosaved, e.g. when the user discards the capture. */
    fun discardDraft(template: CaptureTemplate) {
        val prev = draftInsertion ?: return
        draftInsertion = null
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val text = vault.open(template.targetFile)?.text ?: return@launch
            vault.save(template.targetFile, CaptureInserter.removeInsertion(text, prev))
            app.syncManager.requestSync("capture discarded")
        }
    }

    private suspend fun upsertEntry(template: CaptureTemplate, entryText: String, context: CaptureContext) {
        val settings = app.settingsRepository.settings.first()
        if (settings.vaultTreeUri == null) {
            _saveState.value = SaveState.Failed("No sync folder configured")
            return
        }
        // On a cold start (e.g. launched via app shortcut) the vault may
        // still be initializing even though a folder is configured; await it.
        val vault = app.vault.filterNotNull().first()
        if (vault.open(template.targetFile) == null) {
            vault.createNotebook(template.targetFile)
        }
        val currentText = vault.open(template.targetFile)?.text ?: ""
        // Strip our own previous draft first so re-inserting replaces it in
        // place rather than leaving a stale duplicate behind.
        val baseText = draftInsertion?.let { CaptureInserter.removeInsertion(currentText, it) } ?: currentText
        val result = CaptureInserter.insert(
            docText = baseText,
            location = template.location,
            entry = entryText,
            today = LocalDate.from(context.now),
        )
        vault.save(template.targetFile, result.newText)
        draftInsertion = result
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
