package com.wellnesswingman.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.aakira.napier.Napier

/**
 * Android foreground service that ensures LLM analysis completes even when
 * the screen is locked or the app is backgrounded.
 * Shows a persistent notification while analysis is running.
 */
class AnalysisForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        // Not a bound service
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(lock) {
            activeTaskCount++
            Napier.d("Foreground service started. Active tasks: $activeTaskCount")

            // Create notification channel (required for Android 8+)
            createNotificationChannel()

            // Build and display notification
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification)
        }

        // Return Sticky so service is restarted if killed by system
        return START_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) {
            activeTaskCount--
            Napier.d("Foreground service destroyed. Active tasks: $activeTaskCount")

            if (activeTaskCount <= 0) {
                activeTaskCount = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
        }

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low = no sound/vibration
            ).apply {
                description = "Shows when analyzing meal photos"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Create intent to open app when notification is tapped
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, pendingIntentFlags)
        }

        val taskCountText = if (activeTaskCount == 1) {
            "Processing 1 entry..."
        } else {
            "Processing $activeTaskCount entries..."
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setPriority(Notification.PRIORITY_LOW)
        }

        builder
            .setContentTitle("Analyzing your entries")
            .setContentText(taskCountText)
            .setSmallIcon(android.R.drawable.ic_menu_upload) // Using system icon for now
            .setOngoing(true) // Makes it persistent

        pendingIntent?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "analysis_channel"
        const val CHANNEL_NAME = "Analysis"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TASK_NAME = "taskName"

        private val lock = Any()
        private var activeTaskCount = 0

        /**
         * Decrements the task count and stops the service if no tasks remain.
         * Called by AndroidBackgroundExecutionService when a task completes.
         */
        fun decrementTaskCount(context: Context) {
            synchronized(lock) {
                activeTaskCount--
                Napier.d("Task decremented. Active tasks: $activeTaskCount")

                if (activeTaskCount <= 0) {
                    activeTaskCount = 0
                    val intent = Intent(context, AnalysisForegroundService::class.java)
                    context.stopService(intent)
                    Napier.d("Stopping foreground service - no active tasks")
                }
            }
        }
    }
}
