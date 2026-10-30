package org.trfl.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

fun cacheLifetimeMillis(path: String): Long = when {
    path == "boards.json" -> 24 * 60 * 60 * 1000L
    path.endsWith("/catalog.json") -> 60 * 1000L
    else -> 30 * 1000L
}

data class CachedResponse(val body: String, val checked: Long, val etag: String = "", val modified: String = "") {
    fun fresh(path: String, now: Long): Boolean = now >= checked && now - checked < cacheLifetimeMillis(path)
}

/** Bounded, atomic, disposable cache. Explicit bookmark files are never touched. */
class ApiCacheStore(private val directory: File, private val maxBytes: Long = 64L * 1024 * 1024) {
    init { directory.mkdirs() }
    private fun file(path: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$key.cache")
    }
    @Synchronized fun read(path: String): CachedResponse? {
        val file = file(path)
        if (!file.isFile) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == 1)
                val checked = input.readLong()
                val etag = input.readUTF(); val modified = input.readUTF()
                val length = input.readInt()
                require(length in 0..MAX_BODY)
                val bytes = ByteArray(length); input.readFully(bytes)
                file.setLastModified(System.currentTimeMillis())
                CachedResponse(String(bytes, Charsets.UTF_8), checked, etag, modified)
            }
        } catch (_: Exception) { file.delete(); null }
    }
    @Synchronized fun write(path: String, response: CachedResponse) {
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BODY)
        val target = file(path)
        val pending = File.createTempFile("pending-", ".tmp", directory)
        try {
            DataOutputStream(pending.outputStream().buffered()).use {
                it.writeInt(1); it.writeLong(response.checked)
                it.writeUTF(response.etag.take(8192)); it.writeUTF(response.modified.take(8192))
                it.writeInt(bytes.size); it.write(bytes)
            }
            check(pending.renameTo(target))
            var size = directory.listFiles()?.sumOf { it.length() } ?: 0
            directory.listFiles()?.filter { it.extension == "cache" }?.sortedBy { it.lastModified() }?.forEach {
                if (size > maxBytes) { val length = it.length(); if (it.delete()) size -= length }
            }
        } finally { pending.delete() }
    }
    @Synchronized fun remove(path: String) { file(path).delete() }
    companion object { const val MAX_BODY = 16 * 1024 * 1024 }
}
