package com.rrajath.grove.settings

// Pure-Kotlin preference enums (no android imports) so mapping logic is JVM-testable.

enum class ThemePreference(val storageKey: String) {
    LIGHT("light"),
    DARK("dark"),
    TOKYONIGHT("tokyonight"),
    SYNTHWAVE("synthwave"),
    DRACULA("dracula"),
    CATPPUCCIN("catppuccin"),
    NORD("nord");

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageKey == value } ?: LIGHT
    }
}

enum class FontSizePreference(val storageKey: String, val scale: Float) {
    SMALL("small", 0.9f),
    MEDIUM("medium", 1.0f),
    LARGE("large", 1.12f);

    companion object {
        fun fromStorage(value: String?): FontSizePreference =
            entries.firstOrNull { it.storageKey == value } ?: MEDIUM
    }
}

enum class SyncMode(val storageKey: String, val label: String) {
    MANUAL("manual", "Manual only"),
    ON_OPEN_CLOSE("on_open_close", "On open / close"),
    PERIODIC("periodic", "Periodic"),
    CONTINUOUS("continuous", "Continuous");

    companion object {
        fun fromStorage(value: String?): SyncMode =
            entries.firstOrNull { it.storageKey == value } ?: ON_OPEN_CLOSE
    }
}

enum class OutlineToggle { TAGS, TIMESTAMPS, KEYWORDS }

enum class NoteOpenMode(val storageKey: String) {
    READ("read"),
    EDIT("edit");

    companion object {
        fun fromStorage(value: String?): NoteOpenMode =
            entries.firstOrNull { it.storageKey == value } ?: READ
    }
}

enum class NotebookDisplayNameMode(val storageKey: String) {
    FILENAME("filename"),
    TITLE("title");

    companion object {
        fun fromStorage(value: String?): NotebookDisplayNameMode =
            entries.firstOrNull { it.storageKey == value } ?: FILENAME
    }
}

/**
 * Read mode: how many states a tap cycles a checklist item's box through.
 * [marks] is the tap order, e.g. two-state `[ ]` → `[X]` → `[ ]`…
 */
enum class ChecklistStates(val storageKey: String, val marks: List<Char>) {
    TWO("two", listOf(' ', 'X')),
    THREE("three", listOf(' ', '-', 'X'));

    companion object {
        fun fromStorage(value: String?): ChecklistStates =
            entries.firstOrNull { it.storageKey == value } ?: TWO
    }
}

/** Agenda row swipe gesture (either direction), Settings § Agenda. */
enum class AgendaSwipeAction(val storageKey: String, val label: String) {
    SET_SCHEDULED("set_scheduled", "Schedule Task"),
    SET_DEADLINE("set_deadline", "Set Deadline"),
    MARK_DONE("mark_done", "Mark as Done");

    companion object {
        fun fromStorage(value: String?, default: AgendaSwipeAction): AgendaSwipeAction =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}

/**
 * Agenda levers § "Group by" — how the day's items are bucketed into sections.
 * Lives in settings rather than the ViewModel because a user who always works
 * by priority shouldn't re-pick it on every visit to the screen.
 */
enum class AgendaGrouping(val storageKey: String, val label: String) {
    DATE("date", "Date"),
    PRIORITY("priority", "Priority"),
    TAG("tag", "Tag"),
    FILE("file", "File");

    companion object {
        fun fromStorage(value: String?): AgendaGrouping =
            entries.firstOrNull { it.storageKey == value } ?: DATE
    }
}

/**
 * Agenda levers § "Show" — which TODO states reach the list.
 *
 * [OPEN] and [NEXT] match the keyword names `WAITING` and `NEXT` literally, the
 * near-universal org convention the design prototype assumes. A vault whose
 * `todoKeywords` omits them simply sees [OPEN] behave as "everything not done"
 * and [NEXT] come up empty.
 */
enum class AgendaStateFilter(val storageKey: String, val label: String) {
    OPEN("open", "Open"),
    NEXT("next", "Next only"),
    ALL("all", "Everything");

    companion object {
        fun fromStorage(value: String?): AgendaStateFilter =
            entries.firstOrNull { it.storageKey == value } ?: OPEN
    }
}
