package com.rrajath.grove.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")
private val FAVORITES_KEY = stringPreferencesKey("favorites_json")

class FavoritesRepository(private val context: Context) {

    val favorites: Flow<List<FavoriteNote>> = context.favoritesDataStore.data.map { prefs ->
        prefs[FAVORITES_KEY]?.let { FavoriteNoteSerializer.decode(it) } ?: emptyList()
    }

    suspend fun addFavorite(note: FavoriteNote) {
        val current = favorites.first()
        if (current.any { it.fileName == note.fileName && it.lineIndex == note.lineIndex }) return
        context.favoritesDataStore.edit { it[FAVORITES_KEY] = FavoriteNoteSerializer.encode(current + note) }
    }

    suspend fun removeFavorite(fileName: String, lineIndex: Int) {
        val updated = favorites.first().filterNot { it.fileName == fileName && it.lineIndex == lineIndex }
        context.favoritesDataStore.edit { it[FAVORITES_KEY] = FavoriteNoteSerializer.encode(updated) }
    }

    suspend fun renameFavorite(fileName: String, lineIndex: Int, title: String) {
        val updated = favorites.first().map {
            if (it.fileName == fileName && it.lineIndex == lineIndex) it.copy(title = title) else it
        }
        context.favoritesDataStore.edit { it[FAVORITES_KEY] = FavoriteNoteSerializer.encode(updated) }
    }

    /** Swaps the favorite at (fileName, lineIndex) with its neighbor [delta] slots away (-1 up, +1 down); no-op past either end. */
    suspend fun moveFavorite(fileName: String, lineIndex: Int, delta: Int) {
        val current = favorites.first().toMutableList()
        val from = current.indexOfFirst { it.fileName == fileName && it.lineIndex == lineIndex }
        val to = from + delta
        if (from < 0 || to < 0 || to >= current.size) return
        current.add(to, current.removeAt(from))
        context.favoritesDataStore.edit { it[FAVORITES_KEY] = FavoriteNoteSerializer.encode(current) }
    }
}
