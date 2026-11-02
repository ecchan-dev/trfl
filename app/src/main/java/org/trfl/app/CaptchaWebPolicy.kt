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

// This exact URL is fulfilled locally and must never fall through to the network.
internal const val CAPTCHA_HOST_URL = "https://boards.4chan.org/__trfl_captcha_host"

internal enum class CaptchaRequestRoute { HOST, REMOTE, BLOCK }

internal fun captchaRequestRoute(url: String, method: String, mainFrame: Boolean): CaptchaRequestRoute = when {
    method != "GET" -> CaptchaRequestRoute.BLOCK
    url == CAPTCHA_HOST_URL && mainFrame -> CaptchaRequestRoute.HOST
    !mainFrame && captchaResourceAllowed(url) -> CaptchaRequestRoute.REMOTE
    else -> CaptchaRequestRoute.BLOCK
}

// Categories deliberately contain no URL, query, response body, or credential data.
internal enum class CaptchaEndpoint { HOST, WIDGET_SCRIPT, CHALLENGE, IMAGE, OTHER }

internal fun captchaEndpoint(url: String): CaptchaEndpoint {
    if (url == CAPTCHA_HOST_URL) return CaptchaEndpoint.HOST
    if (!captchaResourceAllowed(url)) return CaptchaEndpoint.OTHER
    val uri = URI(url)
    return when {
        uri.host == "sys.4chan.org" -> CaptchaEndpoint.CHALLENGE
        uri.rawPath == "/js/tcaptcha.min.7.js" -> CaptchaEndpoint.WIDGET_SCRIPT
        else -> CaptchaEndpoint.IMAGE
    }
}

internal fun captchaBlockedDiagnostic(url: String): String =
    "Blocked by app policy (${captchaEndpoint(url).label()})"

// Only use for REMOTE requests: locally generated responses are not server HTTP errors.
internal fun captchaRemoteHttpDiagnostic(url: String, status: Int): String =
    "Remote HTTP ${status.takeIf { it in 100..599 } ?: 0} (${captchaEndpoint(url).label()})"

internal fun captchaNetworkDiagnostic(url: String): String =
    "Network error (${captchaEndpoint(url).label()})"

private fun CaptchaEndpoint.label(): String = when (this) {
    CaptchaEndpoint.HOST -> "host"
    CaptchaEndpoint.WIDGET_SCRIPT -> "widget script"
    CaptchaEndpoint.CHALLENGE -> "challenge"
    CaptchaEndpoint.IMAGE -> "image"
    CaptchaEndpoint.OTHER -> "other resource"
}

internal fun captchaHostHtml(target: PostingDraftKey, darkMode: Boolean = false): String {
    requireCaptchaTarget(target)
    val thread = target.threadId ?: 0L
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { margin: 8px; font: 14px sans-serif; }
#captcha { max-width: 100%; }
#js-t-ext-cnt { box-sizing: border-box; max-width: calc(100vw - 16px); max-height: calc(100vh - 16px); overflow: auto; }
#js-t-ext-cnt input, #js-t-ext-cnt img { max-width: 100%; height: auto; }
</style>
<script src="https://s.4cdn.org/js/tcaptcha.min.7.js"></script></head>
<body><div id="captcha"></div><script>
if (typeof TCaptcha !== 'undefined') {
  TCaptcha.loadTicketCaptcha = TCaptcha.buildTicketCaptcha = function() {
    TCaptcha.setTaskNodeContent('Additional hCaptcha verification is not supported in this app.');
  };
  TCaptcha.init(document.getElementById('captcha'), '${target.board}', $thread, null, $darkMode);
} else document.body.textContent = 'Unable to load CAPTCHA. Close and retry.';
</script></body></html>"""
}
