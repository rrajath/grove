package com.rrajath.grove.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * A picked link target that carries an ID: the "Use the …'s ID?" dialog lets the
 * user commit either [withId] (resilient) or [withPlain]. [subject] is `"file"`
 * or `"heading"` and drives the dialog copy.
 */
internal data class LinkIdChoice(val withId: String, val withPlain: String, val subject: String)

/** What a link-picker confirmation resolves to: a link ready to splice, or an ID choice to ask about. */
internal sealed interface LinkPick {
    data class Direct(val link: String) : LinkPick
    data class AskId(val choice: LinkIdChoice) : LinkPick
}

/**
 * Turn the picked [file] (top level, [heading] null) or [heading] into a link
 * from [editingFile]. [selectedText] becomes the description; with nothing
 * selected the link names its target (the heading title, or the file's
 * `#+TITLE:` / base name). Shared by every editor that hosts the link picker.
 */
internal fun resolveLinkPick(
    editingFile: String,
    doc: OrgDocument,
    file: String,
    heading: OrgHeadline?,
    selectedText: String?,
): LinkPick {
    val desc = selectedText ?: if (heading != null) {
        heading.title
    } else {
        doc.preambleKeywords
            .firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
            ?.second?.takeIf { it.isNotBlank() }
            ?: file.substringAfterLast('/').removeSuffix(".org")
    }
    if (heading == null) {
        // File-level link. A file:-link is always relative to the editing
        // file's directory (just the name for a link into the same file).
        val relPath = relativeOrgPath(editingFile, file)
        val fileId = doc.fileId ?: return LinkPick.Direct(formatFileLink(relPath, desc))
        return LinkPick.AskId(
            LinkIdChoice(
                withId = formatHeadingLink(HeadingLinkTarget.ById(fileId), desc),
                withPlain = formatFileLink(relPath, desc),
                subject = "file",
            ),
        )
    }
    // Heading link. Drop the file: qualifier when the heading is in this note.
    val relPath = if (file == editingFile) null else relativeOrgPath(editingFile, file)
    val plain = formatHeadingLink(HeadingLinkTarget.ByName(heading.title, relPath), desc)
    if (heading.id == null && heading.customId == null) return LinkPick.Direct(plain)
    return LinkPick.AskId(
        LinkIdChoice(
            withId = formatHeadingLink(resilientHeadingTarget(heading.id, heading.customId, relPath), desc),
            withPlain = plain,
            subject = "heading",
        ),
    )
}

@Composable
internal fun LinkIdChoiceDialog(
    choice: LinkIdChoice,
    onUseId: () -> Unit,
    onUsePlain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.grove
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                "Use the ${choice.subject}'s ID?",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, color = c.ink,
            )
        },
        text = {
            Text(
                "This ${choice.subject} has an ID. An ID link keeps working if it is later " +
                    "renamed or moved to another file.",
                fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onUseId) {
                Text("Use ID", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onUsePlain) {
                Text(
                    if (choice.subject == "file") "Use file link" else "Use heading name",
                    color = c.ink2,
                )
            }
        },
    )
}
