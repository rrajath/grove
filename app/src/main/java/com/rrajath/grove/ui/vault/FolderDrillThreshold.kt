package com.rrajath.grove.ui.vault

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug-only, in-memory override of [FOLDER_DRILL_THRESHOLD] — the recursive
 * `.org` count above which a folder row opens the drill-down view instead of
 * expanding in place.
 *
 * Set from Settings › Developer tools (a screen gated on `BuildConfig.DEBUG`) so
 * the drill-down can be exercised without creating 20+ files. Never persisted:
 * every process start resets to [FOLDER_DRILL_THRESHOLD], and no release code
 * path ever writes to it.
 */
val folderDrillThresholdOverride = MutableStateFlow(FOLDER_DRILL_THRESHOLD)
