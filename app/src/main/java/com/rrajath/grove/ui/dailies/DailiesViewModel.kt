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

    fun load(date: LocalDate) {
        viewModelScope.launch {
            val vault = vaultFlow.value ?: run { _state.value = null; return@launch }
            val s = settings.settings.first()
            val repo = DailiesRepository(vault.fileStore())
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
