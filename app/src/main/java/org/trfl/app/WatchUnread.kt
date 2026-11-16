package org.trfl.app

/** Missing baselines must not turn an entire existing thread into new replies. */
fun unreadReplies(total: Int, seen: Int?): Int = (total - (seen ?: total)).coerceAtLeast(0)

/** Background checks are independent of the opt-in notification setting, but skip dead threads. */
fun shouldCheckWatchedThreads(watched: Set<String>, dead: Set<String> = emptySet()): Boolean = watched.any { it !in dead }
