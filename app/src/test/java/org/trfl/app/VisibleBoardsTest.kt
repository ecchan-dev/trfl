package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class VisibleBoardsTest {
    private val boards = listOf(Board("a", "Anime", true), Board("b", "Random", false), Board("g", "Technology", true), Board("lit", "Literature", true))

    @Test fun adultBoardsOnlyWhenEnabled() {
        assertEquals(listOf("a", "g", "lit"), visibleBoards(boards, emptyList(), emptySet(), adult = false, favoritesOnly = false).map { it.id })
        assertEquals(listOf("a", "b", "g", "lit"), visibleBoards(boards, emptyList(), emptySet(), adult = true, favoritesOnly = false).map { it.id })
    }
    @Test fun favoritesLeadAndCanBeShownAlone() {
        assertEquals(listOf("lit", "a", "g"), visibleBoards(boards, emptyList(), setOf("lit"), adult = false, favoritesOnly = false).map { it.id })
        assertEquals(listOf("lit"), visibleBoards(boards, emptyList(), setOf("lit", "b"), adult = false, favoritesOnly = true).map { it.id })
    }
    @Test fun queryMatchesIdOrTitleIgnoringCase() {
        assertEquals(listOf("g"), visibleBoards(boards, emptyList(), emptySet(), adult = false, favoritesOnly = false, query = "TECH").map { it.id })
        assertEquals(listOf("lit"), visibleBoards(boards, emptyList(), emptySet(), adult = false, favoritesOnly = false, query = "li").map { it.id })
        assertEquals(emptyList<String>(), visibleBoards(boards, emptyList(), emptySet(), adult = false, favoritesOnly = false, query = "random").map { it.id })
    }
    @Test fun keepsSavedOrder() {
        assertEquals(listOf("g", "a", "lit"), visibleBoards(boards, listOf("g", "a"), emptySet(), adult = false, favoritesOnly = false).map { it.id })
    }
}
