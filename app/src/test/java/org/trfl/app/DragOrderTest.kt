package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class DragOrderTest {
    @Test fun commitOnlyNeedsOneFinalMove() {
        val original = listOf("a", "g", "lit", "wg")
        val down = listOf("g", "lit", "a", "wg")
        assertEquals(down, moveBoard(original, "a", dragDropNeighbor(original, down, "a")!!))
        val up = listOf("wg", "a", "g", "lit")
        assertEquals(up, moveBoard(original, "wg", dragDropNeighbor(original, up, "wg")!!))
    }
    @Test fun unchangedOrCancelledDragDoesNotWrite() {
        val ids = listOf("a", "g")
        assertNull(dragDropNeighbor(ids, ids, "a"))
        assertNull(dragDropNeighbor(ids, ids, "missing"))
    }
    @Test fun crossingOnlyAnEdgeDoesNotSwap() {
        assertEquals(0, crossedDragNeighbor(160f, 100f, 0f, 200f, 6f))
        assertEquals(0, crossedDragNeighbor(204f, 100f, 0f, 200f, 6f))
        assertEquals(1, crossedDragNeighbor(207f, 100f, 0f, 200f, 6f))
        assertEquals(-1, crossedDragNeighbor(-7f, 100f, 0f, 200f, 6f))
    }
    @Test fun cannotCrossListOrFavoriteGroupBoundary() {
        assertEquals(0, crossedDragNeighbor(-100f, 100f, null, 200f, 6f))
        assertEquals(0, crossedDragNeighbor(400f, 100f, 0f, null, 6f))
    }
}
