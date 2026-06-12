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
