package com.rrajath.grove.ui.editor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.rrajath.grove.org.LineEditing
import com.rrajath.grove.org.TextEdit

/**
 * Org-aware typing behaviour shared by the note editor and quick capture:
 * continues lists on Enter and capitalizes the first letter typed into a
 * heading.
 *
 * An [InputTransformation] runs only for genuine user input (soft/hard
 * keyboard, paste, accessibility). Programmatic [TextFieldState.edit] writes
 * bypass it, so restoring a buffer — an initial load, or a metadata-sheet
 * rewrite — can never re-trigger list continuation on text that already has it.
 */
val OrgInputTransformation = InputTransformation {
    val old = originalText.toString()
    val typed = toString()
    val cursor = selection.start

    val afterList = LineEditing.continueListOnEnter(old, typed, cursor) ?: TextEdit(typed, cursor)
    val result = LineEditing.capitalizeHeadingOnType(old, afterList.text, afterList.cursor)
        ?: afterList

    if (result.text != typed || result.cursor != cursor) {
        replace(0, length, result.text)
        selection = TextRange(result.cursor)
    }
}

/**
 * Applies one of the pure toolbar editing helpers to this [TextFieldState].
 *
 * The helpers stay expressed in [TextFieldValue] terms so they remain plain
 * JVM-testable functions with no [TextFieldState] dependency; this bridges
 * their result back into the field. A [transform] returning null means "no
 * applicable edit here" and leaves the field untouched.
 */
fun TextFieldState.applyEdit(transform: (TextFieldValue) -> TextFieldValue?) {
    val before = TextFieldValue(text.toString(), selection)
    val after = transform(before) ?: return
    edit {
        replace(0, length, after.text)
        selection = after.selection
    }
}
