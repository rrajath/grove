package com.rrajath.grove.sync

/**
 * Syncthing conflict-copy handling. When both sides change a file, Syncthing
 * keeps the newer content in place and writes the loser as
 * `name.sync-conflict-YYYYMMDD-HHMMSS-DEVICEID.org`.
 */
object SyncConflicts {

    private val CONFLICT = Regex("""^(.+)\.sync-conflict-(\d{8})-(\d{6})-([A-Za-z0-9]+)(\.[^.]+)?$""")

    fun isConflictFile(name: String): Boolean = CONFLICT.matches(name)

    /** Base notebook file name a conflict copy belongs to, or null. */
    fun baseName(conflictFileName: String): String? {
        val m = CONFLICT.matchEntire(conflictFileName) ?: return null
        return m.groupValues[1] + m.groupValues[5]
    }

    /** Human-readable "when" of a conflict copy: `2025-06-11 14:32`. */
    fun label(conflictFileName: String): String {
        val m = CONFLICT.matchEntire(conflictFileName) ?: return conflictFileName
        val d = m.groupValues[2]
        val t = m.groupValues[3]
        return "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)} " +
                "${t.substring(0, 2)}:${t.substring(2, 4)}"
    }

    /**
     * Map of base file → conflict copy for every base that has one.
     * If several copies exist, the newest (lexicographically greatest) wins.
     */
    fun detect(names: List<String>): Map<String, String> =
        names.filter { isConflictFile(it) }
            .sorted()
            .mapNotNull { copy -> baseName(copy)?.let { base -> base to copy } }
            .toMap()
}

object ConflictResolver {

    private val HEADLINE = Regex("""^\*+\s""")

    /**
     * "Keep both": append the conflicting copy under a top-level CONFLICT
     * heading, demoting its headlines one level so they nest beneath it
     * (PRD §6.4). The main text is preserved byte-for-byte.
     */
    fun keepBoth(mainText: String, conflictText: String, label: String): String {
        val demoted = conflictText.trimEnd('\n').lines().joinToString("\n") { line ->
            if (HEADLINE.containsMatchIn(line)) "*$line" else line
        }
        val base = if (mainText.isEmpty() || mainText.endsWith("\n")) mainText else mainText + "\n"
        return base + "* CONFLICT (sync copy from $label)\n" + demoted + "\n"
    }
}
