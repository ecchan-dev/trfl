package org.trfl.app

/** Neighbor to pass to moveBoard once a drag transaction commits. */
fun dragDropNeighbor(original: List<String>, reordered: List<String>, id: String): String? {
    val from = original.indexOf(id)
    val to = reordered.indexOf(id)
    if (from < 0 || to < 0 || from == to) return null
    return reordered.getOrNull(if (to > from) to - 1 else to + 1)
}

/** Require crossing a neighbor's center, plus a small dead band, to avoid oscillation. */
fun crossedDragNeighbor(center: Float, currentCenter: Float, previousCenter: Float?, nextCenter: Float?, deadBand: Float): Int = when {
    center < currentCenter && previousCenter != null && center < previousCenter - deadBand -> -1
    center > currentCenter && nextCenter != null && center > nextCenter + deadBand -> 1
    else -> 0
}
