package org.trfl.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** A null threadId identifies a new-thread draft; otherwise it identifies a reply. */
data class PostingDraftKey(val board: String, val threadId: Long?) {
    init {
        require(Regex("[a-z0-9]{1,32}").matches(board))
        require(threadId == null || threadId > 0)
    }
}

/** Metadata only. The UI owns persisted URI grants and must check access after recovery. */
data class PostingDraftAttachment(val uri: String, val displayName: String?, val mimeType: String?, val sizeBytes: Long?) {
    init {
        require(uri.toByteArray(Charsets.UTF_8).size <= 4096)
        require(URI(uri).scheme == "content")
        require(displayName == null || displayName.toByteArray(Charsets.UTF_8).size <= 1024)
        require(mimeType == null || mimeType.toByteArray(Charsets.UTF_8).size <= 256)
        require(sizeBytes == null || sizeBytes >= 0)
    }
}

data class PostingDraft(val key: PostingDraftKey, val text: String, val subject: String, val attachment: PostingDraftAttachment?, val revision: String)

/**
 * Synchronous disk operations: call on an IO dispatcher. Pass an app-private directory.
 * save returns the revision to associate with an actual submission. clearIfRevision removes
 * only that exact revision, so edits made after submission began are not discarded.
 * There are deliberately no fields for CAPTCHA answers, tokens or posting credentials.
 * Thread safe across store instances in this process; not intended for multi-process writers.
 */
class PostingDraftStore(private val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) }

    fun load(key: PostingDraftKey): PostingDraft? = synchronized(lock) { read(key) }

    fun save(key: PostingDraftKey, text: String, subject: String, attachment: PostingDraftAttachment?): PostingDraft = synchronized(lock) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES)
        require(subject.toByteArray(Charsets.UTF_8).size <= MAX_SUBJECT_BYTES)
        val target = file(key)
        if (!target.exists()) check((directory.listFiles() ?: error("Cannot list drafts")).count { it.name.endsWith(".draft") } < MAX_DRAFTS) { "Draft limit reached" }
        val draft = PostingDraft(key, text, subject, attachment, UUID.randomUUID().toString())
        val pending = File.createTempFile("draft-", ".tmp", directory)
        try {
            FileOutputStream(pending).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(1)
                out.writeUTF(key.board); out.writeLong(key.threadId ?: 0)
                out.writeUTF(draft.revision)
                writeString(out, text); writeString(out, subject)
                out.writeBoolean(attachment != null)
                if (attachment != null) {
                    writeString(out, attachment.uri)
                    writeNullable(out, attachment.displayName); writeNullable(out, attachment.mimeType)
                    out.writeBoolean(attachment.sizeBytes != null)
                    attachment.sizeBytes?.let(out::writeLong)
                }
                out.flush(); stream.fd.sync()
            }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
        draft
    }

    /** Returns false if absent, unreadable, or a newer revision exists. */
    fun clearIfRevision(key: PostingDraftKey, revision: String): Boolean = synchronized(lock) {
        val existing = read(key)
        existing != null && existing.revision == revision && file(key).delete()
    }

    /** Explicit discard; unlike clearIfRevision this intentionally removes any revision. */
    fun discard(key: PostingDraftKey): Boolean = synchronized(lock) { file(key).delete() }

    private fun file(key: PostingDraftKey): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("${key.board}/${key.threadId ?: "new"}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.draft")
    }

    private fun read(key: PostingDraftKey): PostingDraft? {
        val target = file(key)
        if (!target.isFile) return null
        return try {
            require(target.length() <= MAX_FILE_BYTES)
            DataInputStream(target.inputStream().buffered()).use { input ->
                require(input.readInt() == 1)
                val storedKey = PostingDraftKey(input.readUTF(), input.readLong().let { if (it == 0L) null else it })
                require(storedKey == key)
                val revision = input.readUTF(); UUID.fromString(revision)
                val text = readString(input, MAX_TEXT_BYTES); val subject = readString(input, MAX_SUBJECT_BYTES)
                val attachment = if (input.readBoolean()) PostingDraftAttachment(
                    readString(input, 4096), readNullable(input, 1024), readNullable(input, 256),
                    if (input.readBoolean()) input.readLong() else null
                ) else null
                require(input.read() == -1)
                PostingDraft(key, text, subject, attachment, revision)
            }
        } catch (_: Exception) { null } // Preserve damaged or future-version data for recovery.
    }

    companion object {
        const val MAX_TEXT_BYTES = 64 * 1024
        const val MAX_SUBJECT_BYTES = 4096
        const val MAX_DRAFTS = 256
        private const val MAX_FILE_BYTES = 80 * 1024L
        private val lock = Any()
        private fun writeString(out: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8); out.writeInt(bytes.size); out.write(bytes)
        }
        private fun writeNullable(out: DataOutputStream, value: String?) {
            out.writeBoolean(value != null); if (value != null) writeString(out, value)
        }
        private fun readString(input: DataInputStream, max: Int): String {
            val length = input.readInt(); require(length in 0..max)
            return ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
        }
        private fun readNullable(input: DataInputStream, max: Int): String? = if (input.readBoolean()) readString(input, max) else null
    }
}
