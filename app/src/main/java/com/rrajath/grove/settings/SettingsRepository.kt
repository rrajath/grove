package com.rrajath.grove.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class GroveSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val fontSize: FontSizePreference = FontSizePreference.MEDIUM,
    val defaultNoteOpenMode: NoteOpenMode = NoteOpenMode.READ,
    val onboardingDone: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontSize = stringPreferencesKey("font_size")
        val noteOpenMode = stringPreferencesKey("note_open_mode")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<GroveSettings> = context.settingsDataStore.data.map { prefs ->
        GroveSettings(
            theme = ThemePreference.fromStorage(prefs[Keys.theme]),
            fontSize = FontSizePreference.fromStorage(prefs[Keys.fontSize]),
            defaultNoteOpenMode = NoteOpenMode.fromStorage(prefs[Keys.noteOpenMode]),
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.settingsDataStore.edit { it[Keys.theme] = theme.storageKey }
    }

    suspend fun setFontSize(fontSize: FontSizePreference) {
        context.settingsDataStore.edit { it[Keys.fontSize] = fontSize.storageKey }
    }

    suspend fun setDefaultNoteOpenMode(mode: NoteOpenMode) {
        context.settingsDataStore.edit { it[Keys.noteOpenMode] = mode.storageKey }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.onboardingDone] = done }
    }
}
