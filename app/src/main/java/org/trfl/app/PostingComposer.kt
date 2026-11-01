package org.trfl.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Enqueue synchronously on the UI thread: navigation and composition disposal cannot
// cancel or reorder a prior edit behind a subsequent load of the same draft.
private val draftExecutor = PostingDraftQueue()
private val mainHandler = Handler(Looper.getMainLooper())

@Composable
fun PostingComposer(
    target: PostingDraftKey,
    quoteId: Long?,
    quoteRequest: Int,
    store: PostingDraftStore,
    newThread: Boolean = false,
    onQuoteHandled: () -> Unit = {},
) {
    var text by remember(target) { mutableStateOf("") }
    var subject by remember(target) { mutableStateOf("") }
    var attachment by remember(target) { mutableStateOf<PostingDraftAttachment?>(null) }
    var loaded by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var focusRequest by remember(target) { mutableIntStateOf(0) }
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
                focusRequest++
            } else error = "Draft is too long to add this quote."
            onQuoteHandled()
        }
    }
    Column {
        Text("Posting is unavailable. Text drafts are saved on this device; use the official board to post.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        CaptchaControl(target)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (newThread) OutlinedTextField(subject, { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_SUBJECT_BYTES) { subject = it; save(s = it) } }, enabled = loaded, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
        ThreadReplyBar(text = text, onTextChange = { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_TEXT_BYTES) { text = it; save(t = it) } }, onSend = {}, canSend = false, focusRequest = focusRequest, enabled = loaded)
    }
}
