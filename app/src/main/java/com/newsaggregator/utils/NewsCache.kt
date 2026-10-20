package com.newsaggregator.utils

import android.content.Context
import com.newsaggregator.models.NewsItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Caches news stories to SharedPreferences for instant startup.
 * No TTL: cache is loaded before network and only replaced on successful fetch.
 */
object NewsCache {

    private const val PREFS = "news_cache"
    private const val KEY_STORIES = "cached_stories"
    private const val KEY_TIMESTAMP = "cache_time"

    /** Save stories to cache */
    fun save(context: Context, stories: List<NewsItem>) {
        try {
            val arr = JSONArray()
            for (item in stories) {
                val obj = JSONObject()
                obj.put("title", item.title ?: "")
                obj.put("desc", item.description ?: "")
                obj.put("link", item.link ?: "")
                obj.put("source", item.source ?: "")
                obj.put("category", item.category ?: "")
                obj.put("pubDate", item.pubDate ?: "")
                obj.put("ts", item.timestamp)
                obj.put("img", item.imageUrl ?: "")
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_STORIES, arr.toString())
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Load cached stories (returns empty list if cache is missing) */
    fun load(context: Context): MutableList<NewsItem> {
        val result = ArrayList<NewsItem>()
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_STORIES, null) ?: return result
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    NewsItem(
                        title = obj.optString("title", ""),
                        description = obj.optString("desc", ""),
                        link = obj.optString("link", ""),
                        source = obj.optString("source", ""),
                        category = obj.optString("category", ""),
                        pubDate = obj.optString("pubDate", ""),
                        timestamp = obj.optLong("ts", 0),
                        imageUrl = obj.optString("img", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** Check if we have cached data */
    fun hasCache(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STORIES, null) != null
    }
}
