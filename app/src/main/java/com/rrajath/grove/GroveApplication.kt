package com.rrajath.grove

import android.app.Application
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rrajath.grove.capture.TemplatesRepository
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.sync.SyncManager
import com.rrajath.grove.vault.FileStore
import com.rrajath.grove.vault.SafFileStore
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Composition root for app-wide singletons (manual DI; the app is small). */
class GroveApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val templatesRepository: TemplatesRepository by lazy { TemplatesRepository(this) }

    val database: GroveDatabase by lazy { GroveDatabase.build(this) }

    val syncManager: SyncManager by lazy { SyncManager(this, appScope, database) }

    /** The active vault file store, swapping whenever the configured tree URI changes. */
    val fileStore: StateFlow<FileStore?> by lazy {
        settingsRepository.settings
            .map { it.vaultTreeUri }
            .distinctUntilChanged()
            .map { uriString -> uriString?.let { SafFileStore(this, Uri.parse(it)) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    val vault: StateFlow<Vault?> by lazy {
        fileStore
            .map { store -> store?.let { Vault(it) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            fileStore.collect { syncManager.attach(it) }
        }
        appScope.launch {
            settingsRepository.settings
                .map { it.syncMode to it.periodicSyncMinutes }
                .distinctUntilChanged()
                .collect { (mode, minutes) -> syncManager.schedulePeriodic(mode, minutes) }
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
