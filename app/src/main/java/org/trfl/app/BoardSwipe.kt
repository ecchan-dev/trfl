package org.trfl.app

/** No wrapping: swiping past either end leaves the current board unchanged. */
fun adjacentBoard(order: List<String>, current: String, delta: Int): String? {
    val index = order.indexOf(current)
    if (index < 0 || delta !in listOf(-1, 1)) return null
    return order.getOrNull(index + delta)
}
