package com.rrajath.grove.sync

import com.rrajath.grove.vault.FileEntry
import com.rrajath.grove.vault.FileStore
import com.rrajath.grove.vault.IgnoreRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sync state machine (PRD §13): Idle → Checking → Pulling → Done/Conflict/Error.
 * For the v1 Local Directory backend the .org files on disk ARE the source of
 * truth, so "pulling" means re-indexing files whose revision changed (Syncthing
 * or any other tool may rewrite them at any time). Merging/Pushing states exist
 * for the v2 remote backends (WebDAV/Dropbox) which implement [FileStore]
 * against a remote and add an upload leg.
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object Checking : SyncState()
    data class Pulling(val fileName: String, val index: Int, val total: Int) : SyncState()
    data class Done(val result: SyncResult) : SyncState()
    data class Error(val message: String) : SyncState()
}

data class SyncResult(
    val pulled: List<String>,
    val removed: List<String>,
    /** Notebook file name → Syncthing conflict copy file name. */
    val conflicts: Map<String, String>,
    val completedAt: Long,
)

/** Persistence boundary so the engine is unit-testable without Room. */
interface NoteIndex {
    /** Last indexed revision per notebook file name. */
    suspend fun knownRevisions(): Map<String, String>

    suspend fun indexNotebook(
        fileName: String,
        revision: String,
        text: String,
        lastModified: Long,
        conflictFileName: String?,
    )

    /** Update only the conflict marker (file content unchanged). */
    suspend fun setConflict(fileName: String, conflictFileName: String?)

    suspend fun removeNotebook(fileName: String)
}

class SyncEngine(
    private val store: FileStore,
    private val index: NoteIndex,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    val revision: (FileEntry) -> String = { "${it.lastModified}:${it.size}" }

    suspend fun sync(log: (String) -> Unit = {}): SyncResult? {
        _state.value = SyncState.Checking
        return try {
            val entries = store.list()
            val ignore = entries.firstOrNull { it.name == IgnoreRules.FILE_NAME }
                ?.let { IgnoreRules(store.read(it.name)) }
                ?: IgnoreRules("")

            val conflicts = SyncConflicts.detect(entries.map { it.name })
            val notebooks = entries.filter {
                it.name.endsWith(".org") &&
                        !SyncConflicts.isConflictFile(it.name) &&
                        !ignore.isIgnored(it.name)
            }

            val known = index.knownRevisions()
            val current = notebooks.associateBy({ it.name }, revision)
            val changed = notebooks.filter { known[it.name] != revision(it) }
            val removed = (known.keys - current.keys).toList()

            changed.forEachIndexed { i, entry ->
                _state.value = SyncState.Pulling(entry.name, i + 1, changed.size)
                index.indexNotebook(
                    fileName = entry.name,
                    revision = revision(entry),
                    text = store.read(entry.name),
                    lastModified = entry.lastModified,
                    conflictFileName = conflicts[entry.name],
                )
                log("pulled ${entry.name}")
            }
            removed.forEach {
                index.removeNotebook(it)
                log("removed $it from index")
            }
            // Conflict markers can change without the file content changing
            // (Syncthing dropping a .sync-conflict copy next to it).
            notebooks.filter { it !in changed }.forEach {
                index.setConflict(it.name, conflicts[it.name])
            }
            conflicts.forEach { (base, copy) -> log("conflict: $base vs $copy") }

            val result = SyncResult(
                pulled = changed.map { it.name },
                removed = removed,
                conflicts = conflicts,
                completedAt = clock(),
            )
            _state.value = SyncState.Done(result)
            result
        } catch (e: Exception) {
            log("sync failed: ${e.message}")
            _state.value = SyncState.Error(e.message ?: "Sync failed")
            null
        }
    }
}
