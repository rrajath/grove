package com.rrajath.grove.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSerializationTest {

    private val sample = GroveSettings(
        theme = ThemePreference.DARK,
        syncAppIconWithTheme = true,
        fontSize = FontSizePreference.LARGE,
        defaultNoteOpenMode = NoteOpenMode.EDIT,
        syncMode = SyncMode.PERIODIC,
        periodicSyncMinutes = 60,
        todoKeywords = "TODO NEXT | DONE",
        defaultPriority = 'B',
        addIdToNewNotes = true,
        addCreatedToNewNotes = false,
        // Nested vault paths are valid keys now the vault is a tree.
        notebookModes = mapOf("work.org" to "edit", "projects/clients/acme.org" to "read"),
        notebookColors = mapOf("work.org" to "green", "projects/clients/acme.org" to "amber"),
        captureNotification = true,
        shareTargetFile = "capture.org",
        showTagsInOutline = false,
        showTimestampsInOutline = false,
        showKeywordsInOutline = true,
        pinnedNotebooks = listOf("pinned-first.org", "pinned-second.org", "area/sub/deep.org"),
        showPreface = false,
        showPropertyDrawers = false,
        showNotebookFileIcons = false,
        checklistStates = ChecklistStates.THREE,
        remindersEnabled = false,
        morningBriefEnabled = false,
        defaultReminderTime = java.time.LocalTime.of(7, 45),
        reminderLeadTime = ReminderLeadTime.MIN_15,
        agendaSwipeLeftAction = AgendaSwipeAction.MARK_DONE,
        agendaSwipeRightAction = AgendaSwipeAction.SET_DEADLINE,
        agendaGroupingToday = AgendaGrouping.PRIORITY,
        agendaGroupingUpcoming = AgendaGrouping.TAG,
        agendaStateFilterToday = AgendaStateFilter.Keyword("NEXT"),
        agendaStateFilterUpcoming = AgendaStateFilter.All,
        agendaWidgetTransparency = 0.35f,
        agendaWidgetDaysAhead = 21,
        autoArchiveDoneItems = true,
        autoArchiveFile = "archive.org",
        autoArchiveHeadingPath = "Done/2026",
        lastRefileFile = "projects.org",
        lastRefileHeadingPath = "Active/Grove",
        // Device-specific fields that must NOT travel with an export.
        vaultTreeUri = "content://com.android.externalstorage/tree/primary%3Aorg",
        onboardingDone = true,
    )

    @Test
    fun `portable preferences survive an export then import`() {
        val json = SettingsSerialization.export(sample)
        // Re-import onto fresh defaults so we can prove every portable field carried.
        val restored = SettingsSerialization.import(json, GroveSettings())

        assertEquals(sample.theme, restored.theme)
        assertEquals(sample.syncAppIconWithTheme, restored.syncAppIconWithTheme)
        assertEquals(sample.fontSize, restored.fontSize)
        assertEquals(sample.defaultNoteOpenMode, restored.defaultNoteOpenMode)
        assertEquals(sample.syncMode, restored.syncMode)
        assertEquals(sample.periodicSyncMinutes, restored.periodicSyncMinutes)
        assertEquals(sample.todoKeywords, restored.todoKeywords)
        assertEquals(sample.defaultPriority, restored.defaultPriority)
        assertEquals(sample.addIdToNewNotes, restored.addIdToNewNotes)
        assertEquals(sample.addCreatedToNewNotes, restored.addCreatedToNewNotes)
        assertEquals(sample.notebookModes, restored.notebookModes)
        assertEquals(sample.notebookColors, restored.notebookColors)
        assertEquals(sample.captureNotification, restored.captureNotification)
        assertEquals(sample.shareTargetFile, restored.shareTargetFile)
        assertEquals(sample.showTagsInOutline, restored.showTagsInOutline)
        assertEquals(sample.showTimestampsInOutline, restored.showTimestampsInOutline)
        assertEquals(sample.showKeywordsInOutline, restored.showKeywordsInOutline)
        assertEquals(sample.pinnedNotebooks, restored.pinnedNotebooks)
        assertEquals(sample.showPreface, restored.showPreface)
        assertEquals(sample.showPropertyDrawers, restored.showPropertyDrawers)
        assertEquals(sample.showNotebookFileIcons, restored.showNotebookFileIcons)
        assertEquals(sample.checklistStates, restored.checklistStates)
        assertEquals(sample.remindersEnabled, restored.remindersEnabled)
        assertEquals(sample.morningBriefEnabled, restored.morningBriefEnabled)
        assertEquals(sample.defaultReminderTime, restored.defaultReminderTime)
        assertEquals(sample.reminderLeadTime, restored.reminderLeadTime)
        assertEquals(sample.agendaSwipeLeftAction, restored.agendaSwipeLeftAction)
        assertEquals(sample.agendaSwipeRightAction, restored.agendaSwipeRightAction)
        assertEquals(sample.agendaGroupingToday, restored.agendaGroupingToday)
        assertEquals(sample.agendaGroupingUpcoming, restored.agendaGroupingUpcoming)
        assertEquals(sample.agendaStateFilterToday, restored.agendaStateFilterToday)
        assertEquals(sample.agendaStateFilterUpcoming, restored.agendaStateFilterUpcoming)
        assertEquals(sample.agendaWidgetTransparency, restored.agendaWidgetTransparency)
        assertEquals(sample.agendaWidgetDaysAhead, restored.agendaWidgetDaysAhead)
        assertEquals(sample.autoArchiveDoneItems, restored.autoArchiveDoneItems)
        assertEquals(sample.autoArchiveFile, restored.autoArchiveFile)
        assertEquals(sample.autoArchiveHeadingPath, restored.autoArchiveHeadingPath)
        assertEquals(sample.lastRefileFile, restored.lastRefileFile)
        assertEquals(sample.lastRefileHeadingPath, restored.lastRefileHeadingPath)
    }

    @Test
    fun `import preserves the existing vault and onboarding state`() {
        val json = SettingsSerialization.export(sample)
        val base = GroveSettings(vaultTreeUri = "content://existing/tree", onboardingDone = false)
        val restored = SettingsSerialization.import(json, base)

        // The export never carried these, so the base install's values win.
        assertEquals("content://existing/tree", restored.vaultTreeUri)
        assertEquals(false, restored.onboardingDone)
        assertNotEquals(sample.vaultTreeUri, restored.vaultTreeUri)
    }

    @Test
    fun `exported document is human-readable json`() {
        val json = SettingsSerialization.export(sample)
        assertTrue(json.contains("\"theme\": \"dark\""))
        assertTrue(json.contains("\"version\": ${SettingsExport.CURRENT_VERSION}"))
        // Pretty-printed across multiple lines.
        assertTrue(json.contains("\n"))
    }

    @Test
    fun `unknown enum values fall back to defaults on import`() {
        val json = """{ "theme": "sepia", "fontSize": "huge", "syncMode": "warp", "checklistStates": "four",
            "agendaSwipeLeftAction": "cartwheel", "agendaSwipeRightAction": "backflip", "reminderLeadTime": "next week" }"""
        val restored = SettingsSerialization.import(json, GroveSettings())

        assertEquals(ThemePreference.LIGHT, restored.theme)
        assertEquals(FontSizePreference.MEDIUM, restored.fontSize)
        assertEquals(SyncMode.ON_OPEN_CLOSE, restored.syncMode)
        assertEquals(ChecklistStates.TWO, restored.checklistStates)
        assertEquals(AgendaSwipeAction.MARK_DONE, restored.agendaSwipeLeftAction)
        assertEquals(AgendaSwipeAction.SET_SCHEDULED, restored.agendaSwipeRightAction)
        assertEquals(ReminderLeadTime.AT_TIME, restored.reminderLeadTime)
    }

    @Test
    fun `agenda widget transparency and days-ahead are clamped on import`() {
        val json = """{ "agendaWidgetTransparency": 1.5, "agendaWidgetDaysAhead": 1 }"""
        val restored = SettingsSerialization.import(json, GroveSettings())
        assertEquals(1f, restored.agendaWidgetTransparency)
        assertEquals(2, restored.agendaWidgetDaysAhead)
    }

    @Test
    fun `malformed default reminder time falls back to 9am`() {
        val json = """{ "defaultReminderTime": "not a time" }"""
        val restored = SettingsSerialization.import(json, GroveSettings())
        assertEquals(java.time.LocalTime.of(9, 0), restored.defaultReminderTime)
    }

    @Test
    fun `unknown keys are ignored so newer exports stay importable`() {
        val json = """{ "theme": "light", "futureField": 42 }"""
        val restored = SettingsSerialization.import(json, GroveSettings())
        assertEquals(ThemePreference.LIGHT, restored.theme)
    }

    @Test
    fun `malformed json throws`() {
        assertThrows(Exception::class.java) {
            SettingsSerialization.import("not json at all", GroveSettings())
        }
    }
}
