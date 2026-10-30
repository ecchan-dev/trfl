package org.trfl.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

@Composable
fun BoardDirectory(boards: List<Board>, favorites: Set<String>, onOpen: (String) -> Unit,
                   onFavorite: (String) -> Unit, onMove: (String, String) -> Unit,
                   positionKey: String = "boards", ready: Boolean = true,
                   scrollTop: Int = 0, onScrollTopVisibility: (Boolean) -> Unit = {}) {
    val state = persistentListState(positionKey, ready)
    val reportScrollTopVisibility by rememberUpdatedState(onScrollTopVisibility)
    val scrollTopRequest by rememberUpdatedState(scrollTop)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 200 }
            .collect { reportScrollTopVisibility(it) }
    }
    LaunchedEffect(state) {
        snapshotFlow { scrollTopRequest }.drop(1).collect { state.animateScrollToItem(0) }
    }
    val scope = rememberCoroutineScope()
    val currentBoards by rememberUpdatedState(boards)
    val currentFavorites by rememberUpdatedState(favorites)
    val move by rememberUpdatedState(onMove)
    var dragging by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<List<Board>?>(null) }
    var original by remember { mutableStateOf(emptyList<String>()) }
    var center by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<String?>(null) }
    val releaseOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val deadBand = with(density) { 6.dp.toPx() }
    val speed = with(density) { 480.dp.toPx() }
    val displayed = draft ?: boards

    fun finish(commit: Boolean) {
        val id = dragging ?: return
        val layout = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        val offset = layout?.let { center - it.offset - it.size / 2f } ?: 0f
        val result = draft ?: currentBoards
        dragging = null
        settling = id
        if (commit) dragDropNeighbor(original, result.map { it.id }, id)?.let { move(id, it) }
        scope.launch {
            releaseOffset.snapTo(offset)
            releaseOffset.animateTo(0f, spring(stiffness = 500f))
            draft = null; settling = null
        }
    }

    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (dragging != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = now
            val info = state.layoutInfo
            val proximity = when {
                center < info.viewportStartOffset + edge -> ((center - info.viewportStartOffset - edge) / edge).coerceIn(-1f, 0f)
                center > info.viewportEndOffset - edge -> ((center - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (proximity != 0f) state.scrollBy(proximity * speed * seconds)
            val id = dragging ?: break
            val order = draft ?: currentBoards
            val index = order.indexOfFirst { it.id == id }
            val visible = state.layoutInfo.visibleItemsInfo
            val item = visible.firstOrNull { it.key == id } ?: continue
            // Never use pre-reorder layout coordinates for another swap.
            if (item.index != index) continue
            fun neighbor(delta: Int) = order.getOrNull(index + delta)?.takeIf { (it.id in currentFavorites) == (id in currentFavorites) }
                ?.let { b -> visible.firstOrNull { it.key == b.id } }
            val previous = neighbor(-1)
            val next = neighbor(1)
            val delta = crossedDragNeighbor(center, item.offset + item.size / 2f,
                previous?.let { it.offset + it.size / 2f }, next?.let { it.offset + it.size / 2f }, deadBand)
            if (delta != 0) draft = order.toMutableList().apply { add(index + delta, removeAt(index)) }
        }
    }
    FastScrollList(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.Top, showScrollbar = false) {
        if (displayed.isEmpty()) item { Text("No matching boards. Star boards to add favorites.") }
        items(displayed, key = { it.id }) { b ->
            val dragged = dragging == b.id
            val releasing = settling == b.id
            fun accessibleMove(delta: Int): Boolean {
                val neighbor = currentBoards.getOrNull(currentBoards.indexOfFirst { it.id == b.id } + delta) ?: return false
                if ((b.id in currentFavorites) != (neighbor.id in currentFavorites)) return false
                move(b.id, neighbor.id)
                return true
            }
            val tileModifier = Modifier.fillMaxWidth()
                .animateItem(placementSpec = if (dragged || releasing) null else spring(stiffness = 400f))
                .zIndex(if (dragged || releasing) 1f else 0f)
                .graphicsLayer {
                    // Read the latest measured position, not a captured composition-time offset.
                    val layout = if (dragged) state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == b.id } else null
                    translationY = when {
                        dragged && layout != null -> center - layout.offset - layout.size / 2f
                        releasing -> releaseOffset.value
                        else -> 0f
                    }
                    shadowElevation = if (dragged) 8.dp.toPx() else 0f
                }
                .semantics { customActions = listOf(CustomAccessibilityAction("Move up") { accessibleMove(-1) }, CustomAccessibilityAction("Move down") { accessibleMove(1) }) }
                .clickable(enabled = dragging == null && settling == null) { onOpen(b.id) }
                ListItem(modifier = tileModifier, headlineContent = { Text(b.title, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("/${b.id}/" + if (!b.safe) " · 18+" else "") },
                    trailingContent = {
                        Row {
                            IconToggleButton(checked = b.id in favorites, enabled = dragging == null && settling == null, onCheckedChange = { onFavorite(b.id) }) {
                                Icon(if (b.id in favorites) Icons.Default.Star else Icons.Default.StarBorder, "Favorite /${b.id}/")
                            }
                            Box(Modifier.size(48.dp).pointerInput(b.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (settling == null) state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == b.id }?.let { item ->
                                            original = currentBoards.map { it.id }; draft = currentBoards
                                            center = item.offset + item.size / 2f; dragging = b.id
                                        }
                                    },
                                    onDragEnd = { finish(true) }, onDragCancel = { finish(false) },
                                    onDrag = { change, amount -> if (dragging == b.id) { change.consume(); center += amount.y } }
                                )
                            }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(Icons.Default.DragHandle, "Drag /${b.id}/ to reorder")
                            }
                        }
                    }, colors = ListItemDefaults.colors(containerColor = if (dragged || releasing) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface))
        }
    }
}
