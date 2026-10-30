package org.trfl.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalReaderInsets = staticCompositionLocalOf { PaddingValues(0.dp) }
val LocalScrollbarInsets = compositionLocalOf { PaddingValues(0.dp) }

@Composable
private fun readerPadding(padding: PaddingValues): PaddingValues {
    val insets = LocalReaderInsets.current
    return PaddingValues(start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = padding.calculateTopPadding() + insets.calculateTopPadding(), bottom = padding.calculateBottomPadding() + insets.calculateBottomPadding())
}

fun fastScrollIndex(fraction: Float, total: Int): Int = ((total - 1).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).toInt()

@Composable
private fun BoxScope.FastScrollHandle(total: Int, position: Float, canScroll: Boolean, scrolling: Boolean, onSeek: (Float) -> Unit) {
    if (!canScroll || total < 2) return
    val haptic = LocalHapticFeedback.current
    val seek by rememberUpdatedState(onSeek)
    val scope = rememberCoroutineScope()
    var momentum by remember { mutableStateOf<Job?>(null) }
    var coasting by remember { mutableStateOf(false) }
    val inertia = remember { Animatable(0f).apply { updateBounds(0f, 1f) } }
    var dragging by remember { mutableStateOf(false) }
    var draggedFraction by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging, holding, coasting) {
        if (scrolling || dragging || holding || coasting) visible = true
        else { delay(1500); visible = false }
    }
    val opacity by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "Scrollbar visibility")
    val interactive by rememberUpdatedState(visible)
    // Position must track the list immediately; only visibility is animated.
    val fraction = if (dragging || coasting) draggedFraction else position
    BoxWithConstraints(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(48.dp)
        .padding(LocalScrollbarInsets.current).padding(vertical = 8.dp)) {
        val height = maxHeight
        val thumbHeight = 64.dp.coerceAtMost(height)
        val travelPx = with(LocalDensity.current) { (height - thumbHeight).toPx() }.coerceAtLeast(1f)
        val thumbPx = with(LocalDensity.current) { thumbHeight.toPx() }
        val currentTravel by rememberUpdatedState(travelPx)
        val currentThumb by rememberUpdatedState(thumbPx)
        val currentFraction by rememberUpdatedState(fraction)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity }.semantics {
            contentDescription = "Quick scroll"
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { value -> momentum?.cancel(); seek(value.coerceIn(0f, 1f)); true }
        }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!interactive) return@awaitEachGesture
                val initialTop = currentFraction * currentTravel
                val hitSlop = 24.dp.toPx()
                if (down.position.y !in (initialTop - hitSlop)..(initialTop + currentThumb + hitSlop)) return@awaitEachGesture
                // Reserve this thumb press before the list wins the vertical gesture.
                down.consume()
                momentum?.cancel(); coasting = false
                holding = true
                var latestY = down.position.y
                var releasedOrMoved = false
                try {
                    withTimeoutOrNull(300L) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null || !pointer.pressed) {
                                releasedOrMoved = true
                                break
                            }
                            latestY = pointer.position.y
                            pointer.consume()
                        }
                    }
                } finally { holding = false }
                if (releasedOrMoved) return@awaitEachGesture
                val grabOffset = (down.position.y - initialTop).coerceIn(0f, currentThumb)
                val travel = currentTravel
                draggedFraction = currentFraction
                dragging = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val tracker = VelocityTracker()
                var released = false
                fun update(y: Float) {
                    draggedFraction = ((y - grabOffset) / currentTravel).coerceIn(0f, 1f)
                    seek(draggedFraction)
                }
                update(latestY)
                try {
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) { released = true; break }
                        tracker.addPosition(pointer.uptimeMillis, pointer.position)
                        pointer.consume(); update(pointer.position.y)
                    } while (true)
                } finally {
                    dragging = false
                    if (released) {
                        val velocity = (tracker.calculateVelocity().y / travel).coerceIn(-2f, 2f)
                        momentum = scope.launch {
                            coasting = true
                            try {
                                inertia.snapTo(draggedFraction)
                                inertia.animateDecay(velocity, exponentialDecay(frictionMultiplier = 6f, absVelocityThreshold = 0.08f)) {
                                    draggedFraction = value
                                    seek(value)
                                }
                            } finally { coasting = false }
                        }
                    }
                }
            }
        }) {
            Box(Modifier.align(Alignment.TopEnd).offset(y = (height - thumbHeight) * fraction)
                .padding(end = 3.dp).width(if (dragging) 10.dp else 6.dp).height(thumbHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.95f else 0.55f), RoundedCornerShape(8.dp)))
        }
    }
}

fun scrollbarPosition(first: Float, visible: Float, total: Int, canBack: Boolean, canForward: Boolean): Float = when {
    !canBack -> 0f
    !canForward -> 1f
    else -> (first / (total - visible).coerceAtLeast(1f)).coerceIn(0f, 1f)
}

private data class ScrollMetrics(val total: Int, val position: Float, val scrollable: Boolean)

// Isolate high-frequency lazy-layout observations from the list's composition scope.
// Equal metrics suppress invalidation while pixels scroll within the same visible rows.
@Composable
private fun BoxScope.ListScrollOverlay(state: LazyListState, onSeek: (Float) -> Unit) {
    val metrics by remember(state) { derivedStateOf {
        val info = state.layoutInfo
        val average = info.visibleItemsInfo.map { it.size }.average().takeIf { it > 0 }?.toFloat() ?: 1f
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        ScrollMetrics(info.totalItemsCount, scrollbarPosition(state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / average,
            viewport / average, info.totalItemsCount, state.canScrollBackward, state.canScrollForward), state.canScrollBackward || state.canScrollForward)
    } }
    FastScrollHandle(metrics.total, metrics.position, metrics.scrollable, state.isScrollInProgress, onSeek)
}

@Composable
private fun BoxScope.GridScrollOverlay(state: LazyGridState, onSeek: (Float) -> Unit) {
    val metrics by remember(state) { derivedStateOf {
        val info = state.layoutInfo
        val columns = info.visibleItemsInfo.groupBy { it.row }.values.maxOfOrNull { it.size } ?: 1
        val height = (info.visibleItemsInfo.firstOrNull()?.size?.height ?: 1).coerceAtLeast(1)
        val visible = (info.viewportEndOffset - info.viewportStartOffset).toFloat() / height * columns
        ScrollMetrics(info.totalItemsCount, scrollbarPosition(state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset.toFloat() / height * columns,
            visible, info.totalItemsCount, state.canScrollBackward, state.canScrollForward), state.canScrollBackward || state.canScrollForward)
    } }
    FastScrollHandle(metrics.total, metrics.position, metrics.scrollable, state.isScrollInProgress, onSeek)
}

@Composable
fun FastScrollList(state: LazyListState = rememberLazyListState(), modifier: Modifier = Modifier,
                   contentPadding: PaddingValues = PaddingValues(0.dp), verticalArrangement: Arrangement.Vertical = Arrangement.Top,
                   showScrollbar: Boolean = true, content: LazyListScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = readerPadding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        if (showScrollbar) ListScrollOverlay(state) { fraction ->
            val info = state.layoutInfo
            val target = fraction * (info.totalItemsCount - 1).coerceAtLeast(0)
            val index = target.toInt()
            val height = info.visibleItemsInfo.firstOrNull { it.index == index }?.size
                ?: info.visibleItemsInfo.map { it.size }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
            val offset = ((target - index) * (height + info.mainAxisItemSpacing)).toInt()
            seekJob?.cancel(); seekJob = scope.launch {
                state.scrollToItem(index, offset)
                if (fraction >= 0.999f) {
                    state.scrollToItem((info.totalItemsCount - 1).coerceAtLeast(0))
                    state.scrollBy(((state.layoutInfo.visibleItemsInfo.lastOrNull()?.size ?: 0) + state.layoutInfo.afterContentPadding).toFloat())
                }
            }
        }
    }
}

@Composable
fun FastScrollGrid(state: LazyGridState, columns: GridCells, contentPadding: PaddingValues,
                   horizontalArrangement: Arrangement.Horizontal, verticalArrangement: Arrangement.Vertical,
                   content: LazyGridScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<Job?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(state = state, columns = columns, contentPadding = readerPadding(contentPadding),
            horizontalArrangement = horizontalArrangement, verticalArrangement = verticalArrangement, content = content)
        GridScrollOverlay(state) { fraction ->
            val info = state.layoutInfo
            val columnsCount = info.visibleItemsInfo.groupBy { it.row }.values.maxOfOrNull { it.size } ?: 1
            val rows = ((info.totalItemsCount + columnsCount - 1) / columnsCount).coerceAtLeast(1)
            val target = fraction * (rows - 1)
            val row = target.toInt()
            val height = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 1
            val offset = ((target - row) * (height + info.mainAxisItemSpacing)).toInt()
            val index = (row * columnsCount).coerceAtMost((info.totalItemsCount - 1).coerceAtLeast(0))
            seekJob?.cancel(); seekJob = scope.launch {
                state.scrollToItem(index, offset)
                if (fraction >= 0.999f) {
                    state.scrollToItem((info.totalItemsCount - 1).coerceAtLeast(0))
                    state.scrollBy(((state.layoutInfo.visibleItemsInfo.lastOrNull()?.size?.height ?: 0) + state.layoutInfo.afterContentPadding).toFloat())
                }
            }
        }
    }
}
