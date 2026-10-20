package com.newsaggregator.ui.theme

import androidx.compose.ui.graphics.Color
import com.newsaggregator.ui.theme.StoryBlue
import com.newsaggregator.ui.theme.StoryGreen
import com.newsaggregator.ui.theme.StoryOrange
import com.newsaggregator.ui.theme.StoryPink
import com.newsaggregator.ui.theme.StoryPurple
import com.newsaggregator.ui.theme.StoryYellow

/** Category accent colors, ported from original NewsAdapter/ArticlePagerAdapter. */
fun categoryColor(category: String?): Color {
    return when (category?.lowercase()?.trim()) {
        "pakistan" -> StoryOrange
        "world" -> StoryPurple
        "business" -> StoryGreen
        "middle east" -> StoryBlue
        "tech" -> StoryBlue
        "sports" -> StoryYellow
        "china" -> StoryPink
        else -> Color(0xFF888888)
    }
}
