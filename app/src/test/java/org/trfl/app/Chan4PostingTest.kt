package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

// Synthetic fixtures only: no recorded responses, network, CAPTCHA or submissions.
class Chan4PostingTest {
    private val target = PostingDraftKey("g", 123)

    @Test fun syntheticReplyFieldsAndEndpoint() {
        assertEquals("https://sys.4chan.org/g/post", chan4PostUrl(target).toString())
        val parts = chan4PostParts(target, "synthetic comment", CaptchaAnswer("synthetic-token", "0213"), "pw").associate { it.name to String(it.bytes) }
        assertEquals(mapOf("mode" to "regist", "pwd" to "pw", "resto" to "123", "name" to "", "email" to "", "com" to "synthetic comment",
            "t-challenge" to "synthetic-token", "t-response" to "0213"), parts)
        assertTrue(runCatching { chan4PostParts(PostingDraftKey("g", null), "x", CaptchaAnswer("t", "0"), "pw") }.isFailure)
        assertTrue(runCatching { chan4PostParts(target, " ", CaptchaAnswer("t", "0"), "pw") }.isFailure)
    }

    @Test fun syntheticResponsesAreClassifiedConservatively() {
        assertEquals(Chan4PostResult.Posted(123, 456), parseChan4PostResponse(200, "<html><!-- thread:123,no:456 --></html>"))
        assertEquals(Chan4PostResult.Posted(456, 456), parseChan4PostResponse(200, "<!-- thread:0,no:456 -->"))
        assertEquals(Chan4PostResult.CaptchaRejected, parseChan4PostResponse(200, "<span id=\"errmsg\">Error: You seem to have mistyped the CAPTCHA.</span>"))
        assertEquals(Chan4PostResult.Rejected("Error: You must wait 30 seconds\nbefore posting & more."),
            parseChan4PostResponse(200, "<span id=\"errmsg\" style=\"color: red;\">Error: You must wait <b>30</b> seconds<br>before posting &amp; more.</span>"))
        assertTrue(parseChan4PostResponse(403, "blocked") is Chan4PostResult.Rejected)
        assertTrue(parseChan4PostResponse(200, "<html>unexpected</html>") is Chan4PostResult.Unknown)
        assertTrue(parseChan4PostResponse(200, "<!-- thread:123,no:0 -->") is Chan4PostResult.Unknown)
    }
}
