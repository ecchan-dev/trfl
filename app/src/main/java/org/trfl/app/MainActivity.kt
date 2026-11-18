package org.trfl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
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

// Thread saves run for the process lifetime so rotation or window changes don't cancel them;
// jobs and live status live here so a recreated composition still sees them.
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
private val saveJobs = mutableMapOf<String, Job>()
private val saveStatusState = mutableStateOf(emptyMap<String, String>())
private fun threadTitle(op: Post) = op.title.ifBlank { op.body.replace(Regex("\\s+"), " ").trim().take(80) }

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
    var saveStatus by saveStatusState
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
    val draftStore = remember { PostingDraftStore(java.io.File(context.noBackupFilesDir, "posting-drafts")) }
    var replyQuoteId by remember(board, thread) { mutableStateOf<Long?>(null) }
    var replyFocusRequest by remember(board, thread) { mutableIntStateOf(0) }
    var showNewThread by remember(board) { mutableStateOf(false) }
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
    var threadTitles by remember { mutableStateOf((watched + saved).mapNotNull { entry -> prefs.getString("title:$entry", null)?.takeIf { it.isNotBlank() }?.let { entry to it } }.toMap()) }
    fun storeTitle(entry: String, op: Post) {
        val title = threadTitle(op)
        if (title.isNotBlank() && threadTitles[entry] != title) { threadTitles = threadTitles + (entry to title); prefs.edit().putString("title:$entry", title).apply() }
    }
    val watchCheckedAt = remember { mutableMapOf<String, Long>() }
    var watchReloadSeen by remember { mutableIntStateOf(0) }
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
    var destination by rememberSaveable { mutableStateOf(Destination.BOARDS) }
    var showScrollTop by remember(destination, board, thread, imagesOnly, catalog) { mutableStateOf(false) }
    var listAtTop by remember(destination, board, thread, catalog) { mutableStateOf(true) }
    var boardsResumeBoard by rememberSaveable { mutableStateOf("") }
    var boardsResumeThread by rememberSaveable { mutableLongStateOf(0L) }
    SideEffect {
        if (destination == Destination.BOARDS) { boardsResumeBoard = board; boardsResumeThread = thread }
    }
    fun selectDestination(label: String) {
        if (destination == label) {
            if (label == Destination.BOARDS) {
                // Board or thread list scrolled down: first tap returns to the top, the next one goes up a level.
                if (thread == 0L && !listAtTop) { scrollTop++; return }
                if (thread != 0L) thread = 0 else if (board.isNotEmpty()) board = ""
                query = ""
            } else if ((label == Destination.WATCHED || label == Destination.SAVES) && board.isEmpty()) scrollTop++
            return
        }
        destination = label
        if (label == Destination.BOARDS) { board = boardsResumeBoard; thread = boardsResumeThread }
        else if (label != Destination.SETTINGS) { board = ""; thread = 0 }
        query = ""
    }
    LaunchedEffect(destination) { if (destination == Destination.LEGACY_BOOKMARKS) destination = Destination.SAVES }
    LaunchedEffect(incomingLink) {
        parseThreadLink(incomingLink)?.let { target ->
            destination = Destination.BOARDS; board = target.board; thread = target.thread
        }
        if (incomingLink != null) consumeLink()
    }
    LaunchedEffect(Unit) {
        // Drop per-thread read counters and titles for threads that are neither watched nor saved (keep the open one).
        val keep = watched + saved + "$board/$thread"
        withContext(Dispatchers.IO) {
            val stale = prefs.all.keys.filter { k -> (k.startsWith("seen:") || k.startsWith("total:") || k.startsWith("title:")) && k.substringAfter(':') !in keep }
            if (stale.isNotEmpty()) prefs.edit().also { editor -> stale.forEach { editor.remove(it) } }.apply()
        }
    }
    val onlySaved = destination == Destination.SAVES
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
    val route = PageRoute(destination, if (destination == Destination.SETTINGS) "" else board, if (destination == Destination.SETTINGS) 0 else thread).key
    var history by rememberSaveable { mutableStateOf(listOf(PageRoute(Destination.BOARDS).key)) }
    SideEffect { history = nextHistory(history, route) }
    val cachedPosts = remember(reload, demo) { linkedMapOf<String, Pair<Long, List<Post>>>() }
    val apiPath = if (board.isEmpty()) "boards.json" else if (thread == 0L) "$board/catalog.json" else "$board/thread/$thread.json"
    val pageStates = rememberSaveableStateHolder()
    var barsVisible by remember { mutableStateOf(true) }
    var scrollTravel by remember { mutableFloatStateOf(0f) }
    val barThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val touchExploration = (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled
    val showBars = barsVisible || searchOpen || destination == Destination.SETTINGS || touchExploration
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
        // Only an explicit refresh (reload bump) re-checks threads that were checked moments ago.
        val explicit = reload != watchReloadSeen
        watchReloadSeen = reload
        if ((destination == Destination.WATCHED || destination == Destination.SAVES) && board.isEmpty()) {
            for (entry in if (destination == Destination.WATCHED) watched else saved) {
                if (demo) { watchStatus = watchStatus + (entry to "Preview thread"); continue }
                val checkedAt = watchCheckedAt[entry]
                if (!explicit && checkedAt != null && System.currentTimeMillis() - checkedAt in 0L until 120_000L) {
                    val total = prefs.getInt("total:$entry", -1)
                    if (entry in dead) watchStatus = watchStatus + (entry to "Dead thread")
                    else if (total >= 0) watchStatus = watchStatus + (entry to "${(total - prefs.getInt("seen:$entry", total)).coerceAtLeast(0)} new · ${(total - 1).coerceAtLeast(0)} replies")
                    continue
                }
                try {
                    val array = JSONObject(request("${entry.substringBefore('/')}/thread/${entry.substringAfter('/')}.json", force = true)).getJSONArray("posts")
                    if (array.length() > 0) storeTitle(entry, parsePost(array.getJSONObject(0), entry.substringBefore('/')))
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
                watchCheckedAt[entry] = System.currentTimeMillis()
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
                posts.firstOrNull()?.let { storeTitle(key, it) }
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
    val visibleBoardIds = visibleBoards(boards, boardOrder, favorites, adult, favoritesOnly).map { it.id }
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
    val boardSwipe = if (catalog && board.isNotEmpty() && thread == 0L && destination != Destination.SETTINGS) Modifier
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
                title = { Text("Posting is experimental") },
                text = { Text("Browse, watch and save threads and media. Text replies need a CAPTCHA solved in the app. New threads and attachments are not supported yet.") },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("lurkNoticeAcknowledged", true).apply()
                        showLurkNotice = false
                    }) { Text("Got it") }
                })
        }
    if (showNewThread && board.isNotEmpty()) {
        AlertDialog(onDismissRequest = { showNewThread = false }, title = { Text("New thread · /$board/") },
            text = { PostingComposer(PostingDraftKey(board, null), null, 0, draftStore, newThread = true) },
            confirmButton = { TextButton(onClick = { showNewThread = false }) { Text("Close") } })
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
        if (thread != 0L && destination == Destination.BOARDS) {
            key(board, thread) {
                PostingComposer(PostingDraftKey(board, thread), replyQuoteId, replyFocusRequest, draftStore, onQuoteHandled = { replyQuoteId = null }, onPosted = ::refresh)
            }
        } else
        AnimatedVisibility(!wideLayout && showBars, enter = slideInVertically { it } + expandVertically(), exit = slideOutVertically { it } + shrinkVertically()) {
        NavigationBar {
            navDestinations.forEach { item ->
                NavigationBarItem(selected = destination == item.label, onClick = { selectDestination(item.label) }, icon = { NavDestinationIcon(item, watchedHasUnread) }, label = { Text(item.label) })
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
            } else AnimatedContent(targetState = if (destination == Destination.SETTINGS) Destination.SETTINGS else if (board.isEmpty()) (if (onlySaved) Destination.SAVES else if (destination == Destination.WATCHED) Destination.WATCHED else "Trfl") else if (thread == 0L) "/$board/" else "/$board/ · $thread", label = "App bar title") { title -> Text(title, maxLines = 1, modifier = Modifier.clickable(onClickLabel = "Scroll to top") { scrollTop++ }) }
        }, navigationIcon = {
            if (searchOpen) IconButton(onClick = { searchOpen = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
            else if (board.isNotEmpty() && destination != Destination.SETTINGS) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            if (searchOpen) {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
            } else if (destination != Destination.SETTINGS) {
            if (thread == 0L && (destination != Destination.WATCHED || board.isNotEmpty())) IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search") }
            if (board.isEmpty() && destination == Destination.BOARDS) {
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
            if (board.isEmpty() && destination == Destination.WATCHED) Box {
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
            if (board.isNotEmpty() && thread == 0L && destination == Destination.BOARDS) IconButton(onClick = { showNewThread = true }) { Icon(Icons.Default.Add, "New thread draft") }
            if (board.isNotEmpty() && thread == 0L) IconButton(onClick = { catalog = !catalog; prefs.edit().putBoolean("catalog", catalog).apply() }) { Icon(if (catalog) Icons.Default.ViewList else Icons.Default.GridView, "Switch catalog/list") }
            if (destination != Destination.SETTINGS && (destination != Destination.WATCHED || board.isNotEmpty())) Box {
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
                                saveJobs[key] = appScope.launch {
                                    try { store.save(key, snapshot) { status -> saveStatus = saveStatus + (key to status); prefs.edit().putString("save:$key", status).apply() } }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { saveStatus = saveStatus + (key to "Local save failed · remove and save again"); prefs.edit().putString("save:$key", "Local save failed · remove and save again").apply() }
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
        val scrollingReader = destination == Destination.BOARDS || (board.isNotEmpty() && destination != Destination.SETTINGS)
        val scrollbarInsets = if (scrollingReader) PaddingValues(
            top = maxOf(padding.calculateTopPadding(), WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()),
            bottom = maxOf(padding.calculateBottomPadding(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) else PaddingValues(0.dp)
        CompositionLocalProvider(
            LocalReaderInsets provides if (scrollingReader) chromeInsets else PaddingValues(0.dp),
            LocalScrollbarInsets provides scrollbarInsets) {
        Row(Modifier.fillMaxSize().then(if (scrollingReader) Modifier else Modifier.padding(chromeInsets))) {
        if (wideLayout) NavigationRail {
            navDestinations.forEach { item ->
                NavigationRailItem(selected = destination == item.label, onClick = { selectDestination(item.label) }, icon = { NavDestinationIcon(item, watchedHasUnread) }, label = { Text(item.label) })
            }
        }
        val refreshState = rememberPullToRefreshState()
        // Indicator starts just below the app bar in every view: reader content sits under the bars,
        // so offset by the bar's top inset; other views are already inset by chromeInsets.
        val readerActionTop = 8.dp + (if (scrollingReader) chromeInsets.calculateTopPadding() else 0.dp)
        RefreshContainer(enabled = destination != Destination.SETTINGS, isRefreshing = loading, onRefresh = { refresh() },
            state = refreshState, modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState, isRefreshing = loading && destination != Destination.SETTINGS,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = readerActionTop))
            }) {
        Column(Modifier.fillMaxSize().then(boardSwipe).graphicsLayer {
            translationX = if (catalog && board.isNotEmpty() && thread == 0L && destination != Destination.SETTINGS) { if (boardSliding) boardSlide.value else boardDrag } else 0f
        }) {
            if (destination == Destination.SETTINGS) {
                SettingsScreen(
                    boardColors = boardColors, onBoardColorsChange = { boardColors = it; prefs.edit().putBoolean("boardColors", it).apply() },
                    chanTheme = chanTheme, onChanThemeChange = { theme ->
                        chanThemeName = theme.name
                        prefs.edit().putString("chanTheme", theme.name).apply()
                    },
                    themeMenuOpen = themeMenuOpen, onThemeMenuOpenChange = { themeMenuOpen = it },
                    chanAppearance = chanAppearance, onChanAppearanceChange = { mode -> chanAppearance = mode; prefs.edit().putString("chanAppearance", mode).apply() },
                    adult = adult, onAdultChange = { adult = it; prefs.edit().putBoolean("adult", it).apply() },
                    onOpenDownloads = { showDownloads = true }, onOpenDonation = { showDonation = true })
            } else {
            AnimatedVisibility(loading) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            // Reader content sits under the app bar, so push the banner below it there.
            if (demo) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = if (scrollingReader) chromeInsets.calculateTopPadding() else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Preview data", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { demo = false; refresh() }) { Text("Exit preview") }
            }
            if (apiPath in ApiRequests.stalePaths) Text("Offline · cached copy", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (thread != 0L && (localCopy || key in dead || key in saveStatus)) Text(
                listOfNotNull(if (key in dead) "Dead thread" else null, if (localCopy) "Offline saved copy" else null, saveStatus[key]).joinToString(" · "),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(), style = MaterialTheme.typography.labelMedium)
            error?.let { message ->
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(message); Button(onClick = { refresh() }) { Text("Retry") }; TextButton(onClick = { demo = true }) { Text("Try offline preview") } }
            }
            if (board.isEmpty()) {
                if (destination == Destination.BOARDS) BoardDirectory(
                    scrollTop = scrollTop, onScrollTopVisibility = { showScrollTop = it }, onAtTop = { listAtTop = it },
                    positionKey = "boards:$favoritesOnly:$adult:$query", ready = !loading && boards.isNotEmpty(),
                    boards = visibleBoards(boards, boardOrder, favorites, adult, favoritesOnly, query),
                    favorites = favorites, onOpen = {
                        boardSequence = visibleBoards(boards, boardOrder, favorites, adult, favoritesOnly, query).map { b -> b.id }
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
                    onRemove = { pendingRemoval = it }, scrollTop = scrollTop
                ) else {
                    val tabState = persistentListState("tab:$destination", !loading)
                    LaunchedEffect(tabState) { snapshotFlow { scrollTop }.drop(1).collect { tabState.animateScrollToItem(0) } }
                    FastScrollList(state = tabState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (destination == Destination.WATCHED) {
                        if (watched.isEmpty()) item { Text("Watch a thread using its eye icon. Open this tab or refresh to check replies.") }
                        items(watched.sortedWith(compareByDescending<String> { (watchUnread[it] ?: 0) > 0 }.thenBy { it }), key = { it }) { entry ->
                            var menu by remember { mutableStateOf(false) }
                            val title = threadTitles[entry]
                            val status = watchStatus[entry] ?: "Checking…"
                            val deadLabel = if (entry in dead && status != "Dead thread") " · Dead" else ""
                            ListItem(leadingContent = { if ((watchUnread[entry] ?: 0) > 0) Badge(modifier = Modifier.semantics { contentDescription = "New replies" }) },
                                headlineContent = { Text(title ?: ("/$entry" + if (entry in dead) " · Dead" else ""), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(if (title != null) "/$entry$deadLabel · $status" else status) },
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
                }
            } else {
                val filtered = remember(posts, query, thread, threadSort) {
                    val matching = posts.filter { it.title.contains(query, true) || it.body.contains(query, true) || it.id.toString().contains(query) }
                    if (thread == 0L) sortThreads(matching, ThreadSort.fromSaved(threadSort)) else matching
                }
                if (!loading && error == null && filtered.isEmpty()) Text("No matching posts", Modifier.padding(24.dp))
                val gridState = persistentGridState("grid:$key:$imagesOnly:$threadSort:$query", !loading && filtered.isNotEmpty())
                LaunchedEffect(gridState) { snapshotFlow { scrollTop }.drop(1).collect { gridState.animateScrollToItem(0) } }
                LaunchedEffect(gridState, catalog, thread) {
                    if (catalog && thread == 0L) snapshotFlow {
                        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                    }.collect { listAtTop = it }
                }
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
                                if (p.isVideo) Icon(Icons.Default.PlayArrow, "Video", Modifier.align(Alignment.Center), tint = Color.White)
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.title.ifBlank { "No. ${p.id}" }, Modifier.weight(1f), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                // Centre the 16dp flags on the title's first line.
                                if (p.sticky || p.closed) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { PostFlags(p) }
                            }
                            Text(p.body, modifier = Modifier.weight(1f), maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            ThreadMetrics(p)
                        }
                    } }
                } else key(board, thread) {
                    val state = persistentListState("list:$key:$threadSort:$query", !loading && filtered.isNotEmpty())
                    LaunchedEffect(state) { snapshotFlow { scrollTop }.drop(1).collect { state.animateScrollToItem(0) } }
                    LaunchedEffect(state) {
                        if (thread == 0L) snapshotFlow {
                            state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
                        }.collect { listAtTop = it }
                    }
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
                                    replyQuoteId = id
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
                                replyQuoteId = postId
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
                appScope.launch { saveJobs.remove(entry)?.cancelAndJoin(); store.remove(entry) }
                pendingRemoval = null
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } })
    }
    media?.let { selected ->
        val attachments = savedMedia ?: posts.filter { it.image != null }
        if (attachments.isNotEmpty()) MediaViewer(selected, attachments, onDismiss = { media = null },
            fromSaves = savedMedia != null, savedMediaArchived = savedMediaArchived,
            onArchiveSaved = savedMediaArchive, onRemoveSaved = savedMediaRemove,
            savingMedia = savingMedia, onSavingMediaChange = { savingMedia = it },
            mediaSaves = mediaSaves, downloads = downloads)
    }
}
