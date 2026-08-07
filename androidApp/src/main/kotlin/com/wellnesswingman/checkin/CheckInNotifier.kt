package com.wellnesswingman.checkin

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.wellnesswingman.data.model.CheckInSlot
import io.github.aakira.napier.Napier

/**
 * Posts the check-in notifications.
 *
 * Deliberately uses its own channel rather than the analysis one: that channel is silent by
 * design (it backs a foreground service), and a check-in nobody hears is a check-in nobody
 * answers. Keeping it separate also lets the user mute check-ins in system settings without
 * losing analysis progress notifications.
 */
class CheckInNotifier(private val context: Context) {

    fun notifyCheckIn(slot: CheckInSlot) {
        if (!hasNotificationPermission()) {
            // Nothing actionable here — the settings screen is responsible for telling the user
            // their check-ins cannot be delivered.
            Napier.w("POST_NOTIFICATIONS not granted; skipping ${slot.toStorageString()} check-in")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Napier.w("NotificationManager unavailable; skipping ${slot.toStorageString()} check-in")
            return
        }

        createChannel(notificationManager)

        val notification = buildNotification(slot)
        notificationManager.notify(notificationIdFor(slot), notification)

        Napier.i("Posted ${slot.toStorageString()} check-in notification")
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning and evening prompts asking how you slept and how the day went"
            setShowBadge(true)
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(slot: CheckInSlot): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setPriority(Notification.PRIORITY_DEFAULT)
        }

        val title = when (slot) {
            CheckInSlot.MORNING -> "Morning check-in"
            CheckInSlot.EVENING -> "Evening check-in"
        }
        val text = when (slot) {
            CheckInSlot.MORNING -> "How did you sleep? How do you feel?"
            CheckInSlot.EVENING -> "How did the day feel? Anything you didn't log?"
        }

        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(slot))
            .build()
    }

    /**
     * Deep-links into the check-in screen for this slot. Uses the same custom scheme as the
     * OAuth callback, and MainActivity is already `singleTask`, so this routes through
     * `onNewIntent` rather than creating a second activity instance.
     */
    private fun contentIntent(slot: CheckInSlot): PendingIntent {
        val path = when (slot) {
            CheckInSlot.MORNING -> "morning"
            CheckInSlot.EVENING -> "evening"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wellnesswingman://checkin/$path")).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getActivity(context, notificationIdFor(slot), intent, flags)
    }

    private fun notificationIdFor(slot: CheckInSlot): Int = when (slot) {
        CheckInSlot.MORNING -> NOTIFICATION_ID_MORNING
        CheckInSlot.EVENING -> NOTIFICATION_ID_EVENING
    }

    companion object {
        const val CHANNEL_ID = "checkin_channel"
        const val CHANNEL_NAME = "Daily check-ins"

        private const val NOTIFICATION_ID_MORNING = 3101
        private const val NOTIFICATION_ID_EVENING = 3102
    }
}
