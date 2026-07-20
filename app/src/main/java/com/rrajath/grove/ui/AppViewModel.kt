package com.rrajath.grove.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.capture.PageTitleFetcher
import com.rrajath.grove.capture.ShareIntake
import com.rrajath.grove.data.FavoriteNote
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.settings.ChecklistStates
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.settings.SettingsSerialization
import com.rrajath.grove.settings.SyncMode
import com.rrajath.grove.settings.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

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

    val favorites: StateFlow<List<FavoriteNote>> = app.favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addFavorite(fileName: String, lineIndex: Int, title: String) =
        viewModelScope.launch { app.favoritesRepository.addFavorite(FavoriteNote(fileName, lineIndex, title)) }

    fun removeFavorite(fileName: String, lineIndex: Int) =
        viewModelScope.launch { app.favoritesRepository.removeFavorite(fileName, lineIndex) }

    fun setTheme(theme: ThemePreference) =
        viewModelScope.launch { settingsRepository.setTheme(theme) }

    fun setSyncAppIconWithTheme(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSyncAppIconWithTheme(enabled)
        if (enabled) toast("Restart the app for the icon change to take effect")
    }

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

    fun setShareTargetFile(fileName: String) =
        viewModelScope.launch { settingsRepository.setShareTargetFile(fileName.trim()) }

    /**
     * Route content shared into Grove (PRD §10) straight to the configured file:
     * a URL becomes a heading linking the fetched page title; long text becomes
     * an empty heading with the text as the body; short text becomes a heading.
     * Appended to the bottom of the target file (created if missing).
     */
    fun consumeSharedContent() {
        val payload = app.pendingShare.value ?: return
        app.pendingShare.value = null
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.vaultTreeUri == null) {
                toast("Set a sync folder before sharing to Grove")
                return@launch
            }
            // On a cold start the vault may still be initializing; await it.
            val vault = app.vault.filterNotNull().first()
            val resolvedTitle =
                if (payload.url.isNotEmpty()) PageTitleFetcher.fetch(payload.url) else null
            val note = ShareIntake.composeNote(payload, resolvedTitle)
            val target = settings.shareTargetFile.trim().ifBlank { GroveSettings.DEFAULT_SHARE_TARGET }
            val fileName = if (target.endsWith(".org")) target else "$target.org"
            if (vault.open(fileName) == null) vault.createNotebook(fileName)
            val doc = vault.open(fileName)
            if (doc == null) {
                toast("Couldn't open $fileName")
                return@launch
            }
            val (newText, _) = OrgMutations.newTopLevel(
                doc,
                note.heading,
                OrgMutations.NewNoteOptions(
                    id = if (settings.addIdToNewNotes) UUID.randomUUID().toString() else null,
                    createdAt = if (settings.addCreatedToNewNotes) LocalDateTime.now() else null,
                    body = note.body,
                ),
            )
            vault.save(fileName, newText)
            app.syncManager.requestSync("shared note")
            toast("Saved to $fileName")
        }
    }

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun setOutlineToggle(toggle: OutlineToggle, enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setOutlineToggle(toggle, enabled) }

    fun setShowHeaderTags(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setShowHeaderTags(enabled) }

    fun setShowPropertyDrawers(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setShowPropertyDrawers(enabled) }

    fun setNotebookDisplayNameMode(mode: NotebookDisplayNameMode) =
        viewModelScope.launch { settingsRepository.setNotebookDisplayNameMode(mode) }

    fun setChecklistStates(states: ChecklistStates) =
        viewModelScope.launch { settingsRepository.setChecklistStates(states) }

    /** Write the current preferences as a JSON document to the user-picked [uri]. */
    fun exportSettings(uri: android.net.Uri) = viewModelScope.launch {
        val current = settingsRepository.settings.first()
        val text = SettingsSerialization.export(current)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("no output stream")
            }.isSuccess
        }
        toast(if (ok) "Settings exported" else "Couldn't write settings file")
    }

    /** Read a JSON document from [uri] and apply the portable preferences within. */
    fun importSettings(uri: android.net.Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        if (text == null) {
            toast("Couldn't read settings file")
            return@launch
        }
        val current = settingsRepository.settings.first()
        val imported = runCatching { SettingsSerialization.import(text, current) }.getOrNull()
        if (imported == null) {
            toast("Not a valid Grove settings file")
            return@launch
        }
        settingsRepository.applyImported(imported)
        toast("Settings imported")
    }

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
