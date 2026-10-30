package org.trfl.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Explicit bookmark storage, separate from disposable image-loader caches. */
class ThreadStore(context: Context) {
    private val root = File(context.filesDir, "bookmarks").apply { mkdirs() }
    private fun directory(key: String): File {
        require(Regex("[a-z0-9]+/[0-9]+").matches(key))
        return File(root, key.replace('/', '_'))
    }
    suspend fun save(key: String, posts: List<Post>, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        require(BoardPolicy.allows(key.substringBefore('/'))) { "Board unavailable in this build" }
        val dir = directory(key).apply { mkdirs() }
        val array = JSONArray()
        posts.forEach { p -> array.put(JSONObject().put("id", p.id).put("title", p.title).put("body", p.body).put("image", p.image).put("thumb", p.thumb).put("replies", p.replies).put("images", p.images).put("created", p.created).put("lastReply", p.lastReply)) }
        val temp = File(dir, "thread.tmp")
        temp.writeText(array.toString())
        check(temp.renameTo(File(dir, "thread.json"))) { "Could not save thread" }
        val urls = posts.flatMap { listOfNotNull(it.thumb, it.image) }.distinct()
        var stored = 0
        var bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
        for (url in urls) {
            coroutineContext.ensureActive()
            val file = mediaFile(dir, url)
            if (file.isFile) { stored++; continue }
            if (bytes >= 256L * 1024 * 1024) break
            try {
                val parsed = URL(url)
                require(parsed.protocol == "https" && parsed.host == "i.4cdn.org")
                val conn = parsed.openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10000; conn.readTimeout = 15000
                    check(conn.responseCode == 200)
                    val pending = File(dir, "media.tmp")
                    try {
                        conn.inputStream.use { input -> pending.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var size = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buffer); if (n < 0) break
                                size += n
                                check(size <= 64L * 1024 * 1024 && bytes + size <= 256L * 1024 * 1024)
                                output.write(buffer, 0, n)
                            }
                        } }
                        check(pending.renameTo(file))
                        bytes += file.length(); stored++
                    } finally { pending.delete() }
                } finally { conn.disconnect() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the text snapshot and report partial media below. */ }
            withContext(Dispatchers.Main) { progress("Saved text · $stored/${urls.size} media") }
        }
        withContext(Dispatchers.Main) { progress("Saved locally · $stored/${urls.size} media") }
    }
    private fun mediaFile(dir: File, url: String) = File(dir, url.substringAfterLast('/'))
    suspend fun load(key: String): List<Post>? = withContext(Dispatchers.IO) {
        if (!BoardPolicy.allows(key.substringBefore('/'))) return@withContext null
        try {
            val dir = directory(key)
            val file = File(dir, "thread.json")
            if (!file.isFile) return@withContext null
            val array = JSONArray(file.readText())
            fun media(p: JSONObject, field: String): String? {
                val url = p.optString(field).takeIf { it.isNotEmpty() && it != "null" } ?: return null
                return mediaFile(dir, url).takeIf { it.isFile }?.toURI()?.toString() ?: url
            }
            (0 until array.length()).map { i -> val p = array.getJSONObject(i); Post(p.getLong("id"), p.optString("title"), p.optString("body"), media(p, "image"), media(p, "thumb"), p.optInt("replies"), p.optInt("images"), p.optLong("created"), p.optLong("lastReply")) }
        } catch (_: Exception) { null }
    }
    suspend fun remove(key: String) = withContext(Dispatchers.IO) { directory(key).deleteRecursively() }
}

class ApiException(val status: Int) : Exception(if (status == 404) "Thread no longer exists" else "Server returned $status. Try again shortly.")
