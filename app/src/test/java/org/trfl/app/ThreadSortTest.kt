package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class ThreadSortTest {
    private val posts = listOf(
        Post(3, "", "", null, null, 5, images = 1, created = 30, lastReply = 31),
        Post(1, "", "", null, null, 20, images = 2, created = 10, lastReply = 50),
        Post(2, "", "", null, null, 5, images = 8, created = 20, lastReply = 40)
    )
    private fun ids(sort: ThreadSort) = sortThreads(posts, sort).map { it.id }
    @Test fun nativeBumpOrderIsUnmodified() { assertEquals(listOf(3L, 1L, 2L), ids(ThreadSort.BUMP)) }
    @Test fun countsSortDescendingAndRetainBumpOrderForTies() {
        assertEquals(listOf(1L, 3L, 2L), ids(ThreadSort.REPLIES))
        assertEquals(listOf(2L, 1L, 3L), ids(ThreadSort.IMAGES))
    }
    @Test fun datesAndLastReplySortIndependently() {
        assertEquals(listOf(3L, 2L, 1L), ids(ThreadSort.NEWEST))
        assertEquals(listOf(1L, 2L, 3L), ids(ThreadSort.OLDEST))
        assertEquals(listOf(1L, 2L, 3L), ids(ThreadSort.LAST_REPLY))
    }
    @Test fun unknownSettingsAndEmptyCatalogAreSafe() {
        assertEquals(ThreadSort.BUMP, ThreadSort.fromSaved("removed"))
        assertEquals(ThreadSort.BUMP, ThreadSort.fromSaved(null))
        assertTrue(sortThreads(emptyList(), ThreadSort.REPLIES).isEmpty())
    }
}
