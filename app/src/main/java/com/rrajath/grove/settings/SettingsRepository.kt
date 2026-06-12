package com.rrajath.grove.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** Persisted SAF tree URI of the sync folder; null until the user picks one. */
    val vaultTreeUri: String? = null,
    val syncMode: SyncMode = SyncMode.ON_OPEN_CLOSE,
    val periodicSyncMinutes: Int = 30,
    /** Org TODO keyword config, `|` splits done-type ("TODO IN-PROGRESS | DONE CANCELLED"). */
    val todoKeywords: String = DEFAULT_TODO_KEYWORDS,
    /** Default priority for the metadata sheet; null = none. */
    val defaultPriority: Char? = null,
    val addIdToNewNotes: Boolean = false,
    val addCreatedToNewNotes: Boolean = true,
    /** Per-notebook last-used note mode overrides: "file.org" → "read"/"edit". */
    val notebookModes: Map<String, String> = emptyMap(),
    /** Per-notebook list glyph overrides: "file.org" → "✦". */
    val notebookIcons: Map<String, String> = emptyMap(),
    val captureNotification: Boolean = false,
    // Outline display toggles (PRD §5.3)
    val showTagsInOutline: Boolean = true,
    val showTimestampsInOutline: Boolean = true,
    val showKeywordsInOutline: Boolean = true,
) {
    companion object {
        const val DEFAULT_TODO_KEYWORDS = "TODO IN-PROGRESS | DONE CANCELLED"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val fontSize = stringPreferencesKey("font_size")
        val noteOpenMode = stringPreferencesKey("note_open_mode")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val vaultTreeUri = stringPreferencesKey("vault_tree_uri")
        val syncMode = stringPreferencesKey("sync_mode")
        val periodicSyncMinutes = intPreferencesKey("periodic_sync_minutes")
        val todoKeywords = stringPreferencesKey("todo_keywords")
        val defaultPriority = stringPreferencesKey("default_priority")
        val addIdToNewNotes = booleanPreferencesKey("add_id_to_new_notes")
        val addCreatedToNewNotes = booleanPreferencesKey("add_created_to_new_notes")
        val notebookModes = stringPreferencesKey("notebook_modes")
        val notebookIcons = stringPreferencesKey("notebook_icons")
        val captureNotification = booleanPreferencesKey("capture_notification")
        val showTagsInOutline = booleanPreferencesKey("show_tags_in_outline")
        val showTimestampsInOutline = booleanPreferencesKey("show_timestamps_in_outline")
        val showKeywordsInOutline = booleanPreferencesKey("show_keywords_in_outline")
    }

    val settings: Flow<GroveSettings> = context.settingsDataStore.data.map { prefs ->
        GroveSettings(
            theme = ThemePreference.fromStorage(prefs[Keys.theme]),
            fontSize = FontSizePreference.fromStorage(prefs[Keys.fontSize]),
            defaultNoteOpenMode = NoteOpenMode.fromStorage(prefs[Keys.noteOpenMode]),
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            vaultTreeUri = prefs[Keys.vaultTreeUri],
            syncMode = SyncMode.fromStorage(prefs[Keys.syncMode]),
            periodicSyncMinutes = prefs[Keys.periodicSyncMinutes] ?: 30,
            todoKeywords = prefs[Keys.todoKeywords] ?: GroveSettings.DEFAULT_TODO_KEYWORDS,
            defaultPriority = prefs[Keys.defaultPriority]?.firstOrNull(),
            addIdToNewNotes = prefs[Keys.addIdToNewNotes] ?: false,
            addCreatedToNewNotes = prefs[Keys.addCreatedToNewNotes] ?: true,
            notebookModes = decodeModes(prefs[Keys.notebookModes]),
            notebookIcons = decodeModes(prefs[Keys.notebookIcons]),
            captureNotification = prefs[Keys.captureNotification] ?: false,
            showTagsInOutline = prefs[Keys.showTagsInOutline] ?: true,
            showTimestampsInOutline = prefs[Keys.showTimestampsInOutline] ?: true,
            showKeywordsInOutline = prefs[Keys.showKeywordsInOutline] ?: true,
        )
    }

    private fun decodeModes(raw: String?): Map<String, String> =
        raw?.split(';')
            ?.mapNotNull { entry ->
                val eq = entry.lastIndexOf('=')
                if (eq <= 0) null else entry.substring(0, eq) to entry.substring(eq + 1)
            }
            ?.toMap()
            ?: emptyMap()

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

    suspend fun setVaultTreeUri(uri: String) {
        context.settingsDataStore.edit { it[Keys.vaultTreeUri] = uri }
    }

    suspend fun setSyncMode(mode: SyncMode) {
        context.settingsDataStore.edit { it[Keys.syncMode] = mode.storageKey }
    }

    suspend fun setPeriodicSyncMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.periodicSyncMinutes] = minutes }
    }

    suspend fun setTodoKeywords(config: String) {
        context.settingsDataStore.edit { it[Keys.todoKeywords] = config }
    }

    suspend fun setDefaultPriority(priority: Char?) {
        context.settingsDataStore.edit {
            if (priority == null) it.remove(Keys.defaultPriority)
            else it[Keys.defaultPriority] = priority.toString()
        }
    }

    suspend fun setAddIdToNewNotes(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.addIdToNewNotes] = enabled }
    }

    suspend fun setAddCreatedToNewNotes(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.addCreatedToNewNotes] = enabled }
    }

    suspend fun setCaptureNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.captureNotification] = enabled }
    }

    suspend fun setOutlineToggle(key: OutlineToggle, enabled: Boolean) {
        context.settingsDataStore.edit {
            it[
                when (key) {
                    OutlineToggle.TAGS -> Keys.showTagsInOutline
                    OutlineToggle.TIMESTAMPS -> Keys.showTimestampsInOutline
                    OutlineToggle.KEYWORDS -> Keys.showKeywordsInOutline
                }
            ] = enabled
        }
    }

    suspend fun setNotebookMode(fileName: String, mode: NoteOpenMode) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[Keys.notebookModes]).toMutableMap()
            current[fileName] = mode.storageKey
            prefs[Keys.notebookModes] = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    suspend fun setNotebookIcon(fileName: String, glyph: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[Keys.notebookIcons]).toMutableMap()
            current[fileName] = glyph
            prefs[Keys.notebookIcons] = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    /** Keep a chosen icon attached to a notebook across renames. */
    suspend fun moveNotebookIcon(oldFileName: String, newFileName: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[Keys.notebookIcons]).toMutableMap()
            val glyph = current.remove(oldFileName) ?: return@edit
            current[newFileName] = glyph
            prefs[Keys.notebookIcons] = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }
}
