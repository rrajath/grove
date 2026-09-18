package com.rrajath.grove.ui.vault

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Largest file, in lines, that may open as one note (the whole-file Read/Edit
 * view behind `Routes.FILE`). Above it the Outline's "View file" item is
 * disabled and the Roam auto-open never fires, so the single-field editor is
 * never handed a buffer big enough to stall the main thread; the user drills
 * into a heading instead, as before.
 *
 * The number is a first guess, not a measurement: tune it from Settings ›
 * Developer tools on a debug build (see [wholeFileLineLimitOverride]) and bake
 * the result in here once it is known where Read/Edit mode actually starts to lag.
 */
const val WHOLE_FILE_LINE_LIMIT = 1000

/**
 * Debug-only, in-memory override of [WHOLE_FILE_LINE_LIMIT], set from Settings ›
 * Developer tools (gated on `BuildConfig.DEBUG`). Never persisted: every process
 * start resets to [WHOLE_FILE_LINE_LIMIT], and no release code path writes to it.
 */
val wholeFileLineLimitOverride = MutableStateFlow(WHOLE_FILE_LINE_LIMIT)

/** True when a file of [lineCount] lines fits under [limit] and may open as one note. */
fun fitsWholeFileView(lineCount: Int, limit: Int): Boolean = lineCount <= limit
