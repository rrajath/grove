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
}
