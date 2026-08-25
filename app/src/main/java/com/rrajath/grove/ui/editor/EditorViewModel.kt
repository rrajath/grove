package com.rrajath.grove.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.OutlineSnack
import com.rrajath.grove.ui.vault.factory
import com.rrajath.grove.ui.vault.headlineFor
import com.rrajath.grove.vault.AutoArchive
import com.rrajath.grove.vault.StateChangeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class EditorUiState(
    val loading: Boolean = true,
    val fileName: String = "",
    val lineIndex: Int = 0,
    /** True when this session is editing the file's preamble (see [EditorViewModel.loadPreface])
     *  rather than a headline's subtree; [lineIndex] is meaningless in that case. */
    val isPreface: Boolean = false,
    /** The note's subtree text being edited (or the file's preamble, when [isPreface]). */
    val buffer: String = "",
    val loadedRevision: String? = null,
    val keywords: OrgKeywords = OrgKeywords.DEFAULT,
    val dirty: Boolean = false,
    val error: String? = null,
    /** File changed on disk since load; offer overwrite (Force Save). */
    val staleFile: Boolean = false,
    val allTags: List<String> = emptyList(),
    /**
     * Counts buffer rewrites that did *not* come from the text field: today
     * only the metadata sheet's mutations. The editor screen pushes the buffer
     * into the field when this changes, and never otherwise: inferring
     * "external rewrite" from the buffer text alone made fast typing race the
     * one-frame lag of the field→view-model report, and re-pushing the older
     * buffer swallowed the characters typed in between. Reset to 0 by [load].
     */
    val bufferRevision: Long = 0,
)

class EditorViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    /** Serializes writes so an idle auto-save and an explicit save can't race. */
    private val saveMutex = Mutex()

    private val _snack = MutableStateFlow<OutlineSnack?>(null)
    val snack: StateFlow<OutlineSnack?> = _snack
    private var eventId = 0L

    /** Everything needed to put the buffer back where [changeKeyword]'s auto-archive found it. */
    private data class ArchiveUndo(
        val files: List<Pair<String, String>>,
        val fileName: String,
        val lineIndex: Int,
        val buffer: String,
    )

    private var archiveUndo: ArchiveUndo? = null

    private fun showSnack(message: String) {
        val s = OutlineSnack(message, ++eventId)
        _snack.value = s
        viewModelScope.launch {
            delay(4200)
            if (_snack.value?.id == s.id) _snack.value = null
        }
    }

    /** Reverts the auto-archive move (state change + refile) from the most recent [changeKeyword]. */
    fun undo() {
        val snap = archiveUndo ?: return
        archiveUndo = null
        _snack.value = null
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            snap.files.forEach { (name, text) -> vault.save(name, text) }
            app.syncManager.requestSync("note undo")
            val revision = vault.revision(snap.fileName)
            _state.update {
                it.copy(
                    fileName = snap.fileName,
                    lineIndex = snap.lineIndex,
                    buffer = snap.buffer,
                    dirty = false,
                    loadedRevision = revision,
                    bufferRevision = it.bufferRevision + 1,
                )
            }
        }
    }

    fun load(ref: NoteRef) {
        // Republished as loading first so a re-load (the stale-file banner's
        // "Reload") is a visible false→true→false transition: that edge is what
        // makes the editor screen re-seed its text field from the fresh buffer.
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val vault = app.vault.value ?: run {
                _state.value = EditorUiState(loading = false, error = "No sync folder configured")
                return@launch
            }
            val doc = vault.open(ref.fileName) ?: run {
                _state.value = EditorUiState(loading = false, error = "${ref.fileName} not found")
                return@launch
            }
            // Resolve by customId first (survives an external edit shifting ref.lineIndex);
            // headline.lineIndex below is then the current, correct line, not ref's possibly
            // stale snapshot — every later lookup in this session (save, delete, keyword
            // change) keys off state.lineIndex, so this is where drift gets healed.
            val headline = doc.headlineFor(ref) ?: run {
                _state.value = EditorUiState(loading = false, error = "Note not found")
                return@launch
            }
            val tags = app.database.indexDao().allTagStrings()
                .flatMap { it.split(':') }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            _state.value = EditorUiState(
                loading = false,
                fileName = ref.fileName,
                lineIndex = headline.lineIndex,
                buffer = OrgMutations.subtreeText(doc, headline),
                loadedRevision = vault.revision(ref.fileName),
                keywords = app.keywords.value,
                allTags = tags,
            )
        }
    }

    /**
     * Loads [fileName]'s preamble (every line before the first headline) for the preface
     * editor, opened via a double-tap on Outline's PREFACE section. Unlike [load], this has
     * no headline to anchor to: [writeBuffer] detects [EditorUiState.isPreface] and splices
     * the buffer back in via [OrgMutations.replacePreface] instead.
     */
    fun loadPreface(fileName: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val vault = app.vault.value ?: run {
                _state.value = EditorUiState(loading = false, error = "No sync folder configured")
                return@launch
            }
            val doc = vault.open(fileName) ?: run {
                _state.value = EditorUiState(loading = false, error = "$fileName not found")
                return@launch
            }
            _state.value = EditorUiState(
                loading = false,
                fileName = fileName,
                isPreface = true,
                buffer = OrgMutations.prefaceText(doc),
                loadedRevision = vault.revision(fileName),
                keywords = app.keywords.value,
            )
        }
    }

    /** The text field reporting the user's own typing; never echoed back to it. */
    fun onBufferChange(text: String) {
        _state.update { it.copy(buffer = text, dirty = true) }
    }

    // Memoize the most recent parse so repeated currentHeadline reads and the
    // metadata mutations don't re-parse the same buffer over and over.
    private var parsedBuffer: String? = null
    private var parsedKeywords: OrgKeywords? = null
    private var parsedResult: Pair<OrgDocument, OrgHeadline>? = null

    /** Parse the buffer alone; its first headline is the note being edited. */
    private fun bufferHeadline(): Pair<OrgDocument, OrgHeadline>? {
        val buffer = _state.value.buffer
        val keywords = _state.value.keywords
        if (buffer == parsedBuffer && keywords == parsedKeywords) return parsedResult
        val doc = OrgParser.parse(buffer, keywords)
        val result = doc.headlines.firstOrNull()?.let { doc to it }
        parsedBuffer = buffer
        parsedKeywords = keywords
        parsedResult = result
        return result
    }

    val currentHeadline: OrgHeadline?
        get() = bufferHeadline()?.second

    /** Metadata-sheet edits: rewrite the buffer outside the text field, and bump
     *  [EditorUiState.bufferRevision] so the screen mirrors it into the field. */
    private fun mutateBuffer(block: (OrgDocument, OrgHeadline) -> String) {
        val (doc, h) = bufferHeadline() ?: return
        _state.update {
            it.copy(buffer = block(doc, h), dirty = true, bufferRevision = it.bufferRevision + 1)
        }
    }

    /**
     * Metadata sheet's state chips: routes through [OrgMutations.changeKeyword] so
     * picking a plain (non-done) keyword away from a done state also clears the
     * stale CLOSED stamp, the same as `OrgMutations.reopen`.
     *
     * Picking a done-type keyword may auto-archive (Settings § Notes), which needs the
     * vault and Settings and so can't stay inside the synchronous [mutateBuffer] path
     * every other chip uses; it flushes the buffer to disk first (auto-archive resolves
     * against the on-disk file, not the in-memory buffer) and, on a successful archive,
     * repoints [EditorUiState.fileName]/[EditorUiState.lineIndex] at the note's new
     * location so editing continues transparently.
     */
    fun changeKeyword(keyword: String?) {
        val (bufDoc, bufHeadline) = bufferHeadline() ?: return
        if (bufHeadline.keyword == keyword) return
        val maybeArchives = keyword != null && bufDoc.keywords.isDone(keyword)
        if (!maybeArchives) {
            mutateBuffer { d, h -> OrgMutations.changeKeyword(d, h, keyword, d.keywords, LocalDateTime.now()) }
            return
        }
        viewModelScope.launch {
            val vault = app.vault.value
            if (vault == null) {
                mutateBuffer { d, h -> OrgMutations.changeKeyword(d, h, keyword, d.keywords, LocalDateTime.now()) }
                return@launch
            }
            saveMutex.withLock { writeBuffer(force = true) }
            val s = _state.value
            val doc = vault.open(s.fileName)
            val headline = doc?.headlines?.firstOrNull { it.lineIndex == s.lineIndex }
            if (doc == null || headline == null) {
                mutateBuffer { d, h -> OrgMutations.changeKeyword(d, h, keyword, d.keywords, LocalDateTime.now()) }
                return@launch
            }
            val settings = app.settingsRepository.settings.first()
            when (
                val result = AutoArchive.apply(vault, settings, doc, s.fileName, headline, keyword, LocalDateTime.now())
            ) {
                is StateChangeResult.Plain -> {
                    vault.save(s.fileName, result.text)
                    app.syncManager.requestSync("note state set")
                    val newHeadline = result.doc.headlines.firstOrNull { it.lineIndex == s.lineIndex }
                    val revision = vault.revision(s.fileName)
                    _state.update {
                        it.copy(
                            buffer = newHeadline?.let { h -> OrgMutations.subtreeText(result.doc, h) } ?: it.buffer,
                            dirty = false,
                            loadedRevision = revision,
                            bufferRevision = it.bufferRevision + 1,
                        )
                    }
                }
                is StateChangeResult.Archived -> {
                    archiveUndo = ArchiveUndo(
                        files = if (result.sourceFile == result.destFile) {
                            listOf(result.sourceFile to doc.text)
                        } else {
                            listOf(s.fileName to doc.text, result.destFile to result.destTextBefore)
                        },
                        fileName = s.fileName,
                        lineIndex = s.lineIndex,
                        buffer = s.buffer,
                    )
                    vault.save(s.fileName, result.sourceText)
                    if (result.destFile != s.fileName) vault.save(result.destFile, result.destText)
                    app.syncManager.requestSync("note state set")
                    val destDoc = OrgParser.parse(result.destText, doc.keywords)
                    val destHeadline = destDoc.headlines.firstOrNull { it.lineIndex == result.destLineIndex }
                    val destRevision = vault.revision(result.destFile)
                    _state.update {
                        it.copy(
                            fileName = result.destFile,
                            lineIndex = result.destLineIndex,
                            buffer = destHeadline?.let { h -> OrgMutations.subtreeText(destDoc, h) } ?: it.buffer,
                            dirty = false,
                            loadedRevision = destRevision,
                            bufferRevision = it.bufferRevision + 1,
                        )
                    }
                    showSnack("Marked done. Refiled to ${result.label}")
                }
            }
        }
    }
    fun setPriority(priority: Char?) = mutateBuffer { d, h -> OrgMutations.setPriority(d, h, priority) }
    fun setTags(tags: List<String>) = mutateBuffer { d, h -> OrgMutations.setTags(d, h, tags) }
    fun setScheduled(ts: OrgTimestamp?) = mutateBuffer { d, h -> OrgMutations.setScheduled(d, h, ts) }
    fun setDeadline(ts: OrgTimestamp?) = mutateBuffer { d, h -> OrgMutations.setDeadline(d, h, ts) }

    /** Both planning dates in one edit: what the Dates screen commits. */
    fun setPlanningDates(scheduled: OrgTimestamp?, deadline: OrgTimestamp?) =
        mutateBuffer { d, h -> OrgMutations.setPlanningDates(d, h, scheduled, deadline) }

    /** Metadata sheet's "Add note": org's C-c C-z, logged into the LOGBOOK drawer. */
    fun addNote(note: String) {
        if (note.isBlank()) return
        val now = LocalDateTime.now()
        val stamp = OrgTimestamp(now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0), active = false)
        mutateBuffer { d, h -> OrgMutations.appendLogbookNote(d, h, note.trim(), stamp) }
    }

    /**
     * Write the buffer back into the file. Refuses (sets [EditorUiState.staleFile])
     * if the file changed on disk since load, unless [force].
     *
     * A save runs concurrently with typing (idle auto-save fires on a timer, and
     * the write itself is slow SAF I/O), so it must never publish a snapshot of
     * the state it captured when it started: doing so would rewind the buffer
     * to the pre-save text and drop whatever was typed in between. Every write
     * back into [_state] therefore goes through `update`, touching only the
     * fields this save actually owns, and [dirty] is recomputed by comparing the
     * text that reached disk against the text the buffer holds *now*.
     */
    fun save(force: Boolean = false, onSaved: () -> Unit = {}) {
        val initial = _state.value
        if (!initial.dirty || initial.error != null) {
            onSaved()
            return
        }
        viewModelScope.launch {
            // Serialized: two overlapping saves would both compute a new file
            // text from the same on-disk revision, and the slower one would
            // silently undo the faster one.
            val saved = saveMutex.withLock { writeBuffer(force) }
            if (saved) onSaved()
        }
    }

    /** @return true when the buffer is on disk (including "already was"). */
    private suspend fun writeBuffer(force: Boolean): Boolean {
        val s = _state.value
        if (s.error != null) return false
        // Another save (e.g. the idle auto-save that fired while the leave
        // dialog was up) got there first; the caller's "then leave" follow-up
        // is still owed.
        if (!s.dirty) return true
        val vault = app.vault.value ?: return false
        // The exact text this save is responsible for. Anything typed after
        // this line belongs to the next save, not this one.
        val savedBuffer = s.buffer
        val currentRevision = vault.revision(s.fileName)
        if (!force && currentRevision != s.loadedRevision) {
            _state.update { it.copy(staleFile = true) }
            return false
        }
        // Parsing and the subtree splice are pure CPU on a whole file; keep them
        // off the main thread so a large notebook can't stall the keyboard
        // mid-keystroke while an auto-save runs.
        val newText = withContext(Dispatchers.Default) {
            val doc = vault.open(s.fileName) ?: return@withContext null
            if (s.isPreface) {
                OrgMutations.replacePreface(doc, savedBuffer)
            } else {
                val headline = doc.headlines.firstOrNull { it.lineIndex == s.lineIndex }
                if (headline != null) {
                    OrgMutations.replaceSubtree(doc, headline, savedBuffer)
                } else {
                    // Note vanished from the file (heavy external edit); append the
                    // buffer at the end rather than lose the user's work.
                    doc.text.trimEnd('\n') + "\n" + savedBuffer.trimEnd('\n') + "\n"
                }
            }
        } ?: return false
        vault.save(s.fileName, newText)
        val newRevision = vault.revision(s.fileName)
        app.syncManager.requestSync("note saved")
        _state.update { current ->
            current.copy(
                // Still dirty if the user typed while the write was in flight:
                // those characters are only in memory, and the idle timer (keyed
                // on the buffer) will have already re-armed for them.
                dirty = current.buffer != savedBuffer,
                staleFile = false,
                loadedRevision = newRevision,
            )
        }
        return true
    }

    fun dismissStale() {
        _state.update { it.copy(staleFile = false) }
    }

    fun isCurrentHeadingBlank(): Boolean = currentHeadline?.title.isNullOrBlank()

    /**
     * Remove the edited subtree from the file without saving the buffer.
     * Used when the user discards a freshly created note that still has no heading.
     */
    fun deleteSubtree(onDeleted: () -> Unit = {}) {
        val s = _state.value
        viewModelScope.launch {
            val vault = app.vault.value ?: run { onDeleted(); return@launch }
            val doc = vault.open(s.fileName) ?: run { onDeleted(); return@launch }
            val headline = doc.headlines.firstOrNull { it.lineIndex == s.lineIndex }
            if (headline != null) {
                val newText = OrgMutations.deleteSubtree(doc, headline)
                vault.save(s.fileName, newText)
                app.syncManager.requestSync("empty note discarded")
            }
            onDeleted()
        }
    }

    companion object {
        val Factory = factory { EditorViewModel(it) }
    }
}
