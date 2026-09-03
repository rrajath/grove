package com.rrajath.grove.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Rebuildable index over the vault (PRD §13): never the source of truth;
 * always derivable by re-parsing the .org files.
 */
@Entity(
    tableName = "notebooks",
    // Backs the file-level `[[id:…]]` lookup in `resolveOrgLink`: an `:ID:` in a
    // file's leading property drawer resolves to that file's outline, indexed so
    // the lookup stays O(log n) as the vault grows to thousands of files.
    indices = [Index("orgId")],
)
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
     * The `:ID:` from the file's leading `:PROPERTIES:` drawer (before the first
     * headline), if any. Lets `[[id:…]]` links that target a whole file resolve
     * to its outline. Null for a stub row and for files with no file-level ID.
     */
    val orgId: String? = null,
    /**
     * False for a lightweight stub row inserted at discovery time (file listed
     * but not yet parsed): note count / title are placeholders until the
     * background parse pass fills them in and flips this to true. Lets the
     * notebook list appear in full immediately instead of growing row-by-row.
     */
    val isIndexed: Boolean = true,
)

/**
 * Indices back the facet pushdown in [IndexDao.notesMatching]: the chip filters
 * and `i.`/`p.` query tokens become SQL `WHERE` predicates instead of a scan
 * over every row. `scheduled`/`deadline` are indexed for their `IS NOT NULL`
 * probes (the agenda screen, and any date facet), not for date comparison:
 * they hold raw org timestamp strings.
 */
@Entity(
    tableName = "notes",
    primaryKeys = ["fileName", "lineIndex"],
    indices = [
        Index("keyword"),
        Index("isDone"),
        Index("priority"),
        Index("scheduled"),
        Index("deadline"),
        // Back the vault-wide `[[id:…]]` / `[[#custom-id]]` link lookups so they
        // probe an index instead of scanning every note row.
        Index("orgId"),
        Index("customId"),
    ],
)
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
    /** Own body text, in full, for full-text search and snippets. */
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
    /** Ancestor titles + own title, "/"-joined: how the heading is re-located on disk. */
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
    /** False when [triggerAtMillis] came from the default reminder time rather than
     *  the timestamp's own time-of-day: those reminders don't fire their own "due
     *  now" notification and are counted into the daily digest instead. */
    val hasExplicitTime: Boolean = true,
    /** [com.rrajath.grove.settings.ReminderLeadTime.storageKey] this row's [triggerAtMillis]
     *  was computed with, baked in at scheduling time so the "due in N minutes" notification
     *  text can't drift out of sync with a lead-time setting change made after this was armed. */
    val leadTime: String = "at_time",
)

/** Projection of the notebook columns the sync engine diffs against disk. */
data class NotebookSyncState(
    val fileName: String,
    val revision: String,
    val conflictFileName: String?,
    val isIndexed: Boolean,
)

/** Primary key of a `notes` row: what an FTS lookup hands back. */
data class NoteKey(val fileName: String, val lineIndex: Int)

/**
 * Binds a statement built by `NoteCandidateQuery` for [IndexDao.notesMatching].
 * Every parameter that builder emits is text (match expressions, keywords,
 * tags, file names), so a single bind kind covers all of them.
 */
fun rawQuery(sql: String, args: List<String> = emptyList()): RoomRawQuery =
    RoomRawQuery(sql) { statement ->
        args.forEachIndexed { i, arg -> statement.bindText(i + 1, arg) }
    }

/**
 * Every column the filter catalog and the blank-state quick counts need, and
 * nothing else. Deliberately excludes `title`/`body`: this projection is held
 * in memory for the whole vault, so carrying note bodies in it would defeat the
 * point of moving search itself to a query-scoped load.
 */
data class NoteFacetRow(
    val fileName: String,
    val keyword: String?,
    val isDone: Boolean,
    val inheritedTags: String,
    val scheduled: String?,
    val deadline: String?,
    /** Mirror of the notebook's mtime, for recency-ranking filename matches. */
    val lastModified: Long,
)

/**
 * An abstract class rather than an interface so it can carry [ftsAvailable]:
 * the FTS statements below live inside the same `@Transaction` methods as the
 * `notes` writes (that is what keeps the two tables from ever drifting), so the
 * "is there an FTS table at all" decision has to be readable from in here.
 */
@Dao
abstract class IndexDao {

    /**
     * Whether [NotesFts] exists and can be written to. Set once by
     * [GroveDatabase]'s bootstrap callback; false only where SQLite was built
     * without FTS5, in which case search falls back to the full scan.
     */
    @Volatile
    var ftsAvailable: Boolean = false

    @Query("SELECT * FROM notebooks")
    abstract suspend fun notebooks(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks")
    abstract fun notebooksFlow(): Flow<List<NotebookEntity>>

    @Query("SELECT fileName, revision, conflictFileName, isIndexed FROM notebooks")
    abstract suspend fun notebookSyncStates(): List<NotebookSyncState>

    @Query("SELECT conflictFileName FROM notebooks WHERE fileName = :fileName")
    abstract suspend fun conflictFileNameFor(fileName: String): String?

    @Query("SELECT DISTINCT tags FROM notes WHERE tags != ''")
    abstract suspend fun allTagStrings(): List<String>

    /** Where a heading with this `:ID:` lives, for resolving `[[id:…]]` links vault-wide. */
    @Query("SELECT fileName, lineIndex FROM notes WHERE orgId = :id LIMIT 1")
    abstract suspend fun noteLocationByOrgId(id: String): NoteKey?

    /**
     * The file whose leading property drawer carries this `:ID:`, for resolving a
     * `[[id:…]]` link that targets a whole file (→ its outline). Indexed lookup.
     */
    @Query("SELECT fileName FROM notebooks WHERE orgId = :id LIMIT 1")
    abstract suspend fun notebookByOrgId(id: String): String?

    /** Where a heading with this `:CUSTOM_ID:` lives, for resolving `[[#id]]` links vault-wide. */
    @Query("SELECT fileName, lineIndex FROM notes WHERE customId = :customId LIMIT 1")
    abstract suspend fun noteLocationByCustomId(customId: String): NoteKey?

    @Query(
        "SELECT fileName, keyword, isDone, inheritedTags, scheduled, deadline, lastModified FROM notes"
    )
    abstract fun noteFacets(): Flow<List<NoteFacetRow>>

    /**
     * Rows the agenda can possibly show. `QueryMatcher.agenda` only ever buckets
     * notes by their SCHEDULED/DEADLINE date, so an undated note can never
     * appear: excluding those in SQL is an exact narrowing, not an
     * approximation.
     */
    @Query("SELECT * FROM notes WHERE scheduled IS NOT NULL OR deadline IS NOT NULL")
    abstract fun plannedNotes(): Flow<List<NoteEntity>>

    /**
     * Candidate rows for one search, built by `NoteCandidateQuery`. Raw because
     * the query joins [NotesFts], which Room does not own as an entity.
     */
    @SkipQueryVerification
    @RawQuery
    abstract suspend fun notesMatching(query: RoomRawQuery): List<NoteEntity>

    /** Keys of the FTS rows matching a MATCH expression (see `FtsQuery`). */
    @SkipQueryVerification
    @RawQuery
    abstract suspend fun noteKeysMatching(query: RoomRawQuery): List<NoteKey>

    @Insert
    abstract suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert
    abstract suspend fun insertNotebook(notebook: NotebookEntity)

    /**
     * Bulk-insert stub rows for newly-discovered files in one transaction (a
     * single [notebooksFlow] emission). IGNORE so files that already have a
     * row (indexed or stub) keep their real data instead of being blanked.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotebookStubs(notebooks: List<NotebookEntity>)

    @Query("UPDATE notebooks SET conflictFileName = :conflictFileName WHERE fileName = :fileName")
    abstract suspend fun setConflict(fileName: String, conflictFileName: String?)

    @Query("DELETE FROM notes WHERE fileName = :fileName")
    abstract suspend fun deleteNotes(fileName: String)

    @Query("DELETE FROM notebooks WHERE fileName = :fileName")
    abstract suspend fun deleteNotebook(fileName: String)

    /**
     * Rewrites one file's rows in `notes` and in the FTS mirror. Both happen in
     * the same transaction, which is what makes it impossible for the mirror to
     * drift from the table it indexes.
     */
    @Transaction
    open suspend fun replaceNotebook(notebook: NotebookEntity, notes: List<NoteEntity>) {
        deleteNotebook(notebook.fileName)
        deleteNotes(notebook.fileName)
        insertNotebook(notebook)
        insertNotes(notes)
        if (ftsAvailable) {
            deleteFtsRows(notebook.fileName)
            notes.forEach { insertFtsRow(it.fileName, it.lineIndex, it.title, it.body) }
        }
    }

    /** Drops a deleted or renamed notebook from every table. */
    @Transaction
    open suspend fun removeNotebook(fileName: String) {
        deleteNotebook(fileName)
        deleteNotes(fileName)
        if (ftsAvailable) deleteFtsRows(fileName)
    }

    @Query("DELETE FROM notebooks")
    abstract suspend fun clearNotebooks()

    @Query("DELETE FROM notes")
    abstract suspend fun clearNotes()

    /** Wipes the whole index (rebuilt on next sync; it's only a cache). */
    @Transaction
    open suspend fun clearAll() {
        clearNotebooks()
        clearNotes()
        if (ftsAvailable) clearFts()
    }

    // --- FTS mirror ---
    //
    // Room cannot validate statements against a virtual table it does not own as
    // an entity, hence @SkipQueryVerification. The table is guaranteed to exist
    // at runtime by GroveDatabase's bootstrap callback; when creating it failed,
    // GroveDatabase.ftsAvailable is false and none of these run.

    @SkipQueryVerification
    @Query("DELETE FROM notes_fts WHERE fileName = :fileName")
    abstract suspend fun deleteFtsRows(fileName: String)

    @SkipQueryVerification
    @Query(
        "INSERT INTO notes_fts(fileName, lineIndex, title, body) " +
            "VALUES (:fileName, :lineIndex, :title, :body)"
    )
    abstract suspend fun insertFtsRow(fileName: String, lineIndex: Int, title: String, body: String)

    @SkipQueryVerification
    @Query("DELETE FROM notes_fts")
    abstract suspend fun clearFts()

    @SkipQueryVerification
    @Query("SELECT COUNT(*) FROM notes_fts")
    abstract suspend fun ftsRowCount(): Int
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
    // v11: added NotebookEntity.orgId (file-level `:ID:` from the leading property
    // drawer, so `[[id:…]]` links to a whole file resolve to its outline) and
    // secondary indices on notebooks.orgId / notes.orgId / notes.customId so the
    // link lookups probe an index instead of scanning. Destructive migration
    // drops the (rebuildable) index; the next sync repopulates it from the .org
    // files, no file data touched.
    // v10: NotebookEntity.fileName / NoteEntity.fileName / notes_fts.fileName are
    // now vault-relative paths ("projects/acme.org"), not bare names, since the
    // vault can contain subfolders. No column change; destructive migration just
    // re-syncs the (rebuildable) index from the .org files under the new keys.
    // v9: added ReminderEntity.leadTime (Settings › Reminders › "Notify me" lead
    // time, baked into each row at scheduling time);
    // v8: added ReminderEntity.hasExplicitTime (date-only reminders now bundle into
    // the daily digest notification instead of firing individually);
    // v7: added the notes_fts FTS5 mirror, secondary indices on notes, and full
    // (no longer 4000-char-capped) note bodies;
    // v6: added ReminderEntity (SCHEDULED/DEADLINE notification scheduling state);
    // v5: added NotebookEntity.isIndexed (stub vs fully-parsed notebook rows);
    // v4: added NotebookEntity.title (cached #+TITLE: preamble value). Destructive
    // migration drops the index so the next sync rebuilds it from the .org files.
    version = 11,
    exportSchema = false,
)
abstract class GroveDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun reminderDao(): ReminderDao

    /**
     * Whether the [NotesFts] virtual table exists and can be used. False only on
     * a device whose SQLite lacks FTS5, where everything falls back to the full
     * in-memory scan: slower, but identical results.
     */
    val ftsAvailable: Boolean get() = indexDao().ftsAvailable

    companion object {
        fun build(context: Context): GroveDatabase =
            create(Room.databaseBuilder(context, GroveDatabase::class.java, "grove-index.db"))

        /**
         * Throwaway in-memory instance wired through the same [create] path, so
         * the instrumented tests exercise the real FTS bootstrap rather than a
         * hand-rolled copy of it that could drift.
         */
        fun inMemory(context: Context): GroveDatabase =
            create(Room.inMemoryDatabaseBuilder(context, GroveDatabase::class.java))

        private fun create(builder: RoomDatabase.Builder<GroveDatabase>): GroveDatabase {
            // The callback only fires on first database access, which is
            // necessarily after build() returns, so `database` is always
            // assigned by the time either override runs.
            lateinit var database: GroveDatabase
            val ftsBootstrap = object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) {
                    database.indexDao().ftsAvailable = NotesFts.create(connection)
                }

                // Also on open: the table lives outside Room's schema, so nothing
                // else would recreate it if a destructive migration or a manual
                // wipe removed it out from under us.
                override fun onOpen(connection: SQLiteConnection) {
                    database.indexDao().ftsAvailable = NotesFts.create(connection)
                }
            }
            database = builder
                // Android's platform SQLite is built without the FTS5 module, so
                // the bundled SQLite ships one that has it (plus the trigram
                // tokenizer the substring semantics depend on).
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(ftsBootstrap)
                .build()
            return database
        }
    }
}
