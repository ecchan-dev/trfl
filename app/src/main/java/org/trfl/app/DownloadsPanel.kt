package org.trfl.app

import android.app.DownloadManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DownloadsPanel(downloads: MediaDownloads) {
    var items by remember { mutableStateOf(emptyList<MediaDownloads.Transfer>()) }
    LaunchedEffect(downloads) {
        while (true) {
            items = withContext(Dispatchers.IO) { downloads.list() }
            delay(1000)
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isEmpty()) item { Text("No media downloads. Use Download in the image viewer.") }
        items(items, key = { it.id }) { transfer ->
            Column {
                Text(transfer.title, style = MaterialTheme.typography.titleSmall)
                Text(when (transfer.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Saved in Downloads"
                    DownloadManager.STATUS_FAILED -> "Download failed"
                    DownloadManager.STATUS_PAUSED -> "Paused · Android resumes when the network is ready"
                    else -> "${transfer.done / 1024} / ${if (transfer.total > 0) (transfer.total / 1024).toString() else "?"} KiB"
                })
                if (transfer.status == DownloadManager.STATUS_RUNNING) {
                    if (transfer.total > 0) LinearProgressIndicator(progress = { transfer.done.toFloat() / transfer.total }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row {
                    if (transfer.status == DownloadManager.STATUS_FAILED) TextButton(onClick = { downloads.retry(transfer.id) }) { Text("Retry") }
                    if (transfer.status != DownloadManager.STATUS_SUCCESSFUL) TextButton(onClick = { downloads.cancel(transfer.id) }) { Text("Cancel") }
                }
            }
        }
    }
}
