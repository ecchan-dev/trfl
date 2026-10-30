package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class BoardSwipeTest {
    @Test fun followsTheDisplayedOrderIncludingFavorites() {
        val order = listOf("g", "lit", "a")
        assertEquals("lit", adjacentBoard(order, "g", 1))
        assertEquals("g", adjacentBoard(order, "lit", -1))
    }
    @Test fun neverWrapsOrInventsABoard() {
        assertNull(adjacentBoard(listOf("g", "a"), "g", -1))
        assertNull(adjacentBoard(listOf("g", "a"), "a", 1))
        assertNull(adjacentBoard(emptyList(), "g", 1))
        assertNull(adjacentBoard(listOf("g"), "a", 1))
    }
    @Test fun respectsFilteredSequences() {
        assertEquals("lit", adjacentBoard(listOf("g", "lit"), "g", 1))
        assertNull(adjacentBoard(listOf("g", "lit"), "g", 2))
    }
}
