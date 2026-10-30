package org.trfl.app

import java.net.URI

fun parseThreadLink(value: String?): PageRoute? = runCatching {
    val uri = URI(value ?: return null)
    if (uri.scheme !in setOf("https", "http") || uri.host !in setOf("boards.4chan.org", "boards.4channel.org")) return null
    val match = Regex("^/([a-z0-9]+)/thread/([0-9]+)(?:/.*)?$").matchEntire(uri.path) ?: return null
    if (!BoardPolicy.allows(match.groupValues[1])) return null
    val id = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
    PageRoute("Boards", match.groupValues[1], id)
}.getOrNull()
