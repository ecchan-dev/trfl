package org.trfl.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object ApiRequests {
    var stalePaths by mutableStateOf(emptySet<String>())
        private set
    private var store: ApiCacheStore? = null
    private val mutex = Mutex()
    fun initialize(cacheDir: File) { if (store == null) store = ApiCacheStore(File(cacheDir, "api-responses")) }

    suspend fun load(path: String, force: Boolean): String = withContext(Dispatchers.IO) {
        require(Regex("(?:boards\\.json|[a-z0-9]+/(?:catalog\\.json|thread/[0-9]+\\.json))").matches(path))
        // Check before cache lookup, network access, and stale-response fallback.
        require(BoardPolicy.allowsPath(path)) { "This board is unavailable in the Play build" }
        mutex.withLock {
            val cached = store?.read(path)
            if (!force && cached?.fresh(path, System.currentTimeMillis()) == true) return@withLock cached.body
            val connection = URL("https://a.4cdn.org/$path").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12000; connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "TRFL/${BuildConfig.VERSION_NAME} (Android read-only reader)")
                cached?.etag?.takeIf { it.isNotEmpty() }?.let { connection.setRequestProperty("If-None-Match", it) }
                cached?.modified?.takeIf { it.isNotEmpty() }?.let { connection.setRequestProperty("If-Modified-Since", it) }
                val status = connection.responseCode
                if (status == 304 && cached != null) {
                    stalePaths = stalePaths - path
                    runCatching { store?.write(path, cached.copy(checked = System.currentTimeMillis())) }
                    return@withLock cached.body
                }
                if (status != 200) {
                    if (status == 404) store?.remove(path)
                    throw ApiException(status)
                }
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        check(output.size() + count <= ApiCacheStore.MAX_BODY) { "API response too large" }
                        output.write(buffer, 0, count)
                    }
                }
                val body = output.toString("UTF-8")
                stalePaths = stalePaths - path
                runCatching { store?.write(path, CachedResponse(body, System.currentTimeMillis(), connection.getHeaderField("ETag") ?: "", connection.getHeaderField("Last-Modified") ?: "")) }
                body
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // A confirmed dead thread must not be resurrected by disposable cache data.
                if (cached != null && !(e is ApiException && e.status in 400..499)) {
                    stalePaths = stalePaths + path
                    cached.body
                } else throw e
            } finally { connection.disconnect() }
        }
    }
}

suspend fun request(path: String, force: Boolean = false): String = ApiRequests.load(path, force)
