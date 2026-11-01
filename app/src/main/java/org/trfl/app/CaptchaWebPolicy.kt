package org.trfl.app

import java.net.URI

// The widget script constructs /captcha on sys and image URLs under this static prefix.
// hCaptcha is intentionally unsupported: its further dependencies are not specified by this script.
internal fun captchaResourceAllowed(url: String): Boolean = try {
    val uri = URI(url)
    val path = uri.rawPath ?: ""
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        when (uri.host) {
            "sys.4chan.org" -> path == "/captcha"
            "s.4cdn.org" -> path == "/js/tcaptcha.min.7.js" ||
                (path.startsWith("/image/temp/april2026/") &&
                    path.split('/').drop(4).all { it.isNotEmpty() && it != "." && it != ".." && it.all { c -> c.isLetterOrDigit() || c in "-_." } })
            else -> false
        }
} catch (_: Exception) { false }

internal const val CAPTCHA_BASE_URL = "https://boards.4chan.org/"

internal fun captchaHostHtml(target: PostingDraftKey): String {
    requireCaptchaTarget(target)
    val thread = target.threadId ?: 0L
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>body { margin: 8px; font: 14px sans-serif; } dialog { max-width: 90vw; }</style>
<script src="https://s.4cdn.org/js/tcaptcha.min.7.js"></script></head>
<body><div id="captcha"></div><script>
if (typeof TCaptcha !== 'undefined') {
  TCaptcha.loadTicketCaptcha = TCaptcha.buildTicketCaptcha = function() {
    TCaptcha.setTaskNodeContent('Additional hCaptcha verification is not supported in this app.');
  };
  TCaptcha.init(document.getElementById('captcha'), '${target.board}', $thread);
} else document.body.textContent = 'Unable to load CAPTCHA. Close and retry.';
</script></body></html>"""
}
