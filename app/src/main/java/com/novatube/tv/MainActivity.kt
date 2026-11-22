package com.novatube.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.novatube.tv.data.Auth
import com.novatube.tv.data.Clients
import com.novatube.tv.data.FeedPage
import com.novatube.tv.data.FeedParser
import com.novatube.tv.data.InnerTube
import com.novatube.tv.data.Prefs
import com.novatube.tv.data.Shelf
import com.novatube.tv.data.VideoItem
import com.novatube.tv.ui.AccentRed
import com.novatube.tv.ui.Bg
import com.novatube.tv.ui.FocusSurface
import com.novatube.tv.ui.Glyph
import com.novatube.tv.ui.GlyphKind
import com.novatube.tv.ui.Muted
import com.novatube.tv.ui.NovaTheme
import com.novatube.tv.ui.RailBg
import com.novatube.tv.ui.ShelfRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { NovaTheme { App() } }
    }
}

enum class Destination(val label: String, val glyph: GlyphKind) {
    Home("Home", GlyphKind.Home),
    Search("Search", GlyphKind.Search),
    Subscriptions("Subscriptions", GlyphKind.Subs),
    Library("Library", GlyphKind.Library),
    Settings("Settings", GlyphKind.Settings),
}

data class PlayRequest(val current: VideoItem, val queue: List<VideoItem>)

/** Holds one feed page and appends further pages as the user scrolls. */
class FeedStore(private val loader: suspend (String?) -> FeedPage) {
    val shelves = mutableStateListOf<Shelf>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    private var continuation: String? = null

    suspend fun refresh() {
        if (loading) return
        loading = true
        error = null
        try {
            val page = loader(null)
            continuation = page.continuation
            shelves.clear()
            shelves.addAll(page.shelves)
            if (shelves.isEmpty()) error = "Nothing to show here yet."
        } catch (t: Throwable) {
            error = t.message ?: "Could not reach YouTube."
        }
        loaded = true
        loading = false
    }

    suspend fun loadMore() {
        val token = continuation ?: return
        if (loading) return
        loading = true
        try {
            val page = loader(token)
            continuation = page.continuation
            val known = shelves.flatMap { it.items }.map { it.id }.toSet()
            val fresh = page.shelves.mapNotNull { shelf ->
                val items = shelf.items.filter { it.id !in known }
                if (items.isEmpty()) null else shelf.copy(items = items)
            }
            shelves.addAll(fresh)
            if (fresh.isEmpty()) continuation = null
        } catch (_: Throwable) {
            continuation = null
        }
        loading = false
    }
}

@Composable
fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(context) }
    val auth = remember { Auth(prefs) }
    val innerTube = remember { InnerTube(prefs, auth) }

    var destination by remember { mutableStateOf(Destination.Home) }
    var playing by remember { mutableStateOf<PlayRequest?>(null) }
    var signedIn by remember { mutableStateOf(prefs.isSignedIn()) }
    var accountLabel by remember { mutableStateOf(prefs.accountLabel) }
    val scope = rememberCoroutineScope()

    val homeStore = remember {
        FeedStore { token ->
            if (token != null) {
                FeedParser.parse(innerTube.browseSignedIn("FEwhat_to_watch", token))
            } else {
                homeFeed(innerTube, prefs.isSignedIn())
            }
        }
    }

    val subsStore = remember {
        FeedStore { token ->
            if (!prefs.isSignedIn()) {
                throw IllegalStateException("Sign in from Settings to see your subscriptions.")
            }
            FeedParser.parse(innerTube.browseSignedIn("FEsubscriptions", token))
        }
    }

    val libraryStore = remember {
        FeedStore { token ->
            if (!prefs.isSignedIn()) {
                throw IllegalStateException("Sign in from Settings to see your library.")
            }
            FeedParser.parse(innerTube.browseSignedIn("FEmy_youtube", token))
        }
    }

    val play: (VideoItem, List<VideoItem>) -> Unit = { item, queue ->
        playing = PlayRequest(item, queue)
    }

    val current = playing
    if (current != null) {
        PlayerScreen(
            request = current,
            onExit = { playing = null },
            onNext = {
                val idx = current.queue.indexOfFirst { it.id == current.current.id }
                val next = current.queue.getOrNull(idx + 1)
                if (next != null) playing = PlayRequest(next, current.queue) else playing = null
            },
        )
        return
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        NavRail(destination) { picked ->
            destination = picked
            if (picked == Destination.Home) scope.launch { homeStore.refresh() }
            if (picked == Destination.Subscriptions) scope.launch { subsStore.refresh() }
            if (picked == Destination.Library) scope.launch { libraryStore.refresh() }
        }

        Box(Modifier.fillMaxSize()) {
            when (destination) {
                Destination.Home -> FeedScreen(
                    store = homeStore,
                    emptyHint = "Loading your feed…",
                    onPlay = play,
                )

                Destination.Subscriptions -> FeedScreen(
                    store = subsStore,
                    emptyHint = if (signedIn) "No subscriptions yet." else "Sign in to see your subscriptions.",
                    onPlay = play,
                )

                Destination.Library -> FeedScreen(
                    store = libraryStore,
                    emptyHint = if (signedIn) "Your library is empty." else "Sign in to see your library.",
                    onPlay = play,
                )

                Destination.Search -> SearchScreen(innerTube = innerTube, onPlay = play)

                Destination.Settings -> SettingsScreen(
                    prefs = prefs,
                    auth = auth,
                    innerTube = innerTube,
                    onAccountChanged = { label ->
                        signedIn = prefs.isSignedIn()
                        accountLabel = label
                        scope.launch { homeStore.refresh() }
                    },
                    accountLabel = accountLabel,
                    isSignedIn = signedIn,
                )
            }
        }
    }
}

/**
 * The side nav rail. Collapsed to a thin strip of icons until the person moves focus onto it (or
 * it holds the current section), the same "rests collapsed, expands on focus" behavior as the
 * current YouTube TV app's left rail, rather than a permanently-labelled sidebar.
 */
@Composable
private fun NavRail(current: Destination, onSelect: (Destination) -> Unit) {
    val entries = listOf(
        Destination.Home,
        Destination.Search,
        Destination.Subscriptions,
        Destination.Library,
        Destination.Settings,
    )
    var railFocused by remember { mutableStateOf(false) }
    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (railFocused) 220.dp else 84.dp,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "railWidth",
    )

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(RailBg)
            .padding(horizontal = 14.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.padding(start = 6.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).background(AccentRed, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(GlyphKind.Play, Color.White, 17.dp)
            }
            if (railFocused) {
                Spacer(Modifier.width(10.dp))
                Text("NovaTube", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        entries.forEach { entry ->
            val selected = entry == current
            FocusSurface(
                onClick = { onSelect(entry) },
                shape = RoundedCornerShape(24.dp),
                focusedScale = 1f,
                modifier = Modifier.fillMaxWidth(),
                onFocused = { focused -> railFocused = focused },
            ) { focused ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                focused -> Color.White
                                selected -> Color(0x26FFFFFF)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(
                        entry.glyph,
                        when {
                            focused -> Color.Black
                            selected -> Color.White
                            else -> Muted
                        },
                        22.dp,
                    )
                    if (railFocused) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            entry.label,
                            color = if (focused) Color.Black else if (selected) Color.White else Muted,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Home page.
 *
 * YouTube only fills the personalised "what to watch" page for a real signed-in session; for an
 * anonymous app (and even for a signed-in one on a fresh session) it comes back empty, and the
 * old public feeds (FEtrending, FEexplore) now answer with HTTP 400 "Request contains an invalid
 * argument.". So Home is filled from ordinary searches, which always return videos, one row per
 * topic. Each attempt is isolated: a single failing request can no longer blank the whole page.
 */
private suspend fun homeFeed(innerTube: InnerTube, signedIn: Boolean): FeedPage {
    val personalised = runCatching {
        if (signedIn) FeedParser.parse(innerTube.browseSignedIn("FEwhat_to_watch"))
        else FeedParser.parse(innerTube.browse("FEwhat_to_watch"))
    }.getOrNull()
    if (personalised != null && personalised.shelves.isNotEmpty()) return personalised

    val topics = listOf(
        "Trending" to "trending on youtube",
        "Music" to "music videos",
        "Gaming" to "gaming videos",
        "Movies & shows" to "movie trailers",
        "News" to "news today",
        "Comedy" to "comedy videos",
    )

    val results = coroutineScope {
        topics.map { (title, query) ->
            async { title to runCatching { FeedParser.parse(innerTube.search(query)) } }
        }.awaitAll()
    }

    val seen = HashSet<String>()
    val shelves = results.mapNotNull { (title, result) ->
        val items = result.getOrNull()
            ?.shelves
            ?.flatMap { it.items }
            ?.filter { seen.add(it.id) }
            ?.take(16)
            .orEmpty()
        if (items.isEmpty()) null else Shelf(title, items)
    }

    if (shelves.isEmpty()) {
        throw results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            ?: IllegalStateException("Could not reach YouTube.")
    }
    return FeedPage(shelves, null)
}

@Composable
private fun FeedScreen(store: FeedStore, emptyHint: String, onPlay: (VideoItem, List<VideoItem>) -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!store.loaded) store.refresh()
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(listState, store.shelves.size) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (store.shelves.isNotEmpty() && last >= store.shelves.size) {
            store.loadMore()
        }
    }

    if (store.shelves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (store.loading) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(emptyHint, color = Muted, fontSize = 15.sp)
                } else {
                    Text(store.error ?: emptyHint, color = Muted, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    FocusSurface(onClick = { scope.launch { store.refresh() } }) {
                        Text(
                            "Retry",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        return
    }

    val hero = store.shelves.firstOrNull()?.items?.firstOrNull()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item {
                HeroBanner(hero) { onPlay(hero, store.shelves.first().items) }
            }
        }
        items(count = store.shelves.size) { index ->
            ShelfRow(
                shelf = store.shelves[index],
                onPlay = onPlay,
                requestFocusOnFirst = if (index == 1) firstFocus else null,
            )
        }
        if (store.loading) {
            item {
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(item: VideoItem, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(Bg, Color(0xCC0F0F0F), Color(0x110F0F0F)),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0F0F0F))),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 30.dp, bottom = 26.dp, end = 30.dp)
                .fillMaxWidth(0.66f),
        ) {
            Text(
                item.title,
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(item.channel, item.meta).joinToString("  •  "),
                color = Muted,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(18.dp))
            FocusSurface(onClick = onPlay, shape = RoundedCornerShape(24.dp)) { focused ->
                Row(
                    Modifier
                        .background(
                            if (focused) Color.White else Color(0xE6FFFFFF),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 24.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(GlyphKind.Play, Color.Black, 16.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("Play", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
