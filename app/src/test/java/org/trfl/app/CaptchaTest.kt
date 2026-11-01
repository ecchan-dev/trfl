package org.trfl.app

// Synthetic fixtures only: no live challenges, network requests or submissions.
import org.junit.Assert.*
import org.junit.Test

class CaptchaTest {
    @Test fun syntheticMalformedOversizeAndUnsupportedFailClosed() {
        val target = PostingDraftKey("g", 123)
        val samples = listOf(
            "not html", "x".repeat(2_000_001), "https://mcl.spur.us",
            ".postMessage({\"twister\":{\"error\":\"blocked\"}})",
            ".postMessage({\"twister\":{\"challenge\":\"x\",\"ttl\":0,\"tasks\":[]}})",
            ".postMessage({\"twister\":{\"challenge\":\"noop\",\"ttl\":120,\"tasks\":[]}})",
            ".postMessage({\"twister\":{\"challenge\":\"x\",\"ttl\":120,\"tasks\":[]}})"
        )
        samples.forEach { assertTrue(runCatching { CaptchaParser.parse(it, target, 0) }.isFailure) }
    }

    @Test fun syntheticAnswerIsScopedBoundedAndExpires() {
        val target = PostingDraftKey("g", 123)
        val task = CaptchaTask("synthetic", null, listOf(byteArrayOf(1), byteArrayOf(2)))
        val challenge = CaptchaChallenge(target, "synthetic-token", listOf(task, task), 1000)
        assertEquals("10", challenge.answer(target, listOf(1, 0), 999)?.response)
        assertNull(challenge.answer(target, listOf(1, 0), 1000))
        assertNull(challenge.answer(PostingDraftKey("g", 124), listOf(1, 0), 1))
        assertNull(challenge.answer(target, listOf(2, 0), 1))
        assertNull(challenge.answer(target, listOf(0), 1))
    }
}
