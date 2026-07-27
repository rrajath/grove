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
    const val NOTE = "note/{noteId}?mode={mode}&isNew={isNew}&planning={planning}&notifId={notifId}"
    const val CAPTURE = "capture"
    const val CAPTURE_TEMPLATE = "capture/{templateId}"
    const val SEARCH = "search?q={q}"
    const val AGENDA = "agenda"
    const val CONFLICT = "conflict/{notebookId}"
    const val SETTINGS = "settings"
    const val TEMPLATE_EDIT = "template/{templateId}"
    const val SYNC_LOG = "settings/synclog"
    /** Reminder "Reschedule" deep link: resolved to a NoteRef, then handed off to NOTE. */
    const val REMINDER = "reminder/{fileName}?headingPath={headingPath}&level={level}&type={type}&notifId={notifId}"

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

    fun outline(notebookId: String) = "outline/${encode(notebookId)}"

    /**
     * [planning] ("scheduled"/"deadline") and [notifId] carry a reminder's
     * "Reschedule" action through to `EditNoteScreen`, which auto-opens
     * `PlanningDatePicker` for that timestamp and dismisses that notification
     * on confirm; both are omitted for ordinary note navigation.
     */
    fun note(noteId: String, mode: String = "read", isNew: Boolean = false, planning: String? = null, notifId: Int? = null) =
        "note/${encode(noteId)}?mode=$mode&isNew=$isNew" +
                (planning?.let { "&planning=$it" } ?: "") +
                (notifId?.let { "&notifId=$it" } ?: "")
    fun capture(templateId: String? = null) =
        if (templateId == null) CAPTURE else "capture/${encode(templateId)}"
    fun conflict(notebookId: String) = "conflict/${encode(notebookId)}"
    fun templateEdit(templateId: String) = "template/${encode(templateId)}"
    fun search(query: String? = null) =
        if (query.isNullOrBlank()) "search?q=" else "search?q=${encode(query)}"
    fun reminder(fileName: String, headingPath: String, level: Int, type: String, notifId: Int) =
        "reminder/${encode(fileName)}?headingPath=${encode(headingPath)}&level=$level&type=$type&notifId=$notifId"
}
