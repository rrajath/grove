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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    // Snapshot for the metadata sheet's tag autocomplete, loaded once per screen
    // visit (same as EditorViewModel.load), not kept live: a capture draft is
    // short-lived, so a tag added by another edit mid-capture is not worth the
    // cost of a reactive query here.
    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    init {
        viewModelScope.launch {
            _allTags.value = app.database.indexDao().allTagStrings()
                .flatMap { it.split(':') }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }

    fun template(id: String): CaptureTemplate? = templates.value.firstOrNull { it.id == id }

    // Tracks the currently autosaved draft (if any) so the next autosave or the
    // final Save replaces it in place instead of inserting a duplicate copy.
    private var draftInsertion: CaptureInserter.Insertion? = null

    // Draft writes are read-modify-write over the whole target file and are
    // triggered from two places (the 5s idle autosave and the Save button), so
    // they must not interleave: the loser would re-insert against a stale
    // `draftInsertion` and leave a duplicate entry behind.
    private val writeMutex = Mutex()

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
                writeMutex.withLock { upsertEntry(template, entryText, context) }
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
                writeMutex.withLock { upsertEntry(template, entryText, context) }
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
            writeMutex.withLock {
                val vault = app.vault.value ?: return@withLock
                val text = withContext(Dispatchers.Default) { vault.open(template.targetFile)?.text }
                    ?: return@withLock
                vault.save(template.targetFile, CaptureInserter.removeInsertion(text, prev))
            }
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
        // Parsing the target file and splicing the entry into it are pure CPU
        // over the whole document. The idle autosave fires while the user is
        // still typing, so this stays off the main thread: a parse stall there
        // desynchronizes the IME from the text field and swallows keystrokes.
        val result = withContext(Dispatchers.Default) {
            if (vault.open(template.targetFile) == null) {
                vault.createNotebook(template.targetFile)
            }
            val currentText = vault.open(template.targetFile)?.text ?: ""
            // Strip our own previous draft first so re-inserting replaces it in
            // place rather than leaving a stale duplicate behind.
            val baseText = draftInsertion?.let { CaptureInserter.removeInsertion(currentText, it) }
                ?: currentText
            CaptureInserter.insert(
                docText = baseText,
                location = template.location,
                entry = entryText,
                today = LocalDate.from(context.now),
            )
        }
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

    /** Existing vault notebook file names, for the target-file picker dropdown. */
    val notebooks: StateFlow<List<String>> = app.database.indexDao().notebooksFlow()
        .map { list -> list.map { it.fileName }.sorted() }
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
