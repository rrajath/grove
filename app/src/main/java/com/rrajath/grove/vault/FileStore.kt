package com.rrajath.grove.vault

/** Metadata for one file in the vault. [name] is a vault-relative path. */
data class FileEntry(
    val name: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Abstraction over the sync directory. The app uses a Storage Access Framework
 * implementation; unit tests use [JvmFileStore].
 *
 * Names are vault-relative paths with `/` separators, e.g. `projects/acme.org`.
 * A file at the root of the vault has a name equal to its bare file name. The
 * vault is a tree: [list] descends into subdirectories, and [create] / [rename]
 * create intermediate directories as needed.
 *
 * Traversal skips dot-directories (`.stversions`, `.stfolder`, `.git`, and any
 * other name starting with `.`); Syncthing's `.stversions` in particular holds
 * `.org` copies that would otherwise surface as phantom notebooks.
 */
interface FileStore {
    /** All files in the vault, recursively (not filtered to .org; callers apply ignore rules). */
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

    /**
     * Create an empty file, creating any missing parent directories; returns
     * false if it already exists.
     */
    suspend fun create(name: String): Boolean

    /**
     * Rename or move a file. [newName] may be in a different directory, in which
     * case intermediate directories are created. Returns false if the target
     * already exists or the source is missing.
     */
    suspend fun rename(oldName: String, newName: String): Boolean

    suspend fun delete(name: String): Boolean

    suspend fun exists(name: String): Boolean
}

/** Directory names never descended into during vault traversal. */
internal fun isSkippedVaultDir(name: String): Boolean = name.startsWith(".")
