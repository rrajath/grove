package com.rrajath.grove.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.ArchiveLocation
import com.rrajath.grove.org.ArchiveTarget
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.sync.SyncState
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    /** Label to show in the notebooks list — the file name or the cached `#+TITLE:`. */
    val displayName: String = fileName,
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

    // Built separately from the sync banner inputs: syncManager.state ticks once
    // per pulled file during a sync, and must not re-map/re-sort the whole list.
    private val notebookItems = combine(
        app.vault,
        app.database.indexDao().notebooksFlow(),
        app.settingsRepository.settings,
    ) { vault, notebooks, settings ->
        if (vault == null) null else notebooks
            .map {
                NotebookItem(
                    fileName = it.fileName,
                    noteCount = it.noteCount,
                    lastModified = it.lastModified,
                    hasConflict = it.conflictFileName != null,
                    icon = settings.notebookIcons[it.fileName],
                    color = settings.notebookColors[it.fileName],
                    pinnedIndex = settings.pinnedNotebooks.indexOf(it.fileName),
                    displayName = if (
                        settings.notebookDisplayNameMode == NotebookDisplayNameMode.TITLE &&
                        !it.title.isNullOrBlank()
                    ) it.title else it.fileName,
                )
            }
            .sortedWith(
                compareBy<NotebookItem> { if (it.isPinned) it.pinnedIndex else Int.MAX_VALUE }
                    .thenBy { it.fileName.lowercase() }
            )
    }.distinctUntilChanged()

    val state: StateFlow<NotebooksUiState> = combine(
        notebookItems,
        app.syncManager.state,
        app.syncManager.lastResult,
    ) { notebooks, syncState, lastResult ->
        if (notebooks == null) {
            NotebooksUiState.NoVault
        } else {
            NotebooksUiState.Loaded(
                notebooks = notebooks,
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

/** Transient bottom-center pill message (design spec: ~1.9s). */
data class OutlineToast(val message: String, val id: Long)

/** Undoable-operation snackbar (design spec: ~4.2s with an UNDO action). */
data class OutlineSnack(val message: String, val id: Long)

/** One step of refile-picker drill-down state (design spec Gestures screen). */
data class RefileNotebook(val fileName: String, val noteCount: Int)

data class RefileUiState(
    /** Line index of the headline being refiled (in the current document). */
    val sourceLine: Int,
    /** Null while the notebook list is loading. */
    val notebooks: List<RefileNotebook>? = null,
    val pickedFile: String? = null,
    val pickedDoc: OrgDocument? = null,
    /** Drill-down trail of headline lineIndexes inside [pickedDoc]; empty = top level. */
    val path: List<Int> = emptyList(),
    /** Effective `ARCHIVE` target for the source headline (nearest-ancestor-wins), if any. */
    val archiveTarget: ArchiveTarget? = null,
    /** Destination of the most recent successful refile, if any. */
    val lastUsedTarget: ArchiveTarget? = null,
)

class DocumentViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow<DocumentUiState>(DocumentUiState.Loading)
    val state: StateFlow<DocumentUiState> = _state

    private val _toast = MutableStateFlow<OutlineToast?>(null)
    val toast: StateFlow<OutlineToast?> = _toast

    private val _snack = MutableStateFlow<OutlineSnack?>(null)
    val snack: StateFlow<OutlineSnack?> = _snack

    /** Headline line the "Move & indent" command bar is acting on; null = not in focus mode. */
    private val _focusedLine = MutableStateFlow<Int?>(null)
    val focusedLine: StateFlow<Int?> = _focusedLine

    private val _refile = MutableStateFlow<RefileUiState?>(null)
    val refile: StateFlow<RefileUiState?> = _refile

    private var eventId = 0L

    fun showToast(message: String) {
        val t = OutlineToast(message, ++eventId)
        _toast.value = t
        viewModelScope.launch {
            delay(1900)
            if (_toast.value?.id == t.id) _toast.value = null
        }
    }

    private fun showSnack(message: String) {
        val s = OutlineSnack(message, ++eventId)
        _snack.value = s
        viewModelScope.launch {
            delay(4200)
            if (_snack.value?.id == s.id) _snack.value = null
        }
    }

    fun setFocus(line: Int?) {
        _focusedLine.value = line
    }

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

    /** Single-step undo: the pre-mutation text of every file the mutation touched. */
    private data class UndoSnapshot(val files: List<Pair<String, String>>)

    private var undoSnapshot: UndoSnapshot? = null

    fun undo() {
        val snap = undoSnapshot ?: return
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        undoSnapshot = null
        _snack.value = null
        // The focused line indexes the pre-undo document; don't let the
        // command bar act on whatever headline lands there after restore.
        _focusedLine.value = null
        viewModelScope.launch {
            snap.files.forEach { (name, text) -> vault.save(name, text) }
            snap.files.firstOrNull { it.first == loaded.fileName }?.let { (_, text) ->
                val doc = withContext(Dispatchers.Default) {
                    OrgParser.parse(text, loaded.document.keywords)
                }
                _state.value = DocumentUiState.Loaded(loaded.fileName, doc)
            }
            app.syncManager.requestSync("undo")
            showToast("Undone")
        }
    }

    /**
     * Apply an undoable single-file mutation: snapshot for undo, publish the
     * in-memory parse immediately, persist and sync in the background.
     * [newFocus] moves the command-bar focus when the headline's line changed.
     */
    private fun applyUndoable(
        headline: OrgHeadline,
        snackMessage: String,
        blockedToast: String,
        newFocus: (Int) -> Int? = { line -> _focusedLine.value?.let { if (it == headline.lineIndex) line else it } },
        block: (OrgDocument, OrgHeadline) -> Pair<String, Int>?,
    ) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                block(loaded.document, headline)
                    ?.let { (text, line) -> Triple(text, line, OrgParser.parse(text, loaded.document.keywords)) }
            }
            if (result == null) {
                showToast(blockedToast)
                return@launch
            }
            val (newText, newLine, newDoc) = result
            undoSnapshot = UndoSnapshot(listOf(loaded.fileName to loaded.document.text))
            _focusedLine.value = newFocus(newLine)
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("outline edit")
            showSnack(snackMessage)
        }
    }

    fun moveUp(headline: OrgHeadline) = applyUndoable(headline, "Moved up", "Can't move further") { d, h ->
        OrgMutations.moveSubtree(d, h, -1)
    }

    fun moveDown(headline: OrgHeadline) = applyUndoable(headline, "Moved down", "Can't move further") { d, h ->
        OrgMutations.moveSubtree(d, h, +1)
    }

    fun promote(headline: OrgHeadline) = applyUndoable(headline, "Promoted", "Already top level") { d, h ->
        OrgMutations.promoteSubtree(d, h)?.let { it to h.lineIndex }
    }

    fun demote(headline: OrgHeadline) = applyUndoable(headline, "Demoted", "Can't demote further") { d, h ->
        OrgMutations.demoteSubtree(d, h)?.let { it to h.lineIndex }
    }

    fun deleteNote(headline: OrgHeadline) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val subtreeEnd = loaded.document.subtreeEndLine(headline)
        applyUndoable(headline, "Note deleted", "", newFocus = { null }) { d, h ->
            OrgMutations.deleteSubtree(d, h) to h.lineIndex
        }
        dropFavoritesInRange(loaded.fileName, headline.lineIndex until subtreeEnd)
    }

    /**
     * Favorites reference headlines by lineIndex; drop the ones a delete or
     * refile removed from the file. (Remapping favorites across other
     * structural edits is a known limitation.)
     */
    private fun dropFavoritesInRange(fileName: String, range: IntRange) {
        viewModelScope.launch {
            app.favoritesRepository.favorites.first()
                .filter { it.fileName == fileName && it.lineIndex in range }
                .forEach { app.favoritesRepository.removeFavorite(it.fileName, it.lineIndex) }
        }
    }

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
            showToast("State → ${next ?: "none"}")
        }
    }

    fun setScheduled(headline: OrgHeadline, ts: OrgTimestamp?) =
        setPlanning(headline, "Scheduled", ts) { d, h -> OrgMutations.setScheduled(d, h, ts) }

    fun setDeadline(headline: OrgHeadline, ts: OrgTimestamp?) =
        setPlanning(headline, "Deadline", ts) { d, h -> OrgMutations.setDeadline(d, h, ts) }

    private fun setPlanning(
        headline: OrgHeadline,
        label: String,
        ts: OrgTimestamp?,
        block: (OrgDocument, OrgHeadline) -> String,
    ) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val text = block(loaded.document, headline)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("planning edit")
            showToast(
                if (ts == null) "$label cleared"
                else "$label · ${ts.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}"
            )
        }
    }

    // --- refile (design spec Gestures screen) ---

    fun startRefile(headline: OrgHeadline) {
        val archiveTarget = (_state.value as? DocumentUiState.Loaded)
            ?.let { ArchiveLocation.resolve(it.document, headline) }
        _refile.value = RefileUiState(sourceLine = headline.lineIndex, archiveTarget = archiveTarget)
        viewModelScope.launch {
            val notebooks = app.vault.value?.notebooks().orEmpty()
                .map { RefileNotebook(it.fileName, it.noteCount) }
            val settings = app.settingsRepository.settings.first()
            val lastUsedTarget = settings.lastRefileFile?.let { fileName ->
                ArchiveTarget(fileName, settings.lastRefileHeadingPath.split('/').filter { it.isNotEmpty() })
            }
            _refile.value = _refile.value?.copy(notebooks = notebooks, lastUsedTarget = lastUsedTarget)
        }
    }

    fun refilePickNotebook(fileName: String) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        viewModelScope.launch {
            val doc = if (fileName == loaded.fileName) loaded.document
            else app.vault.value?.open(fileName)
            if (doc == null) {
                showToast("Couldn't open ${fileName.removeSuffix(".org")}")
                return@launch
            }
            _refile.value = _refile.value?.copy(pickedFile = fileName, pickedDoc = doc, path = emptyList())
        }
    }

    fun refileDrillInto(line: Int) {
        _refile.value = _refile.value?.let { it.copy(path = it.path + line) }
    }

    /** Pop one drill-down level, or return to the notebook list from a file's top level. */
    fun refileBack() {
        _refile.value = _refile.value?.let {
            if (it.path.isNotEmpty()) it.copy(path = it.path.dropLast(1))
            else it.copy(pickedFile = null, pickedDoc = null)
        }
    }

    fun refileCancel() {
        _refile.value = null
    }

    fun refileConfirm() {
        val picker = _refile.value ?: return
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val destFile = picker.pickedFile ?: return
        val source = loaded.document.headlineAtLine(picker.sourceLine) ?: return
        val headingPath = picker.path.mapNotNull { picker.pickedDoc?.headlineAtLine(it)?.title }
        _refile.value = null
        refileTo(source, destFile, picker.path.lastOrNull(), headingPath)
    }

    /** One-tap archive: refile the source subtree straight to its resolved `ARCHIVE` target, creating any missing file/heading. */
    fun refileToArchive() {
        val picker = _refile.value ?: return
        val target = picker.archiveTarget ?: return
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        val source = loaded.document.headlineAtLine(picker.sourceLine) ?: return
        _refile.value = null
        val sourceEnd = loaded.document.subtreeEndLine(source)
        viewModelScope.launch {
            refileToResolvedTarget(
                loaded, vault, source, target,
                verb = "Archived", syncReason = "archive", createFileIfMissing = true,
            )
            dropFavoritesInRange(loaded.fileName, source.lineIndex until sourceEnd)
        }
    }

    /** One-tap refile straight to the destination of the most recent successful refile. */
    fun refileToLastUsed() {
        val picker = _refile.value ?: return
        val target = picker.lastUsedTarget ?: return
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        val source = loaded.document.headlineAtLine(picker.sourceLine) ?: return
        _refile.value = null
        val sourceEnd = loaded.document.subtreeEndLine(source)
        viewModelScope.launch {
            refileToResolvedTarget(
                loaded, vault, source, target,
                verb = "Refiled", syncReason = "refile", createFileIfMissing = false,
            )
            dropFavoritesInRange(loaded.fileName, source.lineIndex until sourceEnd)
        }
    }

    /** Shared refile-to-a-resolved-path logic behind both [refileToArchive] and [refileToLastUsed]. */
    private suspend fun refileToResolvedTarget(
        loaded: DocumentUiState.Loaded,
        vault: Vault,
        source: OrgHeadline,
        target: ArchiveTarget,
        verb: String,
        syncReason: String,
        createFileIfMissing: Boolean,
    ) {
        val label = (listOf(target.fileName.removeSuffix(".org")) + target.headingPath).joinToString(" › ")
        if (target.fileName == loaded.fileName) {
            val result = withContext(Dispatchers.Default) {
                val subtree = OrgMutations.subtreeText(loaded.document, source)
                val afterDelete = OrgParser.parse(
                    OrgMutations.deleteSubtree(loaded.document, source),
                    loaded.document.keywords,
                )
                val (docAfterPath, destHeadline) =
                    ArchiveLocation.findOrCreateHeadingPath(afterDelete, target.headingPath)
                val (finalText, _) = OrgMutations.refileInsert(docAfterPath, destHeadline, subtree)
                finalText to OrgParser.parse(finalText, loaded.document.keywords)
            }
            undoSnapshot = UndoSnapshot(listOf(loaded.fileName to loaded.document.text))
            _focusedLine.value = null
            _state.value = DocumentUiState.Loaded(loaded.fileName, result.second)
            vault.save(loaded.fileName, result.first)
            app.syncManager.requestSync(syncReason)
            showSnack("$verb to $label")
        } else {
            if (vault.open(target.fileName) == null) {
                if (createFileIfMissing) {
                    vault.createNotebook(target.fileName)
                } else {
                    showToast("Couldn't open ${target.fileName.removeSuffix(".org")}")
                    return
                }
            }
            val destDoc = vault.open(target.fileName)
            if (destDoc == null) {
                showToast("Couldn't open ${target.fileName.removeSuffix(".org")}")
                return
            }
            val (newSourceText, newDestText, newSourceDoc) = withContext(Dispatchers.Default) {
                val subtree = OrgMutations.subtreeText(loaded.document, source)
                val srcText = OrgMutations.deleteSubtree(loaded.document, source)
                val (docAfterPath, destHeadline) =
                    ArchiveLocation.findOrCreateHeadingPath(destDoc, target.headingPath)
                val (dstText, _) = OrgMutations.refileInsert(docAfterPath, destHeadline, subtree)
                Triple(srcText, dstText, OrgParser.parse(srcText, loaded.document.keywords))
            }
            undoSnapshot = UndoSnapshot(
                listOf(loaded.fileName to loaded.document.text, target.fileName to destDoc.text)
            )
            _focusedLine.value = null
            _state.value = DocumentUiState.Loaded(loaded.fileName, newSourceDoc)
            vault.save(loaded.fileName, newSourceText)
            vault.save(target.fileName, newDestText)
            app.syncManager.requestSync(syncReason)
            showSnack("$verb to $label")
        }
    }

    private fun rememberRefileTarget(fileName: String, headingPath: List<String>) {
        viewModelScope.launch {
            app.settingsRepository.setLastRefileTarget(fileName, headingPath)
        }
    }

    private fun refileTo(source: OrgHeadline, destFile: String, targetLine: Int?, headingPath: List<String>) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        val sourceEnd = loaded.document.subtreeEndLine(source)
        viewModelScope.launch {
            val destLabel = destFile.removeSuffix(".org")
            if (destFile == loaded.fileName) {
                val targetTitle = targetLine?.let { loaded.document.headlineAtLine(it)?.title }
                val result = withContext(Dispatchers.Default) {
                    OrgMutations.refileWithinFile(loaded.document, source, targetLine)
                        ?.let { (text, _) -> text to OrgParser.parse(text, loaded.document.keywords) }
                }
                if (result == null) {
                    showToast("Can't refile into its own subtree")
                    return@launch
                }
                undoSnapshot = UndoSnapshot(listOf(loaded.fileName to loaded.document.text))
                _focusedLine.value = null
                _state.value = DocumentUiState.Loaded(loaded.fileName, result.second)
                vault.save(loaded.fileName, result.first)
                app.syncManager.requestSync("refile")
                showSnack("Refiled to $destLabel › ${targetTitle ?: "top level"}")
                rememberRefileTarget(destFile, headingPath)
            } else {
                val destDoc = vault.open(destFile)
                if (destDoc == null) {
                    showToast("Couldn't open $destLabel")
                    return@launch
                }
                val target = targetLine?.let { destDoc.headlineAtLine(it) }
                val (newSourceText, newDestText, newSourceDoc) = withContext(Dispatchers.Default) {
                    val subtree = OrgMutations.subtreeText(loaded.document, source)
                    val srcText = OrgMutations.deleteSubtree(loaded.document, source)
                    val (dstText, _) = OrgMutations.refileInsert(destDoc, target, subtree)
                    Triple(srcText, dstText, OrgParser.parse(srcText, loaded.document.keywords))
                }
                undoSnapshot = UndoSnapshot(
                    listOf(loaded.fileName to loaded.document.text, destFile to destDoc.text)
                )
                _focusedLine.value = null
                _state.value = DocumentUiState.Loaded(loaded.fileName, newSourceDoc)
                vault.save(loaded.fileName, newSourceText)
                vault.save(destFile, newDestText)
                app.syncManager.requestSync("refile")
                showSnack("Refiled to $destLabel › ${target?.title ?: "top level"}")
                rememberRefileTarget(destFile, headingPath)
            }
            dropFavoritesInRange(loaded.fileName, source.lineIndex until sourceEnd)
        }
    }

    companion object {
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
