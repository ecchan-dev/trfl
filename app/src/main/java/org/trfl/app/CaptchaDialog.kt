package org.trfl.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

internal enum class CaptchaErrorSource { MAIN_FRAME, WIDGET_SCRIPT, CHALLENGE, OTHER }
internal enum class CaptchaErrorKind { WEB_VIEW, HTTP, POLICY }
internal data class CaptchaError(val source: CaptchaErrorSource, val kind: CaptchaErrorKind, val code: Int, val detail: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptchaControl(target: PostingDraftKey) {
    var open by remember(target) { mutableStateOf(false) }
    var attempt by remember(target) { mutableIntStateOf(0) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Load CAPTCHA") }
        return
    }
    var error by remember(target, attempt) { mutableStateOf<CaptchaError?>(null) }
    var loading by remember(target, attempt) { mutableStateOf(true) }
    var infoOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { open = false }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widgetHeight = (maxHeight * 0.65f).coerceIn(200.dp, 480.dp)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CAPTCHA", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    IconButton(onClick = { open = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Text(
                    when {
                        error != null -> "CAPTCHA could not load."
                        loading -> "Loading CAPTCHA…"
                        else -> "Complete the CAPTCHA below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error == null) {
                    Spacer(Modifier.height(12.dp))
                    key(target, attempt) {
                        CaptchaWebView(target, Modifier.fillMaxWidth().height(widgetHeight),
                            onLoaded = { loading = false }, onError = { loading = false; error = it })
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    error?.let { Text(it.detail ?: "${it.source.name.lowercase().replace('_', ' ')}: ${it.kind.name} ${it.code}", style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { attempt++ }) { Text("Retry") }
                }
                TextButton(onClick = { infoOpen = !infoOpen }) {
                    Text(if (infoOpen) "Hide information" else "About CAPTCHA")
                }
                if (infoOpen) {
                    Text(
                        "Cookies and the CAPTCHA ticket may remain in app storage until app data is cleared. Posting is disabled; completion is not submitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Required by the official interactive widget; no native JS bridge.
@Composable
private fun CaptchaWebView(target: PostingDraftKey, modifier: Modifier, onLoaded: () -> Unit, onError: (CaptchaError) -> Unit) {
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
                    captchaRequestRoute(request.url.toString(), request.method, request.isForMainFrame) == CaptchaRequestRoute.BLOCK

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    when (captchaRequestRoute(request.url.toString(), request.method, request.isForMainFrame)) {
                        CaptchaRequestRoute.HOST -> WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(captchaHostHtml(target, darkMode).toByteArray(Charsets.UTF_8)))
                        CaptchaRequestRoute.REMOTE -> null
                        CaptchaRequestRoute.BLOCK -> {
                            val diagnostic = CaptchaError(CaptchaErrorSource.OTHER, CaptchaErrorKind.POLICY, 0, captchaBlockedDiagnostic(request.url.toString()))
                            dispatch(view) { if (active.getAndSet(false)) onError(diagnostic) }
                            WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                        }
                    }

                private fun dispatch(view: WebView, callback: () -> Unit) {
                    view.post { if (active.get()) callback() }
                }
                override fun onPageFinished(view: WebView, url: String?) { dispatch(view, onLoaded) }
                private fun errorSource(request: WebResourceRequest): CaptchaErrorSource? = when {
                    request.isForMainFrame -> CaptchaErrorSource.MAIN_FRAME
                    request.url.host == "s.4cdn.org" && request.url.path == "/js/tcaptcha.min.7.js" -> CaptchaErrorSource.WIDGET_SCRIPT
                    request.url.host == "sys.4chan.org" -> CaptchaErrorSource.CHALLENGE
                    else -> null
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    val source = errorSource(request) ?: return
                    val diagnostic = CaptchaError(source, CaptchaErrorKind.WEB_VIEW, error.errorCode)
                    dispatch(view) { if (active.getAndSet(false)) onError(diagnostic) }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (captchaRequestRoute(request.url.toString(), request.method, request.isForMainFrame) != CaptchaRequestRoute.REMOTE) return
                    val source = errorSource(request) ?: CaptchaErrorSource.OTHER
                    val diagnostic = CaptchaError(source, CaptchaErrorKind.HTTP, response.statusCode, captchaRemoteHttpDiagnostic(request.url.toString(), response.statusCode))
                    dispatch(view) { if (active.getAndSet(false)) onError(diagnostic) }
                }
            }
            loadUrl(CAPTCHA_HOST_URL)
        }
    })
}
