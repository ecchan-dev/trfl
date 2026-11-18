package org.trfl.app

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

// Enqueue synchronously on the UI thread: navigation and composition disposal cannot
// cancel or reorder a prior edit behind a subsequent load of the same draft.
private val draftExecutor = PostingDraftQueue()
private val mainHandler = Handler(Looper.getMainLooper())
private val postExecutor = Executors.newSingleThreadExecutor()
// The widget does not expose its expiry; stay under the shortest lifetime observed.
private const val CAPTCHA_ANSWER_LIFETIME_MS = 110_000L

@Composable
fun PostingComposer(
    target: PostingDraftKey,
    quoteId: Long?,
    quoteRequest: Int,
    store: PostingDraftStore,
    newThread: Boolean = false,
    onQuoteHandled: () -> Unit = {},
    onPosted: () -> Unit = {},
) {
    val context = LocalContext.current
    var captchaOpen by remember(target) { mutableStateOf(false) }
    var answer by remember(target) { mutableStateOf<CaptchaAnswer?>(null) }
    var sending by remember(target) { mutableStateOf(false) }
    var status by remember(target) { mutableStateOf<String?>(null) }
    var text by remember(target) { mutableStateOf("") }
    var subject by remember(target) { mutableStateOf("") }
    var attachment by remember(target) { mutableStateOf<PostingDraftAttachment?>(null) }
    var loaded by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var focusRequest by remember(target) { mutableIntStateOf(0) }
    // Replies start collapsed to a slim bar; new threads are always expanded.
    var expanded by rememberSaveable(target) { mutableStateOf(newThread) }
    // Not saveable: only focus when expansion came from a user action in this composition.
    var focusOnExpand by remember(target) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val alive = remember(target) { booleanArrayOf(true) }
    DisposableEffect(target) { onDispose { alive[0] = false } }
    fun save(t: String = text, s: String = subject) {
        val a = attachment
        draftExecutor.enqueue {
            val result = runCatching { store.save(target, t, s, a) }
            if (result.isFailure) mainHandler.post { if (alive[0]) error = "Could not save draft. Check storage." }
        }
    }
    LaunchedEffect(target) {
        draftExecutor.enqueue {
            val result = runCatching { store.load(target) }
            mainHandler.post {
                if (alive[0]) {
                    result.getOrNull()?.let { text = it.text; subject = it.subject; attachment = it.attachment }
                    if (result.isFailure) error = "Could not load draft."
                    loaded = result.isSuccess
                }
            }
        }
    }
    LaunchedEffect(quoteRequest, loaded) {
        if (loaded && quoteRequest != 0 && quoteId != null) {
            val updated = text + (if (text.isNotEmpty() && !text.endsWith("\n")) "\n" else "") + ">>$quoteId\n"
            if (updated.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_TEXT_BYTES) {
                text = updated
                save(t = updated)
                if (!expanded) focusOnExpand = true
                focusRequest++
            } else error = "Draft is too long to add this quote."
            expanded = true
            onQuoteHandled()
        }
    }
    LaunchedEffect(answer) {
        if (answer != null) { delay(CAPTCHA_ANSWER_LIFETIME_MS); answer = null; status = "CAPTCHA expired. Get a new one." }
    }
    fun send() {
        val captcha = answer ?: return
        val comment = text
        if (sending || comment.isBlank()) return
        sending = true; answer = null; status = "Posting…" // An answer is single-use, whatever the outcome.
        val client = Chan4PostClient(WebSettings.getDefaultUserAgent(context),
            cookies = { CookieManager.getInstance().getCookie(it) },
            storeCookie = { url, value -> CookieManager.getInstance().setCookie(url, value) })
        postExecutor.execute {
            val result = runCatching { client.reply(target, comment, captcha) }.getOrElse { Chan4PostResult.Unknown("Outcome unknown. Check the thread before trying again.") }
            mainHandler.post {
                if (!alive[0]) return@post
                sending = false
                status = when (result) {
                    is Chan4PostResult.Posted -> {
                        if (text == comment) { text = ""; save(t = "") }
                        onPosted()
                        "Posted."
                    }
                    Chan4PostResult.CaptchaRejected -> "CAPTCHA was not accepted. Get a new one."
                    is Chan4PostResult.Rejected -> result.message
                    is Chan4PostResult.Unknown -> result.message
                }
            }
        }
    }
    if (captchaOpen) CaptchaPage(target, onAnswer = { answer = it; status = null; captchaOpen = false }, onClose = { captchaOpen = false })
    if (!newThread && !expanded) {
        Surface(
            onClick = { focusOnExpand = loaded; expanded = true },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                val label = when {
                    error != null -> "Reply · $error"
                    loaded && text.isNotEmpty() -> "Reply · saved draft"
                    answer != null -> "Reply · CAPTCHA ready"
                    else -> "Reply"
                }
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ExpandLess, "Expand reply editor", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Column {
        if (newThread) Text("New threads cannot be posted from Trfl yet. Drafts are saved on this device.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        else Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { keyboard?.hide(); captchaOpen = true }, enabled = !sending) { Text(if (answer == null) "Get CAPTCHA" else "New CAPTCHA") }
            Text(status ?: if (answer != null) "CAPTCHA ready. You can post." else "Solve a CAPTCHA to post.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (answer != null && status == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { keyboard?.hide(); focusOnExpand = false; expanded = false }) {
                Icon(Icons.Default.ExpandMore, "Collapse reply editor")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (newThread) OutlinedTextField(subject, { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_SUBJECT_BYTES) { subject = it; save(s = it) } }, enabled = loaded, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
        ThreadReplyBar(text = text, onTextChange = { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_TEXT_BYTES) { text = it; save(t = it) } }, onSend = ::send, canSend = !newThread && answer != null && !sending, focusRequest = focusRequest, enabled = loaded && !sending, autoFocus = focusOnExpand)
    }
}
