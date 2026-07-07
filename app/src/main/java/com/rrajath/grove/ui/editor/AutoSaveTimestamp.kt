package com.rrajath.grove.ui.editor

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Formats an auto-save timestamp as 24h `HH:mm:ss`, e.g. "14:03:07". */
object AutoSaveTimestamp {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun format(time: LocalTime): String = time.format(FORMATTER)
}
