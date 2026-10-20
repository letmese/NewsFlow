package com.newsaggregator.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.newsaggregator.MainActivity
import com.newsaggregator.R
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Older/simpler notification worker. Present for parity with the original tree
 * but NOT scheduled anywhere - only BreakingNewsWorker is enqueued.
 */
class NewsNotificationWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    override fun doWork(): Result {
        val newArticles = checkForNewArticles()
        if (newArticles.isNotEmpty()) {
            showNotification(newArticles)
        }
        return Result.success()
    }

    private fun checkForNewArticles(): List<NewsItem> {
        val allFetchedArticles = Collections.synchronizedList(ArrayList<NewsItem>())
        val sources = RssParser.getDefaultSources().toMutableList()

        val prefs = applicationContext.getSharedPreferences("news_sources", Context.MODE_PRIVATE)
        val customUrls = prefs.getStringSet("custom_urls", HashSet()) ?: HashSet()
        val customNames = prefs.getStringSet("custom_names", HashSet()) ?: HashSet()
        val customCats = prefs.getStringSet("custom_categories", HashSet()) ?: HashSet()

        val urlList = ArrayList(customUrls)
        val nameList = ArrayList(customNames)
        val catList = ArrayList(customCats)

        for (i in urlList.indices) {
            val name = if (i < nameList.size) nameList[i] else "Custom"
            val cat = if (i < catList.size) catList[i] else "Custom"
            sources.add(NewsSource(name, urlList[i], cat))
        }

        val latch = CountDownLatch(sources.size)

        for (source in sources) {
            executor.execute {
                try {
                    val fetched = RssParser.fetchNews(source)
                    allFetchedArticles.addAll(fetched)
                } catch (_: Exception) {
                    // Log error but continue
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await(20, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        val notificationPrefs = applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
        val lastSeenTitles = notificationPrefs.getStringSet("last_titles", HashSet()) ?: HashSet()

        val newArticles = ArrayList<NewsItem>()
        val currentTitles = HashSet<String>()

        Collections.sort(allFetchedArticles) { a, b -> java.lang.Long.compare(b.timestamp, a.timestamp) }

        // Get latest 20 articles and compare with last seen
        for (i in 0 until minOf(20, allFetchedArticles.size)) {
            val item = allFetchedArticles[i]
            currentTitles.add(item.title)
            if (!lastSeenTitles.contains(item.title)) {
                newArticles.add(item)
            }
        }

        // Update last seen titles for next check
        notificationPrefs.edit().putStringSet("last_titles", currentTitles).apply()

        return newArticles
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.channel_name)
            val description = applicationContext.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            val notificationManager =
                applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showNotification(newArticles: List<NewsItem>) {
        if (newArticles.isEmpty()) return

        val title: String
        val content: String

        if (newArticles.size == 1) {
            title = applicationContext.getString(R.string.new_article_title)
            content = newArticles[0].title
        } else {
            title = applicationContext.getString(R.string.new_articles_count, newArticles.size)
            content = newArticles[0].title + (if (newArticles.size > 1) " and more..." else "")
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "news_channel"
        const val NOTIFICATION_ID = 1
    }
}
