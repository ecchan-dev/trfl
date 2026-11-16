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
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object ApiRequests {
    var stalePaths by mutableStateOf(emptySet<String>())
        private set
    @Volatile private var store: ApiCacheStore? = null
    // Same-path loads are serialised so a second caller reuses the first one's response;
    // different paths proceed independently. Network starts are spaced globally (4chan API: max 1 req/s).
    private val pathLocks = KeyedMutex<String>()
    private val rateGate = RequestRateGate(1000L)
    private val staleLock = Any()
    fun initialize(cacheDir: File) { if (store == null) store = ApiCacheStore(File(cacheDir, "api-responses")) }

    // Loads for different paths run concurrently, so the read-modify-write must not interleave.
    private fun markStale(path: String, stale: Boolean) = synchronized(staleLock) {
        stalePaths = if (stale) stalePaths + path else stalePaths - path
    }

    suspend fun load(path: String, force: Boolean): String = withContext(Dispatchers.IO) {
        require(Regex("(?:boards\\.json|[a-z0-9]+/(?:catalog\\.json|thread/[0-9]+\\.json))").matches(path))
        // Check before cache lookup, network access, and stale-response fallback.
        require(BoardPolicy.allowsPath(path)) { "This board is unavailable in the Play build" }
        val requestedAt = System.currentTimeMillis()
        // Fresh cache hits never wait on network activity, not even for this path.
        if (!force) store?.read(path)?.takeIf { it.fresh(path, requestedAt) }?.let { return@withContext it.body }
        pathLocks.withLock(path) { fetch(path, force, requestedAt) }
    }

    private suspend fun fetch(path: String, force: Boolean, requestedAt: Long): String {
        val cached = store?.read(path)
        val now = System.currentTimeMillis()
        // A forced load is satisfied by a response fetched after it was requested (e.g. by a concurrent load).
        if (cached != null && cached.fresh(path, now) && (!force || cached.checked >= requestedAt)) return cached.body
        rateGate.acquire()
        val connection = URL("https://a.4cdn.org/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "TRFL/${BuildConfig.VERSION_NAME} (Android read-only reader)")
            cached?.etag?.takeIf { it.isNotEmpty() }?.let { connection.setRequestProperty("If-None-Match", it) }
            cached?.modified?.takeIf { it.isNotEmpty() }?.let { connection.setRequestProperty("If-Modified-Since", it) }
            val status = connection.responseCode
            if (status == 304 && cached != null) {
                markStale(path, false)
                runCatching { store?.write(path, cached.copy(checked = System.currentTimeMillis())) }
                return cached.body
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
            markStale(path, false)
            runCatching { store?.write(path, CachedResponse(body, System.currentTimeMillis(), connection.getHeaderField("ETag") ?: "", connection.getHeaderField("Last-Modified") ?: "")) }
            return body
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // A confirmed dead thread must not be resurrected by disposable cache data.
            if (cached != null && !(e is ApiException && e.status in 400..499)) {
                markStale(path, true)
                return cached.body
            } else throw e
        } finally { connection.disconnect() }
    }
}

suspend fun request(path: String, force: Boolean = false): String = ApiRequests.load(path, force)
