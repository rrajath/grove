package com.rrajath.grove.vault

import java.io.File

/**
 * Plain java.io implementation. Used by JVM unit tests, and reusable for any
 * future direct-filesystem vault location.
 */
class JvmFileStore(private val root: File) : FileStore {

    override suspend fun list(): List<FileEntry> =
        root.listFiles { f: File -> f.isFile }
            ?.map { FileEntry(it.name, it.lastModified(), it.length()) }
            ?.sortedBy { it.name }
            ?: emptyList()

    override suspend fun read(name: String): String = resolve(name).readText()

    override suspend fun write(name: String, content: String) {
        resolve(name).writeText(content)
    }

    override suspend fun create(name: String): Boolean = resolve(name).createNewFile()

    override suspend fun rename(oldName: String, newName: String): Boolean {
        val target = resolve(newName)
        if (target.exists()) return false
        return resolve(oldName).renameTo(target)
    }

    override suspend fun delete(name: String): Boolean = resolve(name).delete()

    override suspend fun exists(name: String): Boolean = resolve(name).exists()

    private fun resolve(name: String): File {
        require(!name.contains('/') && !name.contains('\\')) { "Vault is flat: $name" }
        return File(root, name)
    }
}
