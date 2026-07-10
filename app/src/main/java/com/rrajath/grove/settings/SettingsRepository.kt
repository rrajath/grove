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
    val theme: ThemePreference = ThemePreference.LIGHT,
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
    /** Per-notebook icon color overrides: "file.org" → palette key ("green"…). */
    val notebookColors: Map<String, String> = emptyMap(),
    val captureNotification: Boolean = false,
    /** .org file that receives content shared into Grove from other apps. */
    val shareTargetFile: String = DEFAULT_SHARE_TARGET,
    // Outline display toggles (PRD §5.3)
    val showTagsInOutline: Boolean = true,
    val showTimestampsInOutline: Boolean = true,
    val showKeywordsInOutline: Boolean = true,
    /** Ordered list of pinned notebook file names; first = topmost. */
    val pinnedNotebooks: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_TODO_KEYWORDS = "TODO IN-PROGRESS | DONE CANCELLED"
        const val DEFAULT_SHARE_TARGET = "inbox.org"
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
        val notebookColors = stringPreferencesKey("notebook_colors")
        val captureNotification = booleanPreferencesKey("capture_notification")
        val shareTargetFile = stringPreferencesKey("share_target_file")
        val showTagsInOutline = booleanPreferencesKey("show_tags_in_outline")
        val showTimestampsInOutline = booleanPreferencesKey("show_timestamps_in_outline")
        val showKeywordsInOutline = booleanPreferencesKey("show_keywords_in_outline")
        val pinnedNotebooks = stringPreferencesKey("pinned_notebooks")
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
            notebookColors = decodeModes(prefs[Keys.notebookColors]),
            captureNotification = prefs[Keys.captureNotification] ?: false,
            shareTargetFile = prefs[Keys.shareTargetFile] ?: GroveSettings.DEFAULT_SHARE_TARGET,
            showTagsInOutline = prefs[Keys.showTagsInOutline] ?: true,
            showTimestampsInOutline = prefs[Keys.showTimestampsInOutline] ?: true,
            showKeywordsInOutline = prefs[Keys.showKeywordsInOutline] ?: true,
            pinnedNotebooks = decodePinnedList(prefs[Keys.pinnedNotebooks]),
        )
    }

    private fun decodePinnedList(raw: String?): List<String> =
        raw?.split(';')?.filter { it.isNotEmpty() } ?: emptyList()

    private fun encodePinnedList(list: List<String>): String =
        list.joinToString(";")

    private fun decodeModes(raw: String?): Map<String, String> =
        raw?.split(';')
            ?.mapNotNull { entry ->
                val eq = entry.lastIndexOf('=')
                if (eq <= 0) null else entry.substring(0, eq) to entry.substring(eq + 1)
            }
            ?.toMap()
            ?: emptyMap()

    private fun encodeModes(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    /**
     * Bulk-write an imported settings document in one transaction. Leaves the
     * device-specific vault URI and onboarding flag alone — those don't travel
     * with an export (see [SettingsSerialization]).
     */
    suspend fun applyImported(s: GroveSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.theme] = s.theme.storageKey
            p[Keys.fontSize] = s.fontSize.storageKey
            p[Keys.noteOpenMode] = s.defaultNoteOpenMode.storageKey
            p[Keys.syncMode] = s.syncMode.storageKey
            p[Keys.periodicSyncMinutes] = s.periodicSyncMinutes
            p[Keys.todoKeywords] = s.todoKeywords
            if (s.defaultPriority == null) p.remove(Keys.defaultPriority)
            else p[Keys.defaultPriority] = s.defaultPriority.toString()
            p[Keys.addIdToNewNotes] = s.addIdToNewNotes
            p[Keys.addCreatedToNewNotes] = s.addCreatedToNewNotes
            p[Keys.notebookModes] = encodeModes(s.notebookModes)
            p[Keys.notebookIcons] = encodeModes(s.notebookIcons)
            p[Keys.notebookColors] = encodeModes(s.notebookColors)
            p[Keys.captureNotification] = s.captureNotification
            p[Keys.shareTargetFile] = s.shareTargetFile
            p[Keys.showTagsInOutline] = s.showTagsInOutline
            p[Keys.showTimestampsInOutline] = s.showTimestampsInOutline
            p[Keys.showKeywordsInOutline] = s.showKeywordsInOutline
        }
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

    suspend fun setShareTargetFile(fileName: String) {
        context.settingsDataStore.edit { it[Keys.shareTargetFile] = fileName }
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

    suspend fun setNotebookIcon(fileName: String, glyph: String) {
        setMapEntry(Keys.notebookIcons, fileName, glyph)
    }

    suspend fun setNotebookColor(fileName: String, colorKey: String) {
        setMapEntry(Keys.notebookColors, fileName, colorKey)
    }

    private suspend fun setMapEntry(key: Preferences.Key<String>, mapKey: String, value: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[key]).toMutableMap()
            current[mapKey] = value
            prefs[key] = encodeModes(current)
        }
    }

    suspend fun pinNotebook(fileName: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodePinnedList(prefs[Keys.pinnedNotebooks]).toMutableList()
            if (fileName !in current) {
                current.add(fileName)
                prefs[Keys.pinnedNotebooks] = encodePinnedList(current)
            }
        }
    }

    suspend fun unpinNotebook(fileName: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodePinnedList(prefs[Keys.pinnedNotebooks]).toMutableList()
            if (current.remove(fileName)) {
                prefs[Keys.pinnedNotebooks] = encodePinnedList(current)
            }
        }
    }

    /** Keep a chosen icon, color, and pin position attached to a notebook across renames. */
    suspend fun moveNotebookStyle(oldFileName: String, newFileName: String) {
        context.settingsDataStore.edit { prefs ->
            for (key in listOf(Keys.notebookIcons, Keys.notebookColors)) {
                val current = decodeModes(prefs[key]).toMutableMap()
                val value = current.remove(oldFileName) ?: continue
                current[newFileName] = value
                prefs[key] = encodeModes(current)
            }
            val pinned = decodePinnedList(prefs[Keys.pinnedNotebooks]).toMutableList()
            val pinIdx = pinned.indexOf(oldFileName)
            if (pinIdx >= 0) {
                pinned[pinIdx] = newFileName
                prefs[Keys.pinnedNotebooks] = encodePinnedList(pinned)
            }
        }
    }
}
