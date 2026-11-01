package org.trfl.app

import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import kotlinx.coroutines.flow.first
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import org.json.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var incomingLink by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink = intent.dataString
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        ApiRequests.initialize(cacheDir)
        ImageCaching.initialize(applicationContext)
        incomingLink = intent.dataString
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()) { Trfl(incomingLink) { incomingLink = null } }
        }
    }
}

data class Board(val id: String, val title: String, val safe: Boolean)
data class Post(val id: Long, val title: String, val body: String, val image: String?, val thumb: String?, val replies: Int,
    val images: Int = 0, val created: Long = 0, val lastReply: Long = 0, val video: Boolean = false) {
    val isVideo: Boolean get() = video || image?.substringBefore('?')?.let { it.endsWith(".webm", true) || it.endsWith(".mp4", true) } == true
}
fun plain(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()
fun parsePost(p: JSONObject, board: String): Post {
    val tim = p.optLong("tim")
    return Post(p.getLong("no"), plain(p.optString("sub")), plain(p.optString("com")),
        if (tim > 0) "https://i.4cdn.org/$board/$tim${p.optString("ext")}" else null,
        if (tim > 0) "https://i.4cdn.org/$board/${tim}s.jpg" else null, p.optInt("replies"), p.optInt("images"), p.optLong("time"),
        p.optLong("last_modified", p.optJSONArray("last_replies")?.let { replies ->
            (0 until replies.length()).maxOfOrNull { replies.getJSONObject(it).optLong("time") }
        } ?: p.optLong("time")))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Trfl(incomingLink: String? = null, consumeLink: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trfl", 0) }
    var showLurkNotice by rememberSaveable { mutableStateOf(!prefs.getBoolean("lurkNoticeAcknowledged", false)) }
    val store = remember { ThreadStore(context) }
    val downloads = remember { MediaDownloads(context) }
    val mediaSaves = remember { MediaSaves(context) }
    var savingMedia by remember { mutableStateOf(false) }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var showDonation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveJobs = remember { mutableMapOf<String, Job>() }
    var saveStatus by remember { mutableStateOf(emptyMap<String, String>()) }
    var archived by remember { mutableStateOf(prefs.getStringSet("archived", emptySet())!!.toSet()) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var dead by remember { mutableStateOf(prefs.getStringSet("dead", emptySet())!!.toSet()) }
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    var localCopy by remember { mutableStateOf(false) }
    var watchMenu by remember { mutableStateOf(false) }
    fun markDead(entry: String) { dead = dead + entry; prefs.edit().putStringSet("dead", dead).apply() }
    fun markRead(entry: String) {
        val total = prefs.getInt("total:$entry", prefs.getInt("seen:$entry", 0))
        prefs.edit().putInt("seen:$entry", total).apply()
    }
    var board by rememberSaveable { mutableStateOf("") }
    var thread by rememberSaveable { mutableLongStateOf(0L) }
    var catalog by rememberSaveable { mutableStateOf(prefs.getBoolean("catalog", true)) }
    var adult by rememberSaveable { mutableStateOf(!BuildConfig.SFW_ONLY && prefs.getBoolean("adult", false)) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(board, thread) { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    fun refresh() { forceRefresh = true; reload++ }
    var scrollTop by remember { mutableIntStateOf(0) }
    var quote by remember { mutableStateOf<Post?>(null) }
    var replyText by rememberSaveable(board, thread) { mutableStateOf("") }
    var replyFocusRequest by remember { mutableIntStateOf(0) }
    var replyListFor by remember(board, thread) { mutableStateOf<Long?>(null) }
    var jumpTo by remember { mutableStateOf<Long?>(null) }
    var boards by remember { mutableStateOf(emptyList<Board>()) }
    var posts by remember { mutableStateOf(emptyList<Post>()) }
    val postReplies = remember(posts) { indexPostReplies(posts) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf<Post?>(null) }
    var savedMedia by remember { mutableStateOf<List<Post>?>(null) }
    var savedMediaArchive by remember { mutableStateOf<((Post) -> Unit)?>(null) }
    var savedMediaRemove by remember { mutableStateOf<((Post) -> Unit)?>(null) }
    var savedMediaArchived by remember { mutableStateOf(false) }
    LaunchedEffect(media) { if (media == null) savedMedia = null }
    var saved by remember { mutableStateOf(prefs.getStringSet("bookmarks", emptySet())!!.filter { BoardPolicy.allows(it.substringBefore('/')) }.toSet()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var boardOrder by remember { mutableStateOf(prefs.getString("boardOrder", "")!!.split(',').filter { it.isNotEmpty() }) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var boardSequence by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var watched by remember { mutableStateOf(prefs.getStringSet("watched", emptySet())!!.filter { BoardPolicy.allows(it.substringBefore('/')) }.toSet()) }
    var watchStatus by remember { mutableStateOf(emptyMap<String, String>()) }
    var watchUnread by remember { mutableStateOf(watched.associateWith { entry ->
        val total = prefs.getInt("total:$entry", 0)
        (total - prefs.getInt("seen:$entry", total)).coerceAtLeast(0)
    }) }
    DisposableEffect(prefs, watched) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed?.startsWith("total:") == true || changed?.startsWith("seen:") == true) {
                watchUnread = watched.associateWith { entry ->
                    val total = prefs.getInt("total:$entry", 0)
                    (total - prefs.getInt("seen:$entry", total)).coerceAtLeast(0)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val watchedHasUnread = watched.any { (watchUnread[it] ?: 0) > 0 }
    var imagesOnly by rememberSaveable(board, thread) { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf("Boards") }
    var showScrollTop by remember(destination, board, thread, imagesOnly, catalog) { mutableStateOf(false) }
    var boardsResumeBoard by rememberSaveable { mutableStateOf("") }
    var boardsResumeThread by rememberSaveable { mutableLongStateOf(0L) }
    SideEffect {
        if (destination == "Boards") { boardsResumeBoard = board; boardsResumeThread = thread }
    }
    fun selectDestination(label: String) {
        if (destination == label) {
            if (label == "Boards") {
                if (thread != 0L) thread = 0 else if (board.isNotEmpty()) board = ""
                query = ""
            }
            return
        }
        destination = label
        if (label == "Boards") { board = boardsResumeBoard; thread = boardsResumeThread }
        else if (label != "Settings") { board = ""; thread = 0 }
        query = ""
    }
    LaunchedEffect(destination) { if (destination == "Bookmarks") destination = "Saves" }
    LaunchedEffect(incomingLink) {
        parseThreadLink(incomingLink)?.let { target ->
            destination = "Boards"; board = target.board; thread = target.thread
        }
        if (incomingLink != null) consumeLink()
    }
    val onlySaved = destination == "Saves"
    var searchOpen by rememberSaveable(board, thread, destination) { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var boardColors by rememberSaveable { mutableStateOf(prefs.getBoolean("boardColors", true)) }
    var chanThemeName by rememberSaveable { mutableStateOf(prefs.getString("chanTheme", "Automatic")) }
    val chanTheme = ChanTheme.fromSaved(chanThemeName)
    var themeMenuOpen by remember { mutableStateOf(false) }
    var chanAppearance by rememberSaveable { mutableStateOf(prefs.getString("chanAppearance", "System") ?: "System") }
    val chanDark = when (chanAppearance) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val safe = board.isEmpty() || (boards.find { it.id == board }?.safe ?: prefs.getBoolean("safe:$board", true))
    val phoneDark = isSystemInDarkTheme()
    val phoneColors = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (phoneDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (phoneDark) darkColorScheme() else lightColorScheme()
    val colors = if (boardColors) chanColors(chanTheme, safe, chanDark) else phoneColors
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 600
    val key = "$board/$thread"
    var pageMenu by remember(board, thread, destination) { mutableStateOf(false) }
    var sortMenu by remember(board, thread, destination) { mutableStateOf(false) }
    var threadSort by rememberSaveable(board) { mutableStateOf(ThreadSort.fromSaved(prefs.getString("sort:$board", null)).name) }
    val route = PageRoute(destination, if (destination == "Settings") "" else board, if (destination == "Settings") 0 else thread).key
    var history by rememberSaveable { mutableStateOf(listOf(PageRoute("Boards").key)) }
    SideEffect { history = nextHistory(history, route) }
    val cachedPosts = remember(reload, demo) { linkedMapOf<String, Pair<Long, List<Post>>>() }
    val apiPath = if (board.isEmpty()) "boards.json" else if (thread == 0L) "$board/catalog.json" else "$board/thread/$thread.json"
    val pageStates = rememberSaveableStateHolder()
    var barsVisible by remember { mutableStateOf(true) }
    var scrollTravel by remember { mutableFloatStateOf(0f) }
    val barThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val touchExploration = (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled
    val showBars = barsVisible || searchOpen || destination == "Settings" || touchExploration
    LaunchedEffect(route, scrollTop) { barsVisible = true; scrollTravel = 0f }
    val barScroll = remember(barThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    if (scrollTravel * consumed.y < 0) scrollTravel = 0f
                    scrollTravel += consumed.y
                    if (scrollTravel < -barThreshold) barsVisible = false
                    if (scrollTravel > barThreshold) barsVisible = true
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(destination, board, reload, demo) {
        if ((destination == "Watched" || destination == "Saves") && board.isEmpty()) {
            for (entry in if (destination == "Watched") watched else saved) {
                if (demo) { watchStatus = watchStatus + (entry to "Preview thread"); continue }
                try {
                    val array = JSONObject(request("${entry.substringBefore('/')}/thread/${entry.substringAfter('/')}.json", force = true)).getJSONArray("posts")
                    val count = array.length()
                    val seen = prefs.getInt("seen:$entry", count)
                    watchUnread = watchUnread + (entry to (count - seen).coerceAtLeast(0))
                    prefs.edit().putInt("total:$entry", count).apply()
                    watchStatus = watchStatus + (entry to "${(count - seen).coerceAtLeast(0)} new · ${count - 1} replies")
                    if (array.getJSONObject(0).optInt("archived") == 1) markDead(entry)
                    else if (entry in dead) { dead = dead - entry; prefs.edit().putStringSet("dead", dead).apply() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is ApiException && e.status == 404) markDead(entry)
                    watchStatus = watchStatus + (entry to if (entry in dead) "Dead thread" else "Unavailable · retry with Refresh")
                }
                delay(1100) // Respect the public API; only check on opening this tab or explicit refresh.
            }
        }
    }
    LaunchedEffect(board, thread, reload, demo) {
        val force = forceRefresh
        forceRefresh = false
        val memory = cachedPosts[key]?.takeIf { !force && System.currentTimeMillis() - it.first in 0 until cacheLifetimeMillis(apiPath) }
        loading = true; error = null; posts = memory?.second ?: emptyList(); localCopy = false
        if (posts.isNotEmpty()) { loading = false; return@LaunchedEffect }
        try {
            if (demo) {
                boards = listOf(Board("g", "Technology", true), Board("w", "Anime/Wallpapers", true), Board("lit", "Literature", true))
                posts = (1..24).map { Post(it.toLong(), if (thread == 0L) "Trfl preview · Thread $it" else "Anonymous · Reply $it", "A fast, native home for your boards.\n\nThis is sample data. Turn off Preview to browse live boards.", null, null, it * 3) }
            } else if (board.isEmpty()) {
                val array = JSONObject(request("boards.json", force)).getJSONArray("boards")
                boards = (0 until array.length()).map { val b = array.getJSONObject(it); Board(b.getString("board"), b.getString("title"), b.optInt("ws_board") == 1) }
                    .filter { !BuildConfig.SFW_ONLY || (it.safe && BoardPolicy.allows(it.id)) }
                prefs.edit().also { editor -> boards.forEach { editor.putBoolean("safe:${it.id}", it.safe) } }.apply()
            } else if (thread == 0L) {
                val pages = JSONArray(request("$board/catalog.json", force))
                posts = buildList { for (i in 0 until pages.length()) { val ts = pages.getJSONObject(i).getJSONArray("threads"); for (j in 0 until ts.length()) add(parsePost(ts.getJSONObject(j), board)) } }
            } else {
                val array = JSONObject(request("$board/thread/$thread.json", force)).getJSONArray("posts")
                posts = (0 until array.length()).map { parsePost(array.getJSONObject(it), board) }
                if (array.getJSONObject(0).optInt("archived") == 1) markDead(key)
                prefs.edit().putInt("seen:$key", posts.size).putInt("total:$key", posts.size).apply()
                watchUnread = watchUnread + (key to 0)
                watchStatus = watchStatus + (key to "0 new · ${posts.size - 1} replies")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is ApiException && e.status == 404 && thread != 0L) markDead(key)
            val snapshot = if (thread != 0L && key in saved) store.load(key) else null
            if (snapshot != null) { posts = snapshot; localCopy = true }
            else error = e.message ?: "Unable to load content"
        }
        finally {
            if (posts.isNotEmpty() && !localCopy) {
                cachedPosts[key] = System.currentTimeMillis() to posts
                while (cachedPosts.size > 8) cachedPosts.remove(cachedPosts.keys.first())
            }
            loading = false
        }
    }
    fun back() {
        if (history.size > 1) {
            history = history.dropLast(1)
            val previous = PageRoute.parse(history.last())
            destination = previous.tab; board = previous.board; thread = previous.thread
            query = ""; searchOpen = false
        }
    }
    val visibleBoardIds = orderBoards(boards, boardOrder, favorites).filter { (it.safe || adult) && (!favoritesOnly || it.id in favorites) }.map { it.id }
    val swipeBoards = (if (board in boardSequence) boardSequence else visibleBoardIds).filter { id -> adult || boards.find { it.id == id }?.safe != false }
    val boardSlide = remember { Animatable(0f) }
    var boardDrag by remember { mutableFloatStateOf(0f) }
    var boardSliding by remember { mutableStateOf(false) }
    val pageWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    fun switchBoard(delta: Int) {
        val next = adjacentBoard(swipeBoards, board, delta)
        if (boardSliding) return
        boardSliding = true
        scope.launch {
            try {
                boardSlide.snapTo(boardDrag)
                boardDrag = 0f
                if (next == null) { boardSlide.animateTo(0f, spring()); return@launch }
                val direction = if (delta > 0) -1f else 1f
                boardSlide.animateTo(direction * pageWidth, tween(180))
                history = history.dropLast(1) + PageRoute(destination, next).key
                board = next; thread = 0; query = ""; searchOpen = false
                boardSlide.snapTo(-direction * pageWidth)
                // Let the new page compose before sliding it in; its own loading UI
                // and retained cache remain responsive without blocking on the network.
                withFrameNanos { }
                withFrameNanos { }
                boardSlide.animateTo(0f, tween(220))
            } finally { boardSlide.snapTo(0f); boardDrag = 0f; boardSliding = false }
        }
    }
    fun cancelBoardSwipe() {
        if (boardSliding) return
        boardSliding = true
        scope.launch {
            try { boardSlide.snapTo(boardDrag); boardDrag = 0f; boardSlide.animateTo(0f, spring(stiffness = 500f)) }
            finally { boardDrag = 0f; boardSliding = false }
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val boardSwipe = if (catalog && board.isNotEmpty() && thread == 0L && destination != "Settings") Modifier
        .pointerInput(board, swipeBoards, swipeThreshold, history) {
            detectHorizontalDragGestures(onDragStart = { if (!boardSliding) boardDrag = 0f }, onDragCancel = { cancelBoardSwipe() },
                onDragEnd = { if (boardDrag < -swipeThreshold) switchBoard(1) else if (boardDrag > swipeThreshold) switchBoard(-1) else cancelBoardSwipe() },
                onHorizontalDrag = { change, amount ->
                    if (!boardSliding) {
                        change.consume()
                        val proposed = boardDrag + amount
                        val neighbor = adjacentBoard(swipeBoards, board, if (proposed < 0) 1 else -1)
                        boardDrag += amount * if (neighbor == null) 0.25f else 1f
                    }
                })
        }.semantics {
            paneTitle = "/$board/"
            customActions = buildList {
                if (adjacentBoard(swipeBoards, board, -1) != null) add(CustomAccessibilityAction("Previous board") { switchBoard(-1); true })
                if (adjacentBoard(swipeBoards, board, 1) != null) add(CustomAccessibilityAction("Next board") { switchBoard(1); true })
            }
        } else Modifier
    val animatedBackground by animateColorAsState(colors.background, label = "Board background")
    MaterialTheme(colorScheme = colors.copy(background = animatedBackground, surface = animatedBackground),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))) {
        if (showLurkNotice) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Lurk only") },
                text = { Text("Trfl is a read-only 4chan app. Browse, watch and save threads and media. Posting and replying are not supported.") },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("lurkNoticeAcknowledged", true).apply()
                        showLurkNotice = false
                    }) { Text("Got it") }
                })
        }
    if (showDonation) {
        val address = "0xc496917DD5d1e11e320Dc6D43dd3D361d86e96f9"
        AlertDialog(
            onDismissRequest = { showDonation = false },
            title = { Text("Donate") },
            text = { Text("Ethereum address\n$address") },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ethereum address", address))
                    showDonation = false
                }) { Text("Copy address") }
            },
            dismissButton = { TextButton(onClick = { showDonation = false }) { Text("Close") } },
        )
    }
    PredictivePage(route, history.dropLast(1).lastOrNull(), history.size > 1 && !searchOpen && media == null && pendingRemoval == null, ::back, previewStyle = "$boardColors/${if (boardColors) chanDark else phoneDark}/${if (boardColors) "board-palettes/$chanTheme" else phoneColors.primary}", retainedRoutes = nextHistory(history, route)) {
    BackHandler(searchOpen) { searchOpen = false; query = "" }
    Scaffold(modifier = Modifier.imePadding().nestedScroll(barScroll),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        bottomBar = {
        if (thread != 0L && destination == "Boards" && !wideLayout) {
            key(board, thread) {
                ThreadReplyBar(text = replyText, onTextChange = { replyText = it }, onSend = {}, focusRequest = replyFocusRequest)
            }
        } else
        AnimatedVisibility(!wideLayout && showBars, enter = slideInVertically { it } + expandVertically(), exit = slideOutVertically { it } + shrinkVertically()) {
        NavigationBar {
            listOf("Boards" to Icons.Default.Dashboard, "Watched" to Icons.Default.Visibility, "Saves" to Icons.Default.Bookmark, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                NavigationBarItem(selected = destination == label, onClick = { selectDestination(label) }, icon = { BadgedBox(badge = { if (label == "Watched" && watchedHasUnread) Badge() }) { Icon(icon, if (label == "Watched" && watchedHasUnread) "New replies" else null) } }, label = { Text(label) })
            }
        }
        }
    }, topBar = {
        AnimatedVisibility(showBars, enter = slideInVertically { -it } + expandVertically(), exit = slideOutVertically { -it } + shrinkVertically()) {
        TopAppBar(title = {
            if (searchOpen) {
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    placeholder = { Text(if (board.isEmpty()) "Find a board" else "Search loaded posts") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            } else AnimatedContent(targetState = if (destination == "Settings") "Settings" else if (board.isEmpty()) (if (onlySaved) "Saves" else if (destination == "Watched") "Watched" else "Trfl") else if (thread == 0L) "/$board/" else "/$board/ · $thread", label = "App bar title") { title -> Text(title, maxLines = 1, modifier = Modifier.clickable(onClickLabel = "Scroll to top") { scrollTop++ }) }
        }, navigationIcon = {
            if (searchOpen) IconButton(onClick = { searchOpen = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
            else if (board.isNotEmpty() && destination != "Settings") IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            if (searchOpen) {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
            } else if (destination != "Settings") {
            if (thread == 0L && (destination != "Watched" || board.isNotEmpty())) IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search") }
            if (board.isEmpty() && destination == "Boards") {
                IconToggleButton(checked = favoritesOnly, onCheckedChange = { favoritesOnly = it }) { Icon(if (favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder, "Favorite boards") }
            }
            if (thread != 0L) {
                IconToggleButton(checked = key in watched, onCheckedChange = {
                    watched = if (it) watched + key else watched - key
                    watchUnread = watchUnread + (key to 0)
                    prefs.edit().putStringSet("watched", watched).putInt("seen:$key", posts.size).apply()
                }) { Icon(if (key in watched) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Watch thread") }
                IconToggleButton(checked = imagesOnly, onCheckedChange = { imagesOnly = it }) { Icon(if (imagesOnly) Icons.Default.Article else Icons.Default.Collections, "Thread images") }
            }
            if (board.isEmpty() && destination == "Watched") Box {
                IconButton(onClick = { watchMenu = true }) { Icon(Icons.Default.MoreVert, "Watched options") }
                DropdownMenu(expanded = watchMenu, onDismissRequest = { watchMenu = false }) {
                    DropdownMenuItem(text = { Text("Mark all as read") }, onClick = {
                        watched.forEach { markRead(it) }
                        watchUnread = emptyMap()
                        watchStatus = watchStatus.mapValues { (entry, _) -> if (entry in dead) "Dead thread" else "0 new · ${ (prefs.getInt("total:$entry", 1) - 1).coerceAtLeast(0) } replies" }
                        watchMenu = false
                    })
                    DropdownMenuItem(text = { Text("Prune dead threads") }, onClick = { watched = watched - dead; prefs.edit().putStringSet("watched", watched).apply(); watchMenu = false })
                    DropdownMenuItem(text = { Text("Refresh") }, enabled = !loading, onClick = { refresh(); watchMenu = false })
                }
            }
            if (board.isEmpty() && onlySaved) IconToggleButton(checked = showArchive, onCheckedChange = { showArchive = it }) { Icon(Icons.Default.Archive, "Archived saves") }
            if (board.isNotEmpty() && thread == 0L) IconButton(onClick = { catalog = !catalog; prefs.edit().putBoolean("catalog", catalog).apply() }) { Icon(if (catalog) Icons.Default.ViewList else Icons.Default.GridView, "Switch catalog/list") }
            if (destination != "Settings" && (destination != "Watched" || board.isNotEmpty())) Box {
                IconButton(onClick = { pageMenu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                DropdownMenu(expanded = pageMenu, onDismissRequest = { pageMenu = false }) {
                    if (board.isNotEmpty() && thread == 0L) DropdownMenuItem(text = { Text("Sort threads") },
                        leadingIcon = { Icon(Icons.Default.Sort, null) }, trailingIcon = { Text(ThreadSort.fromSaved(threadSort).label, style = MaterialTheme.typography.labelSmall) },
                        onClick = { pageMenu = false; sortMenu = true })
                    if (thread != 0L) {
                        DropdownMenuItem(text = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { searchOpen = true; pageMenu = false })
                        DropdownMenuItem(text = { Text(if (key in saved) "Remove from Saves" else "Save thread locally") },
                            leadingIcon = { Icon(if (key in saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) }, enabled = !loading && posts.isNotEmpty(), onClick = {
                            pageMenu = false
                            if (key in saved) pendingRemoval = key else {
                                saved = saved + key; prefs.edit().putStringSet("bookmarks", saved).apply()
                                val snapshot = posts.toList()
                                saveStatus = saveStatus + (key to "Saving locally…")
                                saveJobs[key] = scope.launch {
                                    try { store.save(key, snapshot) { status -> saveStatus = saveStatus + (key to status); prefs.edit().putString("save:$key", status).apply() } }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { saveStatus = saveStatus + (key to "Local save failed · remove and save again") }
                                }
                            }
                        })
                        DropdownMenuItem(text = { Text("Share thread") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                            pageMenu = false
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "https://boards.4chan.org/$board/thread/$thread") }, "Share thread"))
                        })
                    }
                    DropdownMenuItem(text = { Text("Refresh") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, enabled = !loading, onClick = { refresh(); pageMenu = false })
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ThreadSort.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) },
                            leadingIcon = { RadioButton(selected = threadSort == option.name, onClick = null) },
                            onClick = { threadSort = option.name; prefs.edit().putString("sort:$board", option.name).apply(); sortMenu = false; scrollTop++ })
                    }
                }
            }
            }
        })
        }
    }) { padding ->
        key(route) {
        pageStates.SaveableStateProvider(route) {
        // Chrome overlays the reader. Insets belong to lazy content, not its viewport:
        // the initial rows clear the bars, then scroll behind them without remeasurement.
        val chromeInsets = PaddingValues(
            top = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding() + 64.dp,
            bottom = if (wideLayout) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp)
        val scrollingReader = destination == "Boards" || (board.isNotEmpty() && destination != "Settings")
        val scrollbarInsets = if (scrollingReader) PaddingValues(
            top = maxOf(padding.calculateTopPadding(), WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()),
            bottom = maxOf(padding.calculateBottomPadding(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) else PaddingValues(0.dp)
        CompositionLocalProvider(
            LocalReaderInsets provides if (scrollingReader) chromeInsets else PaddingValues(0.dp),
            LocalScrollbarInsets provides scrollbarInsets) {
        Row(Modifier.fillMaxSize().then(if (scrollingReader) Modifier else Modifier.padding(chromeInsets))) {
        if (wideLayout) NavigationRail {
            listOf("Boards" to Icons.Default.Dashboard, "Watched" to Icons.Default.Visibility, "Saves" to Icons.Default.Bookmark, "Settings" to Icons.Default.Settings).forEach { (label, icon) ->
                NavigationRailItem(selected = destination == label, onClick = { selectDestination(label) }, icon = { BadgedBox(badge = { if (label == "Watched" && watchedHasUnread) Badge() }) { Icon(icon, if (label == "Watched" && watchedHasUnread) "New replies" else null) } }, label = { Text(label) })
            }
        }
        val refreshState = rememberPullToRefreshState()
        // Indicator starts just below the app bar in every view: reader content sits under the bars,
        // so offset by the bar's top inset; other views are already inset by chromeInsets.
        val readerActionTop = 8.dp + (if (scrollingReader) chromeInsets.calculateTopPadding() else 0.dp)
        RefreshContainer(enabled = destination != "Settings", isRefreshing = loading, onRefresh = { refresh() },
            state = refreshState, modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState, isRefreshing = loading && destination != "Settings",
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = readerActionTop))
            }) {
        Column(Modifier.fillMaxSize().then(boardSwipe).graphicsLayer {
            translationX = if (catalog && board.isNotEmpty() && thread == 0L && destination != "Settings") { if (boardSliding) boardSlide.value else boardDrag } else 0f
        }) {
            if (destination == "Settings") {
                LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                    item { SettingsToggle("4chan colors", "Off follows your phone’s theme and wallpaper colors",  boardColors) { boardColors = it; prefs.edit().putBoolean("boardColors", it).apply() } }
                    if (boardColors) item {
                        Box {
                            ListItem(headlineContent = { Text("Theme") }, supportingContent = { Text(chanTheme.label) },
                                trailingContent = { Icon(Icons.Default.ArrowDropDown, null) },
                                modifier = Modifier.clickable { themeMenuOpen = true })
                            DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                                ChanTheme.entries.forEach { theme ->
                                    DropdownMenuItem(text = { Text(theme.label) },
                                        trailingIcon = { if (theme == chanTheme) Icon(Icons.Default.Check, null) },
                                        onClick = {
                                            chanThemeName = theme.name
                                            prefs.edit().putString("chanTheme", theme.name).apply()
                                            themeMenuOpen = false
                                        })
                                }
                            }
                        }
                    }
                    if (boardColors) item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("4chan appearance", style = MaterialTheme.typography.titleSmall)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                listOf("System", "Light", "Dark").forEachIndexed { index, mode ->
                                    SegmentedButton(selected = chanAppearance == mode, onClick = {
                                        chanAppearance = mode; prefs.edit().putString("chanAppearance", mode).apply()
                                    }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(mode) }
                                }
                            }
                        }
                    }
                    item { WatchNotificationSetting() }
                    if (!BuildConfig.SFW_ONLY) item { SettingsToggle("18+ boards", "Show adult boards in the directory", adult) { adult = it; prefs.edit().putBoolean("adult", it).apply() } }
                    item { ListItem(headlineContent = { Text("Downloads") }, supportingContent = { Text("Progress, cancellation and retry") }, modifier = Modifier.clickable { showDownloads = true }) }
                    item { ListItem(headlineContent = { Text("Donate") }, supportingContent = { Text("Ethereum") }, modifier = Modifier.clickable { showDonation = true }) }
                    item { ListItem(headlineContent = { Text("Trfl ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text(BuildConfig.COMMIT.take(12)) }) }
                }
            } else {
            AnimatedVisibility(loading) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (apiPath in ApiRequests.stalePaths) Text("Offline · cached copy", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (thread != 0L && (localCopy || key in dead || key in saveStatus)) Text(
                listOfNotNull(if (key in dead) "Dead thread" else null, if (localCopy) "Offline saved copy" else null, saveStatus[key]).joinToString(" · "),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(), style = MaterialTheme.typography.labelMedium)
            error?.let { message ->
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(message); Button(onClick = { refresh() }) { Text("Retry") }; TextButton(onClick = { demo = true }) { Text("Try offline preview") } }
            }
            if (board.isEmpty()) {
                if (destination == "Boards") BoardDirectory(
                    scrollTop = scrollTop, onScrollTopVisibility = { showScrollTop = it },
                    positionKey = "boards:$favoritesOnly:$adult:$query", ready = !loading && boards.isNotEmpty(),
                    boards = orderBoards(boards, boardOrder, favorites).filter { (it.safe || adult) && (!favoritesOnly || it.id in favorites) && (it.id.contains(query, true) || it.title.contains(query, true)) },
                    favorites = favorites, onOpen = {
                        boardSequence = orderBoards(boards, boardOrder, favorites).filter { b -> (b.safe || adult) && (!favoritesOnly || b.id in favorites) && (b.id.contains(query, true) || b.title.contains(query, true)) }.map { b -> b.id }
                        board = it
                    },
                    onFavorite = { id -> favorites = if (id in favorites) favorites - id else favorites + id; prefs.edit().putStringSet("favorites", favorites).apply() },
                    onMove = { id, neighbor ->
                        boardOrder = moveBoard(orderBoards(boards, boardOrder, favorites).map { it.id }, id, neighbor)
                        prefs.edit().putString("boardOrder", boardOrder.joinToString(",")).apply()
                    }
                ) else if (onlySaved) SavesPanel(saved, archived, showArchive, query, saveStatus.mapValues { (_, value) -> !value.startsWith("Saving") && !value.startsWith("Saved text") } to reload, store, downloads, dead,
                    status = { entry -> saveStatus[entry] ?: prefs.getString("save:$entry", "Saved copy")!! },
                    onOpenThread = { entry -> board = entry.substringBefore('/'); thread = entry.substringAfter('/').toLong() },
                    onOpenMedia = { assets, selected, archive, remove, isArchived ->
                        savedMedia = assets; savedMediaArchive = archive; savedMediaRemove = remove; savedMediaArchived = isArchived; media = selected
                    },
                    onArchive = { entry -> archived = if (entry in archived) archived - entry else archived + entry; prefs.edit().putStringSet("archived", archived).apply() },
                    onRemove = { pendingRemoval = it }
                ) else FastScrollList(state = persistentListState("tab:$destination", !loading), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (destination == "Watched") {
                        if (watched.isEmpty()) item { Text("Watch a thread using its eye icon. Open this tab or refresh to check replies.") }
                        items(watched.sorted()) { entry ->
                            var menu by remember { mutableStateOf(false) }
                            ListItem(leadingContent = { if ((watchUnread[entry] ?: 0) > 0) Badge(modifier = Modifier.semantics { contentDescription = "New replies" }) }, headlineContent = { Text("/$entry" + if (entry in dead) " · Dead" else "") }, supportingContent = { Text(watchStatus[entry] ?: "Checking…") },
                                trailingContent = { Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options /$entry") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Mark as read") }, onClick = { markRead(entry); watchUnread = watchUnread + (entry to 0); watchStatus = watchStatus + (entry to if (entry in dead) "Dead thread" else "0 new replies"); menu = false })
                                        DropdownMenuItem(text = { Text("Stop watching") }, onClick = { watched = watched - entry; prefs.edit().putStringSet("watched", watched).apply(); menu = false })
                                    }
                                } },
                                modifier = Modifier.animateItem().clickable { board = entry.substringBefore('/'); thread = entry.substringAfter('/').toLong() })
                        }
                    }
                }
            } else {
                val filtered = remember(posts, query, thread, threadSort) {
                    val matching = posts.filter { it.title.contains(query, true) || it.body.contains(query, true) || it.id.toString().contains(query) }
                    if (thread == 0L) sortThreads(matching, ThreadSort.fromSaved(threadSort)) else matching
                }
                if (!loading && error == null && filtered.isEmpty()) Text("No matching posts", Modifier.padding(24.dp))
                val gridState = persistentGridState("grid:$key:$imagesOnly:$threadSort:$query", !loading && filtered.isNotEmpty())
                LaunchedEffect(gridState) { snapshotFlow { scrollTop }.drop(1).collect { gridState.animateScrollToItem(0) } }
                LaunchedEffect(gridState, imagesOnly, catalog, thread) {
                    if ((imagesOnly && thread != 0L) || (catalog && thread == 0L)) snapshotFlow {
                        gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 200
                    }.collect { showScrollTop = it }
                }
                if (imagesOnly && thread != 0L) {
                    val attachments = filtered.filter { it.image != null }
                    if (!loading && attachments.isEmpty()) Text("No attachments in these posts", Modifier.padding(24.dp))
                    FastScrollGrid(state = gridState, columns = GridCells.Adaptive(120.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it.id }) { p ->
                            Box(Modifier.aspectRatio(1f).clickable { media = p }) {
                                AsyncImage(p.thumb, "Open attachment ${p.id}", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                if (p.image?.endsWith(".webm") == true) Icon(Icons.Default.PlayArrow, "Video", Modifier.align(Alignment.Center), tint = Color.White)
                            }
                        }
                    }
                } else if (catalog && thread == 0L) FastScrollGrid(state = gridState, columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { p -> Card(onClick = { thread = p.id }, modifier = Modifier.fillMaxWidth().height(140.dp + (180.dp * maxOf(1f, LocalDensity.current.fontScale)))) {
                        Box(Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(enabled = p.image != null) { media = p }, contentAlignment = Alignment.Center) {
                            if (p.thumb != null) AsyncImage(p.thumb, "Thread thumbnail", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Image, "No attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.fillMaxWidth().weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.title.ifBlank { "No. ${p.id}" }, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(p.body, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            ThreadMetrics(p)
                        }
                    } }
                } else key(board, thread) {
                    val state = persistentListState("list:$key:$threadSort:$query", !loading && filtered.isNotEmpty())
                    LaunchedEffect(state) { snapshotFlow { scrollTop }.drop(1).collect { state.animateScrollToItem(0) } }
                    LaunchedEffect(state) {
                        snapshotFlow { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 200 }.collect { showScrollTop = it }
                    }
                    LaunchedEffect(jumpTo) {
                        jumpTo?.let { id -> filtered.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { state.animateScrollToItem(it) }; jumpTo = null }
                    }
                    FastScrollList(state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { p ->
                            ReaderPostCard(p, compact = thread == 0L,
                                replyCount = if (thread == 0L) p.replies else postReplies[p.id]?.size ?: 0,
                                onClick = { if (thread == 0L) thread = p.id }, onImage = { media = p },
                                onPostId = if (thread == 0L) null else { id ->
                                    replyText += (if (replyText.isNotEmpty() && !replyText.endsWith("\n")) "\n" else "") + ">>$id\n"
                                    replyFocusRequest++
                                },
                                onQuote = { id -> quote = posts.find { it.id == id } },
                                onReplies = { if (thread == 0L) thread = p.id else replyListFor = p.id })
                        }
                    }
                }
            }
        }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    if (showDownloads) ModalBottomSheet(onDismissRequest = { showDownloads = false }) { DownloadsPanel(downloads) }
    replyListFor?.let { id ->
        MaterialTheme(colorScheme = colors, shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))) {
        ModalBottomSheet(onDismissRequest = { replyListFor = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Text("Replies to No. $id", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                FastScrollList(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(postReplies[id].orEmpty(), key = { it.id }) { reply ->
                        ReaderPostCard(reply, compact = false, replyCount = postReplies[reply.id]?.size ?: 0,
                            onClick = {}, onImage = { replyListFor = null; media = reply },
                            onPostId = { postId ->
                                replyListFor = null
                                replyText += (if (replyText.isNotEmpty() && !replyText.endsWith("\n")) "\n" else "") + ">>$postId\n"
                                replyFocusRequest++
                            },
                            onQuote = { quotedId -> replyListFor = null; quote = posts.find { it.id == quotedId } },
                            onReplies = { replyListFor = reply.id }, footer = {
                                TextButton(onClick = { replyListFor = null; imagesOnly = false; query = ""; jumpTo = reply.id }) { Text("Jump to reply") }
                            })
                    }
                }
            }
        }
        }
    }
    quote?.let { referenced ->
        MaterialTheme(colorScheme = colors) {
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
        Dialog(onDismissRequest = { quote = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = maxHeight)) {
                item {
                    ReaderPostCard(referenced, compact = false, replyCount = postReplies[referenced.id]?.size ?: 0,
                        onClick = {}, onImage = { quote = null; media = referenced },
                        onQuote = { id -> posts.find { it.id == id }?.let { quote = it } },
                        onReplies = { replyListFor = referenced.id; quote = null }, footer = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { quote = null }) { Text("Close") }
                                TextButton(onClick = { imagesOnly = false; query = ""; jumpTo = referenced.id; quote = null }) { Text("Jump to reply") }
                            }
                        })
                }
            }
        }
        }
    }
    pendingRemoval?.let { entry ->
        AlertDialog(onDismissRequest = { pendingRemoval = null }, title = { Text("Remove from Saves?") },
            text = { Text("Remove /$entry and its saved local copy? Archive it instead to keep it for later.") },
            confirmButton = { TextButton(onClick = {
                saved = saved - entry; archived = archived - entry
                prefs.edit().putStringSet("bookmarks", saved).putStringSet("archived", archived).remove("save:$entry").apply()
                saveStatus = saveStatus - entry
                scope.launch { saveJobs.remove(entry)?.cancelAndJoin(); store.remove(entry) }
                pendingRemoval = null
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } })
    }
    media?.let { selected ->
        val attachments = savedMedia ?: posts.filter { it.image != null }
        if (attachments.isNotEmpty()) Dialog(onDismissRequest = { media = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
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
                                            media = null
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
                    navigationIcon = { IconButton(onClick = { media = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close media") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xD9171923), titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                    windowInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), actions = {
                    if (savedMedia != null) {
                        IconButton(onClick = { savedMediaArchive?.invoke(attachments[pager.currentPage]); media = null }) {
                            Icon(if (savedMediaArchived) Icons.Default.Unarchive else Icons.Default.Archive, if (savedMediaArchived) "Unarchive media" else "Archive media")
                        }
                        IconButton(onClick = { savedMediaRemove?.invoke(attachments[pager.currentPage]); media = null }) { Icon(Icons.Default.DeleteOutline, "Remove media from Saves") }
                    } else IconButton(enabled = !savingMedia, onClick = {
                        val selected = attachments[pager.currentPage]
                        savingMedia = true
                        scope.launch {
                            try {
                                mediaSaves.save(selected)
                                android.widget.Toast.makeText(context, "Saved in Trfl → Saves", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { android.widget.Toast.makeText(context, "Could not save media", android.widget.Toast.LENGTH_SHORT).show() }
                            finally { savingMedia = false }
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
                    }) { Icon(Icons.Default.Download, "Export media to Downloads", tint = Color.White) }
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
