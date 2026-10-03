package com.rrajath.grove.reminders

/**
 * Pure logic (JVM-testable): whether to show the one-time "Get reminders on time"
 * dialog asking for exact-alarm ("Alarms & reminders") access. Checked on every
 * return to the foreground; once answered it never shows again.
 *
 * Requires notifications to be on: with them off, reminders aren't armed at all
 * (they wait as `pendingPermission`), so exact access would have nothing to apply
 * to. Settings › Reminders asks for notifications first for the same reason.
 * [canScheduleExact] is always true below API 31, so the dialog never shows there.
 */
object ExactAlarmPrompt {
    fun shouldShow(
        onboardingDone: Boolean,
        promptHandled: Boolean,
        remindersEnabled: Boolean,
        morningBriefEnabled: Boolean,
        hasFutureReminder: Boolean,
        notificationsEnabled: Boolean,
        canScheduleExact: Boolean,
    ): Boolean = onboardingDone &&
        !promptHandled &&
        remindersEnabled &&
        // Any reminder kind counts: timed, date-only, or the Morning Brief's own alarm.
        (morningBriefEnabled || hasFutureReminder) &&
        notificationsEnabled &&
        !canScheduleExact
}
