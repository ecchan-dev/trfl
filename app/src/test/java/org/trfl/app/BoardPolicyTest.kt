package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

class BoardPolicyTest {
    @Test fun playAllowsReviewedSafeBoardsOnly() {
        listOf("g", "a", "lit", "3", "w").forEach { assertTrue(BoardPolicy.allows(it, true)) }
        listOf("b", "pol", "s", "hc", "h", "wg", "r9k", "unknown", "", "G", "../g").forEach {
            assertFalse(it, BoardPolicy.allows(it, true))
        }
    }

    @Test fun playBlocksCatalogAndThreadPaths() {
        assertTrue(BoardPolicy.allowsPath("boards.json", true))
        assertTrue(BoardPolicy.allowsPath("g/catalog.json", true))
        assertTrue(BoardPolicy.allowsPath("lit/thread/123.json", true))
        assertFalse(BoardPolicy.allowsPath("b/catalog.json", true))
        assertFalse(BoardPolicy.allowsPath("pol/thread/123.json", true))
    }

    @Test fun standardBuildKeepsBoardAccess() {
        assertTrue(BoardPolicy.allows("b", false))
        assertTrue(BoardPolicy.allowsPath("b/catalog.json", false))
    }

    @Test fun linksRespectBuildPolicy() {
        assertNotNull(parseThreadLink("https://boards.4chan.org/g/thread/123"))
        assertEquals(!BuildConfig.SFW_ONLY,
            parseThreadLink("https://boards.4chan.org/b/thread/123") != null)
    }
}
