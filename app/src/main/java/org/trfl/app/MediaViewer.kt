package org.trfl.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Full-screen pager over [attachments], opened at [selected]. Throw vertically or tap back to dismiss.
 * With [fromSaves] the actions archive/remove the saved media through [onArchiveSaved]/[onRemoveSaved];
 * otherwise they save it into Trfl ([savingMedia] guards against double taps) or export/download it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewer(
    selected: Post,
    attachments: List<Post>,
    onDismiss: () -> Unit,
    fromSaves: Boolean,
    savedMediaArchived: Boolean,
    onArchiveSaved: ((Post) -> Unit)?,
    onRemoveSaved: ((Post) -> Unit)?,
    savingMedia: Boolean,
    onSavingMediaChange: (Boolean) -> Unit,
    mediaSaves: MediaSaves,
    downloads: MediaDownloads,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveViewerWindow()
        val pager = rememberPagerState(initialPage = attachments.indexOfFirst { it.id == selected.id }.coerceAtLeast(0), pageCount = { attachments.size })
        val scope = rememberCoroutineScope()
        val carousel = rememberLazyListState()
        LaunchedEffect(pager.currentPage) { carousel.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                val post = attachments[index]
                var scale by remember(post.id) { mutableFloatStateOf(1f) }
                var offset by remember(post.id) { mutableStateOf(Offset.Zero) }
                var throwDistance by remember(post.id) { mutableFloatStateOf(0f) }
                val throwAnimation = remember(post.id) { Animatable(0f) }
                var throwing by remember(post.id) { mutableStateOf(false) }
                val viewerHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
                val throwGesture = Modifier.pointerInput(post.id, scale) {
                    if (scale <= 1.01f) {
                        val velocity = VelocityTracker()
                        detectVerticalDragGestures(onDragStart = { velocity.resetTracking(); throwDistance = 0f },
                            onDragCancel = { scope.launch { throwing = true; throwAnimation.snapTo(throwDistance); throwDistance = 0f; throwAnimation.animateTo(0f, spring()); throwing = false } },
                            onDragEnd = {
                                val speed = velocity.calculateVelocity().y
                                val dismiss = kotlin.math.abs(throwDistance) > viewerHeight * 0.18f || (kotlin.math.abs(speed) > 1400f && kotlin.math.abs(throwDistance) > 30f)
                                scope.launch {
                                    throwing = true; throwAnimation.snapTo(throwDistance); throwDistance = 0f
                                    if (dismiss) {
                                        throwAnimation.animateTo(if (throwAnimation.value < 0) -viewerHeight else viewerHeight, tween(180))
                                        onDismiss()
                                    } else throwAnimation.animateTo(0f, spring(stiffness = 450f))
                                    throwing = false
                                }
                            }, onVerticalDrag = { change, amount ->
                                if (!throwing) { change.consume(); throwDistance += amount; velocity.addPosition(change.uptimeMillis, change.position) }
                            })
                    }
                }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
                var mediaLoading by remember(post.image) { mutableStateOf(true) }
                var mediaFailed by remember(post.image) { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().then(throwGesture).graphicsLayer {
                    translationY = if (throwing) throwAnimation.value else throwDistance
                    alpha = (1f - kotlin.math.abs(translationY) / viewerHeight).coerceIn(0.15f, 1f)
                }) {
                    if (post.isVideo) {
                        if (pager.currentPage == index) AndroidVideo(post.image!!,
                            onLoadingChanged = { mediaLoading = it }, onError = { mediaLoading = false; mediaFailed = true })
                    } else AsyncImage(post.image, "Full-size attachment; pinch to zoom",
                        Modifier.fillMaxSize().transformable(state = transform, canPan = { scale > 1f }).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                        contentScale = ContentScale.Fit, onLoading = { mediaLoading = true; mediaFailed = false },
                        onSuccess = { mediaLoading = false; mediaFailed = false }, onError = { mediaLoading = false; mediaFailed = true })
                    if (mediaLoading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).displayCutoutPadding().padding(top = 64.dp).fillMaxWidth())
                    if (mediaFailed) Text("Unable to load attachment", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
            }
            TopAppBar(modifier = Modifier.align(Alignment.TopCenter),
                title = { Text("${pager.currentPage + 1} / ${attachments.size}") },
                navigationIcon = { IconButton(onClick = { onDismiss() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close media") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xD9171923), titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                windowInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), actions = {
                if (fromSaves) {
                    IconButton(onClick = { onArchiveSaved?.invoke(attachments[pager.currentPage]); onDismiss() }) {
                        Icon(if (savedMediaArchived) Icons.Default.Unarchive else Icons.Default.Archive, if (savedMediaArchived) "Unarchive media" else "Archive media")
                    }
                    IconButton(onClick = { onRemoveSaved?.invoke(attachments[pager.currentPage]); onDismiss() }) { Icon(Icons.Default.DeleteOutline, "Remove media from Saves") }
                } else IconButton(enabled = !savingMedia, onClick = {
                    val selected = attachments[pager.currentPage]
                    onSavingMediaChange(true)
                    scope.launch {
                        try {
                            mediaSaves.save(selected)
                            android.widget.Toast.makeText(context, "Saved in Trfl → Saves", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { android.widget.Toast.makeText(context, "Could not save media", android.widget.Toast.LENGTH_SHORT).show() }
                        finally { onSavingMediaChange(false) }
                    }
                }) { Icon(Icons.Default.BookmarkBorder, "Save media in Trfl") }
                IconButton(onClick = {
                    val image = attachments[pager.currentPage].image
                    if (image?.startsWith("https://i.4cdn.org/") == true) {
                        if (android.os.Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            (context as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 701)
                            android.widget.Toast.makeText(context, "Allow storage access, then tap Download again", android.widget.Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        runCatching { downloads.start(image) }.onSuccess { android.widget.Toast.makeText(context, "Download started · see Settings → Downloads", android.widget.Toast.LENGTH_SHORT).show() }
                            .onFailure { android.widget.Toast.makeText(context, "Download could not start", android.widget.Toast.LENGTH_SHORT).show() }
                    } else {
                        val selected = attachments[pager.currentPage]
                        scope.launch {
                            try { mediaSaves.exportLocal(selected); android.widget.Toast.makeText(context, "Exported to Downloads", android.widget.Toast.LENGTH_SHORT).show() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { android.widget.Toast.makeText(context, "Could not export media", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    }
                }) { Icon(Icons.Default.Download, "Export media to Downloads") }
            })
            BottomAppBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp),
                containerColor = Color(0xEB171923), contentColor = Color.White,
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                windowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
            LazyRow(state = carousel, modifier = Modifier.align(Alignment.Top).fillMaxWidth().height(64.dp),
                contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments.size, key = { attachments[it].id }) { index ->
                    val attachment = attachments[index]
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                        .border(if (pager.currentPage == index) 2.dp else 0.dp, if (pager.currentPage == index) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                        AsyncImage(attachment.thumb ?: attachment.image.takeUnless { attachment.isVideo }, "Attachment ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (attachment.isVideo) Icon(Icons.Default.PlayArrow, "Video", tint = Color.White)
                    }
                }
            }
            }
        }
    }
}
@Composable private fun AndroidVideo(url: String, onLoadingChanged: (Boolean) -> Unit, onError: () -> Unit) {
    val loadingCallback by rememberUpdatedState(onLoadingChanged)
    val errorCallback by rememberUpdatedState(onError)
    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply {
        loadingCallback(true)
        setOnPreparedListener { it.isLooping = true; loadingCallback(false); start() }
        setOnInfoListener { _, what, _ ->
            when (what) {
                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> loadingCallback(true)
                android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END,
                android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> loadingCallback(false)
            }
            false
        }
        setOnErrorListener { _, _, _ -> errorCallback(); true }
        setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) })
        setVideoURI(android.net.Uri.parse(url))
    } }, modifier = Modifier.fillMaxSize(), onRelease = {
        it.setOnPreparedListener(null); it.setOnInfoListener(null); it.setOnErrorListener(null); it.stopPlayback()
    })
}
