package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.AgendaSwipeAction
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.components.DropdownPicker
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import kotlin.math.roundToInt

/**
 * Only these two are offered from the swipe-action pickers below; [AgendaSwipeAction.SET_DEADLINE]
 * stays a valid stored value (an older export can still carry it) but is no longer user-selectable.
 */
private val SWIPE_ACTION_OPTIONS = listOf(AgendaSwipeAction.MARK_DONE, AgendaSwipeAction.SET_SCHEDULED)

/** Settings § Agenda. */
@Composable
fun SettingsAgendaScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetAgendaSwipeLeftAction: (AgendaSwipeAction) -> Unit,
    onSetAgendaSwipeRightAction: (AgendaSwipeAction) -> Unit,
    onSetAgendaWidgetTransparency: (Float) -> Unit,
    onSetAgendaWidgetDaysAhead: (Int) -> Unit,
    onSetAgendaWidgetShowFileName: (Boolean) -> Unit,
    onSetAgendaWidgetShowTags: (Boolean) -> Unit,
    onSetAgendaWidgetShowPriority: (Boolean) -> Unit,
    onSetAgendaWidgetOverdueDaysCap: (Int) -> Unit,
    onSetAgendaWidgetFontSize: (FontSizePreference) -> Unit,
) {
    val c = MaterialTheme.grove
    SettingsPageScaffold(title = "Agenda", onBack = onBack) {
        SettingsGroup {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Text(
                    "Swipe left",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, color = c.ink,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    "Swiping an agenda item left will " + swipeActionDescription(settings.agendaSwipeLeftAction),
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                DropdownPicker(
                    options = SWIPE_ACTION_OPTIONS.map { it.label },
                    selectedIndex = SWIPE_ACTION_OPTIONS.indexOf(settings.agendaSwipeLeftAction).coerceAtLeast(0),
                    onSelect = { onSetAgendaSwipeLeftAction(SWIPE_ACTION_OPTIONS[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Text(
                    "Swipe right",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, color = c.ink,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    "Swiping an agenda item right will " + swipeActionDescription(settings.agendaSwipeRightAction),
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                DropdownPicker(
                    options = SWIPE_ACTION_OPTIONS.map { it.label },
                    selectedIndex = SWIPE_ACTION_OPTIONS.indexOf(settings.agendaSwipeRightAction).coerceAtLeast(0),
                    onSelect = { onSetAgendaSwipeRightAction(SWIPE_ACTION_OPTIONS[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("WIDGET")
        Box(Modifier.padding(bottom = 14.dp)) {
            AgendaWidgetPreview(settings)
        }
        SettingsGroup {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Transparency",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(settings.agendaWidgetTransparency * 100).roundToInt()}%",
                        fontFamily = PlexMono, fontSize = 13.sp, color = c.accent,
                    )
                }
                Text(
                    "Home-screen Agenda widget background",
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                )
                Slider(
                    value = settings.agendaWidgetTransparency,
                    onValueChange = onSetAgendaWidgetTransparency,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.line,
                    ),
                )
            }
            RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Days ahead",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                    )
                    Text(
                        "How far into the future the widget shows",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DaysAheadField(settings.agendaWidgetDaysAhead, onSetAgendaWidgetDaysAhead)
            }
            RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Days overdue",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                    )
                    Text(
                        "How far back an overdue item can be and still show; 0 shows every overdue item",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                OverdueDaysCapField(settings.agendaWidgetOverdueDaysCap, onSetAgendaWidgetOverdueDaysCap)
            }
            RowDivider()
            ToggleRow(
                label = "Filename",
                checked = settings.agendaWidgetShowFileName,
                description = "Nested folders are contracted to initials, e.g. W/P/notes.org",
                onToggle = onSetAgendaWidgetShowFileName,
            )
            RowDivider()
            ToggleRow(
                label = "Tags",
                checked = settings.agendaWidgetShowTags,
                description = "Own and inherited tags on each item",
                onToggle = onSetAgendaWidgetShowTags,
            )
            RowDivider()
            ToggleRow(
                label = "Priority",
                checked = settings.agendaWidgetShowPriority,
                description = "The A/B/C priority badge on each item",
                onToggle = onSetAgendaWidgetShowPriority,
            )
            RowDivider()
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Text(
                    "Font size",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, color = c.ink,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SegmentedControl(
                    options = listOf("Small", "Medium", "Large"),
                    selectedIndex = settings.agendaWidgetFontSize.ordinal,
                    onSelect = { onSetAgendaWidgetFontSize(FontSizePreference.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Positive-integer field where 0 (or blank) means "unbounded" — no non-digit
 * keystrokes reach the field, and an empty value commits as 0 rather than
 * reverting, since blank is itself a valid, meaningful state here.
 */
@Composable
private fun OverdueDaysCapField(days: Int, onSet: (Int) -> Unit) {
    val c = MaterialTheme.grove
    var text by remember(days) { mutableStateOf(if (days == 0) "" else days.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(4) },
        placeholder = { Text("0", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp, color = c.accent),
        modifier = Modifier
            .width(72.dp)
            .onFocusChanged { state ->
                if (!state.isFocused) onSet(text.toIntOrNull() ?: 0)
            },
    )
}

/**
 * Positive-integer-only field (>1): non-digit keystrokes are filtered as typed,
 * and an invalid value (blank, 0, or 1) reverts to the last committed setting
 * on blur rather than showing an inline error.
 */
@Composable
private fun DaysAheadField(days: Int, onSet: (Int) -> Unit) {
    val c = MaterialTheme.grove
    var text by remember(days) { mutableStateOf(days.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(4) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 14.sp, color = c.accent),
        modifier = Modifier
            .width(72.dp)
            .onFocusChanged { state ->
                if (!state.isFocused) {
                    val parsed = text.toIntOrNull()
                    if (parsed != null && parsed > 1) onSet(parsed) else text = days.toString()
                }
            },
    )
}
