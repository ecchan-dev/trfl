package org.trfl.app

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Character ranges (from the leading `>` to the end of the line, newline excluded) of greentext lines:
 * lines whose first non-space character is `>`, except those starting with a `>>123` post quote or
 * a `>>>/board/` cross-board link.
 */
internal fun greentextRanges(body: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var lineStart = 0
    while (lineStart <= body.length) {
        val newline = body.indexOf('\n', lineStart).let { if (it < 0) body.length else it }
        var start = lineStart
        while (start < newline && body[start].isWhitespace()) start++
        val line = body.substring(start, newline)
        val isLink = line.startsWith(">>") && (line.getOrNull(2)?.isDigit() == true || line.startsWith(">>>/"))
        if (line.startsWith('>') && !isLink) ranges += start until newline
        lineStart = newline + 1
    }
    return ranges
}

@Composable
fun QuoteBody(body: String, onQuote: (Long) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    // Mid-tone greens that stay readable on light and dark surfaces respectively.
    val green = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color(0xFF5B7D1E) else Color(0xFF9CCC65)
    val uriHandler = LocalUriHandler.current
    val links = postLinks(body)
    val references = Regex(">>([0-9]+)").findAll(body).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
    val text = buildAnnotatedString {
        append(body)
        // Greentext first so the link accent added afterwards wins where they overlap.
        greentextRanges(body).forEach { addStyle(SpanStyle(color = green), it.first, it.last + 1) }
        var end = 0
        val quotes = Regex(">>([0-9]+)").findAll(body).toList()
        val spans = (quotes.map { Triple(it.range.first, it.range.last + 1, "quote" to it.groupValues[1]) } +
            links.map { Triple(it.start, it.end, "url" to it.url) }).sortedBy { it.first }
        spans.forEach { (start, stop, annotation) ->
            if (start < end) return@forEach
            addStringAnnotation(annotation.first, annotation.second, start, stop)
            addStyle(SpanStyle(color = accent), start, stop)
            end = stop
        }
    }
    ClickableText(text, modifier = Modifier.semantics {
        customActions = references.map { id -> CustomAccessibilityAction("Preview reply $id") { onQuote(id); true } } +
            links.map { link -> CustomAccessibilityAction("Open ${link.url}") {
                try { uriHandler.openUri(link.url); true } catch (_: IllegalArgumentException) { false } catch (_: android.content.ActivityNotFoundException) { false }
            } }
    }, style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), onClick = { offset ->
        val url = text.getStringAnnotations("url", offset, offset).firstOrNull()?.item
        if (url != null) {
            try { uriHandler.openUri(url) } catch (_: IllegalArgumentException) { } catch (_: android.content.ActivityNotFoundException) { }
        } else {
            text.getStringAnnotations("quote", offset, offset).firstOrNull()?.item?.toLongOrNull()?.let(onQuote)
        }
    })
}
