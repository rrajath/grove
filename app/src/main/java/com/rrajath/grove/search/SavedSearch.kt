package com.rrajath.grove.search

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedSearch(
    val id: String,
    val name: String,
    val query: String,
)

object SavedSearchSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(searches: List<SavedSearch>): String = json.encodeToString(Wrapper(searches))

    fun decode(text: String): List<SavedSearch> =
        runCatching { json.decodeFromString<Wrapper>(text).searches }.getOrDefault(emptyList())

    @Serializable
    private data class Wrapper(val searches: List<SavedSearch>)
}

/** PRD §5.5 default saved searches. */
object DefaultSavedSearches {
    val all = listOf(
        SavedSearch("builtin-scheduled-today", "Scheduled Today", "s.today"),
        SavedSearch("builtin-all-todo", "All TODO", "i.todo"),
        SavedSearch("builtin-this-week", "This Week", "s.7d"),
    )
}

/**
 * Reserved [SavedSearch] ids for the blank-state Quick Start cards (Overdue,
 * Today, Open tasks, Unscheduled). Those cards normally build their query
 * dynamically (see SearchScreen's BlankState); overwriting one from the star
 * button's save dropdown persists a row under its reserved id here instead of
 * a fresh saved search, so the card's own query is what changes. Rows with
 * these ids are hidden from the "Saved Searches" list and are never seeded by
 * default — absent means "use the card's built-in dynamic query."
 */
object QuickStartOverrides {
    const val OVERDUE_ID = "quickstart-overdue"
    const val TODAY_ID = "quickstart-today"
    const val OPEN_TASKS_ID = "quickstart-open-tasks"
    const val UNSCHEDULED_ID = "quickstart-unscheduled"

    val ids = setOf(OVERDUE_ID, TODAY_ID, OPEN_TASKS_ID, UNSCHEDULED_ID)

    private val idsByName = mapOf(
        "Overdue" to OVERDUE_ID,
        "Today" to TODAY_ID,
        "Open tasks" to OPEN_TASKS_ID,
        "Unscheduled" to UNSCHEDULED_ID,
    )

    /** Reserved id for a Quick Start card's exact label, or null if [name] isn't one. */
    fun idForName(name: String): String? = idsByName[name]
}
