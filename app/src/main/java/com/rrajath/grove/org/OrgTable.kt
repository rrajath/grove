package com.rrajath.grove.org

/**
 * A parsed org table, ready to render as a grid (Read mode only; the editor keeps
 * the raw `| a | b |` text). Every row is padded to [columnCount] so callers can
 * index cells without bounds checks.
 *
 * Header detection follows the org convention: every row above the first `|---|`
 * horizontal rule is the header. A table with no rule at all falls back to
 * treating just its first row as the header.
 */
data class OrgTableModel(
    val headerRows: List<List<String>>,
    val bodyRows: List<List<String>>,
    val columnCount: Int,
) {
    val isEmpty: Boolean get() = columnCount == 0
}

/** A rule line is only `|`, `+`, `-` and whitespace, and has at least one `-`. */
private fun isRule(trimmed: String): Boolean =
    trimmed.contains('-') && trimmed.all { it == '|' || it == '+' || it == '-' || it.isWhitespace() }

private fun splitRow(trimmed: String): List<String> {
    var s = trimmed
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.substring(0, s.length - 1)
    return s.split("|").map { it.trim() }
}

fun parseOrgTable(lines: List<String>): OrgTableModel {
    val rows = mutableListOf<List<String>>()
    var headerCount = -1
    for (raw in lines) {
        val t = raw.trim()
        if (t.isEmpty() || !t.startsWith("|")) continue
        if (isRule(t)) {
            if (headerCount < 0 && rows.isNotEmpty()) headerCount = rows.size
            continue
        }
        rows.add(splitRow(t))
    }
    if (rows.isEmpty()) return OrgTableModel(emptyList(), emptyList(), 0)

    val columnCount = rows.maxOf { it.size }
    fun pad(row: List<String>): List<String> =
        if (row.size >= columnCount) row.take(columnCount)
        else row + List(columnCount - row.size) { "" }

    val headerN = (if (headerCount in 1..rows.size) headerCount else 1).coerceAtMost(rows.size)
    return OrgTableModel(
        headerRows = rows.take(headerN).map(::pad),
        bodyRows = rows.drop(headerN).map(::pad),
        columnCount = columnCount,
    )
}
