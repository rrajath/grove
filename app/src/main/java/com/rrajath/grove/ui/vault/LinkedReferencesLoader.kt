package com.rrajath.grove.ui.vault

import com.rrajath.grove.AppDispatchers
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.settings.SettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SQLite's host-parameter limit is 999 on older Android; stay well under it. */
private const val OUTLINE_QUERY_CHUNK = 500

/**
 * Backlinks/mentions for the Linked References bar+sheet, shared by
 * [DocumentViewModel] and `EditorViewModel`. Each [load] cancels the one before
 * it, so a slow scan for an old target can't land after (and overwrite) a newer one.
 */
class LinkedReferencesLoader(
    private val scope: CoroutineScope,
    private val database: GroveDatabase,
    private val settings: SettingsSource,
    private val dispatchers: AppDispatchers,
) {
    private val _result = MutableStateFlow(LinkedReferencesResult.EMPTY)
    val result: StateFlow<LinkedReferencesResult> = _result

    private var job: Job? = null

    /**
     * Loads what elsewhere in the vault links to (or plainly mentions) this
     * note: [targetId] is the heading's own `:ID:` (or the file's, for the
     * intro), null when it has none yet -- the linked half is then always
     * empty, but the unlinked-mentions scan by [title] still runs.
     */
    fun load(fileName: String, lineIndex: Int, targetId: String?, title: String) {
        job?.cancel()
        job = scope.launch {
            // A vault-wide body scan that only the Linked References bar uses: skip it
            // entirely while that bar can't show.
            if (!settings.settings.first().roamBacklinksActive) {
                _result.value = LinkedReferencesResult.EMPTY
                return@launch
            }
            _result.value = withContext(dispatchers.default) {
                val dao = database.indexDao()
                val linkCandidates = targetId
                    ?.let { dao.notesWithBodyContaining(escapeLikeNeedle("id:$it")) }
                    .orEmpty()
                val mentionCandidates = title
                    .takeIf { it.isNotBlank() }
                    ?.let { dao.notesWithBodyContaining(escapeLikeNeedle(it)) }
                    .orEmpty()
                val bare = computeLinkedReferences(
                    targetId, title, fileName to lineIndex, linkCandidates, mentionCandidates, emptyMap(),
                )
                // Crumbs only for the files that actually produced hits, not every heading in the vault.
                val files = hitFiles(bare)
                if (files.isEmpty()) {
                    bare
                } else {
                    val outlines = files.chunked(OUTLINE_QUERY_CHUNK).flatMap { dao.headingOutlinesFor(it) }
                    withCrumbs(bare, buildOwnPathCrumbs(outlines))
                }
            }
        }
    }

    /** Cancels any in-flight [load] and empties the result, for a target whose bar can't show. */
    fun clear() {
        job?.cancel()
        job = null
        _result.value = LinkedReferencesResult.EMPTY
    }
}
