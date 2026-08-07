package com.rrajath.grove.capture

// Path separators plus the characters FAT/exFAT (the common case for the SAF
// tree providers this app targets) reject outright, and C0 control characters.
private val ILLEGAL_FILENAME_CHARS = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

/** Validates a capture template's target-file name (PRD §7.4). */
object FilenameValidation {

    /** `null` when [name] is a valid `.org` filename; otherwise a user-facing reason. */
    fun errorFor(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Enter a filename"
            trimmed == "." || trimmed == ".." -> "Not a valid filename"
            ILLEGAL_FILENAME_CHARS.containsMatchIn(trimmed) -> "Can't contain / \\ : * ? \" < > |"
            !trimmed.endsWith(".org") -> "Filename must end in .org"
            trimmed == ".org" -> "Enter a filename"
            else -> null
        }
    }
}
