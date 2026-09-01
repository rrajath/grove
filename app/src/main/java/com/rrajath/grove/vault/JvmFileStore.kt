package com.rrajath.grove.vault

import java.io.File

/**
 * Plain java.io implementation. Used by JVM unit tests, and reusable for any
 * future direct-filesystem vault location. Recurses into subdirectories,
 * skipping dot-directories (see [isSkippedVaultDir]).
 */
class JvmFileStore(private val root: File) : FileStore {

    override suspend fun list(): List<FileEntry> =
        root.walkTopDown()
            .onEnter { it == root || !isSkippedVaultDir(it.name) }
            .filter { it.isFile }
            .map {
                FileEntry(
                    it.relativeTo(root).invariantSeparatorsPath,
                    it.lastModified(),
                    it.length(),
                )
            }
            .sortedBy { it.name }
            .toList()

    override suspend fun read(name: String): String = resolve(name).readText()

    override suspend fun write(name: String, content: String) {
        resolve(name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    override suspend fun create(name: String): Boolean {
        val target = resolve(name)
        target.parentFile?.mkdirs()
        return target.createNewFile()
    }

    override suspend fun rename(oldName: String, newName: String): Boolean {
        val target = resolve(newName)
        if (target.exists()) return false
        target.parentFile?.mkdirs()
        return resolve(oldName).renameTo(target)
    }

    override suspend fun delete(name: String): Boolean = resolve(name).delete()

    override suspend fun exists(name: String): Boolean = resolve(name).exists()

    override suspend fun pruneEmptyDirs(dir: String) {
        val trimmed = dir.trim('/')
        if (trimmed.isEmpty()) return
        // Deepest-first so an emptied parent whose only child was an (also now
        // empty) subdirectory still collapses.
        File(root, trimmed).takeIf { it.isDirectory }?.walkBottomUp()?.forEach { d ->
            if (d.isDirectory && d.list()?.isEmpty() == true) d.delete()
        }
        var current = trimmed.substringBeforeLast('/', "")
        while (current.isNotEmpty()) {
            val d = File(root, current)
            if (d.list()?.isEmpty() != true || !d.delete()) break
            current = current.substringBeforeLast('/', "")
        }
    }

    private fun resolve(name: String): File {
        require(name.isNotBlank()) { "Empty vault path" }
        require(name.split('/').none { it == ".." }) { "Vault path must not contain '..': $name" }
        return File(root, name)
    }
}
