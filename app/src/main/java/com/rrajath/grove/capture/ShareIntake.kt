package com.rrajath.grove.capture

/**
 * Decides how content shared into Grove becomes an org note (PRD §10):
 * - a URL → a heading linking the page title to the URL,
 * - long text → an empty heading with the text as the body,
 * - short text → the text itself as the heading.
 * Pure so the rule is unit-testable; the network title lookup happens elsewhere.
 */
object ShareIntake {
    /** Text at or below this length reads fine as a heading; longer text becomes a body. */
    const val TEXT_HEADING_LIMIT = 40

    data class Note(val heading: String, val body: String?)

    /**
     * Build the note for [payload]. [resolvedTitle] is the page title fetched for
     * a URL (null if the fetch failed or the payload isn't a URL).
     */
    fun composeNote(payload: SharedPayload, resolvedTitle: String?): Note = when {
        payload.url.isNotEmpty() -> {
            val description = (resolvedTitle?.takeIf { it.isNotBlank() }
                ?: payload.text.takeIf { it.isNotBlank() }
                ?: payload.url)
                // Square brackets would break the [[url][desc]] link syntax.
                .replace('[', '(').replace(']', ')')
                .trim()
            Note(heading = "[[${payload.url}][$description]]", body = null)
        }
        payload.text.length > TEXT_HEADING_LIMIT -> Note(heading = "", body = payload.text)
        else -> Note(heading = payload.text, body = null)
    }
}
