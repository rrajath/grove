package com.rrajath.grove.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.theme.grove
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Date picker for a SCHEDULED/DEADLINE timestamp, with an "Add Time"/"Change Time"
 * step. Confirming the date alone inserts a date-only [OrgTimestamp]; stepping into
 * the time picker and confirming there inserts date+time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningDatePicker(
    existing: OrgTimestamp?,
    onDismiss: () -> Unit,
    onConfirm: (OrgTimestamp) -> Unit,
) {
    val c = MaterialTheme.grove
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf(existing?.date) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = (existing?.date)
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            showTimePicker = true
                        }
                    }) {
                        Text(
                            if (existing?.time != null) "Change Time" else "Add Time",
                            color = c.ink2, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = {
                        val millis = dateState.selectedDateMillis
                        if (millis != null) {
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onConfirm(OrgTimestamp(date))
                        }
                    }) { Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold) }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = existing?.time?.hour ?: LocalTime.now().hour,
            initialMinute = existing?.time?.minute ?: LocalTime.now().minute,
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickedDate ?: LocalDate.now()
                    onConfirm(OrgTimestamp(date, time = LocalTime.of(timeState.hour, timeState.minute)))
                }) { Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Back", color = c.ink2) }
            },
        ) {
            TimePicker(state = timeState)
        }
    }
}

/**
 * Start + end date picker (Search › Filters "Custom range" for Scheduled/Deadline).
 * [DateRangePicker] is designed for a wider, taller surface than [DatePickerDialog]
 * gives it (that dialog is sized for the compact single-month [DatePicker]) — cramming
 * it in there clips the following month and squeezes its header into a centered-looking
 * line. This uses a plain, unconstrained [Dialog] + [Surface] instead so the range
 * picker gets the room its default title/headline/calendar layout expects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePicker(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val c = MaterialTheme.grove
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = initialEnd?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = c.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                DateRangePicker(state = rangeState, modifier = Modifier.weight(1f))
                HorizontalDivider(color = c.line)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2, fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = {
                            val startMillis = rangeState.selectedStartDateMillis
                            val endMillis = rangeState.selectedEndDateMillis
                            if (startMillis != null && endMillis != null) {
                                onConfirm(
                                    Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                                    Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                                )
                            }
                        },
                        enabled = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
                    ) { Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/**
 * Standalone time-of-day picker (no date step) — e.g. Settings › Reminders ›
 * "Default reminder time". Shares [TimePickerDialog]'s chrome with [PlanningDatePicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTimePicker(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val c = MaterialTheme.grove
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(timeState.hour, timeState.minute)) }) {
                Text("Set", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) }
        },
    ) {
        TimePicker(state = timeState)
    }
}

/**
 * Material3 ships no `TimePickerDialog`; this mirrors [DatePickerDialog]'s chrome.
 * Internal (not private) so other same-module time pickers (e.g. Settings ›
 * Reminders › "Default reminder time") can reuse the same chrome.
 */
@Composable
internal fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.grove
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = c.surface,
        ) {
            Column(Modifier.padding(24.dp)) {
                content()
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
