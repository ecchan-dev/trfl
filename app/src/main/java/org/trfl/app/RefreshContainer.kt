package org.trfl.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshContainer(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    modifier: Modifier,
    indicator: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh,
            state = state, modifier = modifier, indicator = indicator, content = content)
    } else {
        Box(modifier = modifier, content = content)
    }
}
