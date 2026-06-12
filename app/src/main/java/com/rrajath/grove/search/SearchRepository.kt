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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search")
private val SAVED_KEY = stringPreferencesKey("saved_searches_json")
private val HISTORY_KEY = stringPreferencesKey("history_json")

/** Saved searches (drawer shortcuts) + last-10 search history. */
class SearchRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val savedSearches: Flow<List<SavedSearch>> = context.searchDataStore.data.map { prefs ->
        prefs[SAVED_KEY]?.let { SavedSearchSerializer.decode(it) } ?: DefaultSavedSearches.all
    }

    val history: Flow<List<String>> = context.searchDataStore.data.map { prefs ->
        prefs[HISTORY_KEY]?.let {
            runCatching { json.decodeFromString<HistoryWrapper>(it).entries }.getOrDefault(emptyList())
        } ?: emptyList()
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

    suspend fun recordHistory(query: String) {
        if (query.isBlank()) return
        val current = history.first()
        val updated = (listOf(query.trim()) + current.filterNot { it == query.trim() }).take(10)
        context.searchDataStore.edit { it[HISTORY_KEY] = json.encodeToString(HistoryWrapper(updated)) }
    }

    @Serializable
    private data class HistoryWrapper(val entries: List<String>)
}
