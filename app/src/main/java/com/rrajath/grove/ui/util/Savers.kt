package com.rrajath.grove.ui.util

import androidx.compose.runtime.saveable.listSaver

/**
 * Persist a set of line indices (collapsed headings) across navigation and
 * process death. A plain [Set] isn't [rememberSaveable][androidx.compose.runtime.saveable.rememberSaveable]
 * by default. Shared by the outline and read views.
 */
val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

/**
 * Persist a set of string keys (e.g. the ids of expanded Tips & Tricks rows)
 * across navigation and process death. A plain [Set] isn't
 * [rememberSaveable][androidx.compose.runtime.saveable.rememberSaveable] by default.
 */
val StringSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
