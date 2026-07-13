package com.rrajath.grove.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
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

/** Projection of the notebook columns the sync engine diffs against disk. */
data class NotebookSyncState(
    val fileName: String,
    val revision: String,
    val conflictFileName: String?,
)

@Dao
interface IndexDao {
    @Query("SELECT * FROM notebooks")
    suspend fun notebooks(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks")
    fun notebooksFlow(): Flow<List<NotebookEntity>>

    @Query("SELECT fileName, revision, conflictFileName FROM notebooks")
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

@Database(
    entities = [NotebookEntity::class, NoteEntity::class, SyncLogEntity::class],
    // v4: added NotebookEntity.title (cached #+TITLE: preamble value); destructive
    // migration drops the index so the next sync rebuilds it.
    version = 4,
    exportSchema = false,
)
abstract class GroveDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        fun build(context: Context): GroveDatabase =
            Room.databaseBuilder(context, GroveDatabase::class.java, "grove-index.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
