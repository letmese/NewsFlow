package com.newsaggregator.models

/** An RSS source. */
data class NewsSource(
    val name: String,
    val rssUrl: String,
    val category: String
)
