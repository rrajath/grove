package com.rrajath.grove.org

import java.time.LocalDateTime

/**
 * The context properties Emacs's `org-archive-subtree` records on an archived
 * heading, in Emacs's order: `ARCHIVE_TIME`, `ARCHIVE_FILE`, `ARCHIVE_OLPATH`,
 * `ARCHIVE_CATEGORY`, `ARCHIVE_TODO`. Like Emacs, a blank value (a top-level
 * heading's outline path, a keyword-less heading's TODO) is left out.
 */
object ArchiveProperties {

    /**
     * The properties for archiving [h] out of [doc] at [now]. [sourceFile] is
     * the vault-relative file name (for the category fallback) and [sourcePath]
     * its full device path (for `ARCHIVE_FILE`).
     */
    fun of(
        doc: OrgDocument,
        h: OrgHeadline,
        sourceFile: String,
        sourcePath: String,
        now: LocalDateTime,
    ): List<Pair<String, String>> {
        // Inactive-timestamp text without its brackets: `2026-09-26 Sat 14:05`.
        val time = OrgTimestamp(now.toLocalDate(), now.toLocalTime().withSecond(0).withNano(0), active = false)
            .format().removePrefix("[").removeSuffix("]")
        return listOf(
            "ARCHIVE_TIME" to time,
            "ARCHIVE_FILE" to sourcePath,
            "ARCHIVE_OLPATH" to outlinePath(doc, h),
            "ARCHIVE_CATEGORY" to category(doc, h, sourceFile),
            "ARCHIVE_TODO" to h.keyword.orEmpty(),
        ).filter { it.second.isNotBlank() }
    }

    /** Titles of [h]'s ancestors, outermost first, `/`-joined (org's outline path). */
    fun outlinePath(doc: OrgDocument, h: OrgHeadline): String =
        generateSequence(doc.parent(h)) { doc.parent(it) }
            .map { it.title.trim() }
            .toList()
            .asReversed()
            .joinToString("/")

    /**
     * org's category: the nearest `:CATEGORY:` property on [h] or an ancestor,
     * then the file-level drawer's, then the last `#+CATEGORY:` keyword, then
     * [sourceFile]'s base name without its extension.
     */
    fun category(doc: OrgDocument, h: OrgHeadline, sourceFile: String): String {
        var current: OrgHeadline? = h
        while (current != null) {
            current.properties.entries
                .firstOrNull { it.key.equals("CATEGORY", ignoreCase = true) && it.value.isNotBlank() }
                ?.let { return it.value.trim() }
            current = doc.parent(current)
        }
        doc.filePropertyDrawer
            .firstOrNull { it.first.equals(":CATEGORY:", ignoreCase = true) && it.second.isNotBlank() }
            ?.let { return it.second.trim() }
        doc.preambleKeywords
            .lastOrNull { it.first.equals("#+CATEGORY:", ignoreCase = true) && it.second.isNotBlank() }
            ?.let { return it.second.trim() }
        return sourceFile.substringAfterLast('/').substringBeforeLast('.')
    }

    /**
     * [subtree] (a detached subtree's text, root headline first) with [props]
     * set in its root's `:PROPERTIES:` drawer: appended to an existing drawer
     * (replacing a same-named key in place), or in a new drawer otherwise.
     */
    fun stamp(subtree: String, keywords: OrgKeywords, props: List<Pair<String, String>>): String =
        props.fold(subtree) { text, (key, value) ->
            val doc = OrgParser.parse(text, keywords)
            val root = doc.headlines.firstOrNull() ?: return text
            OrgMutations.upsertProperty(doc, root, key, value)
        }
}
