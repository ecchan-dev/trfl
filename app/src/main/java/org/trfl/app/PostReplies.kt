package org.trfl.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Reverse quote links, counted once per replying post. Only posts in this thread are indexed. */
fun indexPostReplies(posts: List<Post>): Map<Long, List<Post>> {
    val ids = posts.map { it.id }.toSet()
    val result = linkedMapOf<Long, MutableList<Post>>()
    posts.forEach { post ->
        Regex(">>([0-9]+)").findAll(post.body).mapNotNull { it.groupValues[1].toLongOrNull() }
            .filter { it in ids && it != post.id }.distinct().forEach { target -> result.getOrPut(target) { mutableListOf() }.add(post) }
    }
    return result
}

@Composable
fun ReplyCountPill(count: Int, onClick: () -> Unit) {
    if (count == 0) {
        Box(Modifier.height(24.dp).padding(horizontal = 10.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "0 replies" }) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    // Keep the visual header compact; Compose still expands the touch target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(24.dp).semantics { contentDescription = "$count replies; show replies" }) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}
