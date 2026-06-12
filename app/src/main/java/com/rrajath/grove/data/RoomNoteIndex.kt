package com.rrajath.grove.data

import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.sync.NoteIndex

/** [NoteIndex] over Room: parses notebook text into row entities. */
class RoomNoteIndex(
    private val dao: IndexDao,
    private val keywords: () -> OrgKeywords = { OrgKeywords.DEFAULT },
) : NoteIndex {

    override suspend fun knownRevisions(): Map<String, String> =
        dao.notebooks().associate { it.fileName to it.revision }

    override suspend fun indexNotebook(
        fileName: String,
        revision: String,
        text: String,
        lastModified: Long,
        conflictFileName: String?,
    ) {
        val doc = OrgParser.parse(text, keywords())
        val notes = doc.headlines.map { h ->
            NoteEntity(
                fileName = fileName,
                lineIndex = h.lineIndex,
                level = h.level,
                title = h.title,
                keyword = h.keyword,
                priority = h.priority?.toString(),
                tags = h.tags.joinToString(":"),
                inheritedTags = doc.inheritedTags(h).joinToString(":"),
                scheduled = h.planning.scheduled?.format(),
                deadline = h.planning.deadline?.format(),
                closed = h.planning.closed?.format(),
                orgId = h.id,
                customId = h.customId,
                createdAt = h.properties["CREATED"],
            )
        }
        dao.replaceNotebook(
            NotebookEntity(
                fileName = fileName,
                revision = revision,
                noteCount = notes.size,
                lastModified = lastModified,
                conflictFileName = conflictFileName,
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
}
