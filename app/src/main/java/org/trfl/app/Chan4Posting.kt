package org.trfl.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

// Protocol observations, not a specification: Kuroba-Experimental bf3723ac, Chan4ReplyCall
// (GPL source not copied). Text-only replies; attachments and new threads are not mapped.
internal sealed interface Chan4PostResult {
    data class Posted(val threadId: Long, val postId: Long) : Chan4PostResult
    /** The server refused the CAPTCHA answer; a fresh one is required. */
    data object CaptchaRejected : Chan4PostResult
    data class Rejected(val message: String) : Chan4PostResult
    /** The request may have reached the server. Never retry automatically. */
    data class Unknown(val message: String) : Chan4PostResult
}

internal fun chan4PostUrl(target: PostingDraftKey): URL {
    requireCaptchaTarget(target)
    return URL("https://sys.4chan.org/${target.board}/post")
}

internal fun chan4PostParts(target: PostingDraftKey, comment: String, answer: CaptchaAnswer, password: String): List<PostingMultipartPart> {
    val threadId = requireNotNull(target.threadId) { "Replies only" }
    require(comment.isNotBlank() && '\u0000' !in comment)
    fun field(name: String, value: String) = PostingMultipartPart(name, value.toByteArray(Charsets.UTF_8))
    return listOf(
        field("mode", "regist"), field("pwd", password), field("resto", threadId.toString()),
        field("name", ""), field("email", ""), field("com", comment),
        field("t-challenge", answer.challenge), field("t-response", answer.response),
    )
}

internal fun parseChan4PostResponse(status: Int, html: String): Chan4PostResult {
    if (html.contains("You forgot to solve the CAPTCHA", true) || html.contains("You seem to have mistyped the CAPTCHA", true)) return Chan4PostResult.CaptchaRejected
    Regex("<span id=\"errmsg\"[^>]*>(.*?)</span>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(html)?.let { match ->
        val message = match.groupValues[1].replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#039;", "'").trim()
        return Chan4PostResult.Rejected(message.take(400).ifBlank { "Post rejected." })
    }
    if (status != 200) return Chan4PostResult.Rejected(if (status == 403) "Blocked by the server (HTTP 403). Get a new CAPTCHA and retry." else "Post failed (HTTP $status).")
    val receipt = Regex("<!-- thread:([0-9]+),no:([0-9]+) -->").find(html)
    val threadId = receipt?.groupValues?.get(1)?.toLongOrNull(); val postId = receipt?.groupValues?.get(2)?.toLongOrNull()
    return if (threadId != null && postId != null && postId > 0) Chan4PostResult.Posted(if (threadId == 0L) postId else threadId, postId)
    else Chan4PostResult.Unknown("Outcome unknown. Check the thread before trying again.")
}

/** Exactly one attempt per call; the caller decides whether the user may try again. */
internal class Chan4PostClient(
    private val userAgent: String,
    private val cookies: (String) -> String?,
    private val storeCookie: (String, String) -> Unit,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun reply(target: PostingDraftKey, comment: String, answer: CaptchaAnswer): Chan4PostResult {
        val url = chan4PostUrl(target)
        val random = SecureRandom()
        fun token(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val body = PostingMultipart.encode(chan4PostParts(target, comment, answer, token(12)), "trfl-${token(16)}")
        val connection = try { open(url) } catch (_: IOException) { return Chan4PostResult.Rejected("Network error. Not sent.") }
        var sent = false
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.bytes.size)
            connection.setRequestProperty("Content-Type", body.contentType)
            // Must match the CAPTCHA page: clearance cookies are bound to the browser identity.
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Origin", "https://boards.4chan.org")
            connection.setRequestProperty("Referer", "https://boards.4chan.org/${target.board}/thread/${target.threadId}")
            cookies(url.toString())?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
            connection.connect()
            sent = true
            connection.outputStream.use { it.write(body.bytes) }
            val status = connection.responseCode
            connection.headerFields?.forEach { (name, values) -> if (name.equals("Set-Cookie", true)) values.forEach { storeCookie(url.toString(), it) } }
            val html = (if (status >= 400) connection.errorStream else connection.inputStream)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() < MAX_RESPONSE) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                out.toString("UTF-8")
            } ?: ""
            return parseChan4PostResponse(status, html)
        } catch (_: IOException) {
            return if (sent) Chan4PostResult.Unknown("Connection lost while posting. Check the thread before trying again.")
            else Chan4PostResult.Rejected("Network error. Not sent.")
        } finally { connection.disconnect() }
    }
    private companion object { const val MAX_RESPONSE = 1_000_000 }
}
