package com.rrajath.grove

import android.app.Application
import com.rrajath.grove.settings.SettingsRepository

/** Composition root for app-wide singletons (manual DI; the app is small). */
class GroveApplication : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
