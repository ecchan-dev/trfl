package org.trfl.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Header segments for a thread post: poster (name, trip, capcode), poster ID, age, then "No. <id>" last. */
fun postMetaParts(post: Post, nowSeconds: Long): List<String> = buildList {
    add(buildString {
        append(post.name.ifBlank { "Anonymous" })
        if (post.trip.isNotBlank()) append(' ').append(post.trip)
        if (post.capcode.isNotBlank()) append(" ## ").append(post.capcode.replaceFirstChar { it.uppercaseChar() })
    })
    if (post.posterId.isNotBlank()) add("ID: ${post.posterId}")
    if (post.created > 0) add(relativeTime(post.created, nowSeconds))
    add("No. ${post.id}")
}

/** Builds [text] with its last occurrence of [linkText] clickable (when [onPostId] is set) and coloured [linkColor]. */
private fun headingText(text: String, linkText: String, id: Long, onPostId: ((Long) -> Unit)?,
                        linkColor: Color? = null, boldPrefix: Int = 0): AnnotatedString = buildAnnotatedString {
    val linkStart = text.lastIndexOf(linkText)
    fun plainPart(from: Int, to: Int) {
        if (from >= to) return
        val boldEnd = boldPrefix.coerceIn(from, to)
        if (boldEnd > from) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(from, boldEnd)) }
        append(text.substring(boldEnd, to))
    }
    if (onPostId != null && linkStart >= 0) {
        val linkEnd = linkStart + linkText.length
        plainPart(0, linkStart)
        withLink(LinkAnnotation.Clickable("quote-$id") { onPostId(id) }) {
            if (linkColor != null) withStyle(SpanStyle(color = linkColor)) { append(linkText) } else append(linkText)
        }
        plainPart(linkEnd, text.length)
    } else plainPart(0, text.length)
}

/** Pinned/closed markers shown beside a thread's title (list cards and catalog grid cards). */
@Composable
internal fun PostFlags(post: Post) {
    if (post.sticky) Icon(Icons.Default.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
    if (post.closed) Icon(Icons.Default.Lock, "Closed", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The same post surface is used in the thread and quote overlays. */
@Composable
fun ReaderPostCard(post: Post, compact: Boolean, replyCount: Int, onClick: () -> Unit,
                   onImage: () -> Unit, onQuote: (Long) -> Unit, onReplies: () -> Unit,
                   modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null,
                   onPostId: ((Long) -> Unit)? = null) {
    val nowSeconds = System.currentTimeMillis() / 1000
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    val heading = post.title.ifBlank { "Anonymous · ${post.id}" }
                    Text(headingText(heading, post.id.toString(), post.id, onPostId), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    PostFlags(post)
                    if (post.created > 0) Text(relativeTime(post.created, nowSeconds), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (post.title.isNotBlank()) Text(post.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PostFlags(post)
                            val parts = postMetaParts(post, nowSeconds)
                            Text(headingText(parts.joinToString(" · "), "No. ${post.id}", post.id, onPostId,
                                linkColor = MaterialTheme.colorScheme.primary, boldPrefix = parts.first().length),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ReplyCountPill(replyCount, onReplies)
                }
            }
            WrappedPostBody(post, compact, onImage, onQuote)
            if (compact) ThreadMetrics(post)
            footer?.invoke()
        }
    }
}
