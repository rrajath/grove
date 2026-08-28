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
import com.rrajath.grove.vault.FileEntry
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
    /** Notified right after each notebook is (re)indexed during a sync (see [RoomNoteIndex]). */
    private val onNotebookIndexed: suspend (fileName: String, doc: com.rrajath.grove.org.OrgDocument) -> Unit = { _, _ -> },
    /** Notified after a sync completes (successfully or not), for cheap DB-only catch-up passes. */
    private val onSyncCompleted: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private var engine: SyncEngine? = null
    private var store: FileStore? = null

    // All full-sync triggers funnel through here so overlapping ones coalesce
    // into a single pass instead of running back to back (PERFORMANCE_AUDIT
    // 2026-08-27 #3). Still takes [mutex] so it can't interleave with
    // [requestReindex] / [clearAndResync].
    private val coalescer = SyncCoalescer(scope) { reason ->
        mutex.withLock { runSyncPass(reason) }
    }

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
            SyncEngine(it, RoomNoteIndex(database, keywords, onNotebookIndexed)) { System.currentTimeMillis() }
        }
        engine?.let { e ->
            stateJob = scope.launch { e.state.collect { _state.value = it } }
        }
        if (store != null) requestSync("folder configured")
    }

    fun requestSync(reason: String) {
        if (engine == null) return
        coalescer.request(reason)
    }

    /** One full sync pass. Serialized by [coalescer] (and [mutex]); never run concurrently. */
    private suspend fun runSyncPass(reason: String) {
        val engine = engine ?: return
        log("sync started ($reason)")
        val result = engine.sync(log = { msg -> log(msg) })
        if (result != null) {
            _lastResult.value = result
            log("sync done: ${result.pulled.size} pulled, ${result.conflicts.size} conflicts")
            if (result.conflicts.isNotEmpty()) notifyConflicts(result.conflicts.keys)
        }
        database.syncLogDao().trim()
        onSyncCompleted()
    }

    /**
     * Reindex a single file the caller just wrote to the vault, straight into
     * Room, skipping the full SAF directory list + per-file revision diff that
     * [requestSync] runs. For the local single-note save path (the editor, and
     * any other in-app edit that already knows exactly which one file changed):
     * a full [requestSync] there pays O(vault size) to rediscover the one file
     * it was just handed (PERFORMANCE_AUDIT_2026-08-27 #1).
     *
     * Runs under the same [mutex] as [requestSync] so it can't interleave with
     * an in-flight full sync's delete+reinsert of the same rows, and still fires
     * the sync log + [onSyncCompleted] catch-up (overdue reminders, widget
     * refresh) a full pass would. Unlike [GroveApplication.reindexNow] (widget
     * path, deliberately mutex-free so it works before [attach]), this is only
     * safe to call once a store is attached.
     */
    fun requestReindex(fileName: String, text: String, reason: String) {
        val engine = engine ?: return
        scope.launch {
            mutex.withLock {
                log("reindex started ($reason): $fileName")
                try {
                    engine.reindexOne(fileName, text, database.indexDao().conflictFileNameFor(fileName))
                    log("reindexed $fileName")
                } catch (e: Exception) {
                    log("reindex failed for $fileName: ${e.message}")
                }
                database.syncLogDao().trim()
                onSyncCompleted()
            }
        }
    }

    /**
     * Wipe the index and rebuild it from scratch (todo-keyword config changed,
     * which affects how every file parses). Clearing under the same mutex as
     * [requestSync] keeps it from racing an in-flight sync's [SyncEngine.sync],
     * which would otherwise see a half-cleared table mid-read.
     */
    fun clearAndResync(reason: String) {
        val engine = engine ?: return
        scope.launch {
            mutex.withLock {
                database.indexDao().clearAll()
                log("sync started ($reason)")
                val result = engine.sync(log = { msg -> log(msg) })
                if (result != null) {
                    _lastResult.value = result
                    log("sync done: ${result.pulled.size} pulled, ${result.conflicts.size} conflicts")
                    if (result.conflicts.isNotEmpty()) notifyConflicts(result.conflicts.keys)
                }
                database.syncLogDao().trim()
                onSyncCompleted()
            }
        }
    }

    // --- conflict resolution ---

    suspend fun conflictTexts(baseName: String): Pair<String, String>? {
        val store = store ?: return null
        val copy = database.indexDao().conflictFileNameFor(baseName) ?: return null
        return store.read(baseName) to store.read(copy)
    }

    /**
     * Applies [resolution] to [baseName]'s pending conflict copy. Returns false
     * (and touches nothing) if there is no store attached or the conflict copy
     * has already disappeared from the index by the time this runs (e.g. a
     * background sync or Syncthing itself cleared it while the picker was open).
     * Callers MUST check this rather than assume the resolution was applied,
     * otherwise a no-op reads to the user as "Keep both" silently doing nothing
     * (indistinguishable from "kept current").
     */
    suspend fun resolveConflict(baseName: String, resolution: ConflictResolution): Boolean {
        val store = store ?: return false
        val copyName = database.indexDao().conflictFileNameFor(baseName) ?: return false
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
                    ),
                )
        }
        store.delete(copyName)
        log("conflict on $baseName resolved: ${resolution.name.lowercase()}")
        requestSync("conflict resolved")
        return true
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
            // Fingerprint the directory each tick and only kick off a sync when
            // a file was added, removed, renamed or changed size/mtime. The
            // listing is one cheap SAF cursor query; this skips the Room diff,
            // the SyncEngine pass and the sync-log writes on the common quiet
            // tick. First tick just seeds the fingerprint (onAppForeground
            // already synced). PERFORMANCE_AUDIT_2026-08-27 #2.
            var lastFingerprint: Long? = null
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val entries = runCatching { store?.list() }.getOrNull() ?: continue
                val fingerprint = directoryFingerprint(entries)
                if (lastFingerprint != null && fingerprint != lastFingerprint) {
                    requestSync("change poll")
                }
                lastFingerprint = fingerprint
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

/**
 * A cheap, order-independent hash of a vault directory listing: it changes iff
 * a file is added, removed, renamed, or its size / last-modified time changes.
 * Lets the continuous-mode poll ([SyncManager.startPolling]) skip a full sync
 * pass on ticks where nothing on disk moved (PERFORMANCE_AUDIT_2026-08-27 #2).
 * A hash collision only ever costs one delayed sync (the next real change, or
 * the periodic worker, still catches it), so 64 bits of FNV-1a is plenty.
 */
internal fun directoryFingerprint(entries: List<FileEntry>): Long {
    var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
    for (entry in entries.sortedBy { it.name }) {
        hash = (hash xor entry.name.hashCode().toLong()) * 1099511628211L
        hash = (hash xor entry.lastModified) * 1099511628211L
        hash = (hash xor entry.size) * 1099511628211L
    }
    return hash
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
