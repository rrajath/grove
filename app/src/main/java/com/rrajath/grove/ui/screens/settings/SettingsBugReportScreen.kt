package com.rrajath.grove.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.rrajath.grove.BuildConfig
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.org.TextEdit
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.editor.insertedSoloNewlineAt
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

private data class BugPayloadRow(val key: String, val value: String)

@Serializable
private data class DeviceInfo(
    val app_version: String,
    val build_number: String,
    val android_version: String,
    val device_model: String,
    val locale: String,
)

@Serializable
private data class BugReportPayload(
    val description: String,
    val steps: String? = null,
    val device_info: DeviceInfo? = null,
    val error_log: String? = null,
    val report_time: String,
)

@Serializable
private data class BugReportResponse(
    val received: Boolean = false,
    val issue_url: String? = null,
)

/**
 * Settings § Help → Report a bug (design/Grove.dc.html lines 1811-1881). Send
 * posts the real form payload to the bug-report Worker (see
 * internal-docs/report-bugs/PLAN.md), which files a GitHub issue on
 * rrajath/grove (Phase 3), then shows a popup linking to the filed issue.
 */
@Composable
fun SettingsBugReportScreen(onBack: () -> Unit) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val httpClient = remember {
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
    }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }

    var description by rememberSaveable { mutableStateOf("") }
    val stepsState = rememberTextFieldState()
    var includeDeviceInfo by rememberSaveable { mutableStateOf(true) }
    var includeErrorLog by rememberSaveable { mutableStateOf(false) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    var sending by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }
    var issueUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showSentDialog by rememberSaveable { mutableStateOf(false) }

    // Not rememberSaveable: a captured log is only meaningful for the current process's
    // session, so it's fine (and correct) for this to reset on process death.
    var errorLogLines by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(includeErrorLog) {
        errorLogLines = if (includeErrorLog) captureRecentErrorLog(maxLines = 50) else null
    }

    val canSend = description.isNotBlank() && !sending
    val payload = remember(includeDeviceInfo, includeErrorLog, errorLogLines) {
        bugPayload(includeDeviceInfo, includeErrorLog, errorLogLines)
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
                        sending = sending,
                        sent = sent,
                        onClick = {
                            sending = true
                            scope.launch {
                                runCatching {
                                    // Re-capture right before sending (rather than reusing
                                    // errorLogLines) so the log reflects everything up to the
                                    // moment of the tap, not just when the toggle was flipped on.
                                    val freshErrorLog = if (includeErrorLog) captureRecentErrorLog(maxLines = 50) else null
                                    errorLogLines = freshErrorLog
                                    val body = BugReportPayload(
                                        description = description.trim(),
                                        steps = stepsState.text.toString().trim().ifBlank { null },
                                        device_info = if (includeDeviceInfo) deviceInfo() else null,
                                        error_log = freshErrorLog?.joinToString("\n"),
                                        report_time = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                                    )
                                    httpClient.post(BuildConfig.BUG_REPORT_ENDPOINT) {
                                        contentType(ContentType.Application.Json)
                                        setBody(body)
                                    }.body<BugReportResponse>()
                                }.onSuccess { response ->
                                    sent = true
                                    issueUrl = response.issue_url
                                    showSentDialog = true
                                }.onFailure { error ->
                                    Toast.makeText(context, "Couldn't reach the bug-report Worker: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                                sending = false
                            }
                        },
                    )
                    Text(
                        if (copied) "Copied. Paste it into a GitHub issue."
                        else "Copy as text to attach to a GitHub issue",
                        fontFamily = PlexSans, fontSize = 12.sp,
                        color = if (copied) c.ink2 else c.synLink,
                        textDecoration = if (copied) null else TextDecoration.Underline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                copied = true
                                clipboard.setText(AnnotatedString(formatBugReportText(description, stepsState.text.toString(), payload)))
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
                "Tell us what happened. Everything below is optional except the description. " +
                    "Nothing gets sent until you tap Send Report.",
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
            BugListTextArea(
                state = stepsState,
                placeholder = "1. Open a note\n2. Turn on airplane mode\n3. …",
                minLines = 3,
                modifier = Modifier.padding(bottom = 22.dp),
            )

            SectionLabel("WHAT WE INCLUDE")
            SettingsGroup {
                ToggleRow(
                    label = "Device & app info",
                    description = "Android version, device model, app version (helps us reproduce it)",
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

    if (showSentDialog) {
        IssueFiledDialog(
            issueUrl = issueUrl,
            onDismiss = { showSentDialog = false },
        )
    }
}

/**
 * List-only counterpart of `OrgEditing.kt`'s `orgInputTransformation`: same
 * change-list-based Enter detection (via the shared [insertedSoloNewlineAt]),
 * `LineEditing.continueListOnEnter` and `capitalizeListItemOnType`, minus the
 * heading capitalization this field has no use for. Deliberately built on the
 * newer `TextFieldState`/`InputTransformation` API rather than mutating a
 * classic `TextFieldValue` in `onValueChange`: doing the latter desyncs the
 * IME's composing region on real devices (a keystroke lands right as this
 * function rewrites the buffer) and silently eats the next character typed.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun stepsListInputTransformation() = InputTransformation {
    val old = originalText.toString()
    val typed = toString()
    val cursor = selection.start

    val afterEnter = if (insertedSoloNewlineAt(cursor)) LineEditing.continueListOnEnter(typed, cursor) else null
    val base = afterEnter ?: TextEdit(typed, cursor)
    val result = LineEditing.capitalizeListItemOnType(old, base.text, base.cursor) ?: base

    if (result.text != typed || result.cursor != cursor) {
        replace(0, length, result.text)
        selection = TextRange(result.cursor)
    }
}

@Composable
private fun IssueFiledDialog(issueUrl: String?, onDismiss: () -> Unit) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text("Issue filed successfully", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink)
        },
        text = {
            Text(
                issueUrl ?: "The issue was filed, but no link came back from the server.",
                fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 1.5.em,
                color = if (issueUrl != null) c.synLink else c.ink3,
                textDecoration = if (issueUrl != null) TextDecoration.Underline else null,
            )
        },
        confirmButton = {
            TextButton(
                enabled = issueUrl != null,
                onClick = {
                    issueUrl?.let { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
                    onDismiss()
                },
            ) {
                Text("Go to Issue", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = c.ink2) }
        },
    )
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
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

/**
 * List-aware counterpart of [BugTextArea] for the steps field: a
 * `TextFieldState`-backed `BasicTextField` (see [stepsListInputTransformation]
 * for why) hand-styled to match [BugTextArea]'s `OutlinedTextField` chrome —
 * same 15dp-radius `surface` box with a `line`→`accent` border on focus, since
 * `BasicTextField` doesn't provide that shell itself.
 */
@Composable
private fun BugListTextArea(
    state: TextFieldState,
    placeholder: String,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(c.surface)
            .border(1.dp, if (isFocused) c.accent else c.line, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (state.text.isEmpty()) {
            Text(placeholder, fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 21.sp, color = c.ink3)
        }
        BasicTextField(
            state = state,
            inputTransformation = remember { stepsListInputTransformation() },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = minLines),
            textStyle = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 21.sp, color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        )
    }
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
            "Filed as a public GitHub issue on our open-source repo. Never sent to Google or a third party.",
            fontFamily = PlexSans, fontSize = 11.5.sp, lineHeight = 1.6.em, color = c.ink2,
        )
    }
}

@Composable
private fun SendReportButton(enabled: Boolean, sending: Boolean, sent: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val bg = when {
        sent -> c.green
        enabled || sending -> c.accent
        else -> c.surface3
    }
    val fg = if (enabled || sending || sent) c.accentInk else c.ink3
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
        if (sent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Report sent",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = fg,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
        } else {
            Text(
                if (sending) "Sending…" else "Send report",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = fg,
            )
        }
    }
}

private val ButtonShadowColor = Color(0x668A5A2B)

private fun deviceInfo(): DeviceInfo = DeviceInfo(
    app_version = BuildConfig.VERSION_NAME,
    build_number = BuildConfig.VERSION_CODE.toString(),
    android_version = Build.VERSION.RELEASE,
    device_model = Build.MODEL,
    locale = Locale.getDefault().toLanguageTag(),
)

private fun bugPayload(includeDeviceInfo: Boolean, includeErrorLog: Boolean, errorLogLines: List<String>?): List<BugPayloadRow> {
    val rows = mutableListOf<BugPayloadRow>()
    if (includeDeviceInfo) {
        val info = deviceInfo()
        rows += BugPayloadRow("app_version", info.app_version)
        rows += BugPayloadRow("build_number", info.build_number)
        rows += BugPayloadRow("android_version", info.android_version)
        rows += BugPayloadRow("device_model", info.device_model)
        rows += BugPayloadRow("locale", info.locale)
    }
    rows += BugPayloadRow("report_time", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
    if (includeErrorLog) {
        rows += when {
            errorLogLines == null -> BugPayloadRow("error_log", "couldn't capture (unavailable on this device)")
            errorLogLines.isEmpty() -> BugPayloadRow("error_log", "captured, but this session has no log lines yet")
            else -> {
                val n = errorLogLines.size
                BugPayloadRow("error_log", "$n line${if (n == 1) "" else "s"} captured from this session")
            }
        }
    }
    return rows
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
