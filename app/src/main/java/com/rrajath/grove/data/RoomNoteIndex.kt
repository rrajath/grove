package com.rrajath.grove.data

import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.sync.KnownNotebook
import com.rrajath.grove.sync.NoteIndex

/** [NoteIndex] over Room: parses notebook text into row entities. */
class RoomNoteIndex(
    private val dao: IndexDao,
    private val keywords: () -> OrgKeywords = { OrgKeywords.DEFAULT },
) : NoteIndex {

    override suspend fun knownNotebooks(): Map<String, KnownNotebook> =
        dao.notebookSyncStates().associate {
            it.fileName to KnownNotebook(it.revision, it.conflictFileName)
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
