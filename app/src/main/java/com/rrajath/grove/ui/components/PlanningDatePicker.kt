package com.rrajath.grove.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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

/** Material3 ships no `TimePickerDialog`; this mirrors [DatePickerDialog]'s chrome. */
@Composable
private fun TimePickerDialog(
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
