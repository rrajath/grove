package com.rrajath.grove.data

import com.rrajath.grove.search.NoteMeta

/**
 * Index row → the matcher's view of a note. Shared so the Search screen, the
 * Agenda screen and the FTS parity tests all feed `QueryMatcher` byte-identical
 * input; a divergence here would look like a search bug.
 */
fun NoteEntity.toNoteMeta() = NoteMeta(
    fileName = fileName,
    lineIndex = lineIndex,
    title = title,
    keyword = keyword,
    isDoneKeyword = isDone,
    priority = priority,
    tags = tags.split(':').filter { it.isNotEmpty() },
    inheritedTags = inheritedTags.split(':').filter { it.isNotEmpty() },
    scheduled = scheduled,
    deadline = deadline,
    closed = closed,
    createdAt = createdAt,
    lastModified = lastModified,
    searchText = title + "\n" + body,
)
