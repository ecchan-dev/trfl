package org.trfl.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PostLinksTest {
    @Test fun findsUrlsAndExcludesTrailingPunctuation() {
        val body = "See https://example.org/a?b=c, and HTTP://example.com/test."
        assertEquals(listOf("https://example.org/a?b=c", "HTTP://example.com/test"), postLinks(body).map { it.url })
        postLinks(body).forEach { assertEquals(it.url, body.substring(it.start, it.end)) }
    }

    @Test fun keepsWrappedLinksTogether() {
        val body = "before https://example.org/path after"
        assertEquals(7, wrapCutAtQuoteBoundary(body, 20))
        assertEquals(0, postLinks("javascript:alert(1)").size)
    }
}
