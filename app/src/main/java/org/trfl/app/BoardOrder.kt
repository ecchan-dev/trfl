package org.trfl.app

fun orderBoards(boards: List<Board>, order: List<String>, favorites: Set<String> = emptySet()): List<Board> {
    val ranks = order.withIndex().associate { it.value to it.index }
    return boards.sortedWith(compareBy<Board> { it.id !in favorites }.thenBy { ranks[it.id] ?: Int.MAX_VALUE })
}

/** Move before/after the visible neighbor, keeping hidden boards in the saved order. */
fun moveBoard(order: List<String>, id: String, neighbor: String): List<String> {
    val from = order.indexOf(id)
    val to = order.indexOf(neighbor)
    if (from < 0 || to < 0 || from == to) return order
    return order.toMutableList().apply { removeAt(from); add(to, id) }
}
