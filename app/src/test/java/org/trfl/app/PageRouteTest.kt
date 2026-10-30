package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class PageRouteTest {
    @Test fun routeRoundTrips() {
        val route = PageRoute("Watched", "g", 123456789L)
        assertEquals(route, PageRoute.parse(route.key))
    }
    @Test fun threadReturnsToActualOriginNotAnInventedCatalog() {
        val root = PageRoute("Boards").key
        val saved = PageRoute("Bookmarks").key
        val thread = PageRoute("Bookmarks", "g", 42).key
        val history = nextHistory(nextHistory(listOf(root), saved), thread)
        assertEquals(saved, history.dropLast(1).last())
    }
    @Test fun settingsReturnsToTheOpenThread() {
        val thread = PageRoute("Boards", "g", 42).key
        val history = nextHistory(listOf(PageRoute("Boards").key, thread), PageRoute("Settings").key)
        assertEquals(thread, history.dropLast(1).last())
    }
    @Test fun revisitingRootDoesNotBuildNavigationLoops() {
        val root = PageRoute("Boards").key
        assertEquals(listOf(root), nextHistory(listOf(root, PageRoute("Watched").key), root))
        assertEquals(listOf(root), nextHistory(listOf(root), root))
    }
    @Test fun historyIsBounded() {
        var history = emptyList<String>()
        repeat(20) { history = nextHistory(history, PageRoute("Boards", "g", it.toLong()).key) }
        assertEquals(8, history.size)
        assertEquals(PageRoute("Boards", "g", 19).key, history.last())
    }
}
