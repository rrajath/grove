package com.rrajath.grove.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavoriteNote(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    /**
     * The heading's stable `:CUSTOM_ID:` (or `:ID:`), set by [com.rrajath.grove.ui.AppViewModel.addFavorite]
     * so this favorite survives external edits that shift line numbers (the app's whole model is that
     * `.org` files are edited outside the app too). Null for favorites added before this field existed,
     * or if id-writing failed; those fall back to [lineIndex]-based lookup. Defaulted so old persisted
     * JSON without this key still decodes via [FavoriteNoteSerializer]'s `ignoreUnknownKeys`.
     */
    val customId: String? = null,
)

object FavoriteNoteSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(favorites: List<FavoriteNote>): String = json.encodeToString(Wrapper(favorites))

    fun decode(text: String): List<FavoriteNote> =
        runCatching { json.decodeFromString<Wrapper>(text).favorites }.getOrDefault(emptyList())

    @Serializable
    private data class Wrapper(val favorites: List<FavoriteNote>)
}
