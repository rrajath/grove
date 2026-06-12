package com.rrajath.grove.ui.nav

import java.net.URLEncoder

/**
 * Navigation route table (CLAUDE.md / PRD §13). Pure Kotlin so the builders are
 * JVM-unit-testable. IDs are URL-encoded because notebook ids are file names.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val NOTEBOOKS = "notebooks"
    const val OUTLINE = "outline/{notebookId}"
    const val NOTE = "note/{noteId}?mode={mode}"
    const val CAPTURE = "capture"
    const val CAPTURE_TEMPLATE = "capture/{templateId}"
    const val SEARCH = "search"
    const val CONFLICT = "conflict/{notebookId}"
    const val SETTINGS = "settings"

    fun encode(id: String): String = URLEncoder.encode(id, "UTF-8")

    fun outline(notebookId: String) = "outline/${encode(notebookId)}"
    fun note(noteId: String, mode: String = "read") = "note/${encode(noteId)}?mode=$mode"
    fun capture(templateId: String? = null) =
        if (templateId == null) CAPTURE else "capture/${encode(templateId)}"
    fun conflict(notebookId: String) = "conflict/${encode(notebookId)}"
}
