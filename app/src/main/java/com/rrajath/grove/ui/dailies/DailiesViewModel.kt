package com.rrajath.grove.ui.dailies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.AppDispatchers
import com.rrajath.grove.dailies.DailiesRepository
import com.rrajath.grove.settings.SettingsSource
import com.rrajath.grove.ui.vault.factory
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DailiesNavState(
    val date: LocalDate,
    val fileName: String,
    val exists: Boolean,
    val isToday: Boolean,
    val nextDate: LocalDate,
    val previousDate: LocalDate,
    val existingDates: Set<LocalDate>,
)

/**
 * Thin StateFlow-producing wrapper over [DailiesRepository] and
 * [SettingsSource]: resolves the file name / existence / navigation dates for
 * a given day so `DailyNoteScreen` (Task 15/16) can render them. No dedicated
 * unit test here — [DailiesRepository] is fully covered by Task 8's tests.
 */
class DailiesViewModel(
    private val vaultFlow: StateFlow<Vault?>,
    private val settings: SettingsSource,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _state = MutableStateFlow<DailiesNavState?>(null)
    val state: StateFlow<DailiesNavState?> = _state

    private var lastRepo: DailiesRepository? = null
    private var lastSettings: com.rrajath.grove.settings.GroveSettings? = null

    /** Delegates to the [DailiesRepository]/settings snapshot [load] last resolved, so the
     *  empty-day "start typing" affordance can seed the header template without this screen
     *  needing its own [Vault] access. */
    fun expandedHeaderFor(date: LocalDate): com.rrajath.grove.capture.ExpandedTemplate {
        val repo = lastRepo ?: return com.rrajath.grove.capture.ExpandedTemplate("", 0)
        val template = lastSettings?.dailiesHeaderTemplate ?: return com.rrajath.grove.capture.ExpandedTemplate("", 0)
        return repo.expandHeaderTemplate(template, date)
    }

    fun load(date: LocalDate) {
        viewModelScope.launch {
            val vault = vaultFlow.value ?: run { _state.value = null; return@launch }
            val s = settings.settings.first()
            val repo = DailiesRepository(vault.fileStore())
            lastRepo = repo
            lastSettings = s
            withContext(dispatchers.default) {
                val fileName = repo.resolveFileName(s.dailiesDirectory, s.dailiesFilenamePattern, date)
                val exists = repo.existsForDate(s.dailiesDirectory, s.dailiesFilenamePattern, date)
                val existing = repo.existingDates(s.dailiesDirectory, s.dailiesFilenamePattern).toSet()
                val previous = repo.previousExistingDate(s.dailiesDirectory, s.dailiesFilenamePattern, date)
                DailiesNavState(
                    date = date,
                    fileName = fileName,
                    exists = exists,
                    isToday = date == LocalDate.now(),
                    nextDate = date.plusDays(1),
                    previousDate = previous,
                    existingDates = existing,
                )
            }.let { _state.value = it }
        }
    }

    companion object {
        val Factory = factory {
            DailiesViewModel(it.vault, it.settingsRepository, it.dispatchers)
        }
    }
}
