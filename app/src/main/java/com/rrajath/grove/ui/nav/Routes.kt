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
    const val SEARCH = "search?q={q}"
    const val CONFLICT = "conflict/{notebookId}"
    const val SETTINGS = "settings"
    const val TEMPLATE_EDIT = "template/{templateId}"
    const val SYNC_LOG = "settings/synclog"

    /** Sentinel templateId that opens the editor in create mode. */
    const val NEW_TEMPLATE_ID = "new"

    fun encode(id: String): String = URLEncoder.encode(id, "UTF-8")

    fun outline(notebookId: String) = "outline/${encode(notebookId)}"
    fun note(noteId: String, mode: String = "read") = "note/${encode(noteId)}?mode=$mode"
    fun capture(templateId: String? = null) =
        if (templateId == null) CAPTURE else "capture/${encode(templateId)}"
    fun conflict(notebookId: String) = "conflict/${encode(notebookId)}"
    fun templateEdit(templateId: String) = "template/${encode(templateId)}"
    fun search(query: String? = null) =
        if (query.isNullOrBlank()) "search?q=" else "search?q=${encode(query)}"
}
