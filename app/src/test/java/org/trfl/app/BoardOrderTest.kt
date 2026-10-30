package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class BoardOrderTest {
    @Test fun savedOrderKeepsNewBoards() {
        val boards = listOf(Board("a", "Anime", true), Board("g", "Technology", true), Board("lit", "Literature", true))
        assertEquals(listOf("g", "a", "lit"), orderBoards(boards, listOf("gone", "g")).map { it.id })
    }
    @Test fun movingAcrossHiddenNeighborsPreservesAllBoards() {
        assertEquals(listOf("hidden", "g", "a"), moveBoard(listOf("a", "hidden", "g"), "a", "g"))
        assertEquals(listOf("g", "a", "hidden"), moveBoard(listOf("a", "hidden", "g"), "g", "a"))
    }
    @Test fun favoritesAlwaysLeadWhileKeepingManualOrder() {
        val boards = listOf(Board("a", "Anime", true), Board("g", "Technology", true), Board("lit", "Literature", true))
        assertEquals(listOf("lit", "g", "a"), orderBoards(boards, listOf("a", "lit", "g"), setOf("g", "lit")).map { it.id })
    }
    @Test fun missingNeighborIsSafe() {
        assertEquals(listOf("a", "g"), moveBoard(listOf("a", "g"), "a", "missing"))
    }
}
