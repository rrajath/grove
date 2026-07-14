package com.rrajath.grove

import android.app.Application
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rrajath.grove.capture.SharedPayload
import com.rrajath.grove.capture.ShortcutSyncer
import com.rrajath.grove.capture.TemplatesRepository
import com.rrajath.grove.data.FavoritesRepository
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.search.SearchRepository
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.sync.SyncManager
import com.rrajath.grove.vault.FileStore
import com.rrajath.grove.widget.CaptureNotification
import com.rrajath.grove.vault.SafFileStore
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Composition root for app-wide singletons (manual DI; the app is small). */
class GroveApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val templatesRepository: TemplatesRepository by lazy { TemplatesRepository(this) }

    val searchRepository: SearchRepository by lazy { SearchRepository(this) }

    val favoritesRepository: FavoritesRepository by lazy { FavoritesRepository(this) }

    /**
     * Content shared into the app (PRD §10). Observed by the UI so it can be
     * routed into the configured file even when the app was already running.
     */
    val pendingShare = kotlinx.coroutines.flow.MutableStateFlow<SharedPayload?>(null)

    val database: GroveDatabase by lazy { GroveDatabase.build(this) }

    /** Current TODO keyword config; parser consumers read this. */
    val keywords: StateFlow<OrgKeywords> by lazy {
        settingsRepository.settings
            .map { it.todoKeywords }
            .distinctUntilChanged()
            .map { OrgKeywords.parse(it) }
            .stateIn(appScope, SharingStarted.Eagerly, OrgKeywords.DEFAULT)
    }

    val syncManager: SyncManager by lazy {
        SyncManager(this, appScope, database) { keywords.value }
    }

    /** The active vault file store, swapping whenever the configured tree URI changes. */
    val fileStore: StateFlow<FileStore?> by lazy {
        settingsRepository.settings
            .map { it.vaultTreeUri }
            .distinctUntilChanged()
            .map { uriString -> uriString?.let { SafFileStore(this, Uri.parse(it)) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    val vault: StateFlow<Vault?> by lazy {
        combine(fileStore, keywords) { store, kw -> store?.let { Vault(it, kw) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            fileStore.collect { syncManager.attach(it) }
        }
        appScope.launch {
            // Keyword config changes how files parse — rebuild the index.
            keywords.drop(1).collect {
                database.indexDao().clearAll()
                syncManager.requestSync("keyword config changed")
            }
        }
        appScope.launch {
            settingsRepository.settings
                .map { it.syncMode to it.periodicSyncMinutes }
                .distinctUntilChanged()
                .collect { (mode, minutes) -> syncManager.schedulePeriodic(mode, minutes) }
        }

        appScope.launch {
            settingsRepository.settings
                .map { it.captureNotification }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) CaptureNotification.show(this@GroveApplication)
                    else CaptureNotification.hide(this@GroveApplication)
                }
        }

        appScope.launch {
            // Long-press launcher shortcuts mirror the configured capture
            // templates, so add/edit/delete/reorder in Settings is reflected
            // immediately (including DataStore's initial emission on cold start).
            // Shortcut icon colors follow the same theme + sync-with-icon gate
            // as the launcher icon itself (AppIconManager), so both stay in sync.
            combine(
                templatesRepository.templates,
                settingsRepository.settings
                    .map { it.theme to it.syncAppIconWithTheme }
                    .distinctUntilChanged(),
            ) { templates, (theme, iconThemed) -> Triple(templates, theme, iconThemed) }
                .distinctUntilChanged()
                .collect { (templates, theme, iconThemed) ->
                    ShortcutSyncer.sync(this@GroveApplication, templates, theme, iconThemed)
                }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    syncManager.onAppForeground(settingsRepository.settings.first().syncMode)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                appScope.launch {
                    syncManager.onAppBackground(settingsRepository.settings.first().syncMode)
                }
            }
        })
    }
}
