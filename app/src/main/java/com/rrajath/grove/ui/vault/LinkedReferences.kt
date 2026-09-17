package com.rrajath.grove.ui.vault

import com.rrajath.grove.data.NoteEntity
import com.rrajath.grove.data.NoteOutlineRow
import com.rrajath.grove.org.OrgLinkParser
import com.rrajath.grove.org.OrgLinkTarget
import com.rrajath.grove.ui.components.orgInlineLinks
import com.rrajath.grove.ui.components.orgInlinePlainText

/** One `[[id:…]]` link elsewhere in the vault that resolves to the note the sheet was opened for. */
data class LinkedReferenceHit(
    val fileName: String,
    val lineIndex: Int,
    val orgId: String?,
    val customId: String?,
    /** Ancestor path down to and including the referencing note's own title; empty for a file intro. */
    val crumb: String,
    /** Raw org body of the referencing note, for rendering with `annotateOrgInline`. */
    val body: String,
    /** The raw `[[target]]` string that resolved here, so the UI can find + highlight its rendered range via `orgInlineLinks`. */
    val matchedTarget: String,
)

/** A plain-text mention of the note's title elsewhere, with no link syntax around it. */
data class UnlinkedMentionHit(
    val fileName: String,
    val lineIndex: Int,
    val orgId: String?,
    val customId: String?,
    val crumb: String,
    val body: String,
    /** Character range of the mention within `orgInlinePlainText(body)`. */
    val plainTextRange: IntRange,
)

data class LinkedReferenceFileGroup(val fileName: String, val hits: List<LinkedReferenceHit>)

data class LinkedReferencesResult(
    val linkedByFile: List<LinkedReferenceFileGroup>,
    val unlinked: List<UnlinkedMentionHit>,
) {
    val linkedCount: Int get() = linkedByFile.sumOf { it.hits.size }
    val unlinkedCount: Int get() = unlinked.size

    companion object {
        val EMPTY = LinkedReferencesResult(emptyList(), emptyList())
    }
}

/**
 * Ancestor-path breadcrumb (own title included, file name excluded) for every
 * heading in [headings], keyed by (fileName, lineIndex). Same one-pass
 * ancestor-stack shape as `LinkPickerSearch.buildLinkSearchIndex`'s crumbs,
 * except the path here ends at the heading itself -- this labels "where a
 * reference sits", not "where to browse from" -- and the file name is left
 * off since callers already group references by file.
 */
fun buildOwnPathCrumbs(headings: List<NoteOutlineRow>): Map<Pair<String, Int>, String> {
    val out = HashMap<Pair<String, Int>, String>(headings.size * 2)
    var i = 0
    while (i < headings.size) {
        val file = headings[i].fileName
        val stack = ArrayDeque<Pair<Int, String>>()
        while (i < headings.size && headings[i].fileName == file) {
            val row = headings[i]
            while (stack.isNotEmpty() && stack.last().first >= row.level) stack.removeLast()
            out[file to row.lineIndex] = (stack.map { it.second } + row.title).joinToString(" › ")
            stack.addLast(row.level to row.title)
            i++
        }
    }
    return out
}

/** Escapes `%`, `_`, and `\` in [s] so it can be passed as a literal LIKE needle with `ESCAPE '\\'`. */
fun escapeLikeNeedle(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * Every `[[id:targetId]]` link elsewhere in the vault that actually resolves
 * to this note (heading or whole file), grouped by source file, plus every
 * other note whose body plainly mentions [title] without linking to it.
 * [selfKey] excludes the note's own body from both. Pure over pre-fetched
 * data (see `IndexDao.notesWithBodyContaining`) so it's JVM-testable without
 * a database.
 *
 * [linkCandidates] and [mentionCandidates] only need to contain the
 * substring -- this does the precise parse (via [OrgLinkParser]) that turns a
 * substring hit into a real backlink, filtering out coincidental matches
 * (e.g. an unrelated `id:` fragment that isn't actually inside `[[ ]]`).
 */
fun computeLinkedReferences(
    targetId: String?,
    title: String,
    selfKey: Pair<String, Int>,
    linkCandidates: List<NoteEntity>,
    mentionCandidates: List<NoteEntity>,
    crumbs: Map<Pair<String, Int>, String>,
): LinkedReferencesResult {
    val linked = mutableListOf<LinkedReferenceHit>()
    val linkedKeys = HashSet<Pair<String, Int>>()
    if (targetId != null) {
        for (row in linkCandidates) {
            val key = row.fileName to row.lineIndex
            if (key == selfKey) continue
            val link = orgInlineLinks(row.body).firstOrNull { inline ->
                val parsed = OrgLinkParser.parse(inline.target, row.fileName)
                parsed is OrgLinkTarget.Id && parsed.id == targetId
            } ?: continue
            linked += LinkedReferenceHit(
                fileName = row.fileName,
                lineIndex = row.lineIndex,
                orgId = row.orgId,
                customId = row.customId,
                crumb = crumbs[key].orEmpty(),
                body = row.body,
                matchedTarget = link.target,
            )
            linkedKeys += key
        }
    }

    val unlinked = mutableListOf<UnlinkedMentionHit>()
    if (title.isNotBlank()) {
        for (row in mentionCandidates) {
            val key = row.fileName to row.lineIndex
            if (key == selfKey || key in linkedKeys) continue
            val plain = orgInlinePlainText(row.body)
            val idx = plain.indexOf(title, ignoreCase = true)
            if (idx < 0) continue
            unlinked += UnlinkedMentionHit(
                fileName = row.fileName,
                lineIndex = row.lineIndex,
                orgId = row.orgId,
                customId = row.customId,
                crumb = crumbs[key].orEmpty(),
                body = row.body,
                plainTextRange = idx until (idx + title.length),
            )
        }
    }

    val linkedByFile = linked.groupBy { it.fileName }
        .toSortedMap()
        .map { (file, hits) -> LinkedReferenceFileGroup(file, hits) }

    return LinkedReferencesResult(linkedByFile, unlinked)
}
