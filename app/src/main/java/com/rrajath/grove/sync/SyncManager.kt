package com.rrajath.grove.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.data.RoomNoteIndex
import com.rrajath.grove.data.SyncLogEntity
import com.rrajath.grove.settings.SyncMode
import com.rrajath.grove.vault.FileStore
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Conflict resolution choices (PRD §6.4). */
enum class ConflictResolution { KEEP_CURRENT, KEEP_CONFLICT_COPY, KEEP_BOTH }

/**
 * Android-side orchestration around [SyncEngine]: triggers (manual, lifecycle,
 * periodic work, continuous polling), sync log, conflict notification and
 * resolution.
 */
class SyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val database: GroveDatabase,
    private val keywords: () -> com.rrajath.grove.org.OrgKeywords = { com.rrajath.grove.org.OrgKeywords.DEFAULT },
) {
    private val mutex = Mutex()
    private var engine: SyncEngine? = null
    private var store: FileStore? = null

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    private val _lastResult = MutableStateFlow<SyncResult?>(null)
    val lastResult: StateFlow<SyncResult?> = _lastResult

    private var pollJob: Job? = null
    private var stateJob: Job? = null

    fun attach(store: FileStore?) {
        this.store = store
        stateJob?.cancel()
        engine = store?.let {
            SyncEngine(it, RoomNoteIndex(database.indexDao(), keywords)) { System.currentTimeMillis() }
        }
        engine?.let { e ->
            stateJob = scope.launch { e.state.collect { _state.value = it } }
        }
        if (store != null) requestSync("folder configured")
    }

    fun requestSync(reason: String) {
        val engine = engine ?: return
        scope.launch {
            mutex.withLock {
                log("sync started ($reason)")
                val result = engine.sync(log = { msg -> log(msg) })
                if (result != null) {
                    _lastResult.value = result
                    log("sync done: ${result.pulled.size} pulled, ${result.conflicts.size} conflicts")
                    if (result.conflicts.isNotEmpty()) notifyConflicts(result.conflicts.keys)
                }
            }
        }
    }

    // --- conflict resolution ---

    suspend fun conflictTexts(baseName: String): Pair<String, String>? {
        val store = store ?: return null
        val copy = database.indexDao().notebooks()
            .firstOrNull { it.fileName == baseName }?.conflictFileName ?: return null
        return store.read(baseName) to store.read(copy)
    }

    suspend fun resolveConflict(baseName: String, resolution: ConflictResolution) {
        val store = store ?: return
        val copyName = database.indexDao().notebooks()
            .firstOrNull { it.fileName == baseName }?.conflictFileName ?: return
        when (resolution) {
            ConflictResolution.KEEP_CURRENT -> Unit
            ConflictResolution.KEEP_CONFLICT_COPY ->
                store.write(baseName, store.read(copyName))

            ConflictResolution.KEEP_BOTH ->
                store.write(
                    baseName,
                    ConflictResolver.keepBoth(
                        mainText = store.read(baseName),
                        conflictText = store.read(copyName),
                        label = SyncConflicts.label(copyName),
                    ),
                )
        }
        store.delete(copyName)
        log("conflict on $baseName resolved: ${resolution.name.lowercase()}")
        requestSync("conflict resolved")
    }

    /** Force Load: drop the cached index for this notebook and re-pull from disk. */
    suspend fun forceReload(fileName: String) {
        database.indexDao().removeNotebook(fileName)
        requestSync("force reload $fileName")
    }

    // --- triggers ---

    fun onAppForeground(mode: SyncMode) {
        if (mode != SyncMode.MANUAL) requestSync("app foregrounded")
        if (mode == SyncMode.CONTINUOUS) startPolling()
    }

    fun onAppBackground(mode: SyncMode) {
        stopPolling()
        if (mode != SyncMode.MANUAL) requestSync("app backgrounded")
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                requestSync("change poll")
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun schedulePeriodic(mode: SyncMode, minutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (mode == SyncMode.PERIODIC) {
            // WorkManager floors periodic intervals at 15 minutes.
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    // --- log + notification ---

    private fun log(message: String) {
        scope.launch {
            database.syncLogDao().insert(
                SyncLogEntity(timestamp = System.currentTimeMillis(), level = "info", message = message)
            )
            database.syncLogDao().trim()
        }
    }

    private fun notifyConflicts(names: Set<String>) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sync conflicts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context, 0, launch ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Sync conflict")
            .setContentText("${names.joinToString()} changed on two devices")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "grove-periodic-sync"
        private const val CHANNEL_ID = "sync-conflicts"
        private const val NOTIFICATION_ID = 100
        private const val POLL_INTERVAL_MS = 10_000L
    }
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? com.rrajath.grove.GroveApplication ?: return Result.failure()
        app.syncManager.requestSync("periodic work")
        return Result.success()
    }
}
