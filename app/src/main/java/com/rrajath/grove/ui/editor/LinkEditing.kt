package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.rrajath.grove.org.TextEdit

/**
 * Pure helpers behind the formatting toolbar's link button. Everything here
 * operates on plain strings / [TextFieldValue] so it stays JVM-testable without
 * Compose runtime types.
 *
 * Toolbar link button:
 *  - tap, no selection -> [insertLinkTemplate] (`EditorToolbar.kt`)
 *  - tap, with selection -> [insertHttpsLink]
 *  - long-press -> the file/heading picker (main note editor only)
 * [insertLinkFromToolbar] is the tap dispatcher; the paste clean-up below runs
 * after a URL insert.
 */

/**
 * The toolbar link button's tap action: an `[[https://]]` scaffold when text is
 * selected (that text becomes the description), or the neutral
 * `[[link][description]]` template when nothing is selected.
 */
internal fun insertLinkFromToolbar(value: TextFieldValue): TextFieldValue =
    if (value.selection.collapsed) insertLinkTemplate(value) else insertHttpsLink(value)

/**
 * Insert an `[[https://][description]]` link and park the cursor immediately
 * after `https://`, ready for the URL to be typed or pasted. A non-empty
 * selection becomes the description; a collapsed selection gets the literal
 * `description` placeholder (left un-selected, since the cursor belongs on the
 * scheme).
 */
internal fun insertHttpsLink(value: TextFieldValue): TextFieldValue {
    val sel = value.selection
    val text = value.text
    val description = if (sel.collapsed) "description" else text.substring(sel.min, sel.max)
    val scheme = "https://"
    val template = "[[$scheme][$description]]"
    val cursor = sel.min + "[[".length + scheme.length
    return TextFieldValue(
        text.substring(0, sel.min) + template + text.substring(sel.max),
        TextRange(cursor),
    )
}

private val DOUBLED_SCHEME = Regex("""\[\[(https?://)(https?://)""")

/**
 * After [insertHttpsLink] leaves the cursor on `[[https://|`, pasting a URL that
 * already carries a scheme produces `[[https://https://example.com`. Collapse
 * the leading (pre-inserted) scheme so the pasted one wins — a pasted `http://`
 * link keeps its `http://`. Returns null when there's no doubled scheme.
 *
 * Wired into `orgInputTransformation`, so it runs for real user input (including
 * paste) but never for programmatic buffer writes.
 */
fun collapseDoubledScheme(text: String, cursor: Int): TextEdit? {
    val m = DOUBLED_SCHEME.find(text) ?: return null
    val firstScheme = m.groupValues[1]
    val removeStart = m.range.first + "[[".length
    val removeEnd = removeStart + firstScheme.length
    val newCursor = when {
        cursor <= removeStart -> cursor
        cursor >= removeEnd -> cursor - firstScheme.length
        else -> removeStart
    }
    return TextEdit(text.substring(0, removeStart) + text.substring(removeEnd), newCursor)
}

/**
 * A path from [fromFile]'s directory to [toFile], both vault-relative
 * (`work/journal.org`), the way Emacs org resolves `[[file:…]]` — against the
 * linking file's own directory. Mirrors `OrgLinkParser.resolvePath` in reverse.
 */
fun relativeOrgPath(fromFile: String, toFile: String): String {
    val fromDir = fromFile.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
    val toSegs = toFile.split('/').filter { it.isNotEmpty() }
    val toDir = toSegs.dropLast(1)
    val toName = toSegs.lastOrNull() ?: return toFile

    var shared = 0
    while (shared < fromDir.size && shared < toDir.size && fromDir[shared] == toDir[shared]) shared++

    val ups = List(fromDir.size - shared) { ".." }
    val downs = toDir.drop(shared)
    return (ups + downs + toName).joinToString("/")
}

/** What a picked heading should resolve to, once the id-vs-name choice is made. */
sealed interface HeadingLinkTarget {
    /** `[[id:UUID]]` — resolves vault-wide, survives file moves. */
    data class ById(val id: String) : HeadingLinkTarget

    /** `[[#custom-id]]` (same file) or `[[file:rel/path.org::#custom-id]]`. */
    data class ByCustomId(val customId: String, val relPath: String?) : HeadingLinkTarget

    /** `[[*Heading]]` (same file) or `[[file:rel/path.org::*Heading]]`. */
    data class ByName(val heading: String, val relPath: String?) : HeadingLinkTarget
}

/**
 * The resilient target for a heading that carries an `:ID:` and/or a
 * `:CUSTOM_ID:`, preferring `:ID:`. At least one of [id] / [customId] must be
 * non-null.
 */
fun resilientHeadingTarget(id: String?, customId: String?, relPath: String?): HeadingLinkTarget =
    when {
        id != null -> HeadingLinkTarget.ById(id)
        customId != null -> HeadingLinkTarget.ByCustomId(customId, relPath)
        else -> error("resilientHeadingTarget needs an id or a custom id")
    }

/** Render a [HeadingLinkTarget] as an org link, with an optional description. */
fun formatHeadingLink(target: HeadingLinkTarget, description: String?): String {
    val body = when (target) {
        is HeadingLinkTarget.ById -> "id:${target.id}"
        is HeadingLinkTarget.ByCustomId ->
            if (target.relPath == null) "#${target.customId}"
            else "file:${target.relPath}::#${target.customId}"
        is HeadingLinkTarget.ByName ->
            if (target.relPath == null) "*${target.heading}"
            else "file:${target.relPath}::*${target.heading}"
    }
    return if (description.isNullOrEmpty()) "[[$body]]" else "[[$body][$description]]"
}

/**
 * Render a whole-file link, `[[file:rel/path.org]]`, with an optional
 * description. [relPath] is the target file made relative to the linking file's
 * directory (see [relativeOrgPath]); for a link to the file being edited it is
 * just that file's own name.
 */
fun formatFileLink(relPath: String, description: String?): String =
    if (description.isNullOrEmpty()) "[[file:$relPath]]" else "[[file:$relPath][$description]]"

/** Splice a finished link string over the current selection, cursor after it. */
internal fun insertHeadingLink(value: TextFieldValue, linkText: String): TextFieldValue {
    val sel = value.selection
    val text = value.text
    return TextFieldValue(
        text.substring(0, sel.min) + linkText + text.substring(sel.max),
        TextRange(sel.min + linkText.length),
    )
}
