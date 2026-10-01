package com.rrajath.grove.org

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The date a recurring heading moved to when it was marked done: the earliest
 * repeating SCHEDULED / DEADLINE / dedicated active timestamp on [after] that
 * [before] didn't already have. Null when nothing repeating was advanced (a plain,
 * non-recurring mark-done).
 */
fun nextRepeatOccurrence(before: OrgHeadline, after: OrgHeadline): LocalDate? =
    (after.repeatingDates() - before.repeatingDates().toSet()).minOrNull()

private fun OrgHeadline.repeatingDates(): List<LocalDate> =
    (listOfNotNull(planning.scheduled, planning.deadline) + dedicatedActiveTimestamps)
        .filter { it.repeater != null }
        .map { it.date }

/**
 * The mark-done snackbar text: "Marked done", plus "Next occurrence Jun 5th" for a
 * recurring task. The year is added ("Jan 3rd, 2027") only when [next] falls in a
 * different year than [today].
 */
fun markedDoneMessage(next: LocalDate?, today: LocalDate): String =
    if (next == null) "Marked done" else "Marked done. Next occurrence ${formatOccurrence(next, today)}"

internal fun formatOccurrence(date: LocalDate, today: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val day = date.dayOfMonth
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    val year = if (date.year != today.year) ", ${date.year}" else ""
    return "$month $day$suffix$year"
}
