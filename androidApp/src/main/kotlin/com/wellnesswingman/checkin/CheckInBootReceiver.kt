package com.wellnesswingman.checkin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Re-arms check-in alarms after events that silently invalidate them.
 *
 * - `BOOT_COMPLETED`: alarms do not survive a reboot at all, so without this the check-ins
 *   simply stop until the app is next opened.
 * - `TIMEZONE_CHANGED`: "07:00" means 07:00 where the user actually is. An alarm set before
 *   travelling points at the wrong instant afterwards.
 * - `MY_PACKAGE_REPLACED`: an app update cancels pending alarms.
 */
class CheckInBootReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: CheckInScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Napier.i("Re-arming check-in alarms after ${intent.action}")
                scheduler.rescheduleAll()
            }
        }
    }
}
