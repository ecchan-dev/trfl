package org.trfl.app

/** Missing baselines must not turn an entire existing thread into new replies. */
fun unreadReplies(total: Int, seen: Int?): Int = (total - (seen ?: total)).coerceAtLeast(0)
