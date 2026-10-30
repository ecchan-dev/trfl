package org.trfl.app

import android.app.DownloadManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SavedAsset(val key: String, val post: Post, val label: String, val search: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavesPanel(keys: Set<String>, archived: Set<String>, showArchive: Boolean, query: String,
               revision: Any, store: ThreadStore, downloads: MediaDownloads, dead: Set<String>,
               status: (String) -> String, onOpenThread: (String) -> Unit,
               onOpenMedia: (List<Post>, Post, (Post) -> Unit, (Post) -> Unit, Boolean) -> Unit, onArchive: (String) -> Unit, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trfl", 0) }
    val pager = rememberPagerState(initialPage = prefs.getInt("savesTab", 0).coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { prefs.edit().putInt("savesTab", it).apply() }
    }
    var snapshots by remember { mutableStateOf(emptyMap<String, List<Post>>()) }
    var exports by remember { mutableStateOf(emptyList<MediaDownloads.Transfer>()) }
    var loading by remember { mutableStateOf(true) }
    val mediaSaves = remember { MediaSaves(context) }
    var standalone by remember { mutableStateOf(emptyList<Post>()) }
    var archivedMedia by remember { mutableStateOf(prefs.getStringSet("archivedMedia", emptySet())!!.toSet()) }
    var removedMedia by remember { mutableStateOf(prefs.getStringSet("removedMedia", emptySet())!!.toSet()) }
    var pendingMediaRemoval by remember { mutableStateOf<SavedAsset?>(null) }
    var removalError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(keys, revision) {
        loading = true
        snapshots = keys.associateWith { store.load(it).orEmpty() }
        exports = withContext(Dispatchers.IO) { downloads.list().filter { it.status == DownloadManager.STATUS_SUCCESSFUL && it.library } }
        standalone = withContext(Dispatchers.IO) { mediaSaves.list() }
        loading = false
    }
    val threadKeys = keys.filter { (it in archived) == showArchive }.sorted()
    val assets = remember(snapshots, exports, standalone, keys) {
        buildList {
            keys.sorted().forEach { key -> snapshots[key].orEmpty().filter { it.image?.let { url -> url.startsWith("file:") || url.startsWith("content:") } == true }.forEach { post ->
                add(SavedAsset("$key/${post.id}", post, "/$key · ${post.id}", "$key ${post.id} ${snapshots[key]?.firstOrNull()?.title.orEmpty()} ${post.body} ${post.image}"))
            } }
            standalone.forEach { post -> add(SavedAsset("private:${post.id}", post, post.title, "${post.title} ${post.body}")) }
            exports.forEach { transfer -> transfer.uri?.let { uri ->
                val video = transfer.title.substringBefore('?').endsWith(".webm", true) || transfer.title.endsWith(".mp4", true)
                val post = Post(-transfer.id, transfer.title, transfer.url.orEmpty(), uri, if (video) null else uri, 0, video = video)
                add(SavedAsset("export:${transfer.id}", post, transfer.title, "${transfer.title} ${transfer.url.orEmpty()}"))
            } }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pager.currentPage) {
            listOf("Threads", "Photos", "Videos").forEachIndexed { index, title ->
                Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(title) })
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { tab ->
        if (tab == 0) {
            val matches = threadKeys.filter { key -> "$key ${snapshots[key]?.firstOrNull()?.title.orEmpty()} ${snapshots[key].orEmpty().joinToString(" ") { it.body }}".contains(query, true) }
            FastScrollList(state = persistentListState("saves:threads:$showArchive:$query", !loading), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matches.isEmpty() && !loading) item { Text(if (showArchive) "No matching archived threads" else "No matching saved threads") }
                items(matches, key = { it }) { key ->
                    var menu by remember { mutableStateOf(false) }
                    ListItem(headlineContent = { Text(snapshots[key]?.firstOrNull()?.title?.ifBlank { null } ?: "/$key", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("/$key" + (if (key in dead) " · Dead" else "") + " · ${status(key)}") },
                        leadingContent = { Icon(if (showArchive) Icons.Default.Archive else Icons.Default.Bookmark, null) },
                        trailingContent = { Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Saved thread options /$key") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (showArchive) "Unarchive" else "Archive") }, onClick = { onArchive(key); menu = false })
                                DropdownMenuItem(text = { Text("Remove from Saves") }, onClick = { onRemove(key); menu = false })
                            }
                        } }, modifier = Modifier.animateItem().clickable { onOpenThread(key) })
                }
            }
        } else {
            fun isArchived(asset: SavedAsset) = asset.key in archivedMedia || archived.any { asset.key.startsWith("$it/") }
            val matches = assets.filter { it.key !in removedMedia && isArchived(it) == showArchive && it.post.isVideo == (tab == 2) && it.search.contains(query, true) }
            val grid = persistentGridState("saves:media:$tab:$showArchive:$query", !loading && matches.isNotEmpty())
            if (matches.isEmpty() && !loading) Text("No matching saved ${if (tab == 2) "videos" else "photos"}", Modifier.padding(24.dp))
            FastScrollGrid(state = grid, columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.key }) { asset ->
                    Card(onClick = { onOpenMedia(matches.map { it.post }, asset.post,
                        { selected -> matches.find { it.post.id == selected.id }?.let { selectedAsset ->
                            if (archived.any { selectedAsset.key.startsWith("$it/") }) onArchive(archived.first { selectedAsset.key.startsWith("$it/") })
                            archivedMedia = if (selectedAsset.key in archivedMedia) archivedMedia - selectedAsset.key else if (!isArchived(selectedAsset)) archivedMedia + selectedAsset.key else archivedMedia
                            prefs.edit().putStringSet("archivedMedia", archivedMedia).apply()
                        } },
                        { selected -> pendingMediaRemoval = matches.find { it.post.id == selected.id } }, showArchive)
                    }) {
                        var menu by remember { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            if (asset.post.thumb != null) AsyncImage(asset.post.thumb, asset.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (asset.post.isVideo) Icon(Icons.Default.PlayArrow, "Play saved video")
                            Box(Modifier.align(Alignment.TopEnd)) {
                                FilledTonalIconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Saved media options") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(if (isArchived(asset)) "Unarchive" else "Archive") }, onClick = {
                                        if (archived.any { asset.key.startsWith("$it/") }) onArchive(archived.first { asset.key.startsWith("$it/") })
                                        archivedMedia = if (asset.key in archivedMedia) archivedMedia - asset.key else if (!isArchived(asset)) archivedMedia + asset.key else archivedMedia
                                        prefs.edit().putStringSet("archivedMedia", archivedMedia).apply(); menu = false
                                    })
                                    DropdownMenuItem(text = { Text("Remove from Saves") }, onClick = { pendingMediaRemoval = asset; menu = false })
                                }
                            }
                        }
                        Text(asset.label, Modifier.padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        }
    }
    pendingMediaRemoval?.let { asset ->
        AlertDialog(onDismissRequest = { pendingMediaRemoval = null }, title = { Text("Remove media from Saves?") },
            text = { Text("The standalone Trfl copy will be removed. Phone downloads and saved-thread originals are kept.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try {
                        if (asset.key.startsWith("private:")) mediaSaves.remove(requireNotNull(asset.post.image))
                        if (!asset.key.startsWith("private:")) removedMedia = removedMedia + asset.key
                        archivedMedia = archivedMedia - asset.key
                        prefs.edit().putStringSet("removedMedia", removedMedia).putStringSet("archivedMedia", archivedMedia).apply()
                        standalone = withContext(Dispatchers.IO) { mediaSaves.list() }
                        pendingMediaRemoval = null
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { removalError = "Could not remove saved media"; pendingMediaRemoval = null }
                }
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { pendingMediaRemoval = null }) { Text("Cancel") } })
    }
    removalError?.let { message -> AlertDialog(onDismissRequest = { removalError = null }, title = { Text(message) }, confirmButton = { TextButton(onClick = { removalError = null }) { Text("OK") } }) }
}
