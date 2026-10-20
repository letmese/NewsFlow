package com.newsaggregator.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.newsaggregator.ArticleActivity
import com.newsaggregator.MainActivity
import com.newsaggregator.NewsFlowApplication
import com.newsaggregator.R
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import com.newsaggregator.widgets.NewsWidgetProvider
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

/** Background notification check (v4.2 grouped-notification behavior). */
class BreakingNewsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val seenUrls = HashSet(prefs.getStringSet(KEY_LAST_URLS, HashSet()) ?: HashSet())

        val appPrefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean("notifications_enabled", true)) {
            return Result.success()
        }

        // Load all sources
        val sources = loadSources(ctx)
        if (sources.isEmpty()) return Result.success()

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return Result.success()

        val isFirstRun = seenUrls.isEmpty()
        val allSeenUrls = HashSet(seenUrls)

        // Map: source name -> list of new articles (sorted newest first)
        val sourceArticles = HashMap<String, MutableList<NewsItem>>()
        // Map: source name -> latest timestamp (for sorting sources)
        val sourceLatestTime = HashMap<String, Long>()

        for (source in sources) {
            try {
                val articles = RssParser.fetchNews(source)
                if (articles.isEmpty()) continue

                // Track ALL URLs
                for (a in articles) {
                    val url = a.link
                    if (!url.isNullOrEmpty()) allSeenUrls.add(url)
                }

                if (isFirstRun) continue

                // Find new articles, already sorted newest first from RssParser
                val newArticles = ArrayList<NewsItem>()
                for (a in articles) {
                    val url = a.link
                    if (!url.isNullOrEmpty() && !seenUrls.contains(url)) {
                        newArticles.add(a)
                    }
                }

                if (newArticles.isNotEmpty()) {
                    // Sort newest first within this source
                    Collections.sort(newArticles) { a, b -> java.lang.Long.compare(b.timestamp, a.timestamp) }

                    val name = source.name
                    sourceArticles[name] = newArticles
                    sourceLatestTime[name] = newArticles[0].timestamp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Persist ALL seen URLs
        prefs.edit().putStringSet(KEY_LAST_URLS, allSeenUrls).apply()

        if (sourceArticles.isEmpty()) return Result.success()

        // Sort sources by latest article time (newest source first)
        val sortedSources = ArrayList(sourceArticles.entries)
        Collections.sort(sortedSources) { a, b ->
            java.lang.Long.compare(
                sourceLatestTime.getOrDefault(b.key, 0L),
                sourceLatestTime.getOrDefault(a.key, 0L)
            )
        }

        // Clear all old notifications in this group first
        manager.cancelAll()

        // Build per-source child notifications
        var totalArticles = 0
        var childNid = 1000

        for (entry in sortedSources) {
            val sourceName = entry.key
            val newArticles = entry.value
            totalArticles += newArticles.size

            val limit = minOf(newArticles.size, MAX_ARTICLES_PER_SOURCE)
            val moreCount = newArticles.size - limit

            // Build InboxStyle for this source
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("$sourceName — ${newArticles.size} new")

            for (i in 0 until limit) {
                inbox.addLine(newArticles[i].title)
            }
            if (moreCount > 0) {
                inbox.setSummaryText("+ $moreCount more")
            }

            // Tap opens the latest article from this source
            val topArticle = newArticles[0]
            val intent = Intent(ctx, ArticleActivity::class.java)
            intent.putExtra("url", topArticle.link)
            intent.putExtra("title", topArticle.title)
            intent.putExtra("source", sourceName)
            intent.putExtra("category", topArticle.category)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(ctx, childNid, intent, flag)

            // Stable notification ID per source (so updates replace, not duplicate)
            val sourceId = ("newsflow_" + sourceName).hashCode() and 0x7FFFFFFF

            val child = NotificationCompat.Builder(ctx, NewsFlowApplication.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(sourceName)
                .setContentText("${newArticles.size} new articles")
                .setStyle(inbox)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSortKey(String.format("%010d", Long.MAX_VALUE - (sourceLatestTime[sourceName] ?: 0L)))

            manager.notify(sourceId, child.build())
            childNid++
        }

        // Build group summary notification (the ONE notification user sees)
        val sourceCount = sourceArticles.size

        // Summary opens main app
        val summaryIntent = Intent(ctx, MainActivity::class.java)
        summaryIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val summaryPi = PendingIntent.getActivity(
            ctx, SUMMARY_ID, summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build expanded summary showing source breakdown
        val summaryInbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("$totalArticles new articles from $sourceCount sources")

        for (entry in sortedSources) {
            summaryInbox.addLine("${entry.key} — ${entry.value.size} new")
        }

        val summary = NotificationCompat.Builder(ctx, NewsFlowApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$totalArticles new articles")
            .setContentText("$sourceCount sources updated")
            .setStyle(summaryInbox)
            .setContentIntent(summaryPi)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        manager.notify(SUMMARY_ID, summary.build())

        // Keep the home-screen widget fresh after every notification cycle (cheap redraw)
        try {
            ctx.sendBroadcast(Intent(ctx, NewsWidgetProvider::class.java).setAction(NewsWidgetProvider.ACTION_DATA_CHANGED))
        } catch (_: Exception) {
        }

        return Result.success()
    }

    private fun loadSources(ctx: Context): List<NewsSource> {
        val sources = RssParser.getDefaultSources().toMutableList()
        val srcPrefs: SharedPreferences = ctx.getSharedPreferences("news_sources", Context.MODE_PRIVATE)
        val customUrls = srcPrefs.getStringSet("custom_urls", HashSet()) ?: HashSet()
        val customNames = srcPrefs.getStringSet("custom_names", HashSet()) ?: HashSet()
        val customCats = srcPrefs.getStringSet("custom_categories", HashSet()) ?: HashSet()
        val urlList = ArrayList(customUrls)
        val nameList = ArrayList(customNames)
        val catList = ArrayList(customCats)
        for (i in urlList.indices) {
            val name = if (i < nameList.size) nameList[i] else "Custom"
            val cat = if (i < catList.size) catList[i] else "Custom"
            sources.add(NewsSource(name, urlList[i], cat))
        }
        return sources
    }

    companion object {
        private const val PREF_NAME = "breaking_news_prefs"
        private const val KEY_LAST_URLS = "last_urls"
        private const val GROUP_KEY = "newsflow_group"
        private const val SUMMARY_ID = 9999
        private const val MAX_ARTICLES_PER_SOURCE = 7
    }
}
