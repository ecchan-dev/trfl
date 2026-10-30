package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class FastScrollTest {
    @Test fun actualEndpointsOverrideVisibleItemEstimates() {
        assertEquals(1f, scrollbarPosition(83f, 17f, 100, true, false))
        assertEquals(1f, scrollbarPosition(0f, 1f, 100, true, false))
        assertEquals(0f, scrollbarPosition(5f, 12f, 100, false, true))
        assertEquals(0.5f, scrollbarPosition(40f, 20f, 100, true, true))
        assertEquals(0f, scrollbarPosition(0f, 0f, 0, false, false))
    }

    @Test fun endpointsAndMidpoint() {
        assertEquals(0, fastScrollIndex(0f, 101))
        assertEquals(50, fastScrollIndex(0.5f, 101))
        assertEquals(100, fastScrollIndex(1f, 101))
    }
    @Test fun clampsAndHandlesEmptyLists() {
        assertEquals(0, fastScrollIndex(-1f, 20))
        assertEquals(19, fastScrollIndex(2f, 20))
        assertEquals(0, fastScrollIndex(1f, 0))
        assertEquals(0, fastScrollIndex(1f, 1))
    }
}
