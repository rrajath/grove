package com.rrajath.grove.ui.util

/**
 * "{count} {singular}" for a count of 1, "{count} {plural}" otherwise. [plural]
 * defaults to the singular with an "s" appended; pass it explicitly for
 * irregular words.
 */
fun pluralCount(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
