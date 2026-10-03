package com.rrajath.grove.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPromptTest {

    /** Defaults describe a user who should see the prompt; each test flips one input. */
    private fun shouldShow(
        onboardingDone: Boolean = true,
        promptHandled: Boolean = false,
        remindersEnabled: Boolean = true,
        morningBriefEnabled: Boolean = false,
        hasFutureReminder: Boolean = true,
        notificationsEnabled: Boolean = true,
        canScheduleExact: Boolean = false,
    ) = ExactAlarmPrompt.shouldShow(
        onboardingDone, promptHandled, remindersEnabled, morningBriefEnabled,
        hasFutureReminder, notificationsEnabled, canScheduleExact,
    )

    @Test
    fun `shows when exact access is missing and a reminder is upcoming`() = assertTrue(shouldShow())

    @Test
    fun `Morning Brief alone counts as a reminder`() =
        assertTrue(shouldShow(hasFutureReminder = false, morningBriefEnabled = true))

    @Test
    fun `nothing upcoming and no Morning Brief - no prompt`() =
        assertFalse(shouldShow(hasFutureReminder = false, morningBriefEnabled = false))

    @Test
    fun `exact access already granted - no prompt`() = assertFalse(shouldShow(canScheduleExact = true))

    @Test
    fun `answered once - never again`() = assertFalse(shouldShow(promptHandled = true))

    @Test
    fun `notifications off - exact access would do nothing, so no prompt`() =
        assertFalse(shouldShow(notificationsEnabled = false))

    @Test
    fun `reminders disabled - no prompt`() = assertFalse(shouldShow(remindersEnabled = false))

    @Test
    fun `before onboarding finishes - no prompt`() = assertFalse(shouldShow(onboardingDone = false))
}
