package com.rrajath.grove.ui.editor

/** Counts edits and fires every [threshold]th one, so callers can autosave periodically. */
class KeystrokeCounter(private val threshold: Int = 20) {
    private var count = 0

    /** Record one edit; returns true exactly every [threshold]th call (and resets). */
    fun tick(): Boolean {
        count++
        if (count < threshold) return false
        count = 0
        return true
    }
}
