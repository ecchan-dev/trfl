package org.trfl.app

import org.json.JSONObject
import org.json.JSONTokener

// This exact URL is fulfilled locally and must never fall through to the network.
internal const val CAPTCHA_HOST_URL = "https://boards.4chan.org/__trfl_captcha_host"
internal const val CAPTCHA_WIDGET_SCRIPT = "https://s.4cdn.org/js/tcaptcha.min.7.js"

internal enum class CaptchaRequestRoute { HOST, REMOTE, BLOCK }

// The page behaves like a browser tab showing only the widget: everything the widget and its
// antibot, hCaptcha or Cloudflare steps load is allowed, but the top-level page can never
// navigate away from the locally served host.
internal fun captchaRequestRoute(url: String, method: String, mainFrame: Boolean): CaptchaRequestRoute = when {
    url == CAPTCHA_HOST_URL -> if (mainFrame && method == "GET") CaptchaRequestRoute.HOST else CaptchaRequestRoute.BLOCK
    mainFrame -> CaptchaRequestRoute.BLOCK
    url == "about:blank" || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("https://") -> CaptchaRequestRoute.REMOTE
    else -> CaptchaRequestRoute.BLOCK
}

internal fun captchaHostHtml(target: PostingDraftKey, darkMode: Boolean = false): String {
    requireCaptchaTarget(target)
    val thread = target.threadId ?: 0L
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { margin: 16px 8px; font: 14px sans-serif; ${if (darkMode) "background: #1d1f21; color: #c5c8c6;" else ""} }
#captcha { max-width: 100%; margin: 0 auto; }
#js-t-ext-cnt { box-sizing: border-box; max-width: calc(100vw - 16px); max-height: calc(100vh - 16px); overflow: auto; }
#js-t-ext-cnt input, #js-t-ext-cnt img { max-width: 100%; height: auto; }
</style>
<script src="$CAPTCHA_WIDGET_SCRIPT"></script></head>
<body><div id="captcha"></div><script>
if (typeof TCaptcha !== 'undefined') {
  TCaptcha.init(document.getElementById('captcha'), '${target.board}', $thread, null, $darkMode);
} else document.body.textContent = 'Unable to load CAPTCHA. Close and retry.';
</script></body></html>"""
}

// Read-only probe of the widget's own fields; "d" is true once every task is answered
// or the server said no verification is required.
internal const val CAPTCHA_STATE_JS = """(function(){try{var t=TCaptcha;
var c=t.challengeIdNode?t.challengeIdNode.value:'';var r=t.respNode?t.respNode.value:'';
var done=!!c&&(t.tasks?t.taskId>=t.tasks.length:(r!==''||/not required/i.test(t.taskNode.textContent)));
return JSON.stringify({c:c,r:r,d:done});}catch(e){return '';}})()"""

// evaluateJavascript hands back a JSON string literal that wraps the probe's own JSON.
internal fun parseCaptchaWidgetState(raw: String?): CaptchaAnswer? = try {
    val state = JSONObject(JSONTokener(raw ?: "").nextValue() as String)
    val challenge = state.optString("c"); val response = state.optString("r")
    if (!state.optBoolean("d") || challenge.isBlank() || challenge.length > 1024 || challenge.any { it < ' ' } ||
        !response.matches(Regex("[A-Za-z0-9]{0,256}"))) null
    else CaptchaAnswer(challenge, response)
} catch (_: Exception) { null }
