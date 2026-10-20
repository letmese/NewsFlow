package com.newsaggregator.utils

import android.app.Activity
import android.content.Context
import com.newsaggregator.R

object ThemeUtils {
    private const val PREFS = "app_prefs"
    private const val KEY_THEME = "theme_mode" // 0=dark, 1=light

    fun isLight(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0) == 1
    }

    fun toggleTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentlyLight = prefs.getInt(KEY_THEME, 0) == 1
        prefs.edit().putInt(KEY_THEME, if (currentlyLight) 0 else 1).apply()
    }

    fun getThemeResId(context: Context): Int {
        return if (isLight(context)) R.style.AppThemeLight else R.style.AppTheme
    }

    fun applyTheme(activity: Activity) {
        activity.setTheme(getThemeResId(activity))
    }
}
