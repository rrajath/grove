package com.rrajath.grove.ui.nav

import java.net.URLEncoder

/**
 * Navigation route table (CLAUDE.md / PRD §13). Pure Kotlin so the builders are
 * JVM-unit-testable. IDs are URL-encoded because notebook ids are file names.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val NOTEBOOKS = "notebooks"
    const val OUTLINE = "outline/{notebookId}?narrowTo={narrowTo}"
    const val NOTE = "note/{noteId}?mode={mode}&isNew={isNew}"
    const val CAPTURE = "capture"
    const val CAPTURE_TEMPLATE = "capture/{templateId}"
    const val SEARCH = "search?q={q}&notebook={notebook}"
    const val AGENDA = "agenda"
    const val CONFLICT = "conflict/{notebookId}"
    const val SETTINGS = "settings"
    const val TEMPLATE_EDIT = "template/{templateId}"
    const val SYNC_LOG = "settings/synclog"
    /**
     * Tapping a reminder notification's *body*: the heading is identified by its
     * composite key, which has to be resolved against the vault to get a line
     * index, so it lands here first and is then handed off to NOTE in read mode.
     * (The "Reschedule" action does not come through here — it opens
     * `RescheduleActivity` in its own task instead of entering the app.)
     */
    const val REMINDER = "reminder/{fileName}?headingPath={headingPath}&level={level}"

    /** Sentinel templateId that opens the editor in create mode. */
    const val NEW_TEMPLATE_ID = "new"

    /**
     * Percent-encode a route segment/arg so it survives `NavDeepLink`'s decoding.
     * `URLEncoder` alone encodes a space as `+`, but androidx.navigation decodes
     * path args via `Uri.decode` and query args via `Uri.getQueryParameters` —
     * neither undoes `+`→space, so `"Buy milk"` would round-trip as `"Buy+milk"`
     * and break exact-string matching (e.g. reminder reschedule deep links,
     * multi-word note ids, search queries). Emitting `%20` for spaces round-trips
     * correctly through `Uri.decode` while keeping this object pure-JVM testable
     * (no `android.net.Uri` dependency). Every other char `URLEncoder` escapes as
     * `%XX` already round-trips through `Uri.decode` unchanged.
     */
    fun encode(id: String): String = URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    /**
     * [narrowTo] is a heading's line index — set when navigating here from a
     * Read Mode breadcrumb, so the Outline shows only that heading's subtree
     * (org-narrow-to-subtree semantics) until the user taps "widen".
     */
    fun outline(notebookId: String, narrowTo: Int? = null) =
        "outline/${encode(notebookId)}" + (narrowTo?.let { "?narrowTo=$it" } ?: "")

    fun note(noteId: String, mode: String = "read", isNew: Boolean = false) =
        "note/${encode(noteId)}?mode=$mode&isNew=$isNew"
    fun capture(templateId: String? = null) =
        if (templateId == null) CAPTURE else "capture/${encode(templateId)}"
    fun conflict(notebookId: String) = "conflict/${encode(notebookId)}"
    fun templateEdit(templateId: String) = "template/${encode(templateId)}"
    /**
     * [notebook] pins the search's notebook facet to one file — used by the
     * Outline's search action, which searches inside the notebook you're
     * already looking at. The user can still widen it back to all notebooks
     * from the Filters sheet.
     */
    fun search(query: String? = null, notebook: String? = null) =
        "search?q=" + (if (query.isNullOrBlank()) "" else encode(query)) +
                (if (notebook.isNullOrBlank()) "" else "&notebook=${encode(notebook)}")
    fun reminder(fileName: String, headingPath: String, level: Int) =
        "reminder/${encode(fileName)}?headingPath=${encode(headingPath)}&level=$level"
}
