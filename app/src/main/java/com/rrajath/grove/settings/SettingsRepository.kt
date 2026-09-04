package com.rrajath.grove.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.time.LocalTime

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Whether a [PinnedItem] pins a notebook file or a folder. */
enum class PinKind(val tag: Char) { FILE('f'), FOLDER('d') }

/**
 * One entry in the unified, chronologically-ordered pin list ([GroveSettings.pinnedItems]).
 *
 * Persisted as a single token `<tag><path>` with no separator: the first char is
 * [PinKind.tag] (`f`/`d`), the rest is the raw vault-relative path. A path can
 * contain `:` but never `;` (the list separator), so a fixed 1-char prefix is
 * unambiguous where a `d:`-style prefix would not be.
 */
data class PinnedItem(val kind: PinKind, val path: String) {
    fun encode(): String = "${kind.tag}$path"

    companion object {
        fun decode(token: String): PinnedItem? {
            if (token.length < 2) return null
            val kind = PinKind.entries.firstOrNull { it.tag == token[0] } ?: return null
            return PinnedItem(kind, token.substring(1))
        }

        /**
         * Fold the two legacy per-type pin lists into one unified list. The old
         * lists carry no cross-order, so this reproduces what the strip showed
         * before: folders first, then files.
         */
        fun fromLegacy(pinnedNotebooks: List<String>, pinnedFolders: List<String>): List<PinnedItem> =
            pinnedFolders.map { PinnedItem(PinKind.FOLDER, it) } +
                pinnedNotebooks.map { PinnedItem(PinKind.FILE, it) }
    }
}

data class GroveSettings(
    val theme: ThemePreference = ThemePreference.LIGHT,
    /** When true, the launcher icon and drawer logo follow [theme]; otherwise they stay the default light mark. */
    val syncAppIconWithTheme: Boolean = false,
    /**
     * App-wide text-size baseline (Settings § Look and Feel). Scales every `sp`-sized
     * text in the app from a single lever. [readModeFontSize] / [editModeFontSize]
     * compound on top of this for note content only, so App=Large + Read=Large ≈
     * 1.12 × 1.12.
     */
    val appFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    /** Read mode: scales the rendered note (Settings § Notes). App chrome is unaffected. */
    val readModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    /** Edit mode: scales the editor text field (Settings § Notes). App chrome is unaffected. */
    val editModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    val defaultNoteOpenMode: NoteOpenMode = NoteOpenMode.READ,
    val onboardingDone: Boolean = false,
    /**
     * `BuildConfig.VERSION_CODE` last shown by the What's New modal; device-specific like
     * [onboardingDone], so deliberately left out of [com.rrajath.grove.settings.SettingsSerialization].
     * Keyed on versionCode (the numeric form of versionName, see CHANGELOG.md's "Versioning"
     * section) rather than the version string so the "seen since" comparison stays plain integer
     * ordering.
     */
    val lastSeenChangelogBuild: Int? = null,
    /**
     * `BuildConfig.VERSION_CODE` the "NEW" feature-badge framework was first seeded at
     * (see [SettingsRepository.ensureNewBadgeBaseline]). A feature badges only for
     * installs already on an *older* build than the feature shipped in — i.e. updates,
     * never fresh installs. Written once and never moved after; device-specific like
     * [lastSeenChangelogBuild], so deliberately left out of [SettingsSerialization].
     */
    val newBadgeBaseline: Int? = null,
    /**
     * Ids of `ui/newbadge` features the user has already reached; their badges no
     * longer show. Device-specific, like [newBadgeBaseline] — not exported.
     */
    val seenNewFeatures: Set<String> = emptySet(),
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
    /** Where the caret lands in a note created for immediate editing (outline "+" FAB). */
    val newNoteCursor: NewNoteCursor = NewNoteCursor.BODY,
    /** Per-notebook last-used note mode overrides: "file.org" → "read"/"edit". */
    val notebookModes: Map<String, String> = emptyMap(),
    /** Per-notebook monogram color overrides: "file.org" → palette key ("green"…). */
    val notebookColors: Map<String, String> = emptyMap(),
    /** Per-folder icon color overrides, keyed by vault-relative dir: "projects/clients" → palette key. */
    val folderColors: Map<String, String> = emptyMap(),
    val captureNotification: Boolean = false,
    /** .org file that receives content shared into Grove from other apps. */
    val shareTargetFile: String = DEFAULT_SHARE_TARGET,
    // Outline display toggles (PRD §5.3)
    val showTagsInOutline: Boolean = true,
    val showTimestampsInOutline: Boolean = true,
    val showKeywordsInOutline: Boolean = true,
    /**
     * Single chronologically-ordered pin list across both notebooks and folders;
     * first = topmost. Order in this list is the display order of the Pinned
     * strip (append on pin, remove on unpin).
     */
    val pinnedItems: List<PinnedItem> = emptyList(),
    /** Read mode: show a collapsible section for file-level `#+` keyword lines. */
    val showPreface: Boolean = true,
    /** Read mode: show collapsible sections for `:PROPERTIES:` drawers. */
    val showPropertyDrawers: Boolean = true,
    /** Notebook list label: raw file name, or the `#+TITLE:` cached in the index. */
    val notebookDisplayNameMode: NotebookDisplayNameMode = NotebookDisplayNameMode.FILENAME,
    /** Notebook list: show the per-file icon tile at the start of each row. */
    val showNotebookFileIcons: Boolean = true,
    /**
     * Notebook list: collapse the folder tree to a flat file list. Every `.org`
     * file shows as its own row with its folder path as a subtitle; folder rows,
     * the drill-down view, and expand/collapse all are hidden.
     */
    val flattenNotebookFolders: Boolean = false,
    /** Notebook list sort key; pinned notebooks/folders keep their pin order regardless. */
    val notebookSortKey: NotebookSortKey = NotebookSortKey.ALPHABETICAL,
    /** Notebook list sort direction (pairs with [notebookSortKey]); true = A→Z / oldest→newest. */
    val notebookSortAscending: Boolean = true,
    /** Destination file of the most recent successful refile; null until one has happened. */
    val lastRefileFile: String? = null,
    /** '/'-separated heading path within [lastRefileFile]; empty = top level. */
    val lastRefileHeadingPath: String = "",
    /** Auto-refile a task to [autoArchiveFile] the moment it's marked with a done-type keyword. */
    val autoArchiveDoneItems: Boolean = false,
    /** Fallback archive destination used when a heading has no `ARCHIVE` property/keyword of its own. */
    val autoArchiveFile: String? = null,
    /** '/'-separated heading path within [autoArchiveFile]; empty = top level. */
    val autoArchiveHeadingPath: String = "",
    /** SCHEDULED/DEADLINE reminder notifications (see `reminders` package). */
    val remindersEnabled: Boolean = true,
    /** Daily digest ("You have X tasks due today") opt-in; requires [remindersEnabled] too. */
    val morningBriefEnabled: Boolean = true,
    /** Time of day used for date-only SCHEDULED/DEADLINE stamps (no time-of-day). */
    val defaultReminderTime: LocalTime = LocalTime.of(9, 0),
    /** How far ahead of a timestamp's own time-of-day the "due" notification fires. */
    val reminderLeadTime: ReminderLeadTime = ReminderLeadTime.AT_TIME,
    /** Agenda row swipe-left/swipe-right gestures (Settings § Agenda). */
    val agendaSwipeLeftAction: AgendaSwipeAction = AgendaSwipeAction.MARK_DONE,
    val agendaSwipeRightAction: AgendaSwipeAction = AgendaSwipeAction.SET_SCHEDULED,
    // Agenda levers panel: sticky across visits, unlike the Today/Upcoming tab
    // itself. Tracked separately per tab so picking e.g. "Tag" grouping while on
    // Today doesn't silently re-bucket Upcoming (and vice versa) — each tab
    // remembers its own choice, with Upcoming's own default staying "Date".
    val agendaGroupingToday: AgendaGrouping = AgendaGrouping.DATE,
    val agendaGroupingUpcoming: AgendaGrouping = AgendaGrouping.DATE,
    val agendaStateFilterToday: AgendaStateFilter = AgendaStateFilter.Open,
    val agendaStateFilterUpcoming: AgendaStateFilter = AgendaStateFilter.Open,
    val agendaShowTags: Boolean = true,
    val agendaShowFile: Boolean = false,
    /** Agenda ledger home-screen widget background transparency: 0 = opaque, 1 = fully transparent. */
    val agendaWidgetTransparency: Float = 0f,
    /** How many days ahead the Agenda ledger widget shows, beyond Overdue/Today. */
    val agendaWidgetDaysAhead: Int = 14,
    /**
     * Notebooks screen: directory paths whose folder rows are expanded in the
     * inline tree. Device-specific view state, like [onboardingDone] — deliberately
     * left out of [com.rrajath.grove.settings.SettingsSerialization] and
     * [applyImported].
     */
    val expandedFolders: Set<String> = emptySet(),
    /** True once the first-open folder-expansion heuristic has run for this vault. */
    val notebooksTreeDefaultsApplied: Boolean = false,
) {
    /** Pinned notebook file names in pin order — derived view of [pinnedItems]. */
    val pinnedNotebooks: List<String>
        get() = pinnedItems.filter { it.kind == PinKind.FILE }.map { it.path }

    /** Pinned folder paths (vault-relative dirs) in pin order — derived view of [pinnedItems]. */
    val pinnedFolders: List<String>
        get() = pinnedItems.filter { it.kind == PinKind.FOLDER }.map { it.path }

    companion object {
        const val DEFAULT_TODO_KEYWORDS = "TODO IN-PROGRESS | DONE CANCELLED"
        const val DEFAULT_SHARE_TARGET = "inbox.org"
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
        const val DEFAULT_AGENDA_WIDGET_DAYS_AHEAD = 14
    }
}

class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val syncAppIconWithTheme = booleanPreferencesKey("sync_app_icon_with_theme")

        /**
         * Retired: the single app-wide font-size lever (scaled the whole Material
         * typography). Read only to seed [readModeFontSize] / [editModeFontSize] the
         * first time; every write purges it.
         */
        val legacyFontSize = stringPreferencesKey("font_size")
        val appFontSize = stringPreferencesKey("app_font_size")
        val readModeFontSize = stringPreferencesKey("read_mode_font_size")
        val editModeFontSize = stringPreferencesKey("edit_mode_font_size")
        val noteOpenMode = stringPreferencesKey("note_open_mode")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val lastSeenChangelogBuild = intPreferencesKey("last_seen_changelog_build")
        val newBadgeBaseline = intPreferencesKey("new_badge_baseline")
        val seenNewFeatures = stringPreferencesKey("seen_new_features")
        val vaultTreeUri = stringPreferencesKey("vault_tree_uri")
        val syncMode = stringPreferencesKey("sync_mode")
        val periodicSyncMinutes = intPreferencesKey("periodic_sync_minutes")
        val todoKeywords = stringPreferencesKey("todo_keywords")
        val defaultPriority = stringPreferencesKey("default_priority")
        val addIdToNewNotes = booleanPreferencesKey("add_id_to_new_notes")
        val addCreatedToNewNotes = booleanPreferencesKey("add_created_to_new_notes")
        val newNoteCursor = stringPreferencesKey("new_note_cursor")
        val notebookModes = stringPreferencesKey("notebook_modes")

        /**
         * Retired: per-notebook glyph picks from before monogram icons. Kept only so
         * notebook-style writes can purge any value a pre-monogram install left behind.
         */
        val notebookIcons = stringPreferencesKey("notebook_icons")
        val notebookColors = stringPreferencesKey("notebook_colors")
        val folderColors = stringPreferencesKey("folder_colors")
        val captureNotification = booleanPreferencesKey("capture_notification")
        val shareTargetFile = stringPreferencesKey("share_target_file")
        val showTagsInOutline = booleanPreferencesKey("show_tags_in_outline")
        val showTimestampsInOutline = booleanPreferencesKey("show_timestamps_in_outline")
        val showKeywordsInOutline = booleanPreferencesKey("show_keywords_in_outline")
        /** Unified ordered pin list; `;`-joined `<tag><path>` tokens (see [PinnedItem]). */
        val pinnedItems = stringPreferencesKey("pinned_items")

        /**
         * Retired: the two separate per-type pin lists. Read only for the one-time
         * migration into [pinnedItems]; every write purges them.
         */
        val pinnedNotebooks = stringPreferencesKey("pinned_notebooks")
        val pinnedFolders = stringPreferencesKey("pinned_folders")
        val showPreface = booleanPreferencesKey("show_preface")
        val showPropertyDrawers = booleanPreferencesKey("show_property_drawers")
        val notebookDisplayNameMode = stringPreferencesKey("notebook_display_name_mode")
        val showNotebookFileIcons = booleanPreferencesKey("show_notebook_file_icons")
        val flattenNotebookFolders = booleanPreferencesKey("flatten_notebook_folders")
        val notebookSortKey = stringPreferencesKey("notebook_sort_key")
        val notebookSortAscending = booleanPreferencesKey("notebook_sort_ascending")
        val lastRefileFile = stringPreferencesKey("last_refile_file")
        val lastRefileHeadingPath = stringPreferencesKey("last_refile_heading_path")
        val autoArchiveDoneItems = booleanPreferencesKey("auto_archive_done_items")
        val autoArchiveFile = stringPreferencesKey("auto_archive_file")
        val autoArchiveHeadingPath = stringPreferencesKey("auto_archive_heading_path")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val morningBriefEnabled = booleanPreferencesKey("morning_brief_enabled")
        val defaultReminderTime = stringPreferencesKey("default_reminder_time")
        val reminderLeadTime = stringPreferencesKey("reminder_lead_time")
        val agendaSwipeLeftAction = stringPreferencesKey("agenda_swipe_left_action")
        val agendaSwipeRightAction = stringPreferencesKey("agenda_swipe_right_action")
        val agendaGroupingToday = stringPreferencesKey("agenda_grouping_today")
        val agendaGroupingUpcoming = stringPreferencesKey("agenda_grouping_upcoming")
        val agendaStateFilterToday = stringPreferencesKey("agenda_state_filter_today")
        val agendaStateFilterUpcoming = stringPreferencesKey("agenda_state_filter_upcoming")
        val agendaShowTags = booleanPreferencesKey("agenda_show_tags")
        val agendaShowFile = booleanPreferencesKey("agenda_show_file")
        val agendaWidgetTransparency = floatPreferencesKey("agenda_widget_transparency")
        val agendaWidgetDaysAhead = intPreferencesKey("agenda_widget_days_ahead")
        val expandedFolders = stringPreferencesKey("expanded_folders")
        val notebooksTreeDefaultsApplied = booleanPreferencesKey("notebooks_tree_defaults_applied")
    }

    /**
     * Shared, hot: one `GroveSettings` build per DataStore write, fanned out to all
     * ~40 collectors, instead of each collector re-running the (non-trivial) mapper.
     * `replay = 1` with no seed value preserves the "nothing until the first read"
     * semantics that [com.rrajath.grove.ui.AppViewModel] gates first load on.
     */
    val settings: Flow<GroveSettings> = context.settingsDataStore.data.map { prefs ->
        GroveSettings(
            theme = ThemePreference.fromStorage(prefs[Keys.theme]),
            syncAppIconWithTheme = prefs[Keys.syncAppIconWithTheme] ?: false,
            appFontSize = FontSizePreference.fromStorage(prefs[Keys.appFontSize]),
            readModeFontSize = FontSizePreference.fromStorage(
                prefs[Keys.readModeFontSize] ?: prefs[Keys.legacyFontSize]
            ),
            editModeFontSize = FontSizePreference.fromStorage(
                prefs[Keys.editModeFontSize] ?: prefs[Keys.legacyFontSize]
            ),
            defaultNoteOpenMode = NoteOpenMode.fromStorage(prefs[Keys.noteOpenMode]),
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            lastSeenChangelogBuild = prefs[Keys.lastSeenChangelogBuild],
            newBadgeBaseline = prefs[Keys.newBadgeBaseline],
            seenNewFeatures = decodeStringSet(prefs[Keys.seenNewFeatures]),
            vaultTreeUri = prefs[Keys.vaultTreeUri],
            syncMode = SyncMode.fromStorage(prefs[Keys.syncMode]),
            periodicSyncMinutes = prefs[Keys.periodicSyncMinutes] ?: 30,
            todoKeywords = prefs[Keys.todoKeywords] ?: GroveSettings.DEFAULT_TODO_KEYWORDS,
            defaultPriority = prefs[Keys.defaultPriority]?.firstOrNull(),
            addIdToNewNotes = prefs[Keys.addIdToNewNotes] ?: false,
            addCreatedToNewNotes = prefs[Keys.addCreatedToNewNotes] ?: true,
            newNoteCursor = NewNoteCursor.fromStorage(prefs[Keys.newNoteCursor]),
            notebookModes = decodeModes(prefs[Keys.notebookModes]),
            notebookColors = decodeModes(prefs[Keys.notebookColors]),
            folderColors = decodeModes(prefs[Keys.folderColors]),
            captureNotification = prefs[Keys.captureNotification] ?: false,
            shareTargetFile = prefs[Keys.shareTargetFile] ?: GroveSettings.DEFAULT_SHARE_TARGET,
            showTagsInOutline = prefs[Keys.showTagsInOutline] ?: true,
            showTimestampsInOutline = prefs[Keys.showTimestampsInOutline] ?: true,
            showKeywordsInOutline = prefs[Keys.showKeywordsInOutline] ?: true,
            pinnedItems = readPinnedItems(prefs),
            showPreface = prefs[Keys.showPreface] ?: true,
            showPropertyDrawers = prefs[Keys.showPropertyDrawers] ?: true,
            notebookDisplayNameMode = NotebookDisplayNameMode.fromStorage(prefs[Keys.notebookDisplayNameMode]),
            showNotebookFileIcons = prefs[Keys.showNotebookFileIcons] ?: true,
            flattenNotebookFolders = prefs[Keys.flattenNotebookFolders] ?: false,
            notebookSortKey = NotebookSortKey.fromStorage(prefs[Keys.notebookSortKey]),
            notebookSortAscending = prefs[Keys.notebookSortAscending] ?: true,
            lastRefileFile = prefs[Keys.lastRefileFile],
            lastRefileHeadingPath = prefs[Keys.lastRefileHeadingPath] ?: "",
            autoArchiveDoneItems = prefs[Keys.autoArchiveDoneItems] ?: false,
            autoArchiveFile = prefs[Keys.autoArchiveFile],
            autoArchiveHeadingPath = prefs[Keys.autoArchiveHeadingPath] ?: "",
            remindersEnabled = prefs[Keys.remindersEnabled] ?: true,
            morningBriefEnabled = prefs[Keys.morningBriefEnabled] ?: true,
            defaultReminderTime = decodeTime(prefs[Keys.defaultReminderTime]),
            reminderLeadTime = ReminderLeadTime.fromStorage(prefs[Keys.reminderLeadTime]),
            agendaSwipeLeftAction = AgendaSwipeAction.fromStorage(
                prefs[Keys.agendaSwipeLeftAction], AgendaSwipeAction.MARK_DONE
            ),
            agendaSwipeRightAction = AgendaSwipeAction.fromStorage(
                prefs[Keys.agendaSwipeRightAction], AgendaSwipeAction.SET_SCHEDULED
            ),
            agendaGroupingToday = AgendaGrouping.fromStorage(prefs[Keys.agendaGroupingToday]),
            agendaGroupingUpcoming = AgendaGrouping.fromStorage(prefs[Keys.agendaGroupingUpcoming]),
            agendaStateFilterToday = AgendaStateFilter.fromStorage(prefs[Keys.agendaStateFilterToday]),
            agendaStateFilterUpcoming = AgendaStateFilter.fromStorage(prefs[Keys.agendaStateFilterUpcoming]),
            agendaShowTags = prefs[Keys.agendaShowTags] ?: true,
            agendaShowFile = prefs[Keys.agendaShowFile] ?: false,
            agendaWidgetTransparency = prefs[Keys.agendaWidgetTransparency] ?: 0f,
            agendaWidgetDaysAhead = prefs[Keys.agendaWidgetDaysAhead] ?: GroveSettings.DEFAULT_AGENDA_WIDGET_DAYS_AHEAD,
            expandedFolders = decodeFolderSet(prefs[Keys.expandedFolders]),
            notebooksTreeDefaultsApplied = prefs[Keys.notebooksTreeDefaultsApplied] ?: false,
        )
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** `;`-joined directory paths; `;` can't appear in a path, `/` is the separator. */
    private fun decodeFolderSet(raw: String?): Set<String> =
        raw?.split(';')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    /** `;`-joined token set (feature ids); tokens never contain `;`. */
    private fun decodeStringSet(raw: String?): Set<String> =
        raw?.split(';')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun decodeTime(raw: String?): LocalTime =
        raw?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: GroveSettings.DEFAULT_REMINDER_TIME

    private fun encodeTime(time: LocalTime): String = time.toString()

    /** Legacy per-type pin list decode; used only by [readPinnedItems]'s migration path. */
    private fun decodePinnedList(raw: String?): List<String> =
        raw?.split(';')?.filter { it.isNotEmpty() } ?: emptyList()

    private fun decodePinnedItems(raw: String?): List<PinnedItem> =
        raw?.split(';')?.mapNotNull { if (it.isEmpty()) null else PinnedItem.decode(it) } ?: emptyList()

    private fun encodePinnedItems(items: List<PinnedItem>): String =
        items.joinToString(";") { it.encode() }

    /**
     * The unified pin list, migrating the two legacy keys on the fly: if
     * [Keys.pinnedItems] has never been written, fold [Keys.pinnedNotebooks] +
     * [Keys.pinnedFolders] into one (folders first, matching the old strip order).
     */
    private fun readPinnedItems(prefs: Preferences): List<PinnedItem> {
        prefs[Keys.pinnedItems]?.let { return decodePinnedItems(it) }
        return PinnedItem.fromLegacy(
            decodePinnedList(prefs[Keys.pinnedNotebooks]),
            decodePinnedList(prefs[Keys.pinnedFolders]),
        )
    }

    /** Persist [items] as the sole pin list, purging the retired per-type keys. */
    private fun writePinnedItems(prefs: MutablePreferences, items: List<PinnedItem>) {
        prefs[Keys.pinnedItems] = encodePinnedItems(items)
        prefs.remove(Keys.pinnedNotebooks)
        prefs.remove(Keys.pinnedFolders)
    }

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
     * device-specific vault URI, onboarding flag, and Notebooks tree view state
     * ([expandedFolders] / [notebooksTreeDefaultsApplied]) alone: those don't
     * travel with an export (see [SettingsSerialization]).
     */
    suspend fun applyImported(s: GroveSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.theme] = s.theme.storageKey
            p[Keys.syncAppIconWithTheme] = s.syncAppIconWithTheme
            p[Keys.appFontSize] = s.appFontSize.storageKey
            p[Keys.readModeFontSize] = s.readModeFontSize.storageKey
            p[Keys.editModeFontSize] = s.editModeFontSize.storageKey
            p.remove(Keys.legacyFontSize)
            p[Keys.noteOpenMode] = s.defaultNoteOpenMode.storageKey
            p[Keys.syncMode] = s.syncMode.storageKey
            p[Keys.periodicSyncMinutes] = s.periodicSyncMinutes
            p[Keys.todoKeywords] = s.todoKeywords
            if (s.defaultPriority == null) p.remove(Keys.defaultPriority)
            else p[Keys.defaultPriority] = s.defaultPriority.toString()
            p[Keys.addIdToNewNotes] = s.addIdToNewNotes
            p[Keys.addCreatedToNewNotes] = s.addCreatedToNewNotes
            p[Keys.newNoteCursor] = s.newNoteCursor.storageKey
            p[Keys.notebookModes] = encodeModes(s.notebookModes)
            p[Keys.notebookColors] = encodeModes(s.notebookColors)
            p[Keys.folderColors] = encodeModes(s.folderColors)
            p.remove(Keys.notebookIcons)
            p[Keys.captureNotification] = s.captureNotification
            p[Keys.shareTargetFile] = s.shareTargetFile
            p[Keys.showTagsInOutline] = s.showTagsInOutline
            p[Keys.showTimestampsInOutline] = s.showTimestampsInOutline
            p[Keys.showKeywordsInOutline] = s.showKeywordsInOutline
            writePinnedItems(p, s.pinnedItems)
            p[Keys.showPreface] = s.showPreface
            p[Keys.showPropertyDrawers] = s.showPropertyDrawers
            p[Keys.notebookDisplayNameMode] = s.notebookDisplayNameMode.storageKey
            p[Keys.showNotebookFileIcons] = s.showNotebookFileIcons
            p[Keys.flattenNotebookFolders] = s.flattenNotebookFolders
            p[Keys.remindersEnabled] = s.remindersEnabled
            p[Keys.morningBriefEnabled] = s.morningBriefEnabled
            p[Keys.defaultReminderTime] = encodeTime(s.defaultReminderTime)
            p[Keys.reminderLeadTime] = s.reminderLeadTime.storageKey
            p[Keys.agendaSwipeLeftAction] = s.agendaSwipeLeftAction.storageKey
            p[Keys.agendaSwipeRightAction] = s.agendaSwipeRightAction.storageKey
            p[Keys.agendaGroupingToday] = s.agendaGroupingToday.storageKey
            p[Keys.agendaGroupingUpcoming] = s.agendaGroupingUpcoming.storageKey
            p[Keys.agendaStateFilterToday] = s.agendaStateFilterToday.storageKey
            p[Keys.agendaStateFilterUpcoming] = s.agendaStateFilterUpcoming.storageKey
            p[Keys.agendaShowTags] = s.agendaShowTags
            p[Keys.agendaShowFile] = s.agendaShowFile
            p[Keys.agendaWidgetTransparency] = s.agendaWidgetTransparency
            p[Keys.agendaWidgetDaysAhead] = s.agendaWidgetDaysAhead
            p[Keys.autoArchiveDoneItems] = s.autoArchiveDoneItems
            if (s.autoArchiveFile == null) p.remove(Keys.autoArchiveFile)
            else p[Keys.autoArchiveFile] = s.autoArchiveFile
            p[Keys.autoArchiveHeadingPath] = s.autoArchiveHeadingPath
            if (s.lastRefileFile == null) p.remove(Keys.lastRefileFile)
            else p[Keys.lastRefileFile] = s.lastRefileFile
            p[Keys.lastRefileHeadingPath] = s.lastRefileHeadingPath
        }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.settingsDataStore.edit { it[Keys.theme] = theme.storageKey }
    }

    suspend fun setSyncAppIconWithTheme(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.syncAppIconWithTheme] = enabled }
    }

    suspend fun setAppFontSize(fontSize: FontSizePreference) {
        context.settingsDataStore.edit { it[Keys.appFontSize] = fontSize.storageKey }
    }

    suspend fun setReadModeFontSize(fontSize: FontSizePreference) {
        context.settingsDataStore.edit { it[Keys.readModeFontSize] = fontSize.storageKey }
    }

    suspend fun setEditModeFontSize(fontSize: FontSizePreference) {
        context.settingsDataStore.edit { it[Keys.editModeFontSize] = fontSize.storageKey }
    }

    suspend fun setDefaultNoteOpenMode(mode: NoteOpenMode) {
        context.settingsDataStore.edit { it[Keys.noteOpenMode] = mode.storageKey }
    }

    suspend fun setNewNoteCursor(cursor: NewNoteCursor) {
        context.settingsDataStore.edit { it[Keys.newNoteCursor] = cursor.storageKey }
    }

    /**
     * [seenChangelogBuild], when given, is stamped in the *same* transaction as
     * [onboardingDone]. Onboarding completion is the one moment we want both flags
     * to flip together: the What's New check keys off `onboardingDone` becoming
     * true, and a fresh install has nothing "new" to report, so writing them
     * separately leaves a window where the check sees `onboardingDone == true` but
     * `lastSeenChangelogBuild == null` and wrongly pops the modal.
     */
    suspend fun setOnboardingDone(done: Boolean, seenChangelogBuild: Int? = null) {
        context.settingsDataStore.edit {
            it[Keys.onboardingDone] = done
            if (seenChangelogBuild != null) it[Keys.lastSeenChangelogBuild] = seenChangelogBuild
        }
    }

    suspend fun setLastSeenChangelogBuild(build: Int) {
        context.settingsDataStore.edit { it[Keys.lastSeenChangelogBuild] = build }
    }

    /**
     * Seed [GroveSettings.newBadgeBaseline] the first time the "NEW" badge framework
     * runs, then never touch it again. A fresh install has already stamped
     * [Keys.lastSeenChangelogBuild] with [currentBuild] during onboarding, so the
     * baseline lands on the current build and nothing is badged; an updating install
     * carries its older last-seen build, so features newer than that show a badge.
     * (A rare pre-[Keys.lastSeenChangelogBuild] install seeds to [currentBuild] and
     * misses this one release's badges — the same tradeoff the What's New check makes.)
     */
    suspend fun ensureNewBadgeBaseline(currentBuild: Int) {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.newBadgeBaseline] == null) {
                prefs[Keys.newBadgeBaseline] = prefs[Keys.lastSeenChangelogBuild] ?: currentBuild
            }
        }
    }

    /** Retire the badges for [ids] — the user has reached those features. */
    suspend fun markNewFeaturesSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val merged = decodeStringSet(prefs[Keys.seenNewFeatures]) + ids
            prefs[Keys.seenNewFeatures] = merged.joinToString(";")
        }
    }

    /** Debug only: re-arm every NEW badge (clear the seen set, drop the baseline to 0). */
    suspend fun resetNewBadges() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.seenNewFeatures)
            prefs[Keys.newBadgeBaseline] = 0
        }
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

    suspend fun setShowPreface(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.showPreface] = enabled }
    }

    suspend fun setShowPropertyDrawers(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.showPropertyDrawers] = enabled }
    }

    suspend fun setNotebookDisplayNameMode(mode: NotebookDisplayNameMode) {
        context.settingsDataStore.edit { it[Keys.notebookDisplayNameMode] = mode.storageKey }
    }

    suspend fun setShowNotebookFileIcons(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.showNotebookFileIcons] = enabled }
    }

    suspend fun setFlattenNotebookFolders(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.flattenNotebookFolders] = enabled }
    }

    suspend fun setNotebookSortKey(key: NotebookSortKey) {
        context.settingsDataStore.edit { it[Keys.notebookSortKey] = key.storageKey }
    }

    suspend fun setNotebookSortAscending(ascending: Boolean) {
        context.settingsDataStore.edit { it[Keys.notebookSortAscending] = ascending }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.remindersEnabled] = enabled }
    }

    suspend fun setMorningBriefEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.morningBriefEnabled] = enabled }
    }

    suspend fun setDefaultReminderTime(time: LocalTime) {
        context.settingsDataStore.edit { it[Keys.defaultReminderTime] = encodeTime(time) }
    }

    suspend fun setReminderLeadTime(leadTime: ReminderLeadTime) {
        context.settingsDataStore.edit { it[Keys.reminderLeadTime] = leadTime.storageKey }
    }

    suspend fun setAgendaSwipeLeftAction(action: AgendaSwipeAction) {
        context.settingsDataStore.edit { it[Keys.agendaSwipeLeftAction] = action.storageKey }
    }

    suspend fun setAgendaSwipeRightAction(action: AgendaSwipeAction) {
        context.settingsDataStore.edit { it[Keys.agendaSwipeRightAction] = action.storageKey }
    }

    suspend fun setAgendaGroupingToday(grouping: AgendaGrouping) {
        context.settingsDataStore.edit { it[Keys.agendaGroupingToday] = grouping.storageKey }
    }

    suspend fun setAgendaGroupingUpcoming(grouping: AgendaGrouping) {
        context.settingsDataStore.edit { it[Keys.agendaGroupingUpcoming] = grouping.storageKey }
    }

    suspend fun setAgendaStateFilterToday(filter: AgendaStateFilter) {
        context.settingsDataStore.edit { it[Keys.agendaStateFilterToday] = filter.storageKey }
    }

    suspend fun setAgendaStateFilterUpcoming(filter: AgendaStateFilter) {
        context.settingsDataStore.edit { it[Keys.agendaStateFilterUpcoming] = filter.storageKey }
    }

    suspend fun setAgendaShowTags(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.agendaShowTags] = show }
    }

    suspend fun setAgendaShowFile(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.agendaShowFile] = show }
    }

    suspend fun setAgendaWidgetTransparency(transparency: Float) {
        context.settingsDataStore.edit { it[Keys.agendaWidgetTransparency] = transparency.coerceIn(0f, 1f) }
    }

    suspend fun setAgendaWidgetDaysAhead(days: Int) {
        context.settingsDataStore.edit { it[Keys.agendaWidgetDaysAhead] = days.coerceAtLeast(2) }
    }

    /** Replace the whole expanded-folder set (expand-all / collapse-all). */
    suspend fun setExpandedFolders(dirs: Set<String>) {
        context.settingsDataStore.edit { it[Keys.expandedFolders] = dirs.joinToString(";") }
    }

    /** Flip one folder row's expansion state. */
    suspend fun toggleExpandedFolder(dir: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeFolderSet(prefs[Keys.expandedFolders]).toMutableSet()
            if (!current.add(dir)) current.remove(dir)
            prefs[Keys.expandedFolders] = current.joinToString(";")
        }
    }

    /** First-open heuristic result: seed the expanded set and mark the pass done, in one write. */
    suspend fun applyNotebooksTreeDefaults(expanded: Set<String>) {
        context.settingsDataStore.edit {
            it[Keys.expandedFolders] = expanded.joinToString(";")
            it[Keys.notebooksTreeDefaultsApplied] = true
        }
    }

    suspend fun setLastRefileTarget(fileName: String, headingPath: List<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastRefileFile] = fileName
            prefs[Keys.lastRefileHeadingPath] = headingPath.joinToString("/")
        }
    }

    suspend fun setAutoArchiveDoneItems(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoArchiveDoneItems] = enabled }
    }

    suspend fun setAutoArchiveLocation(fileName: String, headingPath: List<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.autoArchiveFile] = fileName
            prefs[Keys.autoArchiveHeadingPath] = headingPath.joinToString("/")
        }
    }

    suspend fun setNotebookColor(fileName: String, colorKey: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[Keys.notebookColors]).toMutableMap()
            current[fileName] = colorKey
            prefs[Keys.notebookColors] = encodeModes(current)
            // Monogram icons replaced the glyph picker; drop any leftover glyph pick.
            prefs.remove(Keys.notebookIcons)
        }
    }

    private suspend fun editPinnedItems(mutate: (MutableList<PinnedItem>) -> Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = readPinnedItems(prefs).toMutableList()
            if (mutate(current)) writePinnedItems(prefs, current)
        }
    }

    suspend fun pinNotebook(fileName: String) = editPinnedItems { items ->
        val entry = PinnedItem(PinKind.FILE, fileName)
        if (entry in items) false else items.add(entry)
    }

    suspend fun unpinNotebook(fileName: String) = editPinnedItems { items ->
        items.remove(PinnedItem(PinKind.FILE, fileName))
    }

    /** Keep the chosen monogram color and pin position attached to a notebook across renames. */
    suspend fun moveNotebookStyle(oldFileName: String, newFileName: String) {
        context.settingsDataStore.edit { prefs ->
            val colors = decodeModes(prefs[Keys.notebookColors]).toMutableMap()
            colors.remove(oldFileName)?.let { value ->
                colors[newFileName] = value
                prefs[Keys.notebookColors] = encodeModes(colors)
            }
            // Monogram icons replaced the glyph picker; drop any leftover glyph pick.
            prefs.remove(Keys.notebookIcons)
            val pinned = readPinnedItems(prefs).toMutableList()
            val pinIdx = pinned.indexOf(PinnedItem(PinKind.FILE, oldFileName))
            if (pinIdx >= 0) {
                pinned[pinIdx] = PinnedItem(PinKind.FILE, newFileName)
                writePinnedItems(prefs, pinned)
            }
        }
    }

    suspend fun setFolderColor(dir: String, colorKey: String) {
        context.settingsDataStore.edit { prefs ->
            val current = decodeModes(prefs[Keys.folderColors]).toMutableMap()
            current[dir] = colorKey
            prefs[Keys.folderColors] = encodeModes(current)
        }
    }

    suspend fun pinFolder(dir: String) = editPinnedItems { items ->
        val entry = PinnedItem(PinKind.FOLDER, dir)
        if (entry in items) false else items.add(entry)
    }

    suspend fun unpinFolder(dir: String) = editPinnedItems { items ->
        items.remove(PinnedItem(PinKind.FOLDER, dir))
    }

    /**
     * Move the icon colour and pin state of a folder (and every descendant file
     * and sub-folder) from [oldDir] to [newDir] when the folder is renamed. Keys
     * are re-based by prefix: `oldDir` itself and anything under `oldDir/`.
     */
    suspend fun renameFolderStyle(oldDir: String, newDir: String) {
        if (oldDir == newDir) return
        context.settingsDataStore.edit { prefs ->
            val oldPrefix = "$oldDir/"
            fun rebase(key: String): String? = when {
                key == oldDir -> newDir
                key.startsWith(oldPrefix) -> newDir + "/" + key.removePrefix(oldPrefix)
                else -> null
            }

            val folderColors = decodeModes(prefs[Keys.folderColors]).toMutableMap()
            reKeyByPrefix(folderColors, ::rebase)
            prefs[Keys.folderColors] = encodeModes(folderColors)

            val notebookColors = decodeModes(prefs[Keys.notebookColors]).toMutableMap()
            reKeyByPrefix(notebookColors, ::rebase)
            prefs[Keys.notebookColors] = encodeModes(notebookColors)

            // One unified list: re-base every pinned entry (file or folder) whose
            // path is oldDir or sits under it, keeping list order untouched.
            writePinnedItems(
                prefs,
                readPinnedItems(prefs).map { item ->
                    rebase(item.path)?.let { item.copy(path = it) } ?: item
                },
            )
        }
    }

    /** Drop the icon colour and pin state of [dir] and every descendant when the folder is deleted. */
    suspend fun deleteFolderStyle(dir: String) {
        context.settingsDataStore.edit { prefs ->
            val prefix = "$dir/"
            fun own(key: String) = key == dir || key.startsWith(prefix)

            val folderColors = decodeModes(prefs[Keys.folderColors]).filterKeys { !own(it) }
            prefs[Keys.folderColors] = encodeModes(folderColors)

            val notebookColors = decodeModes(prefs[Keys.notebookColors]).filterKeys { !own(it) }
            prefs[Keys.notebookColors] = encodeModes(notebookColors)

            writePinnedItems(prefs, readPinnedItems(prefs).filterNot { own(it.path) })
        }
    }

    /** Re-key a map in place: for every key [rebase] resolves, remove the old entry and re-add under the new key. */
    private fun reKeyByPrefix(map: MutableMap<String, String>, rebase: (String) -> String?) {
        val moves = map.keys.mapNotNull { old -> rebase(old)?.let { old to it } }
        moves.forEach { (old, new) -> map.remove(old)?.let { map[new] = it } }
    }
}
