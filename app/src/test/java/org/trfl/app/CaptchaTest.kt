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

    @Test fun syntheticImageTasksParseAndJoinZeroBasedChoices() {
        val html = ".postMessage({\"twister\":{\"challenge\":\"synthetic-token\",\"ttl\":120,\"tasks\":[{\"str\":\"pick\",\"items\":[\"AQ==\",\"Ag==\"]},{\"img\":\"Aw==\",\"items\":[\"BA==\",\"BQ==\"]}]}})"
        val parsed = CaptchaParser.parse(html, PostingDraftKey("g", 123), 100) { bytes ->
            assertTrue(bytes.size == 1)
            1
        }
        assertEquals("pick", parsed.tasks[0].title)
        assertArrayEquals(byteArrayOf(3), parsed.tasks[1].titleImage)
        assertArrayEquals(byteArrayOf(5), parsed.tasks[1].images[1])
        assertEquals("10", parsed.answer(PostingDraftKey("g", 123), listOf(1, 0), 120099)?.response)
        assertNull(parsed.answer(PostingDraftKey("g", 123), listOf(1, 0), 120100))
        assertTrue(runCatching { CaptchaParser.parse(html.replace("\"ttl\":120,", ""), PostingDraftKey("g", 123), 0) { 1 } }.isFailure)
        assertTrue(runCatching { CaptchaParser.parse(html, PostingDraftKey("g", 123), 0) { 1_048_576 } }.isFailure)
    }

    @Test fun syntheticImageBoundsAndPlayPolicy() {
        assertTrue(validCaptchaImageBounds(1024, 1024))
        assertFalse(validCaptchaImageBounds(0, 1))
        assertFalse(validCaptchaImageBounds(1025, 1))
        requireCaptchaTarget(PostingDraftKey("g", 1), true)
        assertTrue(runCatching { requireCaptchaTarget(PostingDraftKey("b", 1), true) }.isFailure)
        assertTrue(runCatching { requireCaptchaTarget(PostingDraftKey("../g", 1), false) }.isFailure)
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
