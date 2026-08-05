package com.rrajath.grove.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search")
private val SAVED_KEY = stringPreferencesKey("saved_searches_json")

/** Saved searches (drawer shortcuts). */
class SearchRepository(private val context: Context) {

    val savedSearches: Flow<List<SavedSearch>> = context.searchDataStore.data.map { prefs ->
        prefs[SAVED_KEY]?.let { SavedSearchSerializer.decode(it) } ?: DefaultSavedSearches.all
    }

    suspend fun saveSearch(name: String, query: String) {
        val current = savedSearches.first()
        val updated = current + SavedSearch(UUID.randomUUID().toString(), name, query)
        context.searchDataStore.edit { it[SAVED_KEY] = SavedSearchSerializer.encode(updated) }
    }

    suspend fun deleteSearch(id: String) {
        val updated = savedSearches.first().filterNot { it.id == id }
        context.searchDataStore.edit { it[SAVED_KEY] = SavedSearchSerializer.encode(updated) }
    }

    suspend fun renameSearch(id: String, name: String) {
        val updated = savedSearches.first().map { if (it.id == id) it.copy(name = name) else it }
        context.searchDataStore.edit { it[SAVED_KEY] = SavedSearchSerializer.encode(updated) }
    }

    /** Swaps the search [id] with its neighbor [delta] slots away (-1 up, +1 down); no-op past either end. */
    suspend fun moveSearch(id: String, delta: Int) {
        val current = savedSearches.first().toMutableList()
        val from = current.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= current.size) return
        current.add(to, current.removeAt(from))
        context.searchDataStore.edit { it[SAVED_KEY] = SavedSearchSerializer.encode(current) }
    }
}
