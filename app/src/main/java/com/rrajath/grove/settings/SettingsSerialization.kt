package com.rrajath.grove.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Human-readable JSON backup of the user's preferences (PRD §11 settings).
 *
 * Pure Kotlin — no android imports — so the mapping is JVM-testable. Device- and
 * install-specific state (the SAF vault URI and the onboarding flag) is
 * deliberately left out: a folder grant can't travel to another device, so an
 * import keeps whatever folder the current install already points at.
 */
object SettingsSerialization {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(settings: GroveSettings): String =
        json.encodeToString(SettingsExport.serializer(), SettingsExport.fromSettings(settings))

    /**
     * Parse a previously exported document, layering its values over [base] so
     * the non-portable fields (vault folder, onboarding) survive untouched.
     * Throws if [text] isn't valid JSON for this schema.
     */
    fun import(text: String, base: GroveSettings = GroveSettings()): GroveSettings =
        json.decodeFromString(SettingsExport.serializer(), text).applyTo(base)
}

@Serializable
data class SettingsExport(
    val version: Int = CURRENT_VERSION,
    val theme: String = ThemePreference.SYSTEM.storageKey,
    val fontSize: String = FontSizePreference.MEDIUM.storageKey,
    val defaultNoteOpenMode: String = NoteOpenMode.READ.storageKey,
    val syncMode: String = SyncMode.ON_OPEN_CLOSE.storageKey,
    val periodicSyncMinutes: Int = 30,
    val todoKeywords: String = GroveSettings.DEFAULT_TODO_KEYWORDS,
    val defaultPriority: String? = null,
    val addIdToNewNotes: Boolean = false,
    val addCreatedToNewNotes: Boolean = true,
    val notebookModes: Map<String, String> = emptyMap(),
    val notebookIcons: Map<String, String> = emptyMap(),
    val notebookColors: Map<String, String> = emptyMap(),
    val captureNotification: Boolean = false,
    val shareTargetFile: String = GroveSettings.DEFAULT_SHARE_TARGET,
    val showTagsInOutline: Boolean = true,
    val showTimestampsInOutline: Boolean = true,
    val showKeywordsInOutline: Boolean = true,
    val pinnedNotebooks: List<String> = emptyList(),
) {
    /** Map back onto [base], using the enums' tolerant `fromStorage` fallbacks. */
    fun applyTo(base: GroveSettings): GroveSettings = base.copy(
        theme = ThemePreference.fromStorage(theme),
        fontSize = FontSizePreference.fromStorage(fontSize),
        defaultNoteOpenMode = NoteOpenMode.fromStorage(defaultNoteOpenMode),
        syncMode = SyncMode.fromStorage(syncMode),
        periodicSyncMinutes = periodicSyncMinutes,
        todoKeywords = todoKeywords,
        defaultPriority = defaultPriority?.firstOrNull(),
        addIdToNewNotes = addIdToNewNotes,
        addCreatedToNewNotes = addCreatedToNewNotes,
        notebookModes = notebookModes,
        notebookIcons = notebookIcons,
        notebookColors = notebookColors,
        captureNotification = captureNotification,
        shareTargetFile = shareTargetFile,
        showTagsInOutline = showTagsInOutline,
        showTimestampsInOutline = showTimestampsInOutline,
        showKeywordsInOutline = showKeywordsInOutline,
        pinnedNotebooks = pinnedNotebooks,
    )

    companion object {
        const val CURRENT_VERSION = 1

        fun fromSettings(s: GroveSettings): SettingsExport = SettingsExport(
            theme = s.theme.storageKey,
            fontSize = s.fontSize.storageKey,
            defaultNoteOpenMode = s.defaultNoteOpenMode.storageKey,
            syncMode = s.syncMode.storageKey,
            periodicSyncMinutes = s.periodicSyncMinutes,
            todoKeywords = s.todoKeywords,
            defaultPriority = s.defaultPriority?.toString(),
            addIdToNewNotes = s.addIdToNewNotes,
            addCreatedToNewNotes = s.addCreatedToNewNotes,
            notebookModes = s.notebookModes,
            notebookIcons = s.notebookIcons,
            notebookColors = s.notebookColors,
            captureNotification = s.captureNotification,
            shareTargetFile = s.shareTargetFile,
            showTagsInOutline = s.showTagsInOutline,
            showTimestampsInOutline = s.showTimestampsInOutline,
            showKeywordsInOutline = s.showKeywordsInOutline,
            pinnedNotebooks = s.pinnedNotebooks,
        )
    }
}
