package com.newsaggregator.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import com.newsaggregator.ui.glass.NewsGlassCard
import com.newsaggregator.ui.theme.LocalNewsColors
import com.newsaggregator.utils.RssParser

/** Live section (newsflow v4.2.0 pure Kotlin port of MainActivity's embedded live view). */
private val LIVE_SOURCES = listOf("Al Jazeera", "Dawn")
private val LIVE_FEEDS = listOf(
    "https://www.aljazeera.com/xml/rss/all.xml",
    "https://www.dawn.com/feeds/home"
)

private const val LIVE_JS =
    "(function() {" +
        "var s = document.createElement('style');" +
        "s.textContent = '" +
        "nav, header, footer, aside, " +
        "[class*=\"nav\"], [class*=\"footer\"], [class*=\"header\"], " +
        "[class*=\"sidebar\"], [class*=\"ad-\"], [class*=\"advert\"], [id*=\"ad-\"], " +
        "[class*=\"newsletter\"], [class*=\"subscribe\"], [class*=\"cookie\"], " +
        "[class*=\"social\"], [class*=\"share\"], [class*=\"related\"], " +
        "[class*=\"recommendation\"], [class*=\"trending\"] " +
        "{display:none!important} " +
        "body{padding:12px!important;margin:0!important;max-width:100vw!important;overflow-x:hidden!important} " +
        "img{max-width:100%!important;height:auto!important} " +
        "article, main, [class*=\"live\"], [class*=\"blog\"], [class*=\"update\"] " +
        "{max-width:100%!important;padding:0!important;margin:0!important}'" +
        ";document.head.appendChild(s);" +
        "document.body.style.background='#050510';" +
        "document.body.style.color='#F0F0F5';" +
        "})();"

private val LIVE_HINTS = listOf(
    "live", "liveblog", "live-blog", "live-updates", "as-it-happened"
)

@Composable
fun LiveScreen() {
    val context = LocalContext.current
    val colors = LocalNewsColors.current

    var selectedSource by rememberSaveable { mutableIntStateOf(0) }
    var stories by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var liveMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var openedUrl by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val webview = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(LIVE_JS, null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        status = "Failed to load page. Tap another link or pull to retry."
                    }
                }
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { webview.destroy() }
    }

    BackHandler(enabled = openedUrl != null) {
        openedUrl = null
        webview.loadUrl("about:blank")
    }

    LaunchedEffect(selectedSource, tick) {
        loading = true
        status = null
        openedUrl = null
        webview.loadUrl("about:blank")
        val feedUrl = LIVE_FEEDS[selectedSource]
        val sourceName = LIVE_SOURCES[selectedSource]
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                RssParser.fetchNews(NewsSource(sourceName, feedUrl, "Live"))
            } catch (_: Exception) {
                null
            }
        }
        loading = false
        if (result == null) {
            stories = emptyList()
            liveMode = false
            status = "Failed to load feed. Pull down to retry."
        } else {
            val blogs = result.filter { it ->
                val u = it.link.lowercase()
                val t = it.title.lowercase()
                LIVE_HINTS.any(u::contains) ||
                    t.contains("live") || t.contains("as it happened")
            }
            liveMode = blogs.isNotEmpty()
            stories = blogs.ifEmpty { result }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Source switcher pills (pill_active_bg recipe: active #1AFF6B4A +
        // 1dp #33FF6B4A stroke; inactive pills stay clear)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LIVE_SOURCES.forEachIndexed { idx, name ->
                val selected = idx == selectedSource
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (selected) {
                                Modifier
                                    .background(Color(0x1AFF6B4A))
                                    .border(1.dp, Color(0x33FF6B4A), RoundedCornerShape(20.dp))
                            } else Modifier
                        )
                        .clickable { if (selectedSource != idx) selectedSource = idx }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) colors.textPrimary else colors.textSecondary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { tick++ }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "Refresh",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
            }
        }

        if (openedUrl != null) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webview },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (openedUrl != null && view.url != openedUrl) {
                            view.loadUrl(openedUrl!!)
                        }
                    }
                )
                status?.let { msg ->
                    Text(
                        msg,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC15151F))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (stories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        status ?: "No stories found.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 6.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        if (liveMode) Color(0xFFE94560) else colors.accent,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (liveMode) "LIVE BLOGS"
                                else "LATEST (no active live blogs — showing recent stories)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (liveMode) Color(0xFFE94560) else colors.textSecondary
                            )
                        }
                    }
                    items(stories, key = { it.link.ifEmpty { it.title + it.hashCode() } }) { item ->
                        LiveStoryCard(item, liveMode) { url ->
                            if (url.isNotEmpty()) {
                                status = null
                                openedUrl = url
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStoryCard(item: NewsItem, isLive: Boolean, onOpen: (String) -> Unit) {
    val colors = LocalNewsColors.current
    NewsGlassCard(Modifier.fillMaxWidth().clickable { onOpen(item.link) }, cornerRadius = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (isLive) Color(0xFFE94560) else colors.accent,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.2.sp,
                    letterSpacing = (-0.14).sp,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                item.pubDate.takeIf { it.isNotEmpty() }?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(it, fontSize = 10.sp, color = colors.textTertiary)
                }
            }
        }
    }
}
