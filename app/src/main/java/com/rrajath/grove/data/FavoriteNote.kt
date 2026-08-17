package com.rrajath.grove.data

import com.rrajath.grove.org.OrgHeadline
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavoriteNote(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    /**
     * The heading's stable `:CUSTOM_ID:` (or `:ID:`), resolved by
     * [com.rrajath.grove.ui.vault.DocumentViewModel.ensureCustomId] before this favorite is saved,
     * so this favorite survives external edits that shift line numbers (the app's whole model is that
     * `.org` files are edited outside the app too). Null for favorites added before this field existed,
     * or if id-writing failed; those fall back to [lineIndex]-based lookup. Defaulted so old persisted
     * JSON without this key still decodes via [FavoriteNoteSerializer]'s `ignoreUnknownKeys`.
     */
    val customId: String? = null,
)

/**
 * True when [h] is the note this favorite refers to. When this favorite has a [customId], [h]
 * must carry that same `:CUSTOM_ID:`/`:ID:` to match — a headline with no id of its own is
 * never treated as a line-index coincidence, otherwise a note that later drifts onto the
 * favorite's old stored [lineIndex] (e.g. a new note inserted right above it) would wrongly
 * read as favorited too. Only favorites with no [customId] at all (saved before ids existed)
 * fall back to raw line index.
 */
fun FavoriteNote.matches(h: OrgHeadline): Boolean {
    return if (customId != null) customId == (h.customId ?: h.id)
    else lineIndex == h.lineIndex
}

object FavoriteNoteSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(favorites: List<FavoriteNote>): String = json.encodeToString(Wrapper(favorites))

    fun decode(text: String): List<FavoriteNote> =
        runCatching { json.decodeFromString<Wrapper>(text).favorites }.getOrDefault(emptyList())

    @Serializable
    private data class Wrapper(val favorites: List<FavoriteNote>)
}
