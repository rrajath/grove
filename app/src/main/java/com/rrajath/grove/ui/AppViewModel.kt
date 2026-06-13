package com.rrajath.grove.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.settings.SyncMode
import com.rrajath.grove.settings.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val app: GroveApplication) : ViewModel() {

    private val settingsRepository: SettingsRepository = app.settingsRepository

    /** Null until the DataStore emits, so the UI can gate on first load. */
    val settings: StateFlow<GroveSettings?> = settingsRepository.settings
        .map { it as GroveSettings? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val savedSearches: StateFlow<List<SavedSearch>> = app.searchRepository.savedSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteSavedSearch(id: String) =
        viewModelScope.launch { app.searchRepository.deleteSearch(id) }

    fun setTheme(theme: ThemePreference) =
        viewModelScope.launch { settingsRepository.setTheme(theme) }

    fun setFontSize(fontSize: FontSizePreference) =
        viewModelScope.launch { settingsRepository.setFontSize(fontSize) }

    fun setDefaultNoteOpenMode(mode: NoteOpenMode) =
        viewModelScope.launch { settingsRepository.setDefaultNoteOpenMode(mode) }

    fun completeOnboarding() =
        viewModelScope.launch { settingsRepository.setOnboardingDone(true) }

    fun setVaultTreeUri(uri: String) =
        viewModelScope.launch { settingsRepository.setVaultTreeUri(uri) }

    fun setSyncMode(mode: SyncMode) =
        viewModelScope.launch { settingsRepository.setSyncMode(mode) }

    fun setPeriodicSyncMinutes(minutes: Int) =
        viewModelScope.launch { settingsRepository.setPeriodicSyncMinutes(minutes) }

    fun setTodoKeywords(config: String) =
        viewModelScope.launch { settingsRepository.setTodoKeywords(config) }

    fun setDefaultPriority(priority: Char?) =
        viewModelScope.launch { settingsRepository.setDefaultPriority(priority) }

    fun setAddIdToNewNotes(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setAddIdToNewNotes(enabled) }

    fun setAddCreatedToNewNotes(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setAddCreatedToNewNotes(enabled) }

    fun setCaptureNotification(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setCaptureNotification(enabled) }

    fun setOutlineToggle(toggle: OutlineToggle, enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setOutlineToggle(toggle, enabled) }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as GroveApplication
                return AppViewModel(app) as T
            }
        }
    }
}
