package org.trfl.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level tabs. Deliberately plain strings: the current tab is `rememberSaveable` state and is
 * embedded in saved [PageRoute] keys, so these values must never change.
 */
object Destination {
    const val BOARDS = "Boards"
    const val WATCHED = "Watched"
    const val SAVES = "Saves"
    const val SETTINGS = "Settings"
    /** Former name of [SAVES]; restored state may still carry it and is migrated on launch. */
    const val LEGACY_BOOKMARKS = "Bookmarks"
}

class NavDestination(val label: String, val icon: ImageVector)

/** Shared by the bottom navigation bar and the wide-layout navigation rail. */
val navDestinations = listOf(
    NavDestination(Destination.BOARDS, Icons.Default.Dashboard),
    NavDestination(Destination.WATCHED, Icons.Default.Visibility),
    NavDestination(Destination.SAVES, Icons.Default.Bookmark),
    NavDestination(Destination.SETTINGS, Icons.Default.Settings),
)

/** Tab icon; the Watched tab carries a badge while any watched thread has unread replies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavDestinationIcon(item: NavDestination, watchedHasUnread: Boolean) {
    BadgedBox(badge = { if (item.label == Destination.WATCHED && watchedHasUnread) Badge() }) {
        Icon(item.icon, if (item.label == Destination.WATCHED && watchedHasUnread) "New replies" else null)
    }
}
