package com.newsaggregator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Kotlin port of the original ArticlePagerAdapter extraction pipeline:
 * fetch HTML (8s timeouts, one manual redirect hop), extract the article
 * container, then JSON-LD articleBody -> meta description -> RSS description
 * fallbacks. Plus the "share article as image" bitmap renderer.
 */
object ArticleTextExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

    /** Fetches and extracts the article body; returns null on failure. */
    fun fetchArticleText(url: String?, rssDescription: String?): String? {
        if (url.isNullOrEmpty()) return null
        try {
            var conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }

            val code = conn.responseCode
            if (code == 301 || code == 302) {
                val location = conn.getHeaderField("Location")
                if (location != null) {
                    conn.disconnect()
                    conn = (URL(location).openConnection() as HttpURLConnection).apply {
                        setRequestProperty("User-Agent", USER_AGENT)
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                }
            }

            val sb = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
            }
            conn.disconnect()

            val html = sb.toString()
            var articleText = extractArticle(html)

            // Fallback 1: JSON-LD articleBody
            if (articleText == null || articleText.length < 200) {
                val jsonLd = extractJsonLdBody(html)
                if (jsonLd != null && jsonLd.length > 100) articleText = jsonLd
            }
            // Fallback 2: meta description
            if (articleText == null || articleText.length < 200) {
                val meta = extractMetaDescription(html)
                if (meta != null && meta.length > 50) articleText = meta
            }
            // Fallback 3: RSS description/content
            if (articleText == null || articleText.length < 200) {
                if (!rssDescription.isNullOrEmpty()) {
                    val cleaned = stripHtml(rssDescription).trim()
                    if (cleaned.length > 50) articleText = cleaned
                }
            }
            return articleText
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractJsonLdBody(html: String): String? {
        try {
            val m = Pattern.compile(
                "(?is)<script[^>]*type\\s*=\\s*\"application/ld\\+json\"[^>]*>(.*?)</script>"
            ).matcher(html)
            while (m.find()) {
                val json = m.group(1)!!.trim()
                if (json.contains("articleBody")) {
                    val bm = Pattern.compile("\"articleBody\"\\s*:\\s*\"(.*?)\"").matcher(json)
                    if (bm.find()) {
                        val body = bm.group(1)!!
                        if (body.length > 100) return body
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun extractMetaDescription(html: String): String? {
        try {
            var m = Pattern.compile(
                "(?i)<meta[^>]*(?:property\\s*=\\s*\"og:description\"|name\\s*=\\s*\"description\")\\s[^>]*content\\s*=\\s*\"([^\"]+)\""
            ).matcher(html)
            if (!m.find()) {
                m = Pattern.compile(
                    "(?i)<meta[^>]*content\\s*=\\s*\"([^\"]+)\"[^>]*(?:property\\s*=\\s*\"og:description\"|name\\s*=\\s*\"description\")"
                ).matcher(html)
            }
            if (m.find()) {
                val desc = m.group(1)!!.trim()
                if (desc.length > 50) return desc
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun extractArticle(html: String): String? {
        val cleaned = html.replace(
            Regex("(?is)<(script|style|nav|header|footer|aside|form|noscript)[^>]*>.*?</\\1>"), ""
        )

        var article = extractFromContainer(cleaned, "article")
        if (article == null || article.length < 200) {
            article = extractFromContainer(
                cleaned, "div[^>]*class=\"[^\"]*(?:article|content|story|post|entry|body)[^\"]*\""
            )
        }
        if (article == null || article.length < 200) {
            article = extractFromContainer(cleaned, "main")
        }
        if (article == null || article.length < 200) {
            article = cleaned
        }

        val paragraphs = ArrayList<String>()
        val pm = Pattern.compile("(?is)<p[^>]*>(.*?)</p>").matcher(article)
        while (pm.find()) {
            val p = stripHtml(pm.group(1)!!).trim()
            if (p.length > 30 && !isBoilerplate(p)) {
                paragraphs.add(p)
            }
        }

        val result = StringBuilder()
        for (p in paragraphs) {
            result.append(p).append("\n\n")
        }

        var text = result.toString().trim()

        if (text.length < 300) {
            val bm = Pattern.compile("(?is)<(blockquote|li)[^>]*>(.*?)</\\1>").matcher(article)
            while (bm.find()) {
                val b = stripHtml(bm.group(2)!!).trim()
                if (b.length > 20) {
                    text += "\n\n" + b
                }
            }
        }

        return text
    }

    private fun extractFromContainer(html: String, tagPattern: String): String? {
        val closing = tagPattern.split("\\[")[0]
        val m = Pattern.compile("(?is)<" + tagPattern + "[^>]*>(.*?)</" + closing + ">").matcher(html)
        if (m.find()) {
            return m.group(1)
        }
        return null
    }

    fun stripHtml(html: String): String {
        return html.replace("<[^>]*>".toRegex(), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "\u2014")
            .replace("&ndash;", "\u2013")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            .replace("&hellip;", "\u2026")
            .replace("&#\\d+;".toRegex(), "")
            .replace("&[a-zA-Z]+;".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("cookie") && lower.contains("accept")) ||
                (lower.contains("subscribe") && lower.contains("newsletter")) ||
                (lower.contains("sign up") && lower.contains("email")) ||
                (lower.contains("click here") && lower.contains("read more")) ||
                lower.contains("advertisement") ||
                lower.startsWith("©") ||
                lower.startsWith("all rights reserved") ||
                lower.contains("loading") ||
                lower.contains("javascript")
    }

    /** Renders the article card as a shareable PNG; returns the file or null. */
    fun shareAsImage(context: Context, title: String?, body: String?, source: String?): File? {
        return try {
            val width = 1080
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F0F0F5")
                textSize = 48f
                typeface = Typeface.DEFAULT_BOLD
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C8C8D0")
                textSize = 32f
                typeface = Typeface.DEFAULT
            }
            val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8C8C99")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Word wrap
            val titleLines = wrapText(title ?: "", titlePaint, width - 80)
            var firstPara = if (body?.contains("\n\n") == true)
                body.substring(0, body.indexOf("\n\n")) else (body ?: "")
            if (firstPara.length > 300) firstPara = firstPara.substring(0, 300) + "…"
            val bodyLines = wrapText(firstPara, bodyPaint, width - 80)

            var height = 60 + titleLines.size * 60 + 30 + bodyLines.size * 44 + 60 + 40
            height = maxOf(height, 400)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            canvas.drawColor(Color.parseColor("#050510"))

            // Accent line
            val accentPaint = Paint()
            accentPaint.color = Color.parseColor("#FF6B4A")
            canvas.drawRect(40f, 40f, 80f, (40 + titleLines.size * 60 + 20 + bodyLines.size * 44).toFloat(), accentPaint)

            // Source
            canvas.drawText(source?.uppercase() ?: "", 100f, 70f, sourcePaint)

            // Title
            var y = 110f
            for (line in titleLines) {
                canvas.drawText(line, 100f, y, titlePaint)
                y += 60f
            }

            y += 10f

            // Body
            for (line in bodyLines) {
                canvas.drawText(line, 100f, y, bodyPaint)
                y += 44f
            }

            // Watermark
            val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#52FFFFFF")
                textSize = 22f
            }
            canvas.drawText("NewsFlow", (width - 180).toFloat(), (height - 20).toFloat(), watermark)

            // Save
            val file = File(context.getExternalCacheDir(), "newsflow_share.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val lines = ArrayList<String>()
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val test = if (line.isEmpty()) word else line.toString() + " " + word
            if (paint.measureText(test) > maxWidth) {
                if (line.isNotEmpty()) lines.add(line.toString())
                line = StringBuilder(word)
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }
}
