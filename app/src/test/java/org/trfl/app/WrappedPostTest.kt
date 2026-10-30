package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class WrappedPostTest {
    @Test fun quoteLinksAreNeverSplitAcrossTheThumbnailBoundary() {
        val text = "hello >>123456 world"
        val cut = wrapCutAtQuoteBoundary(text, 10)
        assertEquals(6, cut)
        assertEquals(text, text.take(cut) + text.drop(cut))
        assertTrue(text.drop(cut).startsWith(">>123456"))
    }
    @Test fun ordinaryTextAndNewlinesStayIntact() {
        val text = "first line\nsecond line"
        assertEquals(11, wrapCutAtQuoteBoundary(text, 11))
        assertEquals(0, wrapCutAtQuoteBoundary(text, -5))
        assertEquals(text.length, wrapCutAtQuoteBoundary(text, 1000))
        assertEquals(0, wrapCutAtQuoteBoundary("", 2))
    }
}
