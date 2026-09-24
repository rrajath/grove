package com.rrajath.grove.dailies

import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.settings.SettingsSource
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Opens one day's daily note without listing the dailies folder: the file name comes
 * from the Dailies settings, and existence plus the parsed document from a single
 * [Vault.open]. Process-wide, so the drawer can [warm] today's note as it opens and
 * DailiesViewModel can render it from [cached] on the screen's first frames, while
 * the full day index (prev/next skipping, date-picker dots) builds in the background.
 */
class DailyNoteLookup(
    private val vaultFlow: StateFlow<Vault?>,
    private val settings: SettingsSource,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    class Result(
        val vault: Vault,
        val date: LocalDate,
        val fileName: String,
        val exists: Boolean,
        val document: OrgDocument?,
    )

    @Volatile
    private var last: Result? = null

    /** The last resolved result for [date] on the current vault, if any. May be a few
     *  seconds old; callers treat it as provisional and let the index correct it. */
    fun cached(date: LocalDate): Result? =
        last?.takeIf { it.date == date && it.vault === vaultFlow.value }

    /** Resolve [date] in the background so a following [cached] hits. */
    fun warm(date: LocalDate = LocalDate.now()) {
        scope.launch { resolve(date) }
    }

    /** Null when there's no vault or the file couldn't be read: "don't know", so the
     *  caller waits for the index rather than showing a wrong empty day. */
    suspend fun resolve(date: LocalDate): Result? {
        val vault = vaultFlow.value ?: return null
        val s = settings.settings.first()
        val fileName = DailiesRepository(vault.fileStore())
            .resolveFileName(s.dailiesDirectory, s.dailiesFilenamePattern, date)
        val doc = try {
            withContext(io) { vault.open(fileName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return Result(vault, date, fileName, doc != null, doc).also { last = it }
    }
}
