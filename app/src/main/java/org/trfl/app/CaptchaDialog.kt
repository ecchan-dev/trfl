package org.trfl.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** Full-screen CAPTCHA page. Closes itself with the answer as soon as the widget reports completion. */
@Composable
internal fun CaptchaPage(target: PostingDraftKey, onAnswer: (CaptchaAnswer) -> Unit, onClose: () -> Unit) {
    var attempt by remember(target) { mutableIntStateOf(0) }
    var error by remember(target, attempt) { mutableStateOf<String?>(null) }
    var loading by remember(target, attempt) { mutableStateOf(true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("CAPTCHA", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Text(
                    when {
                        error != null -> "CAPTCHA could not load."
                        loading -> "Loading CAPTCHA…"
                        else -> "Tap Get Captcha and solve it. You return to your post automatically."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                val failure = error
                if (failure == null) {
                    key(target, attempt) {
                        CaptchaWebView(target, Modifier.fillMaxWidth().weight(1f),
                            onLoaded = { loading = false }, onError = { loading = false; error = it }, onAnswer = onAnswer)
                    }
                } else {
                    Text(failure, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                    Button(onClick = { attempt++ }, modifier = Modifier.padding(20.dp)) { Text("Retry") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Required by the official interactive widget; no native JS bridge.
@Composable
private fun CaptchaWebView(target: PostingDraftKey, modifier: Modifier, onLoaded: () -> Unit, onError: (String) -> Unit, onAnswer: (CaptchaAnswer) -> Unit) {
    val darkMode = isSystemInDarkTheme()
    var webView by remember { mutableStateOf<WebView?>(null) }
    val active = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            webView?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() }
            webView = null
        }
    }
    // Poll the widget's own fields rather than exposing a bridge object to third-party scripts.
    LaunchedEffect(Unit) {
        while (active.get()) {
            delay(600)
            webView?.evaluateJavascript(CAPTCHA_STATE_JS) { raw ->
                val answer = parseCaptchaWidgetState(raw)
                if (answer != null && active.getAndSet(false)) onAnswer(answer)
            }
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
            settings.saveFormData = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                private fun route(request: WebResourceRequest) =
                    captchaRequestRoute(request.url.toString(), request.method, request.isForMainFrame)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    route(request) == CaptchaRequestRoute.BLOCK

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    when (route(request)) {
                        CaptchaRequestRoute.HOST -> WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(captchaHostHtml(target, darkMode).toByteArray(Charsets.UTF_8)))
                        CaptchaRequestRoute.REMOTE -> null
                        CaptchaRequestRoute.BLOCK -> WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }

                private fun dispatch(view: WebView, callback: () -> Unit) {
                    view.post { if (active.get()) callback() }
                }
                override fun onPageFinished(view: WebView, url: String?) { dispatch(view, onLoaded) }

                // Only the page itself and the widget script are fatal. The challenge frame may
                // legitimately answer with an HTTP error page that carries an interactive check.
                private fun fatal(request: WebResourceRequest) =
                    request.isForMainFrame || request.url.toString() == CAPTCHA_WIDGET_SCRIPT
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (fatal(request)) dispatch(view) { onError("Network error (${error.errorCode}). Check your connection.") }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (fatal(request)) dispatch(view) { onError("CAPTCHA script unavailable (HTTP ${response.statusCode}).") }
                }
            }
            loadUrl(CAPTCHA_HOST_URL)
        }
    })
}
