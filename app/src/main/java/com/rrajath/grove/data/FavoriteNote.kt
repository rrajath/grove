package com.rrajath.grove.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavoriteNote(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
)

object FavoriteNoteSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(favorites: List<FavoriteNote>): String = json.encodeToString(Wrapper(favorites))

    fun decode(text: String): List<FavoriteNote> =
        runCatching { json.decodeFromString<Wrapper>(text).favorites }.getOrDefault(emptyList())

    @Serializable
    private data class Wrapper(val favorites: List<FavoriteNote>)
}
