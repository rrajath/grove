package com.rrajath.grove.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.runBlocking

/**
 * The bridge between the formatting toolbar's link button and the pure
 * [insertLinkFromToolbar] helper: it reads the system clipboard so a URL the
 * user already copied becomes the link target without any typing or trimming.
 *
 * Reading the clipboard shows the Android 13+ "pasted from clipboard" system
 * toast, so [pasteableUrlOnClipboard] is called only on an explicit
 * paste-shaped action — the link button with text selected — never on every
 * keystroke or recomposition. When nothing is selected the button just drops in
 * the neutral template, so there is no reason to look and no toast.
 */

/**
 * The system clipboard's text if it currently looks like a URL that could drop
 * straight into a link ([looksLikePasteableUrl]), else null.
 *
 * `getClipEntry()` is a synchronous Binder call with no real suspension point on
 * Android, so `runBlocking` just reads it inline rather than opening an async
 * gap.
 */
internal fun pasteableUrlOnClipboard(clipboard: Clipboard): String? {
    val text = runBlocking { clipboard.getClipEntry() }?.clipData?.let { data ->
        if (data.itemCount > 0) data.getItemAt(0)?.text?.toString() else null
    } ?: return null
    return text.trim().takeIf(::looksLikePasteableUrl)
}

/**
 * Run the link button's tap against this field. Consults the clipboard for a
 * ready-to-use URL only when there is a selection to turn into a link; a
 * collapsed selection goes straight to the neutral template.
 */
internal fun TextFieldState.applyToolbarLink(clipboard: Clipboard) {
    val url = if (selection.collapsed) null else pasteableUrlOnClipboard(clipboard)
    applyEdit { insertLinkFromToolbar(it, url) }
}
