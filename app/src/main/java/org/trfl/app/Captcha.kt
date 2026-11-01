package org.trfl.app

import android.graphics.BitmapFactory
import android.util.Base64
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

internal fun interface CaptchaTransport { fun load(target: PostingDraftKey): String }
internal class HttpCaptchaTransport : CaptchaTransport {
    override fun load(target: PostingDraftKey): String {
        require(target.board.matches(Regex("[a-zA-Z0-9]{1,16}")) && (target.threadId == null || target.threadId > 0))
        val query = "board=${target.board}" + (target.threadId?.let { "&thread_id=$it" } ?: "")
        val connection = URL("https://sys.4chan.org/captcha?$query").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            check(connection.responseCode == 200)
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(out.size() + n <= MAX_HTML)
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
private const val MAX_HTML = 2_000_000
private const val MAX_IMAGE = 200_000
internal object CaptchaParser {
    fun parse(html: String, target: PostingDraftKey, now: Long): CaptchaChallenge {
        require(html.length <= MAX_HTML && !html.contains("mcl.spur.us", ignoreCase = true))
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
        require(!obj.has("error") || obj.isNull("error") || obj.optString("error").isBlank())
        require(obj.optInt("cd", 0) <= 0 && obj.optInt("pcd", 0) <= 0 && !obj.has("pcd_msg") && !obj.has("cd_until"))
        val token = obj.getString("challenge")
        require(token.isNotBlank() && token.length <= 1024 && !token.equals("noop", true))
        val ttl = obj.getInt("ttl"); require(ttl in 1..600)
        val rawTasks = obj.getJSONArray("tasks"); require(rawTasks.length() in 1..10)
        val tasks = (0 until rawTasks.length()).map { index ->
            val task = rawTasks.getJSONObject(index)
            val title = task.optString("str").takeIf { it.isNotBlank() && it.length <= 500 }
            val titleImage = task.optString("img").takeIf { it.isNotBlank() }?.let(::image)
            require(title != null || titleImage != null)
            val items = task.getJSONArray("items"); require(items.length() in 1..10)
            CaptchaTask(title, titleImage, (0 until items.length()).map { image(items.getString(it)) })
        }
        return CaptchaChallenge(target, token, tasks, now + ttl * 1000L)
    }
    private fun image(encoded: String): ByteArray {
        require(encoded.length <= MAX_IMAGE * 2)
        val bytes = Base64.decode(encoded, Base64.DEFAULT); require(bytes.size in 1..MAX_IMAGE)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth in 1..1024 && opts.outHeight in 1..1024 && opts.outWidth.toLong() * opts.outHeight <= 1_048_576)
        return bytes
    }
}
