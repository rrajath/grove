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
import com.rrajath.grove.icon.AppIconManager
import com.rrajath.grove.icon.NotificationAppearance
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.reminders.ReminderDigestScheduler
import com.rrajath.grove.reminders.ReminderReconciler
import com.rrajath.grove.search.SearchRepository
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.settings.ThemePreference
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

    /**
     * Tint for notification small icons, following "Sync App Icon with Theme".
     * Eagerly shared so notification builders running in a receiver can read
     * `.value` without suspending — see `icon/NotificationAppearance.kt`.
     *
     * [onCreate] touches this so the DataStore read starts at process start
     * rather than on the first notification: `stateIn` hands out its seed until
     * the upstream first emits, so a lazily-created flow would tint the very
     * first notification with the default mark regardless of the setting.
     */
    val notificationMarkColor: StateFlow<Int> by lazy {
        settingsRepository.settings
            .map { AppIconManager.markColor(it.syncAppIconWithTheme, it.theme) }
            .distinctUntilChanged()
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                AppIconManager.markColor(enabled = false, theme = ThemePreference.LIGHT),
            )
    }

    val reminderReconciler: ReminderReconciler by lazy {
        ReminderReconciler(this, database.reminderDao())
    }

    val syncManager: SyncManager by lazy {
        SyncManager(
            this, appScope, database,
            keywords = { keywords.value },
            onNotebookIndexed = { fileName, doc ->
                val settings = settingsRepository.settings.first()
                reminderReconciler.reconcileFile(fileName, doc, settings.defaultReminderTime, settings.remindersEnabled)
            },
            onSyncCompleted = { reminderReconciler.catchUpOverdue() },
        )
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

        // Warm the notification tint before anything can post a notification.
        notificationMarkColor

        appScope.launch {
            // Notifications bake in their color at post time, so a theme switch
            // would otherwise leave everything already in the shade wearing the
            // previous theme's mark while the launcher icon had moved on.
            // drop(1): the first emission is the value everything was posted
            // with, not a change.
            notificationMarkColor
                .drop(1)
                .collect { NotificationAppearance.retintActive(this@GroveApplication) }
        }

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

        // App-start safety net: fire anything overdue that was missed while the
        // process was dead, and pick up reminders that were only waiting on a
        // permission grant. DB-only — doesn't need the vault to be ready.
        appScope.launch {
            reminderReconciler.catchUpOverdue()
            reminderReconciler.reconcilePending()
        }

        appScope.launch {
            // "Enable reminders" toggled off cancels everything immediately rather
            // than waiting for the next per-file reconcile; toggled back on (or the
            // default reminder time changing, which affects date-only stamps)
            // re-scans every already-indexed notebook.
            settingsRepository.settings
                .map { it.remindersEnabled to it.defaultReminderTime }
                .distinctUntilChanged()
                .drop(1)
                .collect { (enabled, defaultTime) ->
                    if (!enabled) {
                        reminderReconciler.disableAll()
                    } else {
                        val vault = vault.value ?: return@collect
                        val documents = database.indexDao().notebooks()
                            .mapNotNull { nb -> vault.open(nb.fileName)?.let { nb.fileName to it } }
                            .toMap()
                        reminderReconciler.reconcileAll(documents, defaultTime, enabled)
                    }
                }
        }

        appScope.launch {
            // The daily digest ("You have X tasks due today") is a single alarm
            // independent of the per-heading ones above — no drop(1): the very
            // first emission (current settings on cold start) must (re)arm it too,
            // since AlarmManager alarms don't survive a process being killed.
            settingsRepository.settings
                .map { it.remindersEnabled to it.defaultReminderTime }
                .distinctUntilChanged()
                .collect { (enabled, defaultTime) ->
                    if (enabled) ReminderDigestScheduler.scheduleNext(this@GroveApplication, defaultTime)
                    else ReminderDigestScheduler.cancel(this@GroveApplication)
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
