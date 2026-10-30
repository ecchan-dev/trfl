package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class ThreadLinkTest {
    @Test fun supportsBothHostsAndOptionalSlug() {
        assertEquals(PageRoute("Boards", "g", 42), parseThreadLink("https://boards.4chan.org/g/thread/42/title#p44"))
        assertEquals(PageRoute("Boards", "a", 55), parseThreadLink("https://boards.4channel.org/a/thread/55"))
    }
    @Test fun rejectsForeignHostsAndMalformedIds() {
        assertNull(parseThreadLink("https://boards.4chan.org.evil.test/g/thread/42"))
        assertNull(parseThreadLink("file:///g/thread/42"))
        assertNull(parseThreadLink("https://boards.4chan.org/g/thread/0"))
        assertNull(parseThreadLink("https://boards.4chan.org/g/thread/no"))
        assertNull(parseThreadLink(null))
    }
}
