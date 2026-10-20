package com.newsaggregator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import com.newsaggregator.ui.glass.GlassBody
import com.newsaggregator.ui.glass.NewsGlassBackdrop
import com.newsaggregator.ui.glass.NewsGlassCard
import com.newsaggregator.ui.glass.NewsGlassIconButton
import com.newsaggregator.ui.glass.NewsGlassPill
import com.newsaggregator.ui.glass.NewsTimeChip
import com.newsaggregator.ui.glass.newsGlassSurfaceStyle
import com.newsaggregator.ui.screens.LiveScreen
import com.newsaggregator.ui.screens.ManageSourcesScreen
import com.newsaggregator.ui.screens.SettingsScreen
import com.newsaggregator.ui.theme.LocalNewsColors
import com.newsaggregator.ui.theme.NewsFlowTheme
import com.newsaggregator.ui.theme.categoryColor
import com.newsaggregator.utils.NewsCache
import com.newsaggregator.utils.RssParser
import com.newsaggregator.utils.ThemeUtils
import com.newsaggregator.widgets.NewsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Collections
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    companion object {
        val LIVE_SOURCES = arrayOf("Al Jazeera", "Dawn")
        val LIVE_FEEDS = arrayOf(
            "https://www.aljazeera.com/xml/rss/all.xml",
            "https://www.dawn.com/feeds/home"
        )

        fun getAllSources(context: Context): MutableList<NewsSource> {
            val list = RssParser.getDefaultSources().toMutableList()
            val prefs = context.getSharedPreferences("news_sources", Context.MODE_PRIVATE)
            val urls = prefs.getStringSet("custom_urls", emptySet()) ?: emptySet()
            val names = prefs.getStringSet("custom_names", emptySet()) ?: emptySet()
            val cats = prefs.getStringSet("custom_categories", emptySet()) ?: emptySet()
            val urlList = ArrayList(urls)
            val nameList = ArrayList(names)
            val catList = ArrayList(cats)
            for (i in urlList.indices) {
                val name = if (i < nameList.size) nameList[i] else "Custom"
                val cat = if (i < catList.size) catList[i] else "Custom"
                list.add(NewsSource(name, urlList[i], cat))
            }
            return list
        }
    }

    /** Bumped from onNewIntent when the widget asks for a refresh. */
    internal val widgetRefreshEvents = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val fontSizeScale = when (
            getSharedPreferences("app_prefs", MODE_PRIVATE).getInt("font_size", 16)
        ) {
            in Int.MIN_VALUE..13 -> 0.9f
            in 20..Int.MAX_VALUE -> 1.2f
            else -> 1.0f
        }
        setContent {
            NewsFlowTheme(
                lightTheme = ThemeUtils.isLight(this),
                fontSizeScale = fontSizeScale
            ) {
                NewsGlassBackdrop {
                    NewsRoot(this)
                }
            }
        }
        handleWidgetRefreshIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetRefreshIntent(intent)
    }

    private fun handleWidgetRefreshIntent(intent: Intent?) {
        if (intent != null &&
            NewsWidgetProvider.ACTION_REFRESH == intent.action
        ) {
            widgetRefreshEvents.value++
            setIntent(Intent(this, MainActivity::class.java)) // consume so recreate doesn't loop
        }
    }
}

/** Route keys for the single-Activity Compose navigation. */
internal const val ROUTE_MAIN = "main"
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_SOURCES = "sources"

@Composable
fun NewsRoot(activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- Core state ----
    var route by remember { mutableStateOf(ROUTE_MAIN) }
    var sources by remember { mutableStateOf(MainActivity.getAllSources(context)) }
    var allNews by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var currentFilter by remember { mutableStateOf("Front Page") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sourcesVersion by remember { mutableStateOf(0) } // bumped when Manage Sources closes
    var resumeTick by remember { mutableStateOf(0) }     // bumped on every ON_RESUME

    // ---- Fetch pipeline (same semantics as the Java doFetchNews) ----
    fun fetchNews() {
        refreshing = true
        if (allNews.isEmpty()) initialLoading = true
        statusText = null
        scope.launch {
            val syncList = Collections.synchronizedList(mutableListOf<NewsItem>())
            val jobs = mutableListOf<Job>()
            for (src in sources) {
                jobs.add(launch(Dispatchers.IO) {
                    try { syncList.addAll(RssParser.fetchNews(src)) } catch (_: Exception) {}
                })
            }
            withTimeoutOrNull(15000) { jobs.joinAll() }
            withContext(Dispatchers.Main) {
                if (syncList.isNotEmpty()) {
                    allNews = syncList.toList()
                    NewsCache.save(context, allNews)
                    statusText = null
                    // Keep the home-screen widget in sync after every successful fetch
                    try {
                        context.sendBroadcast(
                            Intent(context, NewsWidgetProvider::class.java)
                                .setAction(NewsWidgetProvider.ACTION_DATA_CHANGED)
                        )
                    } catch (_: Exception) {}
                }
                refreshing = false
                initialLoading = false
                if (syncList.isEmpty() && allNews.isEmpty()) {
                    statusText = "No stories found.\nPull down to retry."
                }
            }
        }
    }

    // Initial load: cache instantly, then fetch fresh (like Java onCreate)
    LaunchedEffect(Unit) {
        val cached = NewsCache.load(context)
        if (cached.isNotEmpty()) {
            allNews = cached
            initialLoading = false
        }
        fetchNews()
    }

    // Widget refresh events
    LaunchedEffect(activity.widgetRefreshEvents.value) {
        if (activity.widgetRefreshEvents.value > 0) fetchNews()
    }

    // Re-read prefs on resume (ArticleActivity writes read marks / category taps)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- Derived display list (ports filterAndDisplay) ----
    val readUrls = remember(resumeTick) {
        context.getSharedPreferences("read_articles", Context.MODE_PRIVATE)
            .getStringSet("read_urls", emptySet()) ?: emptySet()
    }
    val displayList by remember(allNews, currentFilter, searchQuery, resumeTick) {
        derivedStateOf {
            if (currentFilter == "Front Page" || currentFilter == "Live") {
                allNews
            } else {
                val bookmarkedUrls: Set<String> = if (currentFilter == "Bookmarks") {
                    context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
                        .getStringSet("saved_urls", emptySet()) ?: emptySet()
                } else emptySet()

                var list = allNews.filter { item ->
                    when (currentFilter) {
                        "For You", "All" -> true
                        "Bookmarks" -> item.link.isNotEmpty() && bookmarkedUrls.contains(item.link)
                        else -> currentFilter == item.source
                    } && (searchQuery.isEmpty() ||
                            item.title.lowercase().contains(searchQuery) ||
                            item.description.lowercase().contains(searchQuery) ||
                            item.source.lowercase().contains(searchQuery))
                }

                list = list.sortedByDescending { it.timestamp }

                if (currentFilter == "For You") {
                    try {
                        val taps = JSONObject(
                            context.getSharedPreferences("reading_prefs", Context.MODE_PRIVATE)
                                .getString("category_taps", "{}") ?: "{}"
                        )
                        list = list.sortedWith(
                            compareByDescending<NewsItem> { taps.optInt(it.category, 0) }
                                .thenByDescending { it.timestamp }
                        )
                    } catch (_: Exception) {}
                }
                list
            }
        }
    }

    fun openArticle(item: NewsItem, articles: List<NewsItem>?, position: Int) {
        val i = Intent(context, ArticleActivity::class.java)
        i.putExtra("title", item.title)
        i.putExtra("url", item.link)
        i.putExtra("source", item.source)
        i.putExtra("time", item.pubDate)
        i.putExtra("category", item.category)
        i.putExtra("description", item.description)
        if (articles != null) {
            i.putExtra("articles", ArrayList(articles))
            i.putExtra("position", position)
        }
        context.startActivity(i)
    }

    // ---- UI ----
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        // ===== Header (glass) =====
        NewsGlassPill(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Logo: pill_active_bg recipe — accent disc, 20dp radius on 28dp box
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LocalNewsColors.current.accent),
                    contentAlignment = Alignment.Center
                ) { Text("📰", fontSize = 16.sp) }
                Spacer(Modifier.width(8.dp))
                Text("NewsFlow", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = LocalNewsColors.current.textPrimary)
            }
            // Theme toggle (header_btn_bg recipe)
            NewsGlassIconButton(
                onClick = {
                    ThemeUtils.toggleTheme(context)
                    activity.recreate()
                }
            ) {
                Text(
                    if (LocalNewsColors.current.isLight) "☀️" else "🌙",
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            // Search toggle (header_btn_bg recipe)
            NewsGlassIconButton(onClick = { showSearch = !showSearch }) {
                Text("🔍", fontSize = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            // Settings (header_btn_bg recipe)
            NewsGlassIconButton(onClick = { route = ROUTE_SETTINGS }) {
                Text("⚙", fontSize = 15.sp, color = LocalNewsColors.current.textSecondary)
            }
        }

        // ===== Search bar =====
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.trim().lowercase() },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = LocalNewsColors.current.textPrimary,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(LocalNewsColors.current.accent),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                // search_bg recipe: plate + #14→#08 body under 0.75dp rim
                                .then(newsGlassSurfaceStyle(18.dp, GlassBody.Chrome))
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text("Search news, topics, sources...",
                                    fontSize = 13.sp, color = LocalNewsColors.current.textTertiary)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = LocalNewsColors.current.textSecondary,
                    modifier = Modifier
                        .clickable {
                            searchQuery = ""
                            showSearch = false
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // ===== Content =====
        Box(Modifier.weight(1f)) {
            when (route) {
                ROUTE_SETTINGS -> SettingsScreen(
                    onBack = { route = ROUTE_MAIN },
                    onOpenSources = { route = ROUTE_SOURCES }
                )
                ROUTE_SOURCES -> ManageSourcesScreen(
                    onBack = {
                        route = ROUTE_MAIN
                        // Same as onActivityResult: rebuild tabs + refetch
                        sources = MainActivity.getAllSources(context)
                        sourcesVersion++
                        currentFilter = "Front Page"
                        fetchNews()
                    }
                )
                else -> {
                    if (currentFilter == "Live") {
                        LiveScreen()
                    } else {
                        PullFeedArea(
                            refreshing = refreshing,
                            initialLoading = initialLoading,
                            statusText = statusText,
                            currentFilter = currentFilter,
                            frontPageItems = allNews,
                            displayList = displayList,
                            readUrls = readUrls,
                            isBookmarked = { url ->
                                context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
                                    .getStringSet("saved_urls", emptySet())?.contains(url) == true
                            },
                            onRefresh = { fetchNews() },
                            onOpen = { item, list, pos -> openArticle(item, list, pos) },
                            onBookmarkToggle = { item ->
                                val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
                                val saved = HashSet(prefs.getStringSet("saved_urls", emptySet()) ?: emptySet())
                                if (saved.contains(item.link)) {
                                    saved.remove(item.link)
                                    android.widget.Toast.makeText(context, "Bookmark removed", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    saved.add(item.link)
                                    android.widget.Toast.makeText(context, "Bookmarked!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                prefs.edit().putStringSet("saved_urls", saved).apply()
                                resumeTick++
                            },
                            onShare = { item ->
                                val intent = Intent(Intent.ACTION_SEND)
                                intent.type = "text/plain"
                                var text = item.title
                                if (item.link.isNotEmpty()) text += "\n" + item.link
                                intent.putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(intent, "Share article"))
                            }
                        )
                    }
                }
            }
        }

        // ===== Bottom tab bar (glass lens) =====
        if (route == ROUTE_MAIN) {
            BottomTabBar(
                sources = sources,
                selected = currentFilter,
                version = sourcesVersion,
                onSelect = { name -> currentFilter = name }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullFeedArea(
    refreshing: Boolean,
    initialLoading: Boolean,
    statusText: String?,
    currentFilter: String,
    frontPageItems: List<NewsItem>,
    displayList: List<NewsItem>,
    readUrls: Set<String>,
    isBookmarked: (String) -> Boolean,
    onRefresh: () -> Unit,
    onOpen: (NewsItem, List<NewsItem>?, Int) -> Unit,
    onBookmarkToggle: (NewsItem) -> Unit,
    onShare: (NewsItem) -> Unit
) {
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            pullState.endRefresh()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        if (currentFilter == "Front Page") {
            FrontPageList(
                items = frontPageItems,
                readUrls = readUrls,
                onOpen = { item -> onOpen(item, null, 0) }
            )
        } else {
            NewsList(
                items = displayList,
                readUrls = readUrls,
                isBookmarked = isBookmarked,
                onOpen = { item, idx -> onOpen(item, displayList, idx) },
                onBookmarkToggle = onBookmarkToggle,
                onShare = onShare
            )
        }

        if (initialLoading && refreshing) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = LocalNewsColors.current.accent
            )
        }
        if (statusText != null) {
            Text(
                statusText,
                fontSize = 14.sp,
                color = LocalNewsColors.current.textSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }
        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/** Front Page: hero card + compact per-source top stories. */
@Composable
private fun FrontPageList(
    items: List<NewsItem>,
    readUrls: Set<String>,
    onOpen: (NewsItem) -> Unit
) {
    val topPerSource = remember(items) {
        val map = LinkedHashMap<String, NewsItem>()
        for (item in items) {
            if (item.source.isNotEmpty() && !map.containsKey(item.source)) {
                map[item.source] = item
            }
        }
        map.values.sortedByDescending { it.timestamp }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
    ) {
        itemsIndexed(topPerSource, key = { _, it -> it.link.ifEmpty { it.title + it.timestamp } }) { _, item ->
            val isRead = readUrls.contains(item.link)
            if (item === topPerSource.first()) {
                HeroCard(item, isRead, onOpen)
            } else {
                CompactSourceCard(item, isRead, onOpen)
            }
        }
    }
}

/** Hero card — exact port of item_front_hero.xml (20dp card, 20dp inner padding). */
@Composable
private fun HeroCard(item: NewsItem, isRead: Boolean, onOpen: (NewsItem) -> Unit) {
    val color = categoryColor(item.category)
    val readAlpha = if (isRead) 0.5f else 1f
    NewsGlassCard(
        Modifier.fillMaxWidth().clickable { onOpen(item) },
        cornerRadius = 20.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape).alpha(readAlpha))
            Spacer(Modifier.width(6.dp))
            Text(
                item.source,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = LocalNewsColors.current.textSecondary,
                modifier = Modifier.weight(1f).alpha(readAlpha)
            )
            NewsTimeChip(item.pubDate, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            item.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            letterSpacing = (-0.4).sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            color = LocalNewsColors.current.textPrimary,
            modifier = Modifier.alpha(readAlpha)
        )
        if (item.description.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (item.description.length > 200) item.description.substring(0, 200) + "…"
                else item.description,
                fontSize = 13.sp,
                lineHeight = 18.9.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = LocalNewsColors.current.textSecondary,
                modifier = Modifier.alpha(readAlpha)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth(0.18f)
                .height(3.dp)
                .background(color, RoundedCornerShape(2.dp))
                .alpha(readAlpha)
        )
    }
}

/** Compact per-source card — exact port of item_front_source.xml (20dp card). */
@Composable
private fun CompactSourceCard(item: NewsItem, isRead: Boolean, onOpen: (NewsItem) -> Unit) {
    val color = categoryColor(item.category)
    val readAlpha = if (isRead) 0.5f else 1f
    NewsGlassCard(Modifier.fillMaxWidth().clickable { onOpen(item) }, cornerRadius = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape).alpha(readAlpha))
            Spacer(Modifier.width(6.dp))
            Text(
                item.source,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = LocalNewsColors.current.textSecondary,
                modifier = Modifier.weight(1f).alpha(readAlpha)
            )
            NewsTimeChip(
                item.category,
                fontSize = 9.sp,
                horizontalPadding = 6.dp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.2.sp,
            letterSpacing = (-0.14).sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = LocalNewsColors.current.textPrimary,
            modifier = Modifier.alpha(readAlpha)
        )
        Spacer(Modifier.height(6.dp))
        Text(item.pubDate, fontSize = 10.sp, color = LocalNewsColors.current.textTertiary)
    }
}

/** Feed list (For You / All / Bookmarks / source tabs) with swipe gestures. */
@Composable
private fun NewsList(
    items: List<NewsItem>,
    readUrls: Set<String>,
    isBookmarked: (String) -> Boolean,
    onOpen: (NewsItem, Int) -> Unit,
    onBookmarkToggle: (NewsItem) -> Unit,
    onShare: (NewsItem) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
    ) {
        itemsIndexed(items, key = { _, it -> it.link.ifEmpty { it.title + it.timestamp } + it.source }) { idx, item ->
            StorySwipeCard(
                item = item,
                isRead = readUrls.contains(item.link),
                bookmarked = isBookmarked(item.link),
                onOpen = { onOpen(item, idx) },
                onBookmarkToggle = onBookmarkToggle,
                onShare = onShare
            )
        }
    }
}

/** Right swipe = bookmark toggle (orange), left swipe = share (blue). */
@Composable
private fun StorySwipeCard(
    item: NewsItem,
    isRead: Boolean,
    bookmarked: Boolean,
    onOpen: () -> Unit,
    onBookmarkToggle: (NewsItem) -> Unit,
    onShare: (NewsItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { 110.dp.toPx() }

    Box(Modifier.fillMaxWidth()) {
        // Action background
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    when {
                        offsetX.value > 0 -> com.newsaggregator.ui.theme.SwipeBookmarkBg
                        offsetX.value < 0 -> com.newsaggregator.ui.theme.SwipeShareBg
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (offsetX.value != 0f) {
                Text(
                    if (offsetX.value > 0)
                        (if (bookmarked) "🔖 remove" else "🔖") else "↗",
                    fontSize = 20.sp, color = Color.White
                )
            }
        }

        NewsGlassCard(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > thresholdPx -> onBookmarkToggle(item)
                                    offsetX.value < -thresholdPx -> onShare(item)
                                }
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    )
                }
                .clickable { onOpen() },
            cornerRadius = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, top = 16.dp, end = 18.dp, bottom = 14.dp
            )
        ) {
            // Source row: square 5dp dot + source + time chip (item_news_card recipe)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(categoryColor(item.category))
                        .alpha(if (isRead) 0.4f else 1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    item.source,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = LocalNewsColors.current.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isRead) 0.45f else 1f)
                )
                NewsTimeChip(item.pubDate, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.3.sp,
                letterSpacing = (-0.15).sp,
                color = LocalNewsColors.current.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (isRead) 0.45f else 1f)
            )
        }
    }
}

/** Bottom floating glass tab bar: Front Page / Live / For You / All / Bookmarks / sources. */
@Composable
private fun BottomTabBar(
    sources: List<NewsSource>,
    selected: String,
    version: Int,
    onSelect: (String) -> Unit
) {
    val names = remember(sources, version) {
        mutableListOf("Front Page", "Live", "For You", "All", "Bookmarks").apply {
            for (s in sources) add(s.name)
        }
    }
    val scrollState = rememberScrollState()
    NewsGlassPill(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        cornerRadius = 24.dp
    ) {
        Row(
            Modifier
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            names.forEach { name ->
                val active = name == selected
                // pill_active_bg: #1AFF6B4A fill + 1dp #33FF6B4A stroke, 20dp radius;
                // inactive pills stay clear (no background)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (active) {
                                Modifier
                                    .background(Color(0x1AFF6B4A))
                                    .border(1.dp, Color(0x33FF6B4A), RoundedCornerShape(20.dp))
                            } else Modifier
                        )
                        .clickable { onSelect(name) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) LocalNewsColors.current.textPrimary
                                else LocalNewsColors.current.textSecondary
                    )
                }
            }
        }
    }
}
