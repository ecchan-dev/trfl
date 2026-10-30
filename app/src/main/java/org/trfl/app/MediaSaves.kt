package org.trfl.app

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONObject

class MediaSaves(private val context: Context) {
    private val directory = File(context.filesDir, "saved-media").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("saved-media", 0)
    suspend fun save(post: Post) = withContext(Dispatchers.IO) {
        val source = requireNotNull(post.image)
        val id = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        if (prefs.contains(id)) return@withContext
        val extension = source.substringBefore('?').substringAfterLast('.', "jpg").let { if (it in listOf("jpg", "jpeg", "png", "gif", "webp", "webm", "mp4")) ".$it" else if (post.isVideo) ".webm" else ".jpg" }
        val target = File(directory, id + extension)
        val pending = File(directory, "$id.tmp")
        try {
            val input = if (source.startsWith("https://i.4cdn.org/")) URL(source).openConnection().apply { connectTimeout = 12000; readTimeout = 15000 }.getInputStream()
                else context.contentResolver.openInputStream(Uri.parse(source)) ?: error("Attachment unavailable")
            input.use { stream -> pending.outputStream().use { output ->
                val buffer = ByteArray(32768); var bytes = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = stream.read(buffer); if (n < 0) break
                    bytes += n; check(bytes <= 128L * 1024 * 1024) { "Attachment exceeds 128 MiB" }
                    output.write(buffer, 0, n)
                }
            } }
            check(pending.renameTo(target))
            prefs.edit().putString(id, JSONObject().put("file", target.name).put("title", post.title).put("body", post.body).put("video", post.isVideo).put("source", source).toString()).apply()
        } finally { pending.delete() }
    }
    fun list(): List<Post> = prefs.all.entries.sortedBy { it.key }.mapNotNull { (id, value) ->
        runCatching {
            val obj = JSONObject(value as String)
            val file = File(directory, obj.getString("file")); if (!file.isFile) return@mapNotNull null
            val uri = file.toURI().toString()
            Post(-id.take(12).toLong(16), obj.optString("title").ifBlank { file.name }, obj.optString("body"), uri, if (obj.optBoolean("video")) null else uri, 0, video = obj.optBoolean("video"))
        }.getOrNull()
    }
    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        prefs.all.forEach { (id, value) ->
            runCatching {
                val obj = JSONObject(value as String)
                val file = File(directory, obj.getString("file"))
                if (file.toURI().toString() == uri) {
                    check(!file.exists() || file.delete()) { "Could not remove saved file" }
                    prefs.edit().remove(id).apply()
                }
            }.getOrThrow()
        }
    }
    suspend fun exportLocal(post: Post) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) error("Local export requires Android 10 or newer")
        val suffix = post.image?.substringBefore('?')?.substringAfterLast('.')?.takeIf { it in listOf("jpg", "jpeg", "png", "gif", "webp", "webm", "mp4") } ?: if (post.isVideo) "webm" else "jpg"
        val name = "TRFL-${System.currentTimeMillis()}.$suffix"
        val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.IS_PENDING, 1) }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create download")
        try {
            context.contentResolver.openInputStream(Uri.parse(post.image))!!.use { input -> context.contentResolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
    }
}
