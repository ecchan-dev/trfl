package org.trfl.app

internal data class PostLink(val start: Int, val end: Int, val url: String)

internal fun postLinks(body: String): List<PostLink> =
    Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(body).mapNotNull { match ->
        val url = match.value.trimEnd('.', ',', '!', '?', ':', ';', ')', ']', '}')
        if (url.isEmpty()) null else PostLink(match.range.first, match.range.first + url.length, url)
    }.toList()
