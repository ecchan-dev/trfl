package org.trfl.app

import java.net.URI

/** Origins used by the published tcaptcha.min.7.js, not a general browser allowlist. */
internal fun captchaResourceAllowed(url: String): Boolean = try {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        when (uri.host) {
            "sys.4chan.org" -> uri.path == "/captcha"
            "s.4cdn.org" -> uri.path == "/js/tcaptcha.min.7.js" || uri.path.startsWith("/image/temp/april2026/")
            "js.hcaptcha.com" -> uri.path == "/1/api.js"
            else -> false
        }
} catch (_: Exception) { false }

internal fun captchaHostHtml(target: PostingDraftKey): String {
    requireCaptchaTarget(target)
    val thread = target.threadId ?: 0L
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>body { margin: 8px; font: 14px sans-serif; } dialog { max-width: 90vw; }</style>
<script src="https://s.4cdn.org/js/tcaptcha.min.7.js"></script></head>
<body><div id="captcha"></div><script>
if (typeof TCaptcha !== 'undefined') TCaptcha.init(document.getElementById('captcha'), '${target.board}', $thread);
else document.body.textContent = 'Unable to load CAPTCHA. Close and retry.';
</script></body></html>"""
}
