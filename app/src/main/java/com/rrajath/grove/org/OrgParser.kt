package com.rrajath.grove.org

/** SCHEDULED / DEADLINE / CLOSED metadata from a headline's planning line. */
data class Planning(
    val scheduled: OrgTimestamp? = null,
    val deadline: OrgTimestamp? = null,
    val closed: OrgTimestamp? = null,
) {
    val isEmpty: Boolean get() = scheduled == null && deadline == null && closed == null
}

/**
 * One headline in the document, indexing into the document's line list.
 * The document text is the source of truth; this is a parsed view over it.
 */
data class OrgHeadline(
    /** Position in document order (0-based). */
    val index: Int,
    /** Line index of the `*` headline line. */
    val lineIndex: Int,
    val level: Int,
    val keyword: String?,
    val priority: Char?,
    val title: String,
    /** The headline's own tags (not inherited). */
    val tags: List<String>,
    val planning: Planning,
    val properties: Map<String, String>,
    /** First line of body content (after planning line and properties drawer). */
    val bodyStart: Int,
    /** Exclusive end: line index of the next headline (any level) or EOF. */
    val contentEnd: Int,
) {
    val id: String? get() = properties["ID"]
    val customId: String? get() = properties["CUSTOM_ID"]
}

/**
 * A parsed org file. [text] is verbatim file content — serialization returns
 * it unchanged, so parse → serialize is byte-identical by construction.
 */
class OrgDocument(
    val text: String,
    val keywords: OrgKeywords,
    val lines: List<String>,
    val fileTags: List<String>,
    val headlines: List<OrgHeadline>,
) {
    /** Line index where the first headline starts; preamble is lines before it. */
    val preambleEnd: Int = headlines.firstOrNull()?.lineIndex ?: lines.size

    fun serialize(): String = text

    fun directChildren(h: OrgHeadline): List<OrgHeadline> {
        val children = mutableListOf<OrgHeadline>()
        for (i in (h.index + 1) until headlines.size) {
            val other = headlines[i]
            if (other.level <= h.level) break
            if (other.level == h.level + 1) children.add(other)
        }
        return children
    }

    /** All descendants of [h] (its subtree, excluding itself). */
    fun subtree(h: OrgHeadline): List<OrgHeadline> {
        val result = mutableListOf<OrgHeadline>()
        for (i in (h.index + 1) until headlines.size) {
            val other = headlines[i]
            if (other.level <= h.level) break
            result.add(other)
        }
        return result
    }

    fun parent(h: OrgHeadline): OrgHeadline? {
        for (i in (h.index - 1) downTo 0) {
            if (headlines[i].level < h.level) return headlines[i]
        }
        return null
    }

    /** Own tags + ancestor tags + file tags, deduplicated, own-first. */
    fun inheritedTags(h: OrgHeadline): List<String> {
        val result = LinkedHashSet(h.tags)
        var p = parent(h)
        while (p != null) {
            result.addAll(p.tags)
            p = parent(p)
        }
        result.addAll(fileTags)
        return result.toList()
    }

    /** Body lines belonging to the headline itself (planning/properties excluded). */
    fun bodyOf(h: OrgHeadline): List<String> {
        val end = minOf(h.contentEnd, headlines.getOrNull(h.index + 1)?.lineIndex ?: lines.size)
        if (h.bodyStart >= end) return emptyList()
        return lines.subList(h.bodyStart, end)
    }

    fun findById(id: String): OrgHeadline? = headlines.firstOrNull { it.id == id }
    fun findByCustomId(customId: String): OrgHeadline? =
        headlines.firstOrNull { it.customId == customId }
    fun findByTitle(title: String): OrgHeadline? =
        headlines.firstOrNull { it.title == title }
}

object OrgParser {

    private val HEADLINE = Regex("""^(\*+)\s+(.*)$""")
    private val TAGS_AT_END = Regex("""\s+(:[A-Za-z0-9_@#%]+(?::[A-Za-z0-9_@#%]+)*:)\s*$""")
    private val PRIORITY = Regex("""^\[#([A-Za-z])\]\s*""")
    private val FILETAGS = Regex("""^#\+(?i:FILETAGS):\s*(.*)$""")
    private val PROPERTY_LINE = Regex("""^\s*:([^:\s]+):\s*(.*)$""")
    private val PLANNING_PART = Regex("""(SCHEDULED|DEADLINE|CLOSED):\s*""")

    fun parse(text: String, keywords: OrgKeywords = OrgKeywords.DEFAULT): OrgDocument {
        val lines = text.split("\n")
        val fileTags = mutableListOf<String>()
        data class Raw(val lineIndex: Int, val level: Int, val rest: String)

        val rawHeadlines = mutableListOf<Raw>()
        lines.forEachIndexed { i, line ->
            val m = HEADLINE.matchEntire(line)
            if (m != null) {
                rawHeadlines.add(Raw(i, m.groupValues[1].length, m.groupValues[2]))
            } else if (rawHeadlines.isEmpty()) {
                FILETAGS.matchEntire(line.trim())?.let { ft ->
                    fileTags.addAll(parseTagList(ft.groupValues[1]))
                }
            }
        }

        val headlines = rawHeadlines.mapIndexed { idx, raw ->
            val contentEnd = rawHeadlines.getOrNull(idx + 1)?.lineIndex ?: lines.size
            parseHeadline(idx, raw.lineIndex, raw.level, raw.rest, lines, contentEnd, keywords)
        }

        return OrgDocument(text, keywords, lines, fileTags, headlines)
    }

    private fun parseTagList(value: String): List<String> =
        value.split(':', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseHeadline(
        index: Int,
        lineIndex: Int,
        level: Int,
        restIn: String,
        lines: List<String>,
        contentEnd: Int,
        keywords: OrgKeywords,
    ): OrgHeadline {
        var rest = restIn

        // Trailing tags
        var tags = emptyList<String>()
        TAGS_AT_END.find(rest)?.let { m ->
            tags = m.groupValues[1].trim(':').split(':').filter { it.isNotEmpty() }
            rest = rest.removeRange(m.range)
        }

        // TODO keyword (must be a configured keyword followed by space/EOL)
        var keyword: String? = null
        val firstWord = rest.substringBefore(' ')
        if (firstWord in keywords.all && (rest.length == firstWord.length || rest[firstWord.length] == ' ')) {
            keyword = firstWord
            rest = rest.drop(firstWord.length).trimStart()
        }

        // Priority cookie
        var priority: Char? = null
        PRIORITY.find(rest)?.let { m ->
            priority = m.groupValues[1][0]
            rest = rest.removeRange(m.range)
        }

        val title = rest.trim()

        // Planning line (single line immediately after the headline)
        var cursor = lineIndex + 1
        var planning = Planning()
        if (cursor < contentEnd && isPlanningLine(lines[cursor])) {
            planning = parsePlanning(lines[cursor])
            cursor++
        }

        // Properties drawer
        val properties = linkedMapOf<String, String>()
        if (cursor < contentEnd && lines[cursor].trim().equals(":PROPERTIES:", ignoreCase = true)) {
            val pending = linkedMapOf<String, String>()
            var i = cursor + 1
            var closed = false
            while (i < contentEnd) {
                val line = lines[i].trim()
                if (line.equals(":END:", ignoreCase = true)) {
                    closed = true
                    break
                }
                PROPERTY_LINE.matchEntire(lines[i])?.let { m ->
                    pending[m.groupValues[1]] = m.groupValues[2].trim()
                }
                i++
            }
            // An unclosed drawer is body text, not properties.
            if (closed) {
                properties.putAll(pending)
                cursor = i + 1
            }
        }

        return OrgHeadline(
            index = index,
            lineIndex = lineIndex,
            level = level,
            keyword = keyword,
            priority = priority,
            title = title,
            tags = tags,
            planning = planning,
            properties = properties,
            bodyStart = cursor,
            contentEnd = contentEnd,
        )
    }

    private fun isPlanningLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("SCHEDULED:") || t.startsWith("DEADLINE:") || t.startsWith("CLOSED:")
    }

    private fun parsePlanning(line: String): Planning {
        var scheduled: OrgTimestamp? = null
        var deadline: OrgTimestamp? = null
        var closed: OrgTimestamp? = null
        val parts = PLANNING_PART.findAll(line).toList()
        parts.forEachIndexed { i, m ->
            val valueEnd = parts.getOrNull(i + 1)?.range?.first ?: line.length
            val value = line.substring(m.range.last + 1, valueEnd)
            val ts = OrgTimestamp.parse(value)
            when (m.groupValues[1]) {
                "SCHEDULED" -> scheduled = ts
                "DEADLINE" -> deadline = ts
                "CLOSED" -> closed = ts
            }
        }
        return Planning(scheduled, deadline, closed)
    }
}
