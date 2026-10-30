package org.trfl.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview

private data class ScrollPosition(val index: Int, val offset: Int)

@OptIn(FlowPreview::class)
@Composable
fun persistentListState(key: String, ready: Boolean = true): LazyListState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scroll-positions", 0) }
    val state = remember(key) { LazyListState(prefs.getInt("$key:index", 0).coerceAtLeast(0), prefs.getInt("$key:offset", 0).coerceAtLeast(0)) }
    var restored by remember(key) { mutableStateOf(false) }
    val enabled by rememberUpdatedState(ready && restored)
    LaunchedEffect(key, ready) {
        if (ready && !restored) {
            // Wait until the list has content, rather than recording its temporary empty state.
            snapshotFlow { state.layoutInfo.totalItemsCount }.collect { total ->
                if (total > 0 && !restored) {
                    state.scrollToItem(prefs.getInt("$key:index", 0).coerceIn(0, total - 1), prefs.getInt("$key:offset", 0).coerceAtLeast(0))
                    restored = true
                }
            }
        }
    }
    fun save() {
        if (enabled && state.layoutInfo.totalItemsCount > 0) prefs.edit().putInt("$key:index", state.firstVisibleItemIndex).putInt("$key:offset", state.firstVisibleItemScrollOffset).apply()
    }
    LaunchedEffect(state) {
        snapshotFlow { ScrollPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }.debounce(250).collect { save() }
    }
    DisposableEffect(state) { onDispose { save() } }
    return state
}

@OptIn(FlowPreview::class)
@Composable
fun persistentGridState(key: String, ready: Boolean = true): LazyGridState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scroll-positions", 0) }
    val state = remember(key) { LazyGridState(prefs.getInt("$key:index", 0).coerceAtLeast(0), prefs.getInt("$key:offset", 0).coerceAtLeast(0)) }
    var restored by remember(key) { mutableStateOf(false) }
    val enabled by rememberUpdatedState(ready && restored)
    LaunchedEffect(key, ready) {
        if (ready && !restored) snapshotFlow { state.layoutInfo.totalItemsCount }.collect { total ->
            if (total > 0 && !restored) {
                state.scrollToItem(prefs.getInt("$key:index", 0).coerceIn(0, total - 1), prefs.getInt("$key:offset", 0).coerceAtLeast(0))
                restored = true
            }
        }
    }
    fun save() {
        if (enabled && state.layoutInfo.totalItemsCount > 0) prefs.edit().putInt("$key:index", state.firstVisibleItemIndex).putInt("$key:offset", state.firstVisibleItemScrollOffset).apply()
    }
    LaunchedEffect(state) {
        snapshotFlow { ScrollPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }.debounce(250).collect { save() }
    }
    DisposableEffect(state) { onDispose { save() } }
    return state
}
