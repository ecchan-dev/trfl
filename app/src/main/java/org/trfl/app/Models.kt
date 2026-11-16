package org.trfl.app

import android.text.Html
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Board(val id: String, val title: String, val safe: Boolean)
/** New fields are appended with defaults so positional call sites keep compiling. */
data class Post(val id: Long, val title: String, val body: String, val image: String?, val thumb: String?, val replies: Int,
    val images: Int = 0, val created: Long = 0, val lastReply: Long = 0, val video: Boolean = false,
    val name: String = "", val trip: String = "", val posterId: String = "", val capcode: String = "",
    val filename: String = "", val sticky: Boolean = false, val closed: Boolean = false) {
    val isVideo: Boolean get() = video || image?.substringBefore('?')?.let { it.endsWith(".webm", true) || it.endsWith(".mp4", true) } == true
}
fun plain(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()
/** [decode] turns 4chan's HTML fields into plain text; replaceable so the mapping is testable off-device. */
fun parsePost(p: JSONObject, board: String, decode: (String) -> String = ::plain): Post {
    val tim = p.optLong("tim")
    val ext = p.optString("ext")
    val filename = p.optString("filename")
    return Post(p.getLong("no"), decode(p.optString("sub")), decode(p.optString("com")),
        if (tim > 0) "https://i.4cdn.org/$board/$tim$ext" else null,
        if (tim > 0) "https://i.4cdn.org/$board/${tim}s.jpg" else null, p.optInt("replies"), p.optInt("images"), p.optLong("time"),
        p.optLong("last_modified", p.optJSONArray("last_replies")?.let { replies ->
            (0 until replies.length()).maxOfOrNull { replies.getJSONObject(it).optLong("time") }
        } ?: p.optLong("time")),
        name = decode(p.optString("name")),
        trip = p.optString("trip"),
        posterId = p.optString("id"),
        capcode = p.optString("capcode"),
        filename = if (filename.isNotEmpty()) decode(filename) + ext else "",
        sticky = p.optInt("sticky") == 1,
        closed = p.optInt("closed") == 1)
}

/** Compact age such as "now", "5m", "3h", "2d", falling back to a short date after a week. */
fun relativeTime(epochSeconds: Long, nowSeconds: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val diff = nowSeconds - epochSeconds
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        diff < 7 * 86400 -> "${diff / 86400}d"
        else -> {
            val date = Instant.ofEpochSecond(epochSeconds).atZone(zone)
            val sameYear = date.year == Instant.ofEpochSecond(nowSeconds).atZone(zone).year
            date.format(DateTimeFormatter.ofPattern(if (sameYear) "MMM d" else "MMM d, yyyy", locale))
        }
    }
}
