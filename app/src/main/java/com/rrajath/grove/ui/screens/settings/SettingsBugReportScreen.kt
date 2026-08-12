package com.rrajath.grove.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rrajath.grove.BuildConfig
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class BugPayloadRow(val key: String, val value: String)

/**
 * Settings § Help → Report a bug (design/Grove.dc.html lines 1811-1881). The Send
 * button has no crash server to talk to yet, so it just surfaces what would have
 * been sent as a toast; swap that for the real submission once an endpoint exists.
 */
@Composable
fun SettingsBugReportScreen(onBack: () -> Unit) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var includeDeviceInfo by rememberSaveable { mutableStateOf(true) }
    var includeErrorLog by rememberSaveable { mutableStateOf(false) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }

    val canSend = description.isNotBlank()
    val payload = remember(includeDeviceInfo, includeErrorLog) {
        bugPayload(includeDeviceInfo, includeErrorLog)
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Text(
                        "Report a bug",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(c.bg)) {
                HorizontalDivider(color = c.line)
                Column(Modifier.padding(16.dp)) {
                    SendReportButton(
                        enabled = canSend,
                        sent = sent,
                        onClick = {
                            sent = true
                            Toast.makeText(context, formatBugReportToast(description, steps), Toast.LENGTH_LONG).show()
                        },
                    )
                    Text(
                        if (copied) "Copied — paste it into a GitHub issue"
                        else "Or copy as text to attach to a GitHub issue",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                copied = true
                                clipboard.setText(AnnotatedString(formatBugReportText(description, steps, payload)))
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Tell us what happened. Everything below is optional except the description — " +
                    "nothing is sent until you tap Send.",
                fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 1.55.em, color = c.ink2,
                modifier = Modifier.padding(bottom = 18.dp),
            )

            FieldSectionLabel(label = "WHAT WENT WRONG", required = true)
            BugTextArea(
                value = description,
                onValueChange = { description = it },
                placeholder = "e.g. App crashed when I opened a saved note offline",
                minLines = 4,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            FieldSectionLabel(label = "STEPS TO REPRODUCE", required = false)
            BugTextArea(
                value = steps,
                onValueChange = { steps = it },
                placeholder = "1. Open a note  2. Turn on airplane mode  3. …",
                minLines = 3,
                modifier = Modifier.padding(bottom = 22.dp),
            )

            SectionLabel("WHAT WE INCLUDE")
            SettingsGroup {
                ToggleRow(
                    label = "Device & app info",
                    description = "Android version, device model, app version — helps us reproduce it",
                    checked = includeDeviceInfo,
                    onToggle = { includeDeviceInfo = it },
                )
                RowDivider()
                ToggleRow(
                    label = "Recent error log",
                    description = "Last ~50 lines from this session only. Off by default.",
                    checked = includeErrorLog,
                    onToggle = { includeErrorLog = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            PreviewToggleRow(open = previewOpen, onToggle = { previewOpen = !previewOpen })
            AnimatedVisibility(visible = previewOpen) {
                BugPayloadPreview(payload, modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(Modifier.height(if (previewOpen) 10.dp else 16.dp))
            PrivacyNote()
        }
    }
}

@Composable
private fun FieldSectionLabel(label: String, required: Boolean) {
    val c = MaterialTheme.grove
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, letterSpacing = 1.sp, color = c.accent,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (required) "required" else "optional",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, color = if (required) c.red else c.ink3,
        )
    }
}

@Composable
private fun BugTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, fontFamily = PlexSans, fontSize = 14.sp, color = c.ink3)
        },
        textStyle = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 21.sp, color = c.ink),
        minLines = minLines,
        maxLines = minLines,
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.line,
            cursorColor = c.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewToggleRow(open: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.grove
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "bugPreviewCaret")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        Text(
            "▸",
            fontFamily = PlexSans, fontSize = 10.sp, color = c.accent,
            modifier = Modifier.rotate(rotation),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "Show exactly what gets sent",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.accent,
        )
    }
}

@Composable
private fun BugPayloadPreview(payload: List<BugPayloadRow>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        payload.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    row.key,
                    fontFamily = PlexMono, fontSize = 11.5.sp, color = c.synProp,
                    modifier = Modifier.width(118.dp),
                )
                Text(row.value, fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink)
            }
        }
        DashedDivider(color = c.line2, modifier = Modifier.padding(top = 6.dp, bottom = 9.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.red, fontWeight = FontWeight.SemiBold)) { append("never included: ") }
                append("name, email, account ID, location, contacts, file contents")
            },
            fontFamily = PlexMono, fontSize = 11.5.sp, lineHeight = 1.6.em, color = c.ink3,
        )
    }
}

@Composable
private fun DashedDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
        )
    }
}

@Composable
private fun PrivacyNote() {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.accentSoft)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text("⚿", fontSize = 14.sp, color = c.accent, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                append(
                    "Sent directly to our self-hosted, open-source crash server — never to Google or a " +
                        "third party. "
                )
                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.SemiBold)) {
                    append("Privacy policy →")
                }
            },
            fontFamily = PlexSans, fontSize = 11.5.sp, lineHeight = 1.6.em, color = c.ink2,
        )
    }
}

@Composable
private fun SendReportButton(enabled: Boolean, sent: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val bg = when {
        sent -> c.green
        enabled -> c.accent
        else -> c.surface3
    }
    val fg = if (enabled || sent) c.accentInk else c.ink3
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (enabled && !sent) {
                    Modifier.shadow(6.dp, shape, clip = false, ambientColor = ButtonShadowColor, spotColor = ButtonShadowColor)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (sent) "Report sent ✓" else "Send report",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = fg,
        )
    }
}

private val ButtonShadowColor = Color(0x668A5A2B)

private fun bugPayload(includeDeviceInfo: Boolean, includeErrorLog: Boolean): List<BugPayloadRow> {
    val rows = mutableListOf<BugPayloadRow>()
    if (includeDeviceInfo) {
        rows += BugPayloadRow("app_version", BuildConfig.VERSION_NAME)
        rows += BugPayloadRow("android_version", Build.VERSION.RELEASE)
        rows += BugPayloadRow("device_model", Build.MODEL)
        rows += BugPayloadRow("locale", Locale.getDefault().toLanguageTag())
    }
    rows += BugPayloadRow("report_time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now()))
    if (includeErrorLog) rows += BugPayloadRow("error_log", "not captured yet — coming soon")
    return rows
}

private fun formatBugReportToast(description: String, steps: String): String = buildString {
    append("Bug report queued (no crash server wired up yet):\n\"")
    append(description.trim())
    append("\"")
    if (steps.isNotBlank()) {
        append("\nSteps: ")
        append(steps.trim())
    }
}

private fun formatBugReportText(description: String, steps: String, payload: List<BugPayloadRow>): String = buildString {
    appendLine("Grove bug report")
    appendLine()
    appendLine("Description:")
    appendLine(description.trim())
    if (steps.isNotBlank()) {
        appendLine()
        appendLine("Steps to reproduce:")
        appendLine(steps.trim())
    }
    if (payload.isNotEmpty()) {
        appendLine()
        payload.forEach { appendLine("${it.key}: ${it.value}") }
    }
}.trim()
