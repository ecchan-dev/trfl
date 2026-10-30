package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchUnreadTest {
    @Test fun missingBaselineDoesNotNotifyOldReplies() { assertEquals(0, unreadReplies(100, null)) }
    @Test fun onlyNewRepliesCount() { assertEquals(4, unreadReplies(104, 100)) }
    @Test fun readingClearsUnread() { assertEquals(0, unreadReplies(104, 104)) }
    @Test fun SmallerSnapshotsDoNotProduceNegativeUnread() { assertEquals(0, unreadReplies(99, 100)) }
}
