package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTransitionTest {
    private val root = PageRoute("Boards")
    private val board = PageRoute("Boards", "g")
    private val thread = PageRoute("Boards", "g", 42)
    @Test fun forwardNavigationDoesNotSlide() {
        assertEquals(0f, pageTransitionDirection(root, board))
        assertEquals(0f, pageTransitionDirection(board, thread))
    }
    @Test fun backReturnsFromLeft() {
        assertEquals(-1f, pageTransitionDirection(thread, board))
        assertEquals(0f, pageTransitionDirection(board, root))
    }
    @Test fun tabChangesAndPeerBoardsDoNotTriggerHierarchySlides() {
        assertEquals(0f, pageTransitionDirection(board, PageRoute("Saves")))
        assertEquals(0f, pageTransitionDirection(board, PageRoute("Boards", "a")))
    }
}
