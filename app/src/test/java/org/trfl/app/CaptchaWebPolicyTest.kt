package org.trfl.app

import org.junit.Assert.*
import org.junit.Test

// Synthetic policy fixtures; no network or live CAPTCHA.
class CaptchaWebPolicyTest {
    @Test fun syntheticOfficialResourcesAndBlockedNavigation() {
        assertTrue(captchaResourceAllowed("https://sys.4chan.org/captcha?ext=1&board=g&thread_id=123"))
        assertTrue(captchaResourceAllowed("https://s.4cdn.org/js/tcaptcha.min.7.js"))
        assertTrue(captchaResourceAllowed("https://s.4cdn.org/image/temp/april2026/a/b.png"))
        assertTrue(captchaResourceAllowed("https://js.hcaptcha.com/1/api.js?render=explicit"))
        listOf("http://sys.4chan.org/captcha", "https://sys.4chan.org.evil.test/captcha", "https://sys.4chan.org/other", "https://user@sys.4chan.org/captcha", "file:///captcha", "https://s.4cdn.org/other").forEach {
            assertFalse(it, captchaResourceAllowed(it))
        }
    }
    @Test fun syntheticHostScopesOfficialWidget() {
        val html = captchaHostHtml(PostingDraftKey("g", 123))
        assertTrue(html.contains("TCaptcha.init(document.getElementById('captcha'), 'g', 123)"))
        assertTrue(html.contains("https://s.4cdn.org/js/tcaptcha.min.7.js"))
    }
}
