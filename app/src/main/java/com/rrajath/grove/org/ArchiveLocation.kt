package com.rrajath.grove.org

/** A parsed `:ARCHIVE:`/`#+ARCHIVE:` value: destination file plus an optional nested heading path. */
data class ArchiveTarget(val fileName: String, val headingPath: List<String>)

/**
 * Parses and resolves org's `ARCHIVE` property/keyword, and locates (or
 * creates) the heading path it names inside a destination document.
 */
object ArchiveLocation {

    /**
     * Parses `<relative-path>[::* <heading>[/<heading>...]]` (a `./` prefix on
     * the path is stripped; nested heading segments are `/`-separated). Returns
     * null for a blank value.
     */
    fun parse(raw: String): ArchiveTarget? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split("::", limit = 2)
        val filePart = parts[0].trim().removePrefix("./")
        if (filePart.isEmpty()) return null
        val fileName = if (filePart.endsWith(".org")) filePart else "$filePart.org"
        val headingPath = if (parts.size > 1) {
            parts[1].trim().removePrefix("*").trim()
                .split("/").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        return ArchiveTarget(fileName, headingPath)
    }

    /**
     * Nearest-ancestor-wins resolution, org-property-inheritance style: a
     * heading's own `:ARCHIVE:` wins, else the closest ancestor's, else the
     * file-level `#+ARCHIVE:` keyword.
     */
    fun resolve(doc: OrgDocument, headline: OrgHeadline): ArchiveTarget? {
        var current: OrgHeadline? = headline
        while (current != null) {
            current.properties["ARCHIVE"]?.let { return parse(it) }
            current = doc.parent(current)
        }
        val fileLevel = doc.preambleKeywords.firstOrNull { it.first == "#+ARCHIVE:" }?.second
        return fileLevel?.let { parse(it) }
    }

    /**
     * Finds each heading in [path] in order (top level for the first segment,
     * a direct child of the previous match thereafter), creating any missing
     * one as an empty heading. Returns the possibly-mutated document plus the
     * final heading, or a null heading when [path] is empty (i.e. top level).
     */
    fun findOrCreateHeadingPath(doc: OrgDocument, path: List<String>): Pair<OrgDocument, OrgHeadline?> {
        var currentDoc = doc
        var parent: OrgHeadline? = null
        for (title in path) {
            val siblings = parent?.let { currentDoc.directChildren(it) }
                ?: currentDoc.headlines.filter { currentDoc.parent(it) == null }
            val existing = siblings.firstOrNull { it.title == title }
            parent = if (existing != null) {
                existing
            } else {
                val (newText, newLine) = if (parent != null) {
                    OrgMutations.newChild(currentDoc, parent, title)
                } else {
                    OrgMutations.newTopLevel(currentDoc, title)
                }
                currentDoc = OrgParser.parse(newText, currentDoc.keywords)
                currentDoc.headlines.firstOrNull { it.lineIndex == newLine }
            }
        }
        return currentDoc to parent
    }
}
