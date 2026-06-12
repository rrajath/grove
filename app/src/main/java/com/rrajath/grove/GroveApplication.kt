package com.rrajath.grove

import android.app.Application
import android.net.Uri
import com.rrajath.grove.capture.TemplatesRepository
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.vault.SafFileStore
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Composition root for app-wide singletons (manual DI; the app is small). */
class GroveApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val templatesRepository: TemplatesRepository by lazy { TemplatesRepository(this) }

    /** The active vault, swapping whenever the configured tree URI changes. */
    val vault: StateFlow<Vault?> by lazy {
        settingsRepository.settings
            .map { it.vaultTreeUri }
            .distinctUntilChanged()
            .map { uriString ->
                uriString?.let { Vault(SafFileStore(this, Uri.parse(it))) }
            }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }
}
