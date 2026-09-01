package com.rrajath.grove.ui.vault

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.ArchiveLocation
import com.rrajath.grove.org.ArchiveTarget
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.INTRO_LINE_INDEX
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.settings.PinKind
import com.rrajath.grove.settings.PinnedItem
import com.rrajath.grove.sync.SyncState
import com.rrajath.grove.vault.AutoArchive
import com.rrajath.grove.vault.StateChangeResult
import com.rrajath.grove.vault.Vault
import com.rrajath.grove.vault.vaultPath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

/**
 * The vault folder's own name, from its SAF tree URI — e.g. a URI whose tree
 * document id is `primary:Documents/org` yields `org`. Used as the breadcrumb
 * root in the drill-down view; falls back to "Notebooks" when there's no vault
 * or the id can't be read.
 */
private fun vaultDisplayName(treeUri: String?): String {
    if (treeUri.isNullOrEmpty()) return "Notebooks"
    return runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            .substringAfterLast('/')
            .substringAfterLast(':')
            .ifEmpty { "Notebooks" }
    }.getOrDefault("Notebooks")
}

data class NotebookItem(
    /** Vault-relative path with `/` separators, e.g. `projects/clients/acme.org`. */
    val fileName: String,
    val noteCount: Int,
    val lastModified: Long,
    val hasConflict: Boolean,
    /** User-chosen monogram palette key; null = derive one from the file name. */
    val color: String? = null,
    /** Position in the pinned list (0 = topmost). -1 means not pinned. */
    val pinnedIndex: Int = -1,
    /** Label to show in the notebooks list: the base file name or the cached `#+TITLE:`. */
    val displayName: String = fileName,
    /** False while this is a discovery stub whose content hasn't been parsed yet. */
    val isIndexed: Boolean = true,
) {
    val isPinned: Boolean get() = pinnedIndex >= 0

    /** Parent directory of [fileName], or `""` for a root-level file. */
    val dir: String get() = fileName.substringBeforeLast('/', "")
}

/** One row of the unified Pinned strip, in chronological pin order. */
sealed interface PinnedRow {
    data class File(val item: NotebookItem) : PinnedRow
    data class Folder(val node: FolderNode) : PinnedRow
}

sealed class NotebooksUiState {
    data object NoVault : NotebooksUiState()
    data class Loaded(
        /** Every notebook, flat and sorted — for the top-bar sync icon and the row dialogs. */
        val notebooks: List<NotebookItem>,
        /** Pinned notebooks, pin order; kept for the row dialogs / callers that only want files. */
        val pinned: List<NotebookItem> = emptyList(),
        /**
         * The Pinned strip: pinned files and folders interleaved in the single
         * chronological pin order. Rendered as one block above the tree.
         */
        val pinnedStrip: List<PinnedRow> = emptyList(),
        /** The inline tree (variant 1a), flattened to display rows for the current expansion. */
        val rows: List<NotebookTreeRow> = emptyList(),
        /** Pinned folders, pin order; a pinned folder is shown ONLY in the strip, not in [rows]. */
        val pinnedFolders: List<FolderNode> = emptyList(),
        /**
         * For each currently-expanded pinned folder, the display rows of its
         * subtree (depths shifted to indent under its flush strip row). Lets a
         * pinned folder expand in place in the strip; empty for collapsed ones.
         */
        val pinnedFolderExpansions: Map<String, List<NotebookTreeRow>> = emptyMap(),
        /** Directory paths currently expanded — lets the pinned strip's folder rows share the tree's caret state. */
        val expandedFolders: Set<String> = emptySet(),
        /** Per-folder icon-colour overrides, so the drill-down view tints its folder tiles like the tree. */
        val folderColors: Map<String, String> = emptyMap(),
        /** True when the tree contains at least one folder (gates the expand/collapse-all button). */
        val hasFolders: Boolean = false,
        /** True when no folder is expanded (picks the expand-all vs collapse-all icon). */
        val allFoldersCollapsed: Boolean = true,
        /** The vault folder's own display name — the breadcrumb root in the drill-down view (1b). */
        val vaultDisplayName: String = "Notebooks",
        val syncState: SyncState,
        val lastSyncAt: Long?,
        /** Reminders waiting on POST_NOTIFICATIONS/exact-alarm access (permission banner). */
        val remindersPendingPermission: Int = 0,
        /** Settings § Look and Feel toggle: draw the per-file icon tile on each row. */
        val showFileIcons: Boolean = true,
        /** Settings § Look and Feel toggle: render [flatRows]/[flatPinned] instead of the tree. */
        val flat: Boolean = false,
        /** Flat mode only: every file as a path-grouped row (pinned content excluded). */
        val flatRows: List<NotebookItem> = emptyList(),
        /** Flat mode only: the Pinned strip as file rows (pinned files + pinned folders' files inline). */
        val flatPinned: List<NotebookItem> = emptyList(),
    ) : NotebooksUiState()
}

/**
 * One-shot outcome of a notebook/folder create-or-rename, surfaced to
 * `NotebooksScreen` so it can dismiss the name dialog on success, or toast and
 * keep the dialog mounted (with the user's typed text) when the name collides.
 */
sealed interface NotebookEditEvent {
    /** The create/rename went through; the screen dismisses the open name dialog. */
    data object Succeeded : NotebookEditEvent
    /** [message] names the collision; the screen toasts it and leaves the dialog open. */
    data class NameTaken(val message: String) : NotebookEditEvent
}

class NotebooksViewModel(private val app: GroveApplication) : ViewModel() {

    // One-shot create/rename outcomes for the name dialogs (see NotebookEditEvent).
    private val _editEvents = MutableSharedFlow<NotebookEditEvent>(extraBufferCapacity = 1)
    val editEvents: SharedFlow<NotebookEditEvent> = _editEvents

    private data class TreeInputs(
        val items: List<NotebookItem>,
        val showFileIcons: Boolean,
        /** Settings § Look and Feel: render one flat file list instead of the folder tree. */
        val flattenFolders: Boolean,
        val expandedFolders: Set<String>,
        val vaultDisplayName: String,
        val folderColors: Map<String, String>,
        val pinnedFolders: List<String>,
        /** The single ordered pin list (files + folders) that drives the strip. */
        val pinnedItems: List<PinnedItem>,
    )

    // Built separately from the sync banner inputs: syncManager.state ticks once
    // per pulled file during a sync, and must not re-map/re-group the whole tree.
    private val treeInputs = combine(
        app.vault,
        app.database.indexDao().notebooksFlow(),
        app.settingsRepository.settings,
    ) { vault, notebooks, settings ->
        if (vault == null) return@combine null
        val items = notebooks.map {
            NotebookItem(
                fileName = it.fileName,
                noteCount = it.noteCount,
                lastModified = it.lastModified,
                hasConflict = it.conflictFileName != null,
                color = settings.notebookColors[it.fileName],
                pinnedIndex = settings.pinnedNotebooks.indexOf(it.fileName),
                displayName = if (
                    settings.notebookDisplayNameMode == NotebookDisplayNameMode.TITLE &&
                    !it.title.isNullOrBlank()
                ) it.title else it.fileName.substringAfterLast('/'),
                isIndexed = it.isIndexed,
            )
        }
        TreeInputs(
            items,
            settings.showNotebookFileIcons,
            settings.flattenNotebookFolders,
            settings.expandedFolders,
            vaultDisplayName(settings.vaultTreeUri),
            settings.folderColors,
            settings.pinnedFolders,
            settings.pinnedItems,
        )
    }.distinctUntilChanged()

    val state: StateFlow<NotebooksUiState> = combine(
        treeInputs,
        app.syncManager.state,
        app.syncManager.lastResult,
        app.database.reminderDao().pendingCountFlow(System.currentTimeMillis()),
    ) { inputs, syncState, lastResult, remindersPending ->
        if (inputs == null) {
            NotebooksUiState.NoVault
        } else if (inputs.flattenFolders) {
            // Flat mode: no tree, no drill-down, no strip folders — every file is a
            // path-subtitled row. A pinned folder contributes its files to the strip
            // inline (flatPinnedRows), so its subtree is pulled from flatRows.
            NotebooksUiState.Loaded(
                notebooks = inputs.items.sortedWith(
                    compareBy<NotebookItem> { if (it.isPinned) it.pinnedIndex else Int.MAX_VALUE }
                        .thenBy { it.displayName.lowercase() }
                ),
                pinned = inputs.items.filter { it.isPinned }.sortedBy { it.pinnedIndex },
                flat = true,
                flatRows = flatNotebookRows(inputs.items, inputs.pinnedItems),
                flatPinned = flatPinnedRows(inputs.items, inputs.pinnedItems),
                folderColors = inputs.folderColors,
                hasFolders = false,
                vaultDisplayName = inputs.vaultDisplayName,
                syncState = syncState,
                lastSyncAt = lastResult?.completedAt,
                remindersPendingPermission = remindersPending,
                showFileIcons = inputs.showFileIcons,
            )
        } else {
            val flat = inputs.items.sortedWith(
                compareBy<NotebookItem> { if (it.isPinned) it.pinnedIndex else Int.MAX_VALUE }
                    .thenBy { it.displayName.lowercase() }
            )
            val pinned = inputs.items.filter { it.isPinned }.sortedBy { it.pinnedIndex }
            // Folder nodes for the strip come from the full item list (a folder can
            // hold nothing but pinned files and still deserves a strip row).
            val pinnedFolderNodeList = pinnedFolderNodes(
                inputs.items, inputs.folderColors, inputs.pinnedFolders,
            )
            val pinnedFileByPath = pinned.associateBy { it.fileName }
            val pinnedFolderByDir = pinnedFolderNodeList.associateBy { it.dir }
            // The single chronological strip: resolve each pin token to its live row.
            val pinnedStrip = inputs.pinnedItems.mapNotNull { pi ->
                when (pi.kind) {
                    PinKind.FILE -> pinnedFileByPath[pi.path]?.let { PinnedRow.File(it) }
                    PinKind.FOLDER -> pinnedFolderByDir[pi.path]?.let { PinnedRow.Folder(it) }
                }
            }
            val pinnedPaths = pinned.mapTo(mutableSetOf()) { it.fileName }
            val treeItems = inputs.items.filterNot { it.fileName in pinnedPaths }
            val pinnedFolderDirs = inputs.pinnedFolders.toSet()
            fun underPinnedFolder(dir: String) =
                pinnedFolderDirs.any { p -> dir == p || dir.startsWith("$p/") }
            // A pinned folder's subtree is excluded from the tree (it lives only in
            // the strip), so gate the expand/collapse-all affordance on what's left.
            val folderDirs = allFolderDirs(treeItems).filterNot { underPinnedFolder(it) }
            // Expand-in-place for the strip: only an expanded pinned folder carries
            // its subtree rows (a large one drills to 1b instead and never expands).
            val pinnedFolderExpansions = pinnedFolderNodeList
                .filter { it.dir in inputs.expandedFolders }
                .associate { node ->
                    node.dir to pinnedFolderSubtreeRows(
                        treeItems, node.dir, inputs.expandedFolders,
                        inputs.folderColors, inputs.pinnedFolders,
                    )
                }
            NotebooksUiState.Loaded(
                notebooks = flat,
                pinned = pinned,
                pinnedStrip = pinnedStrip,
                rows = buildNotebookTree(
                    treeItems, inputs.expandedFolders, inputs.folderColors, inputs.pinnedFolders,
                ),
                pinnedFolders = pinnedFolderNodeList,
                pinnedFolderExpansions = pinnedFolderExpansions,
                expandedFolders = inputs.expandedFolders,
                folderColors = inputs.folderColors,
                hasFolders = folderDirs.isNotEmpty(),
                allFoldersCollapsed = folderDirs.none { it in inputs.expandedFolders },
                vaultDisplayName = inputs.vaultDisplayName,
                syncState = syncState,
                lastSyncAt = lastResult?.completedAt,
                remindersPendingPermission = remindersPending,
                showFileIcons = inputs.showFileIcons,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotebooksUiState.NoVault)

    init {
        // First open of a vault: expand every folder that recursively contains a
        // SCHEDULED/DEADLINE note, collapse the rest, then never re-run (a vault
        // that starts fully collapsed reads as empty). Waits for the index to
        // hold something so a pre-first-sync launch doesn't stamp an empty set.
        viewModelScope.launch {
            if (app.settingsRepository.settings.first().notebooksTreeDefaultsApplied) return@launch
            app.database.indexDao().notebooksFlow().first { it.isNotEmpty() }
            val planned = app.database.indexDao().plannedNotes().first()
            app.settingsRepository.applyNotebooksTreeDefaults(
                firstOpenExpandedDirs(planned.map { it.fileName })
            )
        }
    }

    fun requestSync() = app.syncManager.requestSync("manual")

    /** Folder row tap: flip that folder's expansion state (persisted for process-death survival). */
    fun toggleFolder(dir: String) {
        viewModelScope.launch { app.settingsRepository.toggleExpandedFolder(dir) }
    }

    /** Top-bar expand/collapse-all: [expand] every folder in the current tree, or none. */
    fun setAllFoldersExpanded(expand: Boolean) {
        val loaded = state.value as? NotebooksUiState.Loaded ?: return
        val dirs = if (expand) {
            val pinnedDirs = loaded.pinnedFolders.map { it.dir }
            allFolderDirs(loaded.notebooks.filterNot { it.isPinned })
                .filterNot { dir -> pinnedDirs.any { dir == it || dir.startsWith("$it/") } }
                .toSet()
        } else {
            emptySet()
        }
        viewModelScope.launch { app.settingsRepository.setExpandedFolders(dirs) }
    }

    fun saveVaultUri(uri: String) {
        viewModelScope.launch {
            app.settingsRepository.setVaultTreeUri(uri)
            // Picking a folder from the empty-vault state also completes onboarding;
            // stamp the current build as seen in the same write so the What's New
            // modal doesn't fire on this first run (see setOnboardingDone).
            app.settingsRepository.setOnboardingDone(true, com.rrajath.grove.BuildConfig.VERSION_CODE)
        }
    }

    /**
     * Create an empty notebook. [dir] is a vault-relative directory ("" = root);
     * the drill-down view passes the folder currently being browsed. [name] may
     * itself carry `/` segments, which [Vault.createNotebook] appends under [dir],
     * creating any missing folders.
     */
    fun createNotebook(name: String, dir: String = "") {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            if (vault.createNotebook(name.trim(), dir.trim('/'))) {
                _editEvents.emit(NotebookEditEvent.Succeeded)
                app.syncManager.requestSync("notebook created")
            } else {
                _editEvents.emit(
                    NotebookEditEvent.NameTaken("A notebook with that name already exists")
                )
            }
        }
    }

    /**
     * Move a notebook into [newDir] (a vault-relative directory, "" = root),
     * keeping its file name. Re-keys the monogram colour and pin position onto
     * the new path via [com.rrajath.grove.settings.SettingsRepository.moveNotebookStyle]
     * and drops the stale index row so the next sync re-discovers it in place.
     */
    fun moveNotebook(path: String, newDir: String) {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val newPath = vault.moveNotebook(path, newDir.trim('/'))
            if (newPath != null) {
                app.database.indexDao().removeNotebook(path)
                app.settingsRepository.moveNotebookStyle(path, newPath)
                app.syncManager.requestSync("notebook moved")
            }
        }
    }

    fun renameNotebook(oldName: String, newName: String) {
        val vault = app.vault.value ?: return
        val target = newName.trim().let { if (it.endsWith(".org")) it else "$it.org" }
        if (target == oldName) {
            // Confirmed without changing the name — nothing to do, just close.
            viewModelScope.launch { _editEvents.emit(NotebookEditEvent.Succeeded) }
            return
        }
        viewModelScope.launch {
            if (vault.renameNotebook(oldName, newName.trim())) {
                app.database.indexDao().removeNotebook(oldName)
                app.settingsRepository.moveNotebookStyle(oldName, target)
                _editEvents.emit(NotebookEditEvent.Succeeded)
            } else {
                _editEvents.emit(
                    NotebookEditEvent.NameTaken("A notebook with that name already exists")
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

    fun pinFolder(dir: String) {
        viewModelScope.launch { app.settingsRepository.pinFolder(dir) }
    }

    fun unpinFolder(dir: String) {
        viewModelScope.launch { app.settingsRepository.unpinFolder(dir) }
    }

    fun setFolderColor(dir: String, colorKey: String) {
        viewModelScope.launch { app.settingsRepository.setFolderColor(dir, colorKey) }
    }

    /**
     * Rename [dir] in place (keeping its parent). Moves every descendant `.org`
     * file, drops their stale index rows, re-keys the folder's (and every
     * descendant's) icon colour + pin state, and requests a sync.
     */
    fun renameFolder(dir: String, newName: String) {
        val vault = app.vault.value ?: return
        val trimmed = dir.trim('/')
        val newDir = vaultPath(trimmed.substringBeforeLast('/', ""), newName.trim().trim('/'))
        if (newDir == trimmed) {
            // Confirmed without changing the name — nothing to do, just close.
            viewModelScope.launch { _editEvents.emit(NotebookEditEvent.Succeeded) }
            return
        }
        viewModelScope.launch {
            val affected = affectedPaths(dir)
            val renamedTo = vault.renameFolder(dir, newName)
            if (renamedTo != null) {
                affected.forEach { app.database.indexDao().removeNotebook(it) }
                app.settingsRepository.renameFolderStyle(dir, renamedTo)
                _editEvents.emit(NotebookEditEvent.Succeeded)
                app.syncManager.requestSync("folder renamed")
            } else {
                _editEvents.emit(
                    NotebookEditEvent.NameTaken("A folder with that name already exists")
                )
            }
        }
    }

    /**
     * Move every `.org` file under [dir] to the trash (recoverable), drop their
     * index rows, clear the folder's icon colour + pin state, and request a sync.
     */
    fun deleteFolder(dir: String) {
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val affected = affectedPaths(dir)
            if (vault.trashFolder(dir) > 0) {
                affected.forEach { app.database.indexDao().removeNotebook(it) }
                app.settingsRepository.deleteFolderStyle(dir)
                app.syncManager.requestSync("folder deleted")
            }
        }
    }

    /** Vault-relative paths of the notebooks currently indexed under [dir]. */
    private fun affectedPaths(dir: String): List<String> =
        (state.value as? NotebooksUiState.Loaded)?.notebooks
            ?.map { it.fileName }
            ?.filter { it.startsWith("$dir/") }
            .orEmpty()

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
@Immutable
data class RefileNotebook(val fileName: String, val noteCount: Int)

data class RefileUiState(
    /** Line index of the headline being refiled (in the current document). */
    val sourceLine: Int,
    /** Null while the notebook list is loading. */
    val notebooks: ImmutableList<RefileNotebook>? = null,
    val pickedFile: String? = null,
    val pickedDoc: OrgDocument? = null,
    /** Drill-down trail of headline lineIndexes inside [pickedDoc]; empty = top level. */
    val path: ImmutableList<Int> = persistentListOf(),
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

    /** Tag autocomplete pool for the read-mode metadata sheet; refreshed on each [load]. */
    private val _allTags = MutableStateFlow<List<String>>(emptyList())
    val allTags: StateFlow<List<String>> = _allTags

    /**
     * Set to the line of the blank heading [withIntroHeading] just inserted, so
     * the read screen (currently showing the intro pseudo-note) can re-open it
     * as a real note. Cleared by [clearIntroPromoted] once consumed.
     */
    private val _introPromotedLine = MutableStateFlow<Int?>(null)
    val introPromotedLine: StateFlow<Int?> = _introPromotedLine

    fun clearIntroPromoted() { _introPromotedLine.value = null }

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
        viewModelScope.launch {
            _allTags.value = app.database.indexDao().allTagStrings()
                .flatMap { it.split(':') }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
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
     * Favoriting needs a stable `:ID:`/`:CUSTOM_ID:` on [headline] so the favorite survives
     * line drift from later edits; writes a `:CUSTOM_ID:` only when it has neither already
     * (never overwrites an intentionally-set one). Goes through this class's own [_state]
     * (like every other mutation here) instead of a standalone vault read/write, so the id
     * becomes part of the same in-memory document lineage subsequent edits build from —
     * otherwise the very next outline edit in this session would rebuild its saved text from
     * a pre-favorite snapshot and silently drop the id (the bug this was written to fix: a
     * same-session edit right after favoriting was clobbering the just-written CUSTOM_ID).
     * [onResolved] receives the heading's existing or newly-written id.
     */
    fun ensureCustomId(headline: OrgHeadline, onResolved: (String?) -> Unit) {
        val existing = headline.id ?: headline.customId
        if (existing != null) {
            onResolved(existing)
            return
        }
        val loaded = _state.value as? DocumentUiState.Loaded ?: return onResolved(null)
        val vault = app.vault.value ?: return onResolved(null)
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val newText = withContext(Dispatchers.Default) {
                OrgMutations.upsertProperty(loaded.document, headline, "CUSTOM_ID", newId)
            }
            val newDoc = withContext(Dispatchers.Default) {
                OrgParser.parse(newText, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("favorite added custom id")
            onResolved(newId)
        }
    }

    /**
     * A metadata action was invoked on a file's intro, the heading-less content
     * before its first heading, which has
     * no headline to hang metadata on. In one atomic edit: insert a blank
     * top-level heading directly above the content (so the content becomes its
     * body), then apply [mutate] to that heading. [describe] is the metadata
     * action's own toast (shown alongside the "added a heading" snack).
     * Publishes the new heading's line via [introPromotedLine] so the read
     * screen can re-open it as a normal note. Undoable in a single step.
     */
    fun withIntroHeading(describe: String, mutate: (OrgDocument, OrgHeadline) -> String) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        if (!loaded.document.hasIntro) return
        viewModelScope.launch {
            val (finalText, newLine, finalDoc) = withContext(Dispatchers.Default) {
                val (wrapped, line) = OrgMutations.wrapIntroInHeading(loaded.document)
                val wrappedDoc = OrgParser.parse(wrapped, loaded.document.keywords)
                val h = wrappedDoc.headlineAtLine(line)
                    ?: return@withContext Triple(wrapped, line, wrappedDoc)
                val text = mutate(wrappedDoc, h)
                Triple(text, line, OrgParser.parse(text, loaded.document.keywords))
            }
            undoSnapshot = UndoSnapshot(listOf(loaded.fileName to loaded.document.text))
            _state.value = DocumentUiState.Loaded(loaded.fileName, finalDoc)
            vault.save(loaded.fileName, finalText)
            app.syncManager.requestSync("intro promoted to heading")
            showSnack("Added a blank heading for this content")
            if (describe.isNotEmpty()) showToast(describe)
            _introPromotedLine.value = newLine
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
        dropFavoritesInRange(loaded.document, loaded.fileName, headline.lineIndex until subtreeEnd)
    }

    /**
     * Same-session cleanup for an in-app delete/refile: a favorite whose *current* position
     * falls in the removed subtree's range is dropped. [document] is the pre-edit document
     * (the mutation hasn't been applied to [_state] yet at the call sites below), so a
     * favorite's stable customId still resolves against it even though the subtree is about to
     * be deleted/moved out of the file. Resolving by customId here — instead of trusting the
     * favorite's stored lineIndex directly — matters because that stored value is only a
     * snapshot from whenever the favorite was added: any unrelated edit elsewhere in the file
     * since then (adding/removing lines above it) shifts the note's true line without ever
     * updating the stored one, so a stale lineIndex can coincidentally land inside a range being
     * removed even though the favorited note itself isn't part of it. Favorites with no
     * customId (pre-id favorites) fall back to the raw stored lineIndex, same as elsewhere.
     */
    private fun dropFavoritesInRange(document: OrgDocument, fileName: String, range: IntRange) {
        viewModelScope.launch {
            app.favoritesRepository.favorites.first()
                .filter { it.fileName == fileName }
                .filter { fav ->
                    val currentLine = fav.customId
                        ?.let { document.findByCustomId(it) ?: document.findById(it) }
                        ?.lineIndex
                        ?: fav.lineIndex
                    currentLine in range
                }
                .forEach { app.favoritesRepository.removeFavorite(it.fileName, it.lineIndex, it.customId) }
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
     * Swipe-right quick action: set the TODO state to exactly the keyword the
     * user picked from the state sheet (null = clear it). Picking a done-type
     * keyword applies full org-todo "mark done" semantics: a repeating
     * SCHEDULED/DEADLINE advances instead of closing, otherwise a CLOSED stamp
     * is written; leaving a done-type keyword (including clearing it to none)
     * drops that stamp, via [OrgMutations.changeKeyword], so marking a task
     * done/reopening it from the outline behaves the same as from the metadata
     * sheet, Search's state sheet, or the agenda swipe.
     */
    fun setState(headline: OrgHeadline, keyword: String?) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        if (headline.keyword == keyword) return
        viewModelScope.launch {
            val settings = app.settingsRepository.settings.first()
            when (
                val result = AutoArchive.apply(
                    vault, settings, loaded.document, loaded.fileName, headline, keyword, LocalDateTime.now(),
                )
            ) {
                is StateChangeResult.Plain -> {
                    _state.value = DocumentUiState.Loaded(loaded.fileName, result.doc)
                    vault.save(loaded.fileName, result.text)
                    app.syncManager.requestSync("state set")
                    showToast("State → ${keyword ?: "none"}")
                }
                is StateChangeResult.Archived -> {
                    undoSnapshot = UndoSnapshot(
                        if (result.sourceFile == result.destFile) {
                            listOf(result.sourceFile to loaded.document.text)
                        } else {
                            listOf(loaded.fileName to loaded.document.text, result.destFile to result.destTextBefore)
                        }
                    )
                    _focusedLine.value = null
                    _state.value = DocumentUiState.Loaded(loaded.fileName, result.sourceDoc)
                    vault.save(loaded.fileName, result.sourceText)
                    if (result.destFile != loaded.fileName) vault.save(result.destFile, result.destText)
                    app.syncManager.requestSync("state set")
                    showSnack("Marked done. Refiled to ${result.label}")
                }
            }
        }
    }

    /**
     * Read mode: tap a checklist item to cycle its box through [states].
     * [lineIndex] is absolute into the document (a [BlockParser.ListItem]'s
     * body-relative line plus the owning headline's `bodyStart`).
     */
    fun toggleChecklistItem(lineIndex: Int, states: List<Char>) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val newText = withContext(Dispatchers.Default) {
                OrgMutations.toggleCheckbox(loaded.document, lineIndex, states)
            } ?: return@launch
            val newDoc = withContext(Dispatchers.Default) {
                OrgParser.parse(newText, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("checklist toggled")
        }
    }

    fun setScheduled(headline: OrgHeadline, ts: OrgTimestamp?) =
        setPlanning(headline, "Scheduled", ts) { d, h -> OrgMutations.setScheduled(d, h, ts) }

    fun setDeadline(headline: OrgHeadline, ts: OrgTimestamp?) =
        setPlanning(headline, "Deadline", ts) { d, h -> OrgMutations.setDeadline(d, h, ts) }

    /** Read mode's metadata sheet: priority/tag chips write straight to disk. */
    fun setPriority(headline: OrgHeadline, priority: Char?) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val text = OrgMutations.setPriority(loaded.document, headline, priority)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("priority set")
            showToast("Priority → ${priority?.let { "#$it" } ?: "none"}")
        }
    }

    fun setTags(headline: OrgHeadline, tags: List<String>) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val text = OrgMutations.setTags(loaded.document, headline, tags)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("tags set")
        }
    }

    /**
     * Both planning dates in one edit: what the Dates screen commits. The toast
     * names whichever dates survived so clearing one is still acknowledged.
     */
    fun setPlanningDates(headline: OrgHeadline, scheduled: OrgTimestamp?, deadline: OrgTimestamp?) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        viewModelScope.launch {
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val text = OrgMutations.setPlanningDates(loaded.document, headline, scheduled, deadline)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("planning edit")
            val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
            val parts = listOfNotNull(
                scheduled?.let { "Scheduled · ${it.date.format(fmt)}" },
                deadline?.let { "Deadline · ${it.date.format(fmt)}" },
            )
            showToast(if (parts.isEmpty()) "Planning cleared" else parts.joinToString("  ·  "))
        }
    }

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

    /** Outline swipe "Note" action: org's C-c C-z, logged into the LOGBOOK drawer. */
    fun addNote(headline: OrgHeadline, note: String) {
        val loaded = _state.value as? DocumentUiState.Loaded ?: return
        val vault = app.vault.value ?: return
        if (note.isBlank()) return
        viewModelScope.launch {
            val (newText, newDoc) = withContext(Dispatchers.Default) {
                val now = LocalDateTime.now()
                val stamp = OrgTimestamp(
                    now.toLocalDate(),
                    time = now.toLocalTime().withSecond(0).withNano(0),
                    active = false,
                )
                val text = OrgMutations.appendLogbookNote(loaded.document, headline, note.trim(), stamp)
                text to OrgParser.parse(text, loaded.document.keywords)
            }
            _state.value = DocumentUiState.Loaded(loaded.fileName, newDoc)
            vault.save(loaded.fileName, newText)
            app.syncManager.requestSync("note added")
            showToast("Note added")
        }
    }

    // --- refile (design spec Gestures screen) ---

    fun startRefile(headline: OrgHeadline) {
        _refile.value = RefileUiState(sourceLine = headline.lineIndex)
        viewModelScope.launch {
            val notebooks = app.vault.value?.notebooks().orEmpty()
                .map { RefileNotebook(it.fileName, it.noteCount) }
                .toImmutableList()
            val settings = app.settingsRepository.settings.first()
            val archiveTarget = (_state.value as? DocumentUiState.Loaded)?.let {
                ArchiveLocation.resolve(it.document, headline, AutoArchive.settingsFallback(settings))
            }
            val lastUsedTarget = settings.lastRefileFile?.let { fileName ->
                ArchiveTarget(fileName, settings.lastRefileHeadingPath.split('/').filter { it.isNotEmpty() })
            }
            _refile.value = _refile.value?.copy(
                notebooks = notebooks, archiveTarget = archiveTarget, lastUsedTarget = lastUsedTarget,
            )
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
            _refile.value = _refile.value?.copy(pickedFile = fileName, pickedDoc = doc, path = persistentListOf())
        }
    }

    fun refileDrillInto(line: Int) {
        _refile.value = _refile.value?.let { it.copy(path = (it.path + line).toImmutableList()) }
    }

    /** Pop one drill-down level, or return to the notebook list from a file's top level. */
    fun refileBack() {
        _refile.value = _refile.value?.let {
            if (it.path.isNotEmpty()) it.copy(path = it.path.dropLast(1).toImmutableList())
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
            dropFavoritesInRange(loaded.document, loaded.fileName, source.lineIndex until sourceEnd)
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
            dropFavoritesInRange(loaded.document, loaded.fileName, source.lineIndex until sourceEnd)
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
        val write = AutoArchive.refileSubtree(vault, loaded.document, loaded.fileName, source, target, createFileIfMissing)
        if (write == null) {
            showToast("Couldn't open ${target.fileName.removeSuffix(".org")}")
            return
        }
        undoSnapshot = UndoSnapshot(
            if (write.sourceFile == write.destFile) {
                listOf(write.sourceFile to loaded.document.text)
            } else {
                listOf(loaded.fileName to loaded.document.text, write.destFile to write.destTextBefore)
            }
        )
        _focusedLine.value = null
        _state.value = DocumentUiState.Loaded(
            loaded.fileName, OrgParser.parse(write.sourceText, loaded.document.keywords),
        )
        vault.save(loaded.fileName, write.sourceText)
        if (write.destFile != loaded.fileName) vault.save(write.destFile, write.destText)
        app.syncManager.requestSync(syncReason)
        showSnack("$verb to ${write.label}")
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
            dropFavoritesInRange(loaded.document, loaded.fileName, source.lineIndex until sourceEnd)
        }
    }

    companion object {
        val Factory = factory { DocumentViewModel(it) }
    }
}

/**
 * Note identity: "fileName@headlineLineIndex[#customId]". [lineIndex] is a snapshot from
 * whenever this ref was created (e.g. a favorite added earlier, or an outline row tapped just
 * now); it can go stale if the file was edited externally (this app's primary edit path) or
 * elsewhere in-app between then and when the ref is resolved. [customId] — the heading's
 * `:CUSTOM_ID:`/`:ID:`, when known — is the stable identity to resolve against; see
 * [OrgDocument.headlineFor]. Null for refs with no id at hand (fresh outline taps resolve
 * against the live doc so [lineIndex] alone is already correct there).
 */
data class NoteRef(val fileName: String, val lineIndex: Int, val customId: String? = null) {
    fun encode(): String = if (customId != null) "$fileName@$lineIndex#$customId" else "$fileName@$lineIndex"

    /** True when this ref points at the file's intro (see [INTRO_LINE_INDEX]). */
    val isIntro: Boolean get() = lineIndex == INTRO_LINE_INDEX

    companion object {
        /** A ref to [fileName]'s intro: its heading-less content. */
        fun intro(fileName: String) = NoteRef(fileName, INTRO_LINE_INDEX)

        fun decode(noteId: String): NoteRef? {
            val hash = noteId.indexOf('#')
            val base = if (hash >= 0) noteId.substring(0, hash) else noteId
            val customId = if (hash >= 0) noteId.substring(hash + 1).takeIf { it.isNotEmpty() } else null
            val at = base.lastIndexOf('@')
            if (at <= 0) return null
            val line = base.substring(at + 1).toIntOrNull() ?: return null
            return NoteRef(base.substring(0, at), line, customId)
        }
    }
}

fun OrgDocument.headlineAtLine(lineIndex: Int): OrgHeadline? =
    headlines.firstOrNull { it.lineIndex == lineIndex }

/**
 * Resolves a [NoteRef] to its headline. Tries [NoteRef.customId] first (via `:CUSTOM_ID:` then
 * `:ID:`), which stays correct even when [NoteRef.lineIndex] has drifted from an external edit;
 * falls back to a raw line lookup when there's no id (older favorites, refs created directly
 * from a just-loaded doc, or an id search that came up empty).
 */
fun OrgDocument.headlineFor(ref: NoteRef): OrgHeadline? {
    val byId = ref.customId?.let { findByCustomId(it) ?: findById(it) }
    return byId ?: headlineAtLine(ref.lineIndex)
}

internal fun <T : ViewModel> factory(create: (GroveApplication) -> T) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GroveApplication
            return create(app) as V
        }
    }
