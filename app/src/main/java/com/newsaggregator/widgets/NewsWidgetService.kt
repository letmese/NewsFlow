package com.newsaggregator.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.newsaggregator.R
import com.newsaggregator.models.NewsItem
import com.newsaggregator.utils.NewsCache
import java.util.ArrayList
import java.util.Collections

/**
 * v4.1.6 fix: NEVER block the main thread (the Android 16 ANR that killed the widget).
 * Stories come from NewsCache only — instant, offline-safe, zero network here.
 * Freshness comes from MainActivity/BreakingNewsWorker refreshing the cache and
 * broadcasting WIDGET_REFRESH.
 */
class NewsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NewsWidgetFactory(applicationContext)
    }

    class NewsWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

        private var items: MutableList<NewsItem> = ArrayList()

        override fun onCreate() {
            loadData()
        }

        override fun onDataSetChanged() {
            loadData()
        }

        /** Cache-only: reads SharedPreferences JSON (~ms), sorts newest-first, caps at 8 */
        private fun loadData() {
            items.clear()
            try {
                val cached = NewsCache.load(context)
                Collections.sort(cached) { a, b -> java.lang.Long.compare(b.timestamp, a.timestamp) }
                val max = minOf(8, cached.size)
                items.addAll(cached.subList(0, max))
            } catch (_: Exception) {
            }
        }

        override fun onDestroy() {
            items.clear()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews? {
            if (position < 0 || position >= items.size) return null
            val item = items[position]

            val views = RemoteViews(context.packageName, R.layout.news_widget_item)
            views.setTextViewText(R.id.widget_source, item.source ?: "")
            views.setTextViewText(R.id.widget_title, item.title ?: "")

            // Fill-in extras merged into the provider's pending-intent template
            val fillIn = Intent()
            fillIn.putExtra("url", item.link)
            fillIn.putExtra("title", item.title)
            fillIn.putExtra("source", item.source)
            fillIn.putExtra("category", item.category)
            fillIn.putExtra("description", item.description)
            views.setOnClickFillInIntent(R.id.widget_item_root, fillIn)

            return views
        }

        override fun getLoadingView(): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.news_widget_item)
            v.setTextViewText(R.id.widget_source, "")
            v.setTextViewText(R.id.widget_title, "Loading…")
            return v
        }

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
