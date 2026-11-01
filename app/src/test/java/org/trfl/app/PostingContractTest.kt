package org.trfl.app

// Synthetic fixtures only: no recorded responses, network, CAPTCHA or submissions.
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PostingContractTest {
    private val rules = mapOf("g" to PostingBoardRules(100, 10, setOf("image/png")), "b" to PostingBoardRules(100, 10, setOf("image/png")))
    private fun policy(restricted: Boolean = false) = PostingPolicy(rules, restricted)
    private fun request(text: String = "synthetic") = PostingRequest(PostingTarget("g", 123), text)

    @Test fun syntheticPolicyFailsClosed() {
        assertTrue(PostingPolicy(restricted = false).validate(request()).isNotEmpty())
        assertTrue(policy(true).validate(request().copy(target = PostingTarget("b", 123))).isNotEmpty())
        assertTrue(policy().validate(request().copy(target = PostingTarget("../g", 123))).isNotEmpty())
        assertTrue(policy().validate(request().copy(target = PostingTarget("g", -1))).isNotEmpty())
        assertTrue(policy().validate(request().copy(target = PostingTarget("g"))).isNotEmpty())
        assertTrue(policy().validate(request(" ")).isNotEmpty())
        assertTrue(policy().validate(request("x".repeat(101))).isNotEmpty())
        assertTrue(policy().validate(request().copy(attachment = PostingAttachment("x.png", "image/png", ByteArray(11)))).isNotEmpty())
        assertTrue(policy().validate(request().copy(attachment = PostingAttachment("x.exe", "application/exe", byteArrayOf(1)))).isNotEmpty())
    }

    @Test fun syntheticDefaultAdapterIsHonestlyGated() {
        assertTrue(PostingSender(policy()).sendExplicit(request()) is PostingResult.Unsupported)
    }

    @Test fun syntheticSuccessAndAmbiguityBlockDuplicateWithoutRetry() {
        for (receipt in listOf(PostingResult.ConfirmedSuccess(124), PostingResult.Ambiguous())) {
            var calls = 0
            val sender = PostingSender(policy(), PostingTransport { calls++; receipt })
            assertEquals(receipt, sender.sendExplicit(request()))
            assertEquals(PostingResult.Duplicate, sender.sendExplicit(request()))
            assertEquals(1, calls)
            sender.sendExplicit(request("edited"))
            assertEquals(2, calls)
        }
    }

    @Test fun syntheticExceptionIsAmbiguousAndRejectedAllowsExplicitRetry() {
        val broken = PostingSender(policy(), PostingTransport { error("synthetic exception") })
        assertTrue(broken.sendExplicit(request()) is PostingResult.Ambiguous)
        assertEquals(PostingResult.Duplicate, broken.sendExplicit(request()))
        var calls = 0
        val rejected = PostingSender(policy(), PostingTransport { calls++; PostingResult.Rejected("synthetic rejection") })
        rejected.sendExplicit(request()); rejected.sendExplicit(request())
        assertEquals(2, calls)
    }

    @Test fun syntheticCooldownNeverSchedulesRetry() {
        var time = 10L
        var calls = 0
        val sender = PostingSender(policy(), PostingTransport { calls++; PostingResult.Cooldown(100) }, { time })
        sender.sendExplicit(request())
        assertEquals(PostingResult.Cooldown(100), sender.sendExplicit(request("changed")))
        assertEquals(1, calls)
        time = 101
        sender.sendExplicit(request())
        assertEquals(2, calls)
    }

    @Test fun syntheticConcurrentSendIsRejectedAndAttachmentIsSnapshot() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val bytes = byteArrayOf(1)
        val sender = PostingSender(policy(), PostingTransport {
            entered.countDown()
            check(finish.await(2, TimeUnit.SECONDS))
            assertEquals(1.toByte(), it.attachment!!.bytes[0])
            PostingResult.ConfirmedSuccess(124)
        })
        val worker = Thread { sender.sendExplicit(request().copy(attachment = PostingAttachment("x.png", "image/png", bytes))) }
        worker.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            bytes[0] = 2
            assertEquals(PostingResult.InFlight, sender.sendExplicit(request("edited")))
        } finally { finish.countDown(); worker.join(3000) }
        assertFalse(worker.isAlive)
    }

    @Test fun syntheticMultipartPreservesBinaryAndUtf8() {
        val body = PostingMultipart.encode(listOf(PostingMultipartPart("text", "é".toByteArray()), PostingMultipartPart("file", byteArrayOf(0, -1), "x.png", "image/png")), "synthetic_boundary")
        assertEquals("multipart/form-data; boundary=synthetic_boundary", body.contentType)
        assertTrue(body.bytes.toString(Charsets.UTF_8).startsWith("--synthetic_boundary\r\nContent-Disposition: form-data; name=\"text\"\r\n\r\né\r\n"))
        assertTrue(body.bytes.toList().windowed(2).any { it == listOf(0.toByte(), (-1).toByte()) })
        assertTrue(body.bytes.toString(Charsets.UTF_8).endsWith("--synthetic_boundary--\r\n"))
    }

    @Test fun syntheticMultipartRejectsHeaderInjectionAndCollision() {
        val bad = listOf(
            PostingMultipartPart("bad\r\n", byteArrayOf()),
            PostingMultipartPart("file", byteArrayOf(), "bad\"name"),
            PostingMultipartPart("file", byteArrayOf(), "ok", "text/plain\r\nx: y"),
            PostingMultipartPart("text", "--boundary".toByteArray())
        )
        bad.forEach { part ->
            try { PostingMultipart.encode(listOf(part), "boundary"); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        }
    }
}
