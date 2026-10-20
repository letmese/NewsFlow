package com.newsaggregator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.newsaggregator.utils.BreakingNewsWorker
import com.newsaggregator.utils.RssParser
import java.util.concurrent.TimeUnit

class NewsFlowApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        RssParser.initOkHttpClient(this)
        scheduleNotifications()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Breaking News",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notifications for new articles"
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun scheduleNotifications() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        val intervalMinutes = prefs.getInt("notification_interval", 120)

        val workManager = WorkManager.getInstance(this)

        if (enabled) {
            val request = PeriodicWorkRequest.Builder(
                BreakingNewsWorker::class.java, intervalMinutes.toLong(), TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val CHANNEL_ID = "breaking_news"
        private const val WORK_NAME = "breaking_news_check"
    }
}
