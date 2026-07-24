package com.rrajath.grove.data

import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.sync.KnownNotebook
import com.rrajath.grove.sync.NoteIndex
import com.rrajath.grove.sync.NotebookStub

/** [NoteIndex] over Room: parses notebook text into row entities. */
class RoomNoteIndex(
    private val dao: IndexDao,
    private val keywords: () -> OrgKeywords = { OrgKeywords.DEFAULT },
    /**
     * Notified with the freshly parsed [com.rrajath.grove.org.OrgDocument] right
     * after each notebook is (re)indexed, so e.g. reminder reconciliation can run
     * per-file instead of waiting for the whole vault to finish syncing.
     */
    private val onIndexed: suspend (fileName: String, doc: com.rrajath.grove.org.OrgDocument) -> Unit = { _, _ -> },
) : NoteIndex {

    override suspend fun knownNotebooks(): Map<String, KnownNotebook> =
        dao.notebookSyncStates().associate {
            it.fileName to KnownNotebook(it.revision, it.conflictFileName, it.isIndexed)
        }

    override suspend fun stubNotebooks(stubs: List<NotebookStub>) {
        dao.insertNotebookStubs(
            stubs.map {
                NotebookEntity(
                    fileName = it.fileName,
                    revision = it.revision,
                    noteCount = 0,
                    lastModified = it.lastModified,
                    conflictFileName = it.conflictFileName,
                    title = null,
                    isIndexed = false,
                )
            }
        )
    }

    override suspend fun indexNotebook(
        fileName: String,
        revision: String,
        text: String,
        lastModified: Long,
        conflictFileName: String?,
    ) {
        val doc = OrgParser.parse(text, keywords())
        val inheritedTagsAll = doc.inheritedTagsAll()
        val notes = doc.headlines.mapIndexed { i, h ->
            NoteEntity(
                fileName = fileName,
                lineIndex = h.lineIndex,
                level = h.level,
                title = h.title,
                keyword = h.keyword,
                priority = h.priority?.toString(),
                tags = h.tags.joinToString(":"),
                inheritedTags = inheritedTagsAll[i].joinToString(":"),
                scheduled = h.planning.scheduled?.format(),
                deadline = h.planning.deadline?.format(),
                closed = h.planning.closed?.format(),
                orgId = h.id,
                customId = h.customId,
                createdAt = h.properties["CREATED"],
                body = doc.bodyOf(h).joinToString("\n").take(MAX_BODY_CHARS),
                isDone = h.keyword != null && doc.keywords.isDone(h.keyword),
                lastModified = lastModified,
            )
        }
        dao.replaceNotebook(
            NotebookEntity(
                fileName = fileName,
                revision = revision,
                // Top-level headings only — subheadings are part of their note.
                noteCount = doc.headlines.count { it.level == 1 },
                lastModified = lastModified,
                conflictFileName = conflictFileName,
                title = doc.preambleKeywords.firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }?.second,
            ),
            notes,
        )
        onIndexed(fileName, doc)
    }

    override suspend fun setConflict(fileName: String, conflictFileName: String?) {
        dao.setConflict(fileName, conflictFileName)
    }

    override suspend fun removeNotebook(fileName: String) {
        dao.removeNotebook(fileName)
    }

    companion object {
        private const val MAX_BODY_CHARS = 4000
    }
}
