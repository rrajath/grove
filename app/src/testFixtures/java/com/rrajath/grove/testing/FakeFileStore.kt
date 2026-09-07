package com.rrajath.grove.testing

import com.rrajath.grove.vault.FileEntry
import com.rrajath.grove.vault.FileStore
import java.io.FileNotFoundException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [FileStore] for tests. Backed by a `MutableMap` of vault-relative
 * path to content, with a monotonic logical clock standing in for mtime so a
 * test can make a file's revision ("mtime:size") change under a ViewModel
 * ([touch]) to exercise stale-file handling.
 *
 * Semantics mirror [com.rrajath.grove.vault.JvmFileStore] and the cases pinned
 * by `JvmFileStoreTest`: [list] returns paths sorted ascending and skips
 * anything under a dot-directory, [read] on a missing file throws, [create] /
 * [rename] refuse to clobber. Directories are not real entities here, so
 * [pruneEmptyDirs] is a no-op (as the interface allows).
 *
 * See internal/test-suite-00-overview.md § Fakes to build.
 */
class FakeFileStore(
    initial: Map<String, String> = emptyMap(),
) : FileStore {

    private data class Node(val content: String, val modified: Long)

    private val files = HashMap<String, Node>()
    private val mutex = Mutex()
    private var clock = 0L

    init {
        initial.forEach { (name, content) -> files[normalize(name)] = Node(content, tick()) }
    }

    private fun tick(): Long = ++clock

    private fun normalize(name: String): String {
        require(name.isNotBlank()) { "Empty vault path" }
        require(name.split('/').none { it == ".." }) { "Vault path must not contain '..': $name" }
        return name.trim('/')
    }

    private fun underDotDir(name: String): Boolean =
        name.split('/').dropLast(1).any { it.startsWith(".") }

    override suspend fun list(): List<FileEntry> = mutex.withLock {
        files.entries
            .filterNot { underDotDir(it.key) }
            .map { (name, node) -> FileEntry(name, node.modified, node.content.length.toLong()) }
            .sortedBy { it.name }
    }

    override suspend fun stat(name: String): FileEntry? = mutex.withLock {
        val key = normalize(name)
        files[key]?.let { FileEntry(key, it.modified, it.content.length.toLong()) }
    }

    override suspend fun read(name: String): String = mutex.withLock {
        files[normalize(name)]?.content ?: throw FileNotFoundException(name)
    }

    override suspend fun write(name: String, content: String) = mutex.withLock {
        files[normalize(name)] = Node(content, tick())
    }

    override suspend fun create(name: String): Boolean = mutex.withLock {
        val key = normalize(name)
        if (files.containsKey(key)) return@withLock false
        files[key] = Node("", tick())
        true
    }

    override suspend fun rename(oldName: String, newName: String): Boolean = mutex.withLock {
        val from = normalize(oldName)
        val to = normalize(newName)
        val node = files[from] ?: return@withLock false
        if (files.containsKey(to)) return@withLock false
        files.remove(from)
        files[to] = node // keep the original mtime, like File.renameTo
        true
    }

    override suspend fun delete(name: String): Boolean = mutex.withLock {
        files.remove(normalize(name)) != null
    }

    override suspend fun exists(name: String): Boolean = mutex.withLock {
        files.containsKey(normalize(name))
    }

    // --- test helpers ---

    /** Bump [name]'s logical mtime without changing content, simulating an external edit. */
    suspend fun touch(name: String) = mutex.withLock {
        val key = normalize(name)
        val node = files[key] ?: throw FileNotFoundException(name)
        files[key] = node.copy(modified = tick())
    }

    /** The revision string an indexer would compute for [name] ("mtime:size"), or null if absent. */
    suspend fun revisionOf(name: String): String? = mutex.withLock {
        files[normalize(name)]?.let { "${it.modified}:${it.content.length}" }
    }

    /** Immutable snapshot of every file's content, for assertions. */
    suspend fun snapshot(): Map<String, String> = mutex.withLock {
        files.mapValues { it.value.content }.toSortedMap()
    }
}
