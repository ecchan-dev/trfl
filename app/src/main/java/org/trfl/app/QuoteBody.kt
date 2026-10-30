package org.trfl.app

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun QuoteBody(body: String, onQuote: (Long) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val references = Regex(">>([0-9]+)").findAll(body).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()
    val text = buildAnnotatedString {
        var end = 0
        Regex(">>([0-9]+)").findAll(body).forEach { match ->
            append(body.substring(end, match.range.first))
            pushStringAnnotation("quote", match.groupValues[1])
            withStyle(SpanStyle(color = accent)) { append(match.value) }
            pop()
            end = match.range.last + 1
        }
        append(body.substring(end))
    }
    ClickableText(text, modifier = Modifier.semantics {
        customActions = references.map { id -> CustomAccessibilityAction("Preview reply $id") { onQuote(id); true } }
    }, style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), onClick = { offset ->
        text.getStringAnnotations("quote", offset, offset).firstOrNull()?.item?.toLongOrNull()?.let(onQuote)
    })
}
