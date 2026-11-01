package org.trfl.app

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All disk work is serialized, including loads after navigation. */
private val draftIo = Dispatchers.IO.limitedParallelism(1)

@Composable
fun PostingComposer(
    target: PostingDraftKey,
    quoteId: Long?,
    quoteRequest: Int,
    scope: CoroutineScope,
    store: PostingDraftStore,
    newThread: Boolean = false,
    onQuoteHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    var text by remember(target) { mutableStateOf("") }
    var subject by remember(target) { mutableStateOf("") }
    var attachment by remember(target) { mutableStateOf<PostingDraftAttachment?>(null) }
    var loaded by remember(target) { mutableStateOf(false) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var focusRequest by remember(target) { mutableIntStateOf(0) }
    fun save(t: String = text, s: String = subject, a: PostingDraftAttachment? = attachment) {
        scope.launch {
            val result = withContext(draftIo) { runCatching { store.save(target, t, s, a) } }
            if (result.isFailure) error = "Could not save draft. Check storage and try again."
        }
    }
    LaunchedEffect(target) {
        val result = withContext(draftIo) { runCatching { store.load(target) } }
        result.getOrNull()?.let { text = it.text; subject = it.subject; attachment = it.attachment }
        if (result.isFailure) error = "Could not load draft."
        loaded = true
    }
    LaunchedEffect(quoteRequest, loaded) {
        if (loaded && quoteRequest != 0 && quoteId != null) {
            text += (if (text.isNotEmpty() && !text.endsWith("\n")) "\n" else "") + ">>$quoteId\n"
            save(t = text)
            focusRequest++
            onQuoteHandled()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var name: String? = null
                var size: Long? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val z = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)
                        if (z >= 0 && !cursor.isNull(z)) size = cursor.getLong(z)
                    }
                }
                attachment = PostingDraftAttachment(uri.toString(), name, context.contentResolver.getType(uri), size)
                save(a = attachment)
            } catch (_: Exception) { error = "Could not retain access to that attachment. Choose another file." }
        }
    }
    Column {
        Text("Posting is unavailable in Trfl. Drafts are saved locally; use the official board to post.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (newThread) OutlinedTextField(subject, { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_SUBJECT_BYTES) { subject = it; save(s = it) } }, enabled = loaded, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
        attachment?.let { file ->
            val accessible = remember(file.uri) { context.contentResolver.persistedUriPermissions.any { it.uri.toString() == file.uri && it.isReadPermission } }
            if (!accessible) Text("Attachment access lost. Remove it and choose it again.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (file.mimeType?.startsWith("image/") == true) AsyncImage(model = Uri.parse(file.uri), contentDescription = "Attachment preview", modifier = Modifier.size(48.dp))
                Text(file.displayName ?: "Attachment", modifier = Modifier.weight(1f).padding(8.dp), maxLines = 1)
                IconButton(onClick = { attachment = null; save(a = null) }) { Icon(Icons.Default.Close, "Remove attachment") }
            }
        }
        Row {
            IconButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }, enabled = loaded) { Icon(Icons.Default.AttachFile, "Add attachment") }
            ThreadReplyBar(text = text, onTextChange = { if (it.toByteArray(Charsets.UTF_8).size <= PostingDraftStore.MAX_TEXT_BYTES) { text = it; save(t = it) } }, onSend = {}, canSend = false, focusRequest = focusRequest, enabled = loaded, modifier = Modifier.weight(1f))
        }
    }
}
