package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

// Synthetic, offline fixtures: no challenge endpoint or network access.
class CaptchaWebPolicyTest {
    @Test fun syntheticRoutingKeepsTopLevelPageLocal() {
        assertEquals(CaptchaRequestRoute.HOST, captchaRequestRoute(CAPTCHA_HOST_URL, "GET", true))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(CAPTCHA_HOST_URL, "GET", false))
        assertEquals(CaptchaRequestRoute.BLOCK, captchaRequestRoute(CAPTCHA_HOST_URL, "POST", true))
        listOf("$CAPTCHA_HOST_URL?x=1", "data:text/html,hello", "https://boards.4chan.org/", "https://sys.4chan.org/captcha?board=g").forEach {
            assertEquals(it, CaptchaRequestRoute.BLOCK, captchaRequestRoute(it, "GET", true))
        }
    }

    @Test fun syntheticRoutingLetsWidgetDependenciesLoad() {
        listOf("https://sys.4chan.org/captcha?board=g", CAPTCHA_WIDGET_SCRIPT, "https://js.hcaptcha.com/1/api.js?render=explicit",
            "https://mcl.spur.us/d/mcl.js", "https://challenges.cloudflare.com/turnstile/v0/api.js", "about:blank", "data:image/png;base64,aGVsbG8=").forEach {
            assertEquals(it, CaptchaRequestRoute.REMOTE, captchaRequestRoute(it, "GET", false))
        }
        assertEquals(CaptchaRequestRoute.REMOTE, captchaRequestRoute("https://sys.4chan.org/captcha?board=g", "POST", false))
        listOf("http://sys.4chan.org/captcha", "file:///captcha", "intent://x", "javascript:alert(1)").forEach {
            assertEquals(it, CaptchaRequestRoute.BLOCK, captchaRequestRoute(it, "GET", false))
        }
    }

    @Test fun syntheticHtmlContractForReplyAndNewThread() {
        val reply = captchaHostHtml(PostingDraftKey("g", 123))
        val thread = captchaHostHtml(PostingDraftKey("g", null))
        for (html in listOf(reply, thread)) {
            assertTrue(html.contains("<div id=\"captcha\"></div>"))
            assertTrue(html.contains(CAPTCHA_WIDGET_SCRIPT))
            assertFalse(html.contains("<form"))
            assertFalse(html.contains(".submit("))
        }
        assertTrue(reply.contains("TCaptcha.init(document.getElementById('captcha'), 'g', 123, null, false)"))
        assertTrue(thread.contains("TCaptcha.init(document.getElementById('captcha'), 'g', 0, null, false)"))
        assertTrue(captchaHostHtml(PostingDraftKey("g", 123), true).contains("'g', 123, null, true)"))
    }

    @Test fun syntheticWidgetStateOnlyYieldsCompletedAnswers() {
        fun wrap(json: String) = org.json.JSONObject.quote(json)
        val done = parseCaptchaWidgetState(wrap("""{"c":"synthetic-token","r":"0213","d":true}"""))
        assertEquals("synthetic-token", done?.challenge)
        assertEquals("0213", done?.response)
        assertEquals("", parseCaptchaWidgetState(wrap("""{"c":"noop","r":"","d":true}"""))?.response)
        listOf(null, "null", "\"\"", "garbage", wrap("""{"c":"synthetic-token","r":"02","d":false}"""),
            wrap("""{"c":"","r":"02","d":true}"""), wrap("""{"c":"synthetic-token","r":"0 2","d":true}""")).forEach {
            assertNull(it, parseCaptchaWidgetState(it))
        }
    }
}
