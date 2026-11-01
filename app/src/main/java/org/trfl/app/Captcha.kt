package org.trfl.app

import android.graphics.BitmapFactory
import java.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Protocol observations, not a specification: Kuroba-Experimental bf3723ac,
// LoadChan4CaptchaUseCase and Chan4CaptchaLayoutViewModel (GPL source not copied).
internal data class CaptchaTask(val title: String?, val titleImage: ByteArray?, val images: List<ByteArray>)
internal class CaptchaChallenge(val target: PostingDraftKey, private val token: String, val tasks: List<CaptchaTask>, private val expiresAt: Long) {
    fun expired(now: Long) = now >= expiresAt
    fun answer(target: PostingDraftKey, choices: List<Int>, now: Long): CaptchaAnswer? {
        if (target != this.target || expired(now) || choices.size != tasks.size || choices.indices.any { choices[it] !in tasks[it].images.indices }) return null
        return CaptchaAnswer(token, choices.joinToString(""))
    }
}
// Intentionally not a data class: don't accidentally include credentials in toString().
internal class CaptchaAnswer(val challenge: String, val response: String)

internal enum class CaptchaFailure { HTTP, NETWORK, ANTIBOT, COOLDOWN, UNSUPPORTED }
internal class CaptchaException(val failure: CaptchaFailure, val httpStatus: Int? = null) : Exception()
internal fun interface CaptchaTransport { fun load(target: PostingDraftKey): String }
internal fun captchaHttpMessage(status: Int?): String {
    val detail = when (status) {
        403 -> "access forbidden"
        429 -> "rate limited"
        in 300..399 -> "redirect not followed"
        404 -> "endpoint not found"
        else -> "request failed"
    }
    return if (status != null && status in 100..599) "CAPTCHA $detail (HTTP $status)." else "CAPTCHA request failed."
}
internal fun requireCaptchaTarget(target: PostingDraftKey, restricted: Boolean = BuildConfig.SFW_ONLY) {
    require(target.board.matches(Regex("[a-zA-Z0-9]{1,16}")) && (target.threadId == null || target.threadId > 0))
    require(BoardPolicy.allows(target.board, restricted || BuildConfig.SFW_ONLY)) { "Board unavailable in this build" }
}
// Initial, ticket-free request only. Kuroba bf3723ac and 4cdn tcaptcha.min.7.js
// both use this endpoint and query; ticket, ticket_resp, cookies and mcl are unsupported.
internal fun buildCaptchaRequest(target: PostingDraftKey): URL {
    requireCaptchaTarget(target)
    val query = "board=${target.board}" + (target.threadId?.let { "&thread_id=$it" } ?: "")
    return URL("https://sys.4chan.org/captcha?$query")
}

internal class HttpCaptchaTransport(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : CaptchaTransport {
    override fun load(target: PostingDraftKey): String {
        val connection = open(buildCaptchaRequest(target))
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Referer", "https://boards.4chan.org/${target.board}/")
            val status = connection.responseCode
            if (status != 200) throw CaptchaException(CaptchaFailure.HTTP, status.takeIf { it in 100..599 })
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > MAX_HTML) throw CaptchaException(CaptchaFailure.UNSUPPORTED)
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } catch (e: CaptchaException) { throw e
        } catch (_: java.io.IOException) { throw CaptchaException(CaptchaFailure.NETWORK)
        } finally { connection.disconnect() }
    }
}
private const val MAX_HTML = 2_000_000
private const val MAX_IMAGE = 200_000
internal fun validCaptchaImageBounds(width: Int, height: Int): Boolean =
    width in 1..1024 && height in 1..1024 && width.toLong() * height <= 1_048_576

private fun imagePixels(bytes: ByteArray): Long {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    require(validCaptchaImageBounds(opts.outWidth, opts.outHeight))
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    bitmap.recycle()
    return opts.outWidth.toLong() * opts.outHeight
}

internal object CaptchaParser {
    fun parse(html: String, target: PostingDraftKey, now: Long, validateImage: (ByteArray) -> Long = ::imagePixels): CaptchaChallenge {
        requireCaptchaTarget(target)
        require(html.length <= MAX_HTML)
        if (html.contains("mcl.spur.us", ignoreCase = true)) throw CaptchaException(CaptchaFailure.ANTIBOT)
        val marker = ".postMessage("
        val start = html.indexOf(marker).let { if (it < 0) -1 else it + marker.length }
        require(start >= 0 && html.getOrNull(start) == '{')
        var depth = 0; var quoted = false; var escaped = false; var end = -1
        for (i in start until html.length) {
            val c = html[i]
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when (c) { '"' -> quoted = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) { end = i + 1; break } } }
        }
        require(end > start)
        val wrapper = JSONObject(html.substring(start, end))
        val obj = wrapper.getJSONObject("twister")
        if (!obj.isNull("error") && obj.optString("error").isNotBlank()) throw CaptchaException(if (obj.optInt("cd", 0) > 0 || obj.optInt("pcd", 0) > 0) CaptchaFailure.COOLDOWN else CaptchaFailure.UNSUPPORTED)
        if (obj.optInt("pcd", 0) > 0 || obj.optBoolean("mpcd", false) || obj.optInt("mpcd", 0) > 0 || (!obj.isNull("pcd_msg") && obj.optString("pcd_msg").isNotBlank())) throw CaptchaException(CaptchaFailure.COOLDOWN)
        require(!obj.has("extTask") || obj.isNull("extTask"))
        val token = obj.getString("challenge")
        require(token.isNotBlank() && token.length <= 1024 && !token.equals("noop", true))
        // The referenced client uses 120 seconds as its missing-TTL fallback, but
        // rejects missing TTL for ordinary image tasks. Follow the stricter path.
        val ttl = obj.getInt("ttl"); require(ttl in 1..600)
        val rawTasks = obj.getJSONArray("tasks"); require(rawTasks.length() in 1..10)
        var totalPixels = 0L
        fun image(encoded: String): ByteArray {
            require(encoded.length <= MAX_IMAGE * 2)
            val bytes = Base64.getDecoder().decode(encoded.filterNot { it == '\n' || it == '\r' })
            require(bytes.size in 1..MAX_IMAGE)
            val pixels = validateImage(bytes)
            require(pixels in 1..1_048_576)
            totalPixels += pixels
            require(totalPixels <= 4_194_304) // At most 16 MiB decoded ARGB in the dialog.
            return bytes
        }
        val tasks = (0 until rawTasks.length()).map { index ->
            val task = rawTasks.getJSONObject(index)
            val rawTitle = if (task.isNull("str")) null else task.getString("str").takeIf { it.isNotBlank() }
            val rawImage = if (task.isNull("img")) null else task.getString("img").takeIf { it.isNotBlank() }
            val prompt = if (rawImage != null) CaptchaPrompt(null, image(rawImage)) else rawTitle?.let { parseCaptchaPrompt(it, ::image) }
            val title = prompt?.text
            val titleImage = prompt?.image
            require(title != null || titleImage != null)
            val items = task.getJSONArray("items"); require(items.length() in 1..10)
            CaptchaTask(title, titleImage, (0 until items.length()).map { image(items.getString(it)) })
        }
        return CaptchaChallenge(target, token, tasks, now + ttl * 1000L)
    }
}
