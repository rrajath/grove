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
import com.rrajath.grove.ui.vault.factory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class EditorUiState(
    val loading: Boolean = true,
    val fileName: String = "",
    val lineIndex: Int = 0,
    /** The note's subtree text being edited. */
    val buffer: String = "",
    val loadedRevision: String? = null,
    val keywords: OrgKeywords = OrgKeywords.DEFAULT,
    val dirty: Boolean = false,
    val error: String? = null,
    /** File changed on disk since load; offer overwrite (Force Save). */
    val staleFile: Boolean = false,
    val allTags: List<String> = emptyList(),
)

class EditorViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    fun load(ref: NoteRef) {
        viewModelScope.launch {
            val vault = app.vault.value ?: run {
                _state.value = EditorUiState(loading = false, error = "No sync folder configured")
                return@launch
            }
            val doc = vault.open(ref.fileName) ?: run {
                _state.value = EditorUiState(loading = false, error = "${ref.fileName} not found")
                return@launch
            }
            val headline = doc.headlines.firstOrNull { it.lineIndex == ref.lineIndex } ?: run {
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
                lineIndex = ref.lineIndex,
                buffer = OrgMutations.subtreeText(doc, headline),
                loadedRevision = vault.revision(ref.fileName),
                keywords = app.keywords.value,
                allTags = tags,
            )
        }
    }

    fun onBufferChange(text: String) {
        _state.value = _state.value.copy(buffer = text, dirty = true)
    }

    /** Parse the buffer alone; its first headline is the note being edited. */
    private fun bufferHeadline(): Pair<OrgDocument, OrgHeadline>? {
        val doc = OrgParser.parse(_state.value.buffer, _state.value.keywords)
        val h = doc.headlines.firstOrNull() ?: return null
        return doc to h
    }

    val currentHeadline: OrgHeadline?
        get() = bufferHeadline()?.second

    private fun mutateBuffer(block: (OrgDocument, OrgHeadline) -> String) {
        val (doc, h) = bufferHeadline() ?: return
        _state.value = _state.value.copy(buffer = block(doc, h), dirty = true)
    }

    fun setKeyword(keyword: String?) = mutateBuffer { d, h -> OrgMutations.setKeyword(d, h, keyword) }
    fun setPriority(priority: Char?) = mutateBuffer { d, h -> OrgMutations.setPriority(d, h, priority) }
    fun setTags(tags: List<String>) = mutateBuffer { d, h -> OrgMutations.setTags(d, h, tags) }
    fun setScheduled(ts: OrgTimestamp?) = mutateBuffer { d, h -> OrgMutations.setScheduled(d, h, ts) }
    fun setDeadline(ts: OrgTimestamp?) = mutateBuffer { d, h -> OrgMutations.setDeadline(d, h, ts) }

    /** Apply the specific [doneKeyword] the user picked (repeaters advance instead). */
    fun markDone(doneKeyword: String) {
        mutateBuffer { d, h -> OrgMutations.markDone(d, h, doneKeyword, LocalDateTime.now()) }
    }

    /**
     * Write the buffer back into the file. Refuses (sets [EditorUiState.staleFile])
     * if the file changed on disk since load, unless [force].
     */
    fun save(force: Boolean = false, onSaved: () -> Unit = {}) {
        val s = _state.value
        if (!s.dirty || s.error != null) {
            onSaved()
            return
        }
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val currentRevision = vault.revision(s.fileName)
            if (!force && currentRevision != s.loadedRevision) {
                _state.value = s.copy(staleFile = true)
                return@launch
            }
            val doc = vault.open(s.fileName) ?: return@launch
            val headline = doc.headlines.firstOrNull { it.lineIndex == s.lineIndex }
            val newText = if (headline != null) {
                OrgMutations.replaceSubtree(doc, headline, s.buffer)
            } else {
                // Note vanished from the file (heavy external edit) — append the
                // buffer at the end rather than lose the user's work.
                doc.text.trimEnd('\n') + "\n" + s.buffer.trimEnd('\n') + "\n"
            }
            vault.save(s.fileName, newText)
            app.syncManager.requestSync("note saved")
            _state.value = s.copy(
                dirty = false,
                staleFile = false,
                loadedRevision = vault.revision(s.fileName),
            )
            onSaved()
        }
    }

    fun dismissStale() {
        _state.value = _state.value.copy(staleFile = false)
    }

    companion object {
        val Factory = factory { EditorViewModel(it) }
    }
}
