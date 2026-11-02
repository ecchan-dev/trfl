package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

// Synthetic, offline fixtures: no challenge endpoint or network access.
class CaptchaWebPolicyTest {
    @Test fun syntheticOfficialResourcesAndBlockedNavigation() {
        assertTrue(captchaResourceAllowed("https://sys.4chan.org/captcha?board=g&thread_id=123"))
        assertTrue(captchaResourceAllowed("https://s.4cdn.org/js/tcaptcha.min.7.js"))
        assertTrue(captchaResourceAllowed("https://s.4cdn.org/image/temp/april2026/root/a.png"))
        listOf("http://sys.4chan.org/captcha", "https://sys.4chan.org.evil.test/captcha", "https://sys.4chan.org/other", "https://user@sys.4chan.org/captcha", "file:///captcha", "https://s.4cdn.org/other", "https://s.4cdn.org/image/temp/april2026/../other", "https://s.4cdn.org/image/temp/april2026/%2e%2e/other", "https://js.hcaptcha.com/1/api.js?render=explicit").forEach {
            assertFalse(it, captchaResourceAllowed(it))
        }
    }

    @Test fun syntheticRequestRoutingKeepsBootstrapLocal() {
        assertEquals(CaptchaRequestRoute.HOST, captchaRequestRoute(CAPTCHA_HOST_URL, "GET", true))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(CAPTCHA_HOST_URL, "GET", false))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(CAPTCHA_HOST_URL, "POST", true))
        listOf("$CAPTCHA_HOST_URL?x=1", "$CAPTCHA_HOST_URL/", "data:text/html,hello", "https://boards.4chan.org/").forEach {
            assertEquals(it, CaptchaRequestRoute.BLOCK, captchaRequestRoute(it, "GET", true))
        }
        val remote = "https://sys.4chan.org/captcha?board=g"
        assertEquals(CaptchaRequestRoute.REMOTE, captchaRequestRoute(remote, "GET", false))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(remote, "GET", true))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(remote, "POST", false))
    }

    @Test fun syntheticHtmlContractForReplyAndNewThread() {
        assertEquals("https://boards.4chan.org/__trfl_captcha_host", CAPTCHA_HOST_URL)
        val reply = captchaHostHtml(PostingDraftKey("g", 123))
        val thread = captchaHostHtml(PostingDraftKey("g", null))
        for (html in listOf(reply, thread)) {
            assertTrue(html.contains("<div id=\"captcha\"></div>"))
            assertTrue(html.contains("https://s.4cdn.org/js/tcaptcha.min.7.js"))
            assertTrue(html.contains("Additional hCaptcha verification is not supported"))
            assertFalse(html.contains("<form"))
            assertFalse(html.contains(".submit("))
            assertFalse(html.contains("addJavascriptInterface"))
        }
        assertTrue(reply.contains("TCaptcha.init(document.getElementById('captcha'), 'g', 123)"))
        assertTrue(thread.contains("TCaptcha.init(document.getElementById('captcha'), 'g', 0)"))
    }
}
