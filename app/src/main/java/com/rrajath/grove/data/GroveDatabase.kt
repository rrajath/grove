package com.rrajath.grove.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Rebuildable index over the vault (PRD §13): never the source of truth —
 * always derivable by re-parsing the .org files.
 */
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val fileName: String,
    /** Last indexed revision ("mtime:size"). */
    val revision: String,
    val noteCount: Int,
    val lastModified: Long,
    /** Name of a Syncthing .sync-conflict file shadowing this notebook, if any. */
    val conflictFileName: String?,
    /** Cached `#+TITLE:` preamble value, so the list doesn't re-parse files just to display it. */
    val title: String? = null,
    /**
     * False for a lightweight stub row inserted at discovery time (file listed
     * but not yet parsed): note count / title are placeholders until the
     * background parse pass fills them in and flips this to true. Lets the
     * notebook list appear in full immediately instead of growing row-by-row.
     */
    val isIndexed: Boolean = true,
)

@Entity(tableName = "notes", primaryKeys = ["fileName", "lineIndex"])
data class NoteEntity(
    val fileName: String,
    val lineIndex: Int,
    val level: Int,
    val title: String,
    val keyword: String?,
    val priority: String?,
    /** Own tags, ":"-joined. */
    val tags: String,
    /** Inherited tags incl. own and file tags, ":"-joined (for t. searches). */
    val inheritedTags: String,
    val scheduled: String?,
    val deadline: String?,
    val closed: String?,
    val orgId: String?,
    val customId: String?,
    val createdAt: String?,
    /** Own body text (capped) for full-text search and snippets. */
    val body: String,
    /** Done-type keyword flag resolved at index time. */
    val isDone: Boolean,
    /** Mirror of the notebook's lastModified for recency ranking. */
    val lastModified: Long,
)

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val message: String,
)

/**
 * A scheduled SCHEDULED/DEADLINE reminder (see `reminders` package). Rebuildable
 * from the .org files like the rest of the index, but also carries live
 * scheduling state (the AlarmManager alarm, whether it already fired, whether
 * it's waiting on a permission) that isn't derivable from disk alone.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    /** Composite: fileName + ancestor-title-path + own title + level + planning type. */
    @PrimaryKey val key: String,
    val fileName: String,
    /** Ancestor titles + own title, "/"-joined — how the heading is re-located on disk. */
    val headingPath: String,
    /** Own title only, used as the notification's title. */
    val headingTitle: String,
    val headingLevel: Int,
    /** "SCHEDULED" or "DEADLINE". */
    val planningType: String,
    val triggerAtMillis: Long,
    /** Stable per-[key] id for both the shown notification and its AlarmManager PendingIntent. */
    val notificationId: Int,
    /** True when scheduling was skipped for lack of POST_NOTIFICATIONS/exact-alarm access. */
    val pendingPermission: Boolean = false,
    /** Set once the "due now" notification has been shown for this trigger time, so
     *  catch-up passes don't re-fire it. Cleared whenever [triggerAtMillis] changes. */
    val firedAt: Long? = null,
)

/** Projection of the notebook columns the sync engine diffs against disk. */
data class NotebookSyncState(
    val fileName: String,
    val revision: String,
    val conflictFileName: String?,
    val isIndexed: Boolean,
)

@Dao
interface IndexDao {
    @Query("SELECT * FROM notebooks")
    suspend fun notebooks(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks")
    fun notebooksFlow(): Flow<List<NotebookEntity>>

    @Query("SELECT fileName, revision, conflictFileName, isIndexed FROM notebooks")
    suspend fun notebookSyncStates(): List<NotebookSyncState>

    @Query("SELECT conflictFileName FROM notebooks WHERE fileName = :fileName")
    suspend fun conflictFileNameFor(fileName: String): String?

    @Query("SELECT DISTINCT tags FROM notes WHERE tags != ''")
    suspend fun allTagStrings(): List<String>

    @Query("SELECT * FROM notes")
    fun allNotes(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert
    suspend fun insertNotebook(notebook: NotebookEntity)

    /**
     * Bulk-insert stub rows for newly-discovered files in one transaction (a
     * single [notebooksFlow] emission). IGNORE so files that already have a
     * row — indexed or stub — keep their real data instead of being blanked.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotebookStubs(notebooks: List<NotebookEntity>)

    @Query("UPDATE notebooks SET conflictFileName = :conflictFileName WHERE fileName = :fileName")
    suspend fun setConflict(fileName: String, conflictFileName: String?)

    @Query("DELETE FROM notes WHERE fileName = :fileName")
    suspend fun deleteNotes(fileName: String)

    @Query("DELETE FROM notebooks WHERE fileName = :fileName")
    suspend fun deleteNotebook(fileName: String)

    @Transaction
    suspend fun replaceNotebook(notebook: NotebookEntity, notes: List<NoteEntity>) {
        deleteNotebook(notebook.fileName)
        deleteNotes(notebook.fileName)
        insertNotebook(notebook)
        insertNotes(notes)
    }

    @Transaction
    suspend fun removeNotebook(fileName: String) {
        deleteNotebook(fileName)
        deleteNotes(fileName)
    }

    @Query("DELETE FROM notebooks")
    suspend fun clearNotebooks()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    /** Wipe the whole index (rebuilt on next sync — it's only a cache). */
    @Transaction
    suspend fun clearAll() {
        clearNotebooks()
        clearNotes()
    }
}

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SyncLogEntity>>

    @Query("SELECT COUNT(*) FROM sync_log")
    fun count(): Flow<Int>

    @Query("DELETE FROM sync_log WHERE id NOT IN (SELECT id FROM sync_log ORDER BY id DESC LIMIT 500)")
    suspend fun trim()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE fileName = :fileName")
    suspend fun forFile(fileName: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE key = :key")
    suspend fun get(key: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE pendingPermission = 1")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE pendingPermission = 0 AND firedAt IS NULL AND triggerAtMillis <= :now")
    suspend fun overdueUnfired(now: Long): List<ReminderEntity>

    // Overdue rows are excluded: reconcilePending() settles those silently rather
    // than firing them, so they shouldn't be counted as "need permission" either.
    @Query("SELECT COUNT(*) FROM reminders WHERE pendingPermission = 1 AND triggerAtMillis > :now")
    fun pendingCountFlow(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET firedAt = :firedAt WHERE key = :key")
    suspend fun markFired(key: String, firedAt: Long)

    @Query("DELETE FROM reminders WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()
}

@Database(
    entities = [NotebookEntity::class, NoteEntity::class, SyncLogEntity::class, ReminderEntity::class],
    // v6: added ReminderEntity (SCHEDULED/DEADLINE notification scheduling state);
    // v5: added NotebookEntity.isIndexed (stub vs fully-parsed notebook rows);
    // v4: added NotebookEntity.title (cached #+TITLE: preamble value). Destructive
    // migration drops the index so the next sync rebuilds it from the .org files.
    version = 6,
    exportSchema = false,
)
abstract class GroveDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun build(context: Context): GroveDatabase =
            Room.databaseBuilder(context, GroveDatabase::class.java, "grove-index.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
