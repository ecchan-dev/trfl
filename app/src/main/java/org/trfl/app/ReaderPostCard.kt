package org.trfl.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The same post surface is used in the thread and quote overlays. */
@Composable
fun ReaderPostCard(post: Post, compact: Boolean, replyCount: Int, onClick: () -> Unit,
                   onImage: () -> Unit, onQuote: (Long) -> Unit, onReplies: () -> Unit,
                   modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null,
                   onPostId: ((Long) -> Unit)? = null) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val heading = post.title.ifBlank { "Anonymous · ${post.id}" }
                val idText = post.id.toString()
                val idStart = heading.lastIndexOf(idText)
                val title = buildAnnotatedString {
                    if (onPostId != null && idStart >= 0) {
                        append(heading.substring(0, idStart))
                        withLink(LinkAnnotation.Clickable("quote-${post.id}") { onPostId(post.id) }) { append(idText) }
                        append(heading.substring(idStart + idText.length))
                    } else append(heading)
                }
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (!compact) ReplyCountPill(replyCount, onReplies)
            }
            WrappedPostBody(post, compact, onImage, onQuote)
            if (compact) ThreadMetrics(post)
            footer?.invoke()
        }
    }
}
