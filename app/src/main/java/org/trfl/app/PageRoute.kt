package org.trfl.app

data class PageRoute(val tab: String, val board: String = "", val thread: Long = 0) {
    val key: String get() = "$tab|$board|$thread"
    companion object {
        fun parse(value: String): PageRoute {
            val parts = value.split('|')
            return PageRoute(parts[0], parts[1], parts[2].toLong())
        }
    }
}

fun nextHistory(history: List<String>, route: String): List<String> {
    if (history.lastOrNull() == route) return history
    val existing = history.indexOf(route)
    return if (existing >= 0) history.take(existing + 1) else (history + route).takeLast(8)
}
