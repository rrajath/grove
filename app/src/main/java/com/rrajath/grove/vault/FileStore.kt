package com.rrajath.grove.vault

/** Metadata for one file in the vault. */
data class FileEntry(
    val name: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Abstraction over the sync directory. The app uses a Storage Access Framework
 * implementation; unit tests use [JvmFileStore]. Names are plain file names —
 * the vault is flat in v1 (org files at the root of the chosen folder).
 */
interface FileStore {
    /** All files in the vault (not filtered to .org; callers apply ignore rules). */
    suspend fun list(): List<FileEntry>

    /**
     * Metadata for a single file, or null if it doesn't exist. Implementations
     * should avoid enumerating the whole vault when only one file is needed; the
     * default falls back to [list] for stores where that's already cheap.
     */
    suspend fun stat(name: String): FileEntry? = list().firstOrNull { it.name == name }

    suspend fun read(name: String): String

    /**
     * Write full content, truncating. Implementations MUST truncate properly
     * (SAF streams need mode "wt" or shorter content leaves trailing bytes).
     */
    suspend fun write(name: String, content: String)

    /** Create an empty file; returns false if it already exists. */
    suspend fun create(name: String): Boolean

    suspend fun rename(oldName: String, newName: String): Boolean

    suspend fun delete(name: String): Boolean

    suspend fun exists(name: String): Boolean
}
