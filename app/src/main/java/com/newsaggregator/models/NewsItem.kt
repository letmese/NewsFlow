package com.newsaggregator.models

import java.io.Serializable

/** Single news story (newsflow v4.2.0 pure Kotlin port). */
data class NewsItem(
    var title: String = "",
    var description: String = "",
    var link: String = "",
    var source: String = "",
    var category: String = "",
    var pubDate: String = "",
    var timestamp: Long = 0L,
    var imageUrl: String = ""
) : Serializable
