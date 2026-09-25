package com.rrajath.grove.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * Whether the keyboard currently takes up any space, from the live IME inset
 * (not `isImeVisible`, which can stick after a gesture-dismiss). Read through
 * [derivedStateOf] so callers recompose only when the answer flips, not on
 * every frame of the keyboard's show/hide animation.
 */
@Composable
fun rememberImeVisible(): State<Boolean> {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    return remember(density, ime) { derivedStateOf { ime.getBottom(density) > 0 } }
}
