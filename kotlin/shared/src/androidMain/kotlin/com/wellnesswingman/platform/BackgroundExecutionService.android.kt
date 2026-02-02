package com.wellnesswingman.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.wellnesswingman.service.AnalysisForegroundService
import io.github.aakira.napier.Napier

/**
 * Android implementation of BackgroundExecutionService.
 * Uses foreground service to ensure background tasks complete even when screen is locked.
 */
class AndroidBackgroundExecutionService(
    private val context: Context
) : BackgroundExecutionService {

    override fun startBackgroundTask(taskName: String) {
        // Check if we have POST_NOTIFICATIONS permission on Android 13+
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Only start foreground service if we have notification permission
        // Otherwise, fall back to best-effort background execution
        if (!hasNotificationPermission) {
            Napier.w("No notification permission; skipping foreground service for task $taskName")
            // Without notification permission, we can't use a foreground service safely
            // The task will still run, but may be killed if the app is backgrounded for too long
            return
        }

        val intent = Intent(context, AnalysisForegroundService::class.java).apply {
            putExtra(AnalysisForegroundService.EXTRA_TASK_NAME, taskName)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Napier.d("Started foreground service for task: $taskName")
        } catch (e: Exception) {
            Napier.e("Failed to start foreground service for task $taskName", e)
        }
    }

    override fun stopBackgroundTask(taskName: String) {
        // Decrement the task count; service will stop itself if count reaches 0
        AnalysisForegroundService.decrementTaskCount(context)
        Napier.d("Stopped background task: $taskName")
    }
}
