package com.rrajath.grove.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Human-readable JSON backup of the user's preferences (PRD §11 settings).
 *
 * Pure Kotlin (no android imports), so the mapping is JVM-testable. Device- and
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
    val theme: String = ThemePreference.LIGHT.storageKey,
    val syncAppIconWithTheme: Boolean = false,
    val fontSize: String = FontSizePreference.MEDIUM.storageKey,
    val defaultNoteOpenMode: String = NoteOpenMode.READ.storageKey,
    val syncMode: String = SyncMode.ON_OPEN_CLOSE.storageKey,
    val periodicSyncMinutes: Int = 30,
    val todoKeywords: String = GroveSettings.DEFAULT_TODO_KEYWORDS,
    val defaultPriority: String? = null,
    val addIdToNewNotes: Boolean = false,
    val addCreatedToNewNotes: Boolean = true,
    val notebookModes: Map<String, String> = emptyMap(),
    val notebookColors: Map<String, String> = emptyMap(),
    val folderColors: Map<String, String> = emptyMap(),
    val captureNotification: Boolean = false,
    val shareTargetFile: String = GroveSettings.DEFAULT_SHARE_TARGET,
    val showTagsInOutline: Boolean = true,
    val showTimestampsInOutline: Boolean = true,
    val showKeywordsInOutline: Boolean = true,
    /**
     * Unified ordered pin list: `<tag><path>` tokens (`f`/`d` prefix, see
     * [PinnedItem]). [pinnedNotebooks] / [pinnedFolders] are kept alongside it,
     * written for older builds to read and read back when an older export (which
     * has no [pinnedItems]) is imported.
     */
    val pinnedItems: List<String> = emptyList(),
    val pinnedNotebooks: List<String> = emptyList(),
    val pinnedFolders: List<String> = emptyList(),
    val showPreface: Boolean = true,
    val showPropertyDrawers: Boolean = true,
    val notebookDisplayNameMode: String = NotebookDisplayNameMode.FILENAME.storageKey,
    val showNotebookFileIcons: Boolean = true,
    val checklistStates: String = ChecklistStates.TWO.storageKey,
    val remindersEnabled: Boolean = true,
    val morningBriefEnabled: Boolean = true,
    /** "HH:mm", e.g. "09:00". */
    val defaultReminderTime: String = "09:00",
    val reminderLeadTime: String = ReminderLeadTime.AT_TIME.storageKey,
    val agendaSwipeLeftAction: String = AgendaSwipeAction.MARK_DONE.storageKey,
    val agendaSwipeRightAction: String = AgendaSwipeAction.SET_SCHEDULED.storageKey,
    val agendaGroupingToday: String = AgendaGrouping.DATE.storageKey,
    val agendaGroupingUpcoming: String = AgendaGrouping.DATE.storageKey,
    val agendaStateFilterToday: String = AgendaStateFilter.Open.storageKey,
    val agendaStateFilterUpcoming: String = AgendaStateFilter.Open.storageKey,
    val agendaShowTags: Boolean = true,
    val agendaShowFile: Boolean = false,
    val agendaWidgetTransparency: Float = 0f,
    val agendaWidgetDaysAhead: Int = GroveSettings.DEFAULT_AGENDA_WIDGET_DAYS_AHEAD,
    val autoArchiveDoneItems: Boolean = false,
    val autoArchiveFile: String? = null,
    val autoArchiveHeadingPath: String = "",
    val lastRefileFile: String? = null,
    val lastRefileHeadingPath: String = "",
) {
    /** Map back onto [base], using the enums' tolerant `fromStorage` fallbacks. */
    fun applyTo(base: GroveSettings): GroveSettings = base.copy(
        theme = ThemePreference.fromStorage(theme),
        syncAppIconWithTheme = syncAppIconWithTheme,
        fontSize = FontSizePreference.fromStorage(fontSize),
        defaultNoteOpenMode = NoteOpenMode.fromStorage(defaultNoteOpenMode),
        syncMode = SyncMode.fromStorage(syncMode),
        periodicSyncMinutes = periodicSyncMinutes,
        todoKeywords = todoKeywords,
        defaultPriority = defaultPriority?.firstOrNull(),
        addIdToNewNotes = addIdToNewNotes,
        addCreatedToNewNotes = addCreatedToNewNotes,
        notebookModes = notebookModes,
        notebookColors = notebookColors,
        folderColors = folderColors,
        captureNotification = captureNotification,
        shareTargetFile = shareTargetFile,
        showTagsInOutline = showTagsInOutline,
        showTimestampsInOutline = showTimestampsInOutline,
        showKeywordsInOutline = showKeywordsInOutline,
        pinnedItems = if (pinnedItems.isNotEmpty()) {
            pinnedItems.mapNotNull { PinnedItem.decode(it) }
        } else {
            PinnedItem.fromLegacy(pinnedNotebooks, pinnedFolders)
        },
        showPreface = showPreface,
        showPropertyDrawers = showPropertyDrawers,
        notebookDisplayNameMode = NotebookDisplayNameMode.fromStorage(notebookDisplayNameMode),
        showNotebookFileIcons = showNotebookFileIcons,
        checklistStates = ChecklistStates.fromStorage(checklistStates),
        remindersEnabled = remindersEnabled,
        morningBriefEnabled = morningBriefEnabled,
        defaultReminderTime = runCatching { java.time.LocalTime.parse(defaultReminderTime) }
            .getOrDefault(GroveSettings.DEFAULT_REMINDER_TIME),
        reminderLeadTime = ReminderLeadTime.fromStorage(reminderLeadTime),
        agendaSwipeLeftAction = AgendaSwipeAction.fromStorage(agendaSwipeLeftAction, AgendaSwipeAction.MARK_DONE),
        agendaSwipeRightAction = AgendaSwipeAction.fromStorage(agendaSwipeRightAction, AgendaSwipeAction.SET_SCHEDULED),
        agendaGroupingToday = AgendaGrouping.fromStorage(agendaGroupingToday),
        agendaGroupingUpcoming = AgendaGrouping.fromStorage(agendaGroupingUpcoming),
        agendaStateFilterToday = AgendaStateFilter.fromStorage(agendaStateFilterToday),
        agendaStateFilterUpcoming = AgendaStateFilter.fromStorage(agendaStateFilterUpcoming),
        agendaShowTags = agendaShowTags,
        agendaShowFile = agendaShowFile,
        agendaWidgetTransparency = agendaWidgetTransparency.coerceIn(0f, 1f),
        agendaWidgetDaysAhead = agendaWidgetDaysAhead.coerceAtLeast(2),
        autoArchiveDoneItems = autoArchiveDoneItems,
        autoArchiveFile = autoArchiveFile,
        autoArchiveHeadingPath = autoArchiveHeadingPath,
        lastRefileFile = lastRefileFile,
        lastRefileHeadingPath = lastRefileHeadingPath,
    )

    companion object {
        const val CURRENT_VERSION = 1

        fun fromSettings(s: GroveSettings): SettingsExport = SettingsExport(
            theme = s.theme.storageKey,
            syncAppIconWithTheme = s.syncAppIconWithTheme,
            fontSize = s.fontSize.storageKey,
            defaultNoteOpenMode = s.defaultNoteOpenMode.storageKey,
            syncMode = s.syncMode.storageKey,
            periodicSyncMinutes = s.periodicSyncMinutes,
            todoKeywords = s.todoKeywords,
            defaultPriority = s.defaultPriority?.toString(),
            addIdToNewNotes = s.addIdToNewNotes,
            addCreatedToNewNotes = s.addCreatedToNewNotes,
            notebookModes = s.notebookModes,
            notebookColors = s.notebookColors,
            folderColors = s.folderColors,
            captureNotification = s.captureNotification,
            shareTargetFile = s.shareTargetFile,
            showTagsInOutline = s.showTagsInOutline,
            showTimestampsInOutline = s.showTimestampsInOutline,
            showKeywordsInOutline = s.showKeywordsInOutline,
            pinnedItems = s.pinnedItems.map { it.encode() },
            pinnedNotebooks = s.pinnedNotebooks,
            pinnedFolders = s.pinnedFolders,
            showPreface = s.showPreface,
            showPropertyDrawers = s.showPropertyDrawers,
            notebookDisplayNameMode = s.notebookDisplayNameMode.storageKey,
            showNotebookFileIcons = s.showNotebookFileIcons,
            checklistStates = s.checklistStates.storageKey,
            remindersEnabled = s.remindersEnabled,
            morningBriefEnabled = s.morningBriefEnabled,
            defaultReminderTime = s.defaultReminderTime.toString(),
            reminderLeadTime = s.reminderLeadTime.storageKey,
            agendaSwipeLeftAction = s.agendaSwipeLeftAction.storageKey,
            agendaSwipeRightAction = s.agendaSwipeRightAction.storageKey,
            agendaGroupingToday = s.agendaGroupingToday.storageKey,
            agendaGroupingUpcoming = s.agendaGroupingUpcoming.storageKey,
            agendaStateFilterToday = s.agendaStateFilterToday.storageKey,
            agendaStateFilterUpcoming = s.agendaStateFilterUpcoming.storageKey,
            agendaShowTags = s.agendaShowTags,
            agendaShowFile = s.agendaShowFile,
            agendaWidgetTransparency = s.agendaWidgetTransparency,
            agendaWidgetDaysAhead = s.agendaWidgetDaysAhead,
            autoArchiveDoneItems = s.autoArchiveDoneItems,
            autoArchiveFile = s.autoArchiveFile,
            autoArchiveHeadingPath = s.autoArchiveHeadingPath,
            lastRefileFile = s.lastRefileFile,
            lastRefileHeadingPath = s.lastRefileHeadingPath,
        )
    }
}
