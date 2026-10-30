package org.trfl.app

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun QuoteBody(body: String, onQuote: (Long) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    val links = postLinks(body)
    val references = Regex(">>([0-9]+)").findAll(body).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
    val text = buildAnnotatedString {
        var end = 0
        val quotes = Regex(">>([0-9]+)").findAll(body).toList()
        val spans = (quotes.map { Triple(it.range.first, it.range.last + 1, "quote" to it.groupValues[1]) } +
            links.map { Triple(it.start, it.end, "url" to it.url) }).sortedBy { it.first }
        spans.forEach { (start, stop, annotation) ->
            if (start < end) return@forEach
            append(body.substring(end, start))
            pushStringAnnotation(annotation.first, annotation.second)
            withStyle(SpanStyle(color = accent)) { append(body.substring(start, stop)) }
            pop()
            end = stop
        }
        append(body.substring(end))
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
