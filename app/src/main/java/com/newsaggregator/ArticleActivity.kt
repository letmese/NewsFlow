package com.newsaggregator

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.newsaggregator.ui.glass.NewsGlassBackdrop
import com.newsaggregator.ui.glass.NewsGlassCard
import com.newsaggregator.ui.glass.NewsGlassDesc
import com.newsaggregator.ui.glass.NewsGlassPill
import com.newsaggregator.ui.theme.LocalNewsColors
import com.newsaggregator.ui.theme.NewsFlowTheme
import com.newsaggregator.ui.theme.categoryColor
import com.newsaggregator.utils.ArticleTextExtractor
import com.newsaggregator.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Compose rewrite of ArticleActivity (single-article mode; the ViewPager2
 * multi-article branch of the original was unreachable — no caller ever put
 * the "articles" extra).
 */
class ArticleActivity : ComponentActivity() {

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
                    ArticleScreen(
                        title = intent.getStringExtra("title") ?: "",
                        url = intent.getStringExtra("url") ?: "",
                        source = intent.getStringExtra("source") ?: "",
                        time = intent.getStringExtra("time") ?: "",
                        category = intent.getStringExtra("category") ?: "",
                        description = intent.getStringExtra("description") ?: "",
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ArticleScreen(
    title: String,
    url: String,
    source: String,
    time: String,
    category: String,
    description: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bodyText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val accent = categoryColor(category)

    // Mark as read + record category tap (same as Java onCreate side effects)
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            val readPrefs: SharedPreferences =
                context.getSharedPreferences("read_articles", Context.MODE_PRIVATE)
            val read = HashSet(readPrefs.getStringSet("read_urls", emptySet()) ?: emptySet())
            read.add(url)
            readPrefs.edit().putStringSet("read_urls", read).apply()
        }
        try {
            val prefs = context.getSharedPreferences("reading_prefs", Context.MODE_PRIVATE)
            val taps = JSONObject(prefs.getString("category_taps", "{}") ?: "{}")
            if (category.isNotEmpty()) {
                taps.put(category, taps.optInt(category, 0) + 1)
                prefs.edit().putString("category_taps", taps.toString()).apply()
            }
        } catch (_: Exception) {}
    }

    // Fetch article body
    LaunchedEffect(url) {
        if (url.isEmpty()) {
            bodyText = null
            loading = false
        } else {
            loading = true
            failed = false
            val text = withContext(Dispatchers.IO) {
                try { ArticleTextExtractor.fetchArticleText(url, description) } catch (_: Exception) { null }
            }
            bodyText = text
            failed = text.isNullOrEmpty()
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        // ===== Top action bar =====
        NewsGlassPill(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "←",
                    fontSize = 18.sp,
                    color = LocalNewsColors.current.textPrimary,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNewsColors.current.glassBg)
                        .border(1.dp, LocalNewsColors.current.glassBorder, RoundedCornerShape(10.dp))
                        .wrapContentSize(Alignment.Center)
                        .clickable { onBack() }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(accent, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            source,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalNewsColors.current.textSecondary
                        )
                        if (category.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("· $category", fontSize = 10.sp,
                                color = LocalNewsColors.current.textTertiary)
                        }
                    }
                    if (time.isNotEmpty()) {
                        Text(time, fontSize = 10.sp, color = LocalNewsColors.current.textTertiary)
                    }
                }
                Text(
                    "🔗",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNewsColors.current.glassBg)
                        .border(1.dp, LocalNewsColors.current.glassBorder, RoundedCornerShape(10.dp))
                        .wrapContentSize(Alignment.Center)
                        .clickable {
                            if (url.isNotEmpty()) {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                                        },
                                        "Share article"
                                    )
                                )
                            }
                        }
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "🖼",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalNewsColors.current.glassBg)
                        .border(1.dp, LocalNewsColors.current.glassBorder, RoundedCornerShape(10.dp))
                        .wrapContentSize(Alignment.Center)
                        .clickable {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    try {
                                        ArticleTextExtractor.shareAsImage(
                                            context, title,
                                            bodyText ?: description,
                                            source.ifEmpty { "NewsFlow" }
                                        )
                                    } catch (_: Exception) { null }
                                }
                                if (file != null) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(sendIntent, "Share article image")
                                    )
                                } else {
                                    Toast.makeText(
                                        context, "Could not create image", Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                )
            }
        }

        // ===== Body =====
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalNewsColors.current.accent)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    color = LocalNewsColors.current.textPrimary
                )
                if (description.isNotEmpty()) {
                    NewsGlassDesc(description, Modifier.fillMaxWidth())
                }
                when {
                    !failed && !bodyText.isNullOrEmpty() -> Text(
                        bodyText!!,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = LocalNewsColors.current.textPrimary
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Couldn't extract the full article text.",
                            fontSize = 14.sp,
                            color = LocalNewsColors.current.textSecondary
                        )
                        if (url.isNotEmpty()) {
                            Text(
                                "Open in browser →",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalNewsColors.current.accent,
                                modifier = Modifier.clickable {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
