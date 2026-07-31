package com.rrajath.grove.settings

// Pure-Kotlin preference enums (no android imports) so mapping logic is JVM-testable.

enum class ThemePreference(val storageKey: String) {
    LIGHT("light"),
    DARK("dark"),
    TOKYONIGHT("tokyonight"),
    TOKYODAY("tokyoday"),
    SYNTHWAVE("synthwave"),
    DRACULA("dracula"),
    CATPPUCCIN("catppuccin"),
    CATPPUCCINLATTE("catppuccinlatte"),
    NORD("nord"),
    ROSEPINEDAWN("rosepinedawn"),
    ROSEPINEMOON("rosepinemoon");

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
 * The middle chips are the vault's own active (todo-type) keywords rather than
 * a fixed `NEXT`/`WAITING` pair, so the filter follows whatever `todoKeywords`
 * is configured. [Open] is state-agnostic: anything not done-type, including
 * headings with no keyword at all.
 */
sealed interface AgendaStateFilter {

    val storageKey: String

    /** Everything that is not a done-type keyword. */
    data object Open : AgendaStateFilter {
        override val storageKey: String get() = "open"
    }

    /** Every planned heading, completed ones included. */
    data object All : AgendaStateFilter {
        override val storageKey: String get() = "all"
    }

    /** Exactly one active keyword, e.g. `NEXT`. */
    data class Keyword(val name: String) : AgendaStateFilter {
        override val storageKey: String get() = "$KEYWORD_PREFIX$name"
    }

    companion object {
        private const val KEYWORD_PREFIX = "kw:"

        fun fromStorage(value: String?): AgendaStateFilter = when {
            value == null -> Open
            value == All.storageKey -> All
            value.startsWith(KEYWORD_PREFIX) ->
                value.removePrefix(KEYWORD_PREFIX).takeIf { it.isNotEmpty() }?.let(::Keyword) ?: Open
            else -> Open
        }
    }
}
