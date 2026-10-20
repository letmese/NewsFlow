package com.newsaggregator.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.newsaggregator.ArticleActivity
import com.newsaggregator.MainActivity
import com.newsaggregator.R
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import com.newsaggregator.utils.NewsCache
import com.newsaggregator.utils.RssParser
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Home-screen widget (RemoteViews — cannot be Compose). Android 16: do not touch. */
class NewsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        if (ACTION_REFRESH == action) {
            // Redraw instantly from cache, then fetch in background and redraw again.
            // goAsync() gives this broadcast ~10s of life for the fetch.
            val result = goAsync()
            val manager = AppWidgetManager.getInstance(context)
            val widget = ComponentName(context, NewsWidgetProvider::class.java)
            val appCtx = context.applicationContext

            updateAll(appCtx, manager, widget)

            FETCH_POOL.execute {
                try {
                    val fresh = fetchAllSourcesBounded()
                    if (fresh.isNotEmpty()) {
                        NewsCache.save(appCtx, fresh)
                    }
                } catch (_: Exception) {
                } finally {
                    updateAll(appCtx, manager, widget)
                    result.finish()
                }
            }
        } else if (ACTION_DATA_CHANGED == action) {
            // Cache was already refreshed by the app/worker — cheap redraw only
            val manager = AppWidgetManager.getInstance(context)
            updateAll(context, manager, ComponentName(context, NewsWidgetProvider::class.java))
        }
    }

    companion object {
        /** From widget refresh button: fetch fresh data in the background, then redraw */
        const val ACTION_REFRESH = "com.newsaggregator.WIDGET_REFRESH"

        /** From app/worker after they already refreshed the cache: just re-read + redraw */
        const val ACTION_DATA_CHANGED = "com.newsaggregator.WIDGET_DATA_CHANGED"

        private val FETCH_POOL: ExecutorService = Executors.newSingleThreadExecutor()

        private fun updateAll(context: Context, manager: AppWidgetManager, widget: ComponentName) {
            for (id in manager.getAppWidgetIds(widget)) {
                updateWidget(context, manager, id)
            }
        }

        /** Bounded background fetch of all sources (~8s worst case). Called OFF the main thread. */
        @JvmStatic
        fun fetchAllSourcesBounded(): List<NewsItem> {
            val all = ArrayList<NewsItem>()
            try {
                val sources = RssParser.getDefaultSources()
                val syncList = Collections.synchronizedList(ArrayList<NewsItem>())
                val pool = Executors.newFixedThreadPool(6)
                val latch = CountDownLatch(sources.size)
                for (src in sources) {
                    pool.execute {
                        try {
                            syncList.addAll(RssParser.fetchNews(src))
                        } catch (_: Exception) {
                        } finally {
                            latch.countDown()
                        }
                    }
                }
                latch.await(8, TimeUnit.SECONDS)
                pool.shutdownNow()
                all.addAll(syncList)
                Collections.sort(all) { a, b -> java.lang.Long.compare(b.timestamp, a.timestamp) }
            } catch (_: Exception) {
            }
            return all
        }

        @JvmStatic
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.news_widget)

            // Unread badge: cached stories not yet in read_urls
            try {
                val cached = NewsCache.load(context)
                val readPrefs: SharedPreferences = context.getSharedPreferences("read_articles", 0)
                val readUrls = readPrefs.getStringSet("read_urls", HashSet()) ?: HashSet()
                var unread = 0
                for (n in cached) {
                    if (!n.link.isNullOrEmpty() && !readUrls.contains(n.link)) unread++
                }
                views.setViewVisibility(
                    R.id.widget_unread,
                    if (unread > 0) View.VISIBLE else View.GONE
                )
                views.setTextViewText(R.id.widget_unread, "• $unread new")
            } catch (_: Exception) {
            }

            // List adapter
            val serviceIntent = Intent(context, NewsWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            serviceIntent.data = Uri.parse("widget://$widgetId")
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Tap on a story → open it in the reader (template + fill-in extras)
            val tapIntent = Intent(context, ArticleActivity::class.java)
            tapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val tapTemplate = PendingIntent.getActivity(
                context, 11, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, tapTemplate)

            // Header tap → open the app
            val openApp = Intent(context, MainActivity::class.java)
            openApp.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context, 12, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Refresh button → pure background broadcast (NO app launch)
            val refreshIntent = Intent(context, NewsWidgetProvider::class.java)
            refreshIntent.action = ACTION_REFRESH
            views.setOnClickPendingIntent(
                R.id.btn_widget_refresh,
                PendingIntent.getBroadcast(
                    context, 13, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }
    }
}
