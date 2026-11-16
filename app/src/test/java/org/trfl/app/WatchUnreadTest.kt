package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchUnreadTest {
    @Test fun backgroundChecksFollowWatchedThreads() {
        assertEquals(false, shouldCheckWatchedThreads(emptySet()))
        assertEquals(true, shouldCheckWatchedThreads(setOf("g/123")))
    }
    @Test fun backgroundChecksIgnoreDeadThreads() {
        assertEquals(false, shouldCheckWatchedThreads(setOf("g/123"), setOf("g/123")))
        assertEquals(true, shouldCheckWatchedThreads(setOf("g/123", "g/456"), setOf("g/123")))
        assertEquals(false, shouldCheckWatchedThreads(emptySet(), setOf("g/123")))
        assertEquals(true, shouldCheckWatchedThreads(setOf("g/123"), setOf("w/999")))
    }
    @Test fun missingBaselineDoesNotNotifyOldReplies() { assertEquals(0, unreadReplies(100, null)) }
    @Test fun onlyNewRepliesCount() { assertEquals(4, unreadReplies(104, 100)) }
    @Test fun readingClearsUnread() { assertEquals(0, unreadReplies(104, 104)) }
    @Test fun SmallerSnapshotsDoNotProduceNegativeUnread() { assertEquals(0, unreadReplies(99, 100)) }
}
