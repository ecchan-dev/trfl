package org.trfl.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@Composable
internal fun CaptchaControl(target: PostingDraftKey) {
    var open by remember(target) { mutableStateOf(false) }
    var attempt by remember(target) { mutableIntStateOf(0) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Load CAPTCHA") }
        return
    }
    var error by remember(target, attempt) { mutableStateOf(false) }
    var loading by remember(target, attempt) { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("CAPTCHA") },
        text = {
            Column {
                Text("Official CAPTCHA. Posting remains disabled; completion is not submitted.")
                Text("Official site cookies and a CAPTCHA ticket may remain in app-private browser storage until app data is cleared.")
                if (loading) Text("Loading…")
                if (error) Text("Could not load the CAPTCHA. Check your connection and retry. Additional anti-bot checks may not be supported.")
                key(target, attempt) {
                    CaptchaWebView(target, Modifier.fillMaxWidth().height(360.dp),
                        onLoaded = { loading = false }, onError = { loading = false; error = true })
                }
            }
        },
        confirmButton = { TextButton(onClick = { attempt++ }) { Text("Retry") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } },
    )
}

@SuppressLint("SetJavaScriptEnabled") // Required by the official interactive widget; no native JS bridge.
@Composable
private fun CaptchaWebView(target: PostingDraftKey, modifier: Modifier, onLoaded: () -> Unit, onError: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() }
            webView = null
        }
    }
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            webView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // Official widget stores its ticket in localStorage.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.saveFormData = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame || !captchaResourceAllowed(request.url.toString())

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    if (captchaResourceAllowed(request.url.toString())) null else
                        WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { super.onPageStarted(view, url, favicon) }
                override fun onPageFinished(view: WebView, url: String?) { onLoaded() }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame || request.url.toString().startsWith("https://s.4cdn.org/js/tcaptcha.min.7.js") || request.url.host == "sys.4chan.org") onError()
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame || request.url.host == "sys.4chan.org" || request.url.path == "/js/tcaptcha.min.7.js") onError()
                }
            }
            loadDataWithBaseURL("https://sys.4chan.org/", captchaHostHtml(target), "text/html", "UTF-8", null)
        }
    })
}
