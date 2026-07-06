package com.rrajath.grove.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

data class NotebookItem(
    val fileName: String,
    val noteCount: Int,
    val lastModified: Long,
    val hasConflict: Boolean,
    /** User-chosen list glyph; null = derive one from the file name. */
    val icon: String? = null,
    /** User-chosen icon palette key; null = derive one from the file name. */
    val color: String? = null,
    /** Position in the pinned list (0 = topmost). -1 means not pinned. */
    val pinnedIndex: Int = -1,
) {
    val isPinned: Boolean get() = pinnedIndex >= 0
}

sealed class NotebooksUiState {
    data object NoVault : NotebooksUiState()
    data class Loaded(
        val notebooks: List<NotebookItem>,
        val syncState: SyncState,
        val lastSyncAt: Long?,
    ) : NotebooksUiState()
}

class NotebooksViewModel(private val app: GroveApplication) : ViewModel() {

    val state: StateFlow<NotebooksUiState> = combine(
        app.vault,
        app.database.indexDao().notebooksFlow(),
        app.syncManager.state,
        app.syncManager.lastResult,
        app.settingsRepository.settings,
    ) { vault, notebooks, syncState, lastResult, settings ->
        if (vault == null) {
            NotebooksUiState.NoVault
        } else {
            NotebooksUiState.Loaded(
                notebooks = notebooks
                    .map {
                        NotebookItem(
                            fileName = it.fileName,
                            noteCount = it.noteCount,
                            lastModified = it.lastModified,
                            hasConflict = it.conflictFileName != null,
                            icon = settings.notebookIcons[it.fileName],
                            color = settings.notebookColors[it.fileName],
                            pinnedIndex = settings.pinnedNotebooks.indexOf(it.fileName),
                        )
                    }
                    .sortedWith(
                        compareBy<NotebookItem> { if (it.isPinned) it.pinnedIndex else Int.MAX_VALUE }
                            .thenBy { it.fileName.lowercase() }
                    ),
                syncState = syncState,
                lastSyncAt = lastResult?.completedAt,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotebooksUiState.NoVault)

    fun requestSync() = app.syncManager.requestSync("manual")

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
            app.syncManager.requestSync("notebook created")
        }
    }

    fun renameNotebook(oldName: String, newName: String) {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            if (vault.renameNotebook(oldName, newName.trim())) {
                app.database.indexDao().removeNotebook(oldName)
                app.settingsRepository.moveNotebookStyle(
                    oldName,
                    if (newName.trim().endsWith(".org")) newName.trim() else "${newName.trim()}.org",
                )
            }
            app.syncManager.requestSync("notebook renamed")
        }
    }

    fun trashNotebook(name: String) {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            if (vault.trashNotebook(name)) {
                app.database.indexDao().removeNotebook(name)
            }
            app.syncManager.requestSync("notebook deleted")
        }
    }

    fun setNotebookIcon(fileName: String, glyph: String) {
        viewModelScope.launch { app.settingsRepository.setNotebookIcon(fileName, glyph) }
    }

    fun setNotebookColor(fileName: String, colorKey: String) {
        viewModelScope.launch { app.settingsRepository.setNotebookColor(fileName, colorKey) }
    }

    fun forceReload(name: String) {
        viewModelScope.launch { app.syncManager.forceReload(name) }
    }

    fun pinNotebook(fileName: String) {
        viewModelScope.launch { app.settingsRepository.pinNotebook(fileName) }
    }

    fun unpinNotebook(fileName: String) {
        viewModelScope.launch { app.settingsRepository.unpinNotebook(fileName) }
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

    // --- structural outline operations (PRD §5.3) ---

    private fun mutate(block: (OrgDocument, OrgHeadline) -> String?): (OrgHeadline) -> Unit =
        { headline ->
            val loaded = _state.value as? DocumentUiState.Loaded
            val vault = app.vault.value
            if (loaded != null && vault != null) {
                viewModelScope.launch {
                    // Compute the new text and parse it once, off the main thread.
                    val parsed = withContext(Dispatchers.Default) {
                        block(loaded.document, headline)
                            ?.let { it to OrgParser.parse(it, loaded.document.keywords) }
                    } ?: return@launch
                    val (newText, newDoc) = parsed
                    // Show the result immediately from the in-memory parse; persist
                    // and re-index in the background (no disk read + reparse round-trip).
                    _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
                    vault.save(loaded.fileName, newText)
                    app.syncManager.requestSync("outline edit")
                }
            }
        }

    val moveUp = mutate { d, h -> OrgMutations.moveSubtree(d, h, -1) }
    val moveDown = mutate { d, h -> OrgMutations.moveSubtree(d, h, +1) }
    val deleteSubtree = mutate { d, h -> OrgMutations.deleteSubtree(d, h) }

    val cutSubtree = mutate { d, h ->
        subtreeClipboard = OrgMutations.subtreeText(d, h)
        OrgMutations.deleteSubtree(d, h)
    }

    fun copySubtree(headline: OrgHeadline) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        subtreeClipboard = OrgMutations.subtreeText(loaded.document, headline)
    }

    val pasteUnder = mutate { d, h ->
        subtreeClipboard?.let { OrgMutations.pasteUnder(d, h, it) }
    }

    val hasClipboard: Boolean get() = subtreeClipboard != null

    /** Create a blank child note and report its line index so the caller can open the editor. */
    fun newChild(headline: OrgHeadline, onCreated: (Int) -> Unit) = newNote(onCreated) { doc, options ->
        OrgMutations.newChild(doc, headline, "", options)
    }

    /** Insert menu: blank sibling note immediately above [headline] (same level). */
    fun insertSiblingAbove(headline: OrgHeadline, onCreated: (Int) -> Unit) = newNote(onCreated) { doc, options ->
        OrgMutations.insertSiblingAbove(doc, headline, "", options)
    }

    /** Insert menu: blank sibling note immediately below [headline]'s subtree (same level). */
    fun insertSiblingBelow(headline: OrgHeadline, onCreated: (Int) -> Unit) = newNote(onCreated) { doc, options ->
        OrgMutations.insertSiblingBelow(doc, headline, "", options)
    }

    /** Outline FAB: add a blank top-level note to this notebook (PRD §5.3). */
    fun newTopLevelNote(onCreated: (Int) -> Unit) = newNote(onCreated) { doc, options ->
        OrgMutations.newTopLevel(doc, "", options)
    }

    private fun newNote(
        onCreated: (Int) -> Unit,
        insert: (OrgDocument, OrgMutations.NewNoteOptions) -> Pair<String, Int>,
    ) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val settings = app.settingsRepository.settings.first()
            val (newText, lineIndex) = insert(
                loaded.document,
                OrgMutations.NewNoteOptions(
                    id = if (settings.addIdToNewNotes) UUID.randomUUID().toString() else null,
                    createdAt = if (settings.addCreatedToNewNotes) LocalDateTime.now() else null,
                ),
            )
            val newDoc = withContext(Dispatchers.Default) {
                OrgParser.parse(newText, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("note added")
            onCreated(lineIndex)
        }
    }

    /**
     * Swipe-right quick action: cycle the TODO state like org's shift-cycle,
     * stepping through every keyword and back to none:
     * none → active… → done… → none. Plain keyword change (no CLOSED stamp);
     * use the metadata sheet's DONE chip for org-todo "mark done" semantics.
     */
    fun cycleState(headline: OrgHeadline) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val next = loaded.document.keywords.next(headline.keyword)
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val text = OrgMutations.setKeyword(loaded.document, headline, next)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("state cycled")
        }
    }

    companion object {
        /** App-level cut/copy buffer for subtrees. */
        private var subtreeClipboard: String? = null

        val Factory = factory { DocumentViewModel(it) }
    }
}

/** Note identity until cross-file ids land in search (M6): "fileName@headlineLineIndex". */
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

internal fun <T : ViewModel> factory(create: (GroveApplication) -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GroveApplication
            return create(app) as V
        }
    }
