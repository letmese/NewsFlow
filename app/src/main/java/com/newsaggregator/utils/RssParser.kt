package com.newsaggregator.utils

import android.content.Context
import com.newsaggregator.models.NewsItem
import com.newsaggregator.models.NewsSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Hand-written RSS/Atom parser + OkHttp fetch. THE SOURCE OF TRUTH - never replace.
 */
object RssParser {

    private var okClient: OkHttpClient? = null

    fun initOkHttpClient(context: Context) {
        okClient = DnsHelper.buildFastClient(context)
    }

    private fun checkUrlExists(str: String): Boolean {
        return try {
            if (okClient != null) {
                val req = Request.Builder().url(str).head()
                    .header("User-Agent", "Mozilla/5.0").build()
                okClient!!.newCall(req).execute().use { resp ->
                    resp.code in 200..399
                }
            } else {
                val conn = URL(str).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                conn.disconnect()
                code in 200..399
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanText(str: String?): String {
        if (str == null) return ""
        return str.replace("<[^>]*>".toRegex(), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#\\d+;".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun discoverRssFromHtml(str: String): String? {
        return try {
            val html: String
            if (okClient != null) {
                val req = Request.Builder().url(str)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .build()
                okClient!!.newCall(req).execute().use { resp ->
                    if (resp.code != 200) return null
                    val body = resp.body ?: return null
                    var text = body.string()
                    if (text.length > 50000) text = text.substring(0, 50000)
                    html = text
                }
            } else {
                val conn = URL(str).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return null
                }
                val reader = BufferedReader(InputStreamReader(conn.getInputStream(), "UTF-8"))
                val sb = StringBuilder()
                val buf = CharArray(4096)
                var read: Int
                while (reader.read(buf).also { read = it } != -1) {
                    sb.append(buf, 0, read)
                    if (sb.length > 50000) break
                }
                reader.close()
                conn.disconnect()
                html = sb.toString()
            }
            val m1 = Pattern.compile(
                "<link[^>]+type\\s*=\\s*\"application/(rss|atom)\\+xml\"[^>]+href\\s*=\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE
            ).matcher(html)
            if (m1.find()) {
                val href = m1.group(2)!!
                if (!href.startsWith("/")) return href
                val url = URL(str)
                return url.protocol + "://" + url.host + href
            }
            val m2 = Pattern.compile(
                "<link[^>]+href\\s*=\\s*\"([^\"]+)\"[^>]+type\\s*=\\s*\"application/(rss|atom)\\+xml\"",
                Pattern.CASE_INSENSITIVE
            ).matcher(html)
            if (m2.find()) {
                val href2 = m2.group(1)!!
                if (!href2.startsWith("/")) return href2
                val url2 = URL(str)
                return url2.protocol + "://" + url2.host + href2
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractImageFromHtml(str: String?): String? {
        if (str == null) return null
        val m1 = Pattern.compile("srcset\\s*=\\s*\"(https?://[^\"]+?)\\s").matcher(str)
        if (m1.find()) return m1.group(1)
        val m2 = Pattern.compile("<img[^>]+src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(str)
        while (m2.find()) {
            val src = m2.group(1) ?: continue
            if (!src.contains("pixel") && !src.contains("tracking") && !src.contains("1x1") && !src.contains("icon") && !src.contains("logo")
                && (src.contains(".jpg") || src.contains(".jpeg") || src.contains(".png") || src.contains(".webp")
                    || src.contains("image") || src.contains("photo") || src.contains("wp-content") || src.contains("media"))
            ) {
                return src
            }
        }
        val m3 = Pattern.compile("(https?://[^\"'\\s<>]+\\.(?:jpg|jpeg|png|webp))", Pattern.CASE_INSENSITIVE).matcher(str)
        if (m3.find()) return m3.group(1)
        return null
    }

    fun fetchNews(newsSource: NewsSource): List<NewsItem> {
        val result = ArrayList<NewsItem>()
        try {
            val resolvedUrl = resolveRssUrl(newsSource.rssUrl)
            val inputStream: java.io.InputStream
            var charset = "UTF-8"

            if (okClient != null) {
                val req = Request.Builder().url(resolvedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .header("Accept-Charset", "UTF-8")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .build()
                val resp = okClient!!.newCall(req).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    return result
                }
                val body = resp.body
                if (body == null) {
                    resp.close()
                    return result
                }
                resp.header("Content-Type")?.let { ct ->
                    val m = Pattern.compile("charset\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE).matcher(ct)
                    if (m.find()) charset = m.group(1)!!.trim()
                }
                inputStream = body.byteStream()
            } else {
                val url = URL(resolvedUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                conn.instanceFollowRedirects = true
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return result
                }
                conn.contentType?.let { contentType ->
                    val m = Pattern.compile("charset\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE).matcher(contentType)
                    if (m.find()) charset = m.group(1)!!.trim()
                }
                inputStream = conn.inputStream
            }

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, charset)

            var inItem = false
            var title: String? = null
            var description: String? = null
            var link: String? = null
            var pubDate: String? = null
            var imageUrl: String? = null

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if ("item".equals(name, true) || "entry".equals(name, true)) {
                            inItem = true
                            title = null
                            description = null
                            link = null
                            pubDate = null
                            imageUrl = null
                        } else if (inItem) {
                            if ("title".equals(name, true)) {
                                title = readText(parser)
                            } else if ("description".equals(name, true) || "summary".equals(name, true)
                                || "content".equals(name, true) || "content:encoded".equals(name, true)
                            ) {
                                val text = readText(parser)
                                if (description == null || (text != null && text.length > description!!.length)) {
                                    description = text
                                }
                                if (imageUrl == null) {
                                    imageUrl = extractImageFromHtml(text)
                                }
                            } else if ("link".equals(name, true)) {
                                val href = parser.getAttributeValue(null, "href")
                                link = if (href != null) href else readText(parser)
                            } else if ("pubDate".equals(name, true) || "published".equals(name, true)
                                || "updated".equals(name, true) || "dc:date".equals(name, true)
                            ) {
                                pubDate = readText(parser)
                            } else if ("media:content".equals(name, true) || "media:thumbnail".equals(name, true)) {
                                val mediaUrl = parser.getAttributeValue(null, "url")
                                if (mediaUrl != null && imageUrl == null) {
                                    imageUrl = mediaUrl
                                }
                            } else if ("enclosure".equals(name, true)) {
                                val encUrl = parser.getAttributeValue(null, "url")
                                val encType = parser.getAttributeValue(null, "type")
                                if (encUrl != null && encType != null && encType.startsWith("image") && imageUrl == null) {
                                    imageUrl = encUrl
                                }
                            } else if ("guid".equals(name, true) && link == null) {
                                val guid = readText(parser)
                                if (guid != null && guid.startsWith("http")) {
                                    link = guid
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (("item".equals(name, true) || "entry".equals(name, true)) && inItem) {
                            if (title != null && title!!.trim().isNotEmpty()) {
                                val item = NewsItem()
                                item.title = cleanText(title).trim()
                                item.description =
                                    if (description != null && description!!.trim().isNotEmpty()) cleanText(description).trim() else ""
                                item.link = link?.trim() ?: ""
                                item.imageUrl = imageUrl ?: ""
                                item.pubDate = if (pubDate != null) formatDateTime(pubDate!!.trim()) else ""
                                item.timestamp = if (pubDate != null) parseTimestamp(pubDate!!.trim()) else 0
                                item.source = newsSource.name
                                item.category = newsSource.category
                                result.add(item)
                            }
                            inItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()

            Collections.sort(result) { a, b -> java.lang.Long.compare(b.timestamp, a.timestamp) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun formatDateTime(str: String): String {
        val ts = parseTimestamp(str)
        if (ts == 0L) {
            if (str.length > 16) {
                val parts = str.split("\\s+".toRegex()).toTypedArray()
                if (parts.size >= 4) {
                    return parts[1].replace("[^0-9]".toRegex(), "") + " " + parts[2] + " " +
                        (if (parts.size > 3) parts[3].substring(0, minOf(5, parts[3].length)) else "")
                }
            }
            return if (str.length > 20) str.substring(0, 20) else str
        }
        val diff = System.currentTimeMillis() - ts
        if (diff < 60000) return "Just now"
        if (diff < 3600000) return "${diff / 60000}m ago"
        if (diff < 86400000) return "${diff / 3600000}h ago"
        if (diff < 604800000) return "${diff / 86400000}d ago"
        return try {
            SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH).format(Date(ts))
        } catch (_: Exception) {
            str
        }
    }

    fun getDefaultSources(): List<NewsSource> {
        val list = ArrayList<NewsSource>()
        // Pakistan
        list.add(NewsSource("ProPakistani", "https://propakistani.pk/feed/", "Pakistan"))
        list.add(NewsSource("Dawn", "https://www.dawn.com/feeds/home", "Pakistan"))
        list.add(NewsSource("City42", "https://www.city42.tv/rss/latest", "Pakistan"))
        list.add(NewsSource("ARY Urdu", "https://urdu.arynews.tv/feed/", "Pakistan"))
        list.add(NewsSource("BBC Urdu", "https://feeds.bbci.co.uk/urdu/rss.xml", "Pakistan"))
        list.add(NewsSource("Bol News Urdu", "https://bolnews.com/urdu/feed/", "Pakistan"))
        list.add(NewsSource("Hum News Urdu", "https://humnews.pk/feed/", "Pakistan"))
        list.add(NewsSource("Abb Takk", "https://abbtakk.tv/feed/", "Pakistan"))
        // World
        list.add(NewsSource("BBC", "https://feeds.bbci.co.uk/news/rss.xml", "World"))
        list.add(NewsSource("RT", "https://www.rt.com/rss/news/", "World"))
        list.add(NewsSource("Anadolu Agency", "https://www.aa.com.tr/en/rss/default?cat=world", "World"))
        list.add(NewsSource("TRT World", "https://news.google.com/rss/search?q=site:trtworld.com&hl=en-US&gl=US&ceid=US:en", "World"))
        list.add(NewsSource("CNN", "https://news.google.com/rss/search?q=site:cnn.com&hl=en-US&gl=US&ceid=US:en", "World"))

        list.add(NewsSource("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "Middle East"))
        // China English
        list.add(NewsSource("CGTN", "https://www.cgtn.com/subscribe/rss/section/world.xml", "World"))
        list.add(NewsSource("Global Times", "https://www.globaltimes.cn/rss/outbrain.xml", "World"))
        // Business
        list.add(NewsSource("WSJ", "https://news.google.com/rss/search?q=site:wsj.com&hl=en-US&gl=US&ceid=US:en", "Business"))
        return list
    }

    private fun parseTimestamp(str: String?): Long {
        if (str == null || str.trim().isEmpty()) return 0
        val trim = str.trim()
        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm zzz", "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssz",
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd", "dd MMM yyyy HH:mm:ss", "dd MMM yyyy"
        )
        var best: Long = 0
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(trim)
                if (d != null && d.time > best) best = d.time
            } catch (_: Exception) {
            }
        }
        if (best == 0L) {
            val m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(trim)
            if (m.find()) {
                try {
                    val d2 = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(m.group(0))
                    if (d2 != null) best = d2.time
                } catch (_: Exception) {
                }
            }
        }
        return best
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        try {
            if (parser.next() == XmlPullParser.TEXT) {
                sb.append(parser.text)
                parser.nextTag()
            }
        } catch (_: Exception) {
        }
        return sb.toString()
    }

    fun resolveRssUrl(str: String): String {
        val trim = str.trim()
        if (trim.contains("/feed") || trim.contains("/rss") || trim.contains(".xml")
            || trim.contains("atom") || trim.contains("rss.xml")
            || trim.contains("feedburner") || trim.contains("feeds.")
        ) {
            return trim
        }
        discoverRssFromHtml(trim)?.let { return it }
        val suffixes = arrayOf(
            "/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml",
            "/feeds/posts/default", "/index.xml", "/feed/", "/rss/"
        )
        val base = if (trim.endsWith("/")) trim.substring(0, trim.length - 1) else trim
        for (suffix in suffixes) {
            val test = base + suffix
            if (checkUrlExists(test)) return test
        }
        return trim
    }
}
