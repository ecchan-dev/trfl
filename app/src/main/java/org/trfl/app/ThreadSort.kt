package org.trfl.app

enum class ThreadSort(val label: String) {
    BUMP("Bump order"), REPLIES("Most replies"), IMAGES("Most images"),
    NEWEST("Newest first"), OLDEST("Oldest first"), LAST_REPLY("Latest reply");
    companion object { fun fromSaved(value: String?) = entries.firstOrNull { it.name == value } ?: BUMP }
}

/** The catalog endpoint already supplies native bump order. Stable sorts retain it on ties. */
fun sortThreads(posts: List<Post>, sort: ThreadSort): List<Post> = when (sort) {
    ThreadSort.BUMP -> posts
    ThreadSort.REPLIES -> posts.sortedByDescending { it.replies }
    ThreadSort.IMAGES -> posts.sortedByDescending { it.images }
    ThreadSort.NEWEST -> posts.sortedByDescending { it.created.takeIf { time -> time > 0 } ?: it.id }
    ThreadSort.OLDEST -> posts.sortedBy { it.created.takeIf { time -> time > 0 } ?: it.id }
    ThreadSort.LAST_REPLY -> posts.sortedByDescending { it.lastReply.takeIf { time -> time > 0 } ?: it.created }
}
