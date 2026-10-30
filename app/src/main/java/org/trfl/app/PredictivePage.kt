package org.trfl.app

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer

fun pageTransitionDirection(previous: PageRoute, next: PageRoute): Float {
    if (previous.tab != next.tab || (previous.thread == 0L && next.thread == 0L) || (previous.thread == 0L && next.thread != 0L)) return 0f
    fun depth(route: PageRoute) = if (route.thread != 0L) 2 else if (route.board.isNotEmpty()) 1 else 0
    return when {
        depth(next) > depth(previous) -> 1f
        depth(next) < depth(previous) -> -1f
        else -> 0f
    }
}

internal fun predictivePageProgress(hasParentPreview: Boolean, progress: Float): Float =
    if (hasParentPreview) progress.coerceIn(0f, 1f) else 0f

/** Window previews use PixelCopy: never re-enter View.draw or record Compose layers. */
@Composable
fun PredictivePage(route: String, parent: String?, enabled: Boolean, onBack: () -> Unit,
                   previewStyle: String = "default", retainedRoutes: List<String> = listOfNotNull(parent, route),
                   content: @Composable () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val view = LocalView.current
    val previews = remember(previewStyle) { mutableStateMapOf<String, Bitmap>() }
    var captureGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(route, previewStyle) {
        captureGeneration++
        onDispose { captureGeneration++ }
    }
    SideEffect { previews.keys.toList().filter { it !in retainedRoutes }.forEach { previews.remove(it) } }
    var capturePaused by remember { mutableStateOf(false) }
    LaunchedEffect(route, previewStyle) {
        // Capture this page after it has actually drawn, not the outgoing window
        // during navigation. In particular this primes the board-directory parent.
        for (wait in listOf(0L, 120L, 500L)) {
            delay(wait)
            withFrameNanos { }
            withFrameNanos { }
            if (capturePaused) continue
            val generation = captureGeneration
            var context = view.context
            while (context is ContextWrapper && context !is Activity) context = context.baseContext
            val window = (context as? Activity)?.window
            if (window != null && view.width > 0 && view.height > 0) {
                // Half-resolution previews bound memory for the entire eight-page history.
                val bitmap = Bitmap.createBitmap((view.width / 2).coerceAtLeast(1), (view.height / 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val location = IntArray(2); view.getLocationInWindow(location)
                runCatching {
                    PixelCopy.request(window, Rect(location[0], location[1], location[0] + view.width, location[1] + view.height), bitmap, { result ->
                        if (result == PixelCopy.SUCCESS && generation == captureGeneration && !capturePaused) {
                            previews[route] = bitmap
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            }
        }
    }
    val parentPreview = parent?.let { previews[it] }
    val displacement = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(route) }
    val scope = rememberCoroutineScope()
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var gestureEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var gestureActive by remember { mutableStateOf(false) }
    var skipTransition by remember { mutableStateOf(false) }
    val recovery = remember { Animatable(0f) }
    var recovering by remember { mutableStateOf(false) }
    val direction = pageTransitionDirection(PageRoute.parse(previous), PageRoute.parse(route))
    LaunchedEffect(route) {
        val start = if (skipTransition) 0f else pageTransitionDirection(PageRoute.parse(previous), PageRoute.parse(route))
        skipTransition = false
        previous = route
        displacement.snapTo(start)
        displacement.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
    }
    PredictiveBackHandler(enabled) { events ->
        capturePaused = true
        captureGeneration++ // Reject in-flight captures of a transformed gesture frame.
        gestureActive = true
        try {
            events.collect { event -> gestureProgress = event.progress; gestureEdge = event.swipeEdge }
            skipTransition = true
            currentOnBack()
            gestureProgress = 0f; gestureActive = false; capturePaused = false
        } catch (cancelled: CancellationException) {
            val value = gestureProgress
            recovering = true; gestureActive = false
            scope.launch {
                try { recovery.snapTo(value); recovery.animateTo(0f, spring(stiffness = 500f)) }
                finally { gestureProgress = 0f; recovering = false; capturePaused = false }
            }
            throw cancelled
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        if (gestureActive || recovering) parentPreview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Box(Modifier.fillMaxSize().graphicsLayer {
            // Apply the initial offset immediately on route change, before its first draw.
            // Restored sessions, very fast navigation and theme changes may have no
            // screenshot yet. Keep the page opaque instead of exposing an empty window.
            val progress = predictivePageProgress(parentPreview != null, if (recovering) recovery.value else if (gestureActive) gestureProgress else 0f)
            translationX = if (progress > 0f) size.width * progress * 0.3f * (if (gestureEdge == BackEventCompat.EDGE_LEFT) 1 else -1)
                else size.width * if (skipTransition) 0f else if (previous != route) direction else displacement.value
            scaleX = 1f - progress * 0.1f; scaleY = 1f - progress * 0.1f
            shape = RoundedCornerShape((progress * 24).dp); clip = progress > 0f
        }) { content() }
    }
}
