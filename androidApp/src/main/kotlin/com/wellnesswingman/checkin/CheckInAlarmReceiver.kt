package com.wellnesswingman.checkin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wellnesswingman.data.model.CheckInSlot
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fires when a check-in alarm goes off: posts the notification, then immediately arms the next
 * day's alarm.
 *
 * The re-arm is what keeps the schedule alive — these are one-shot alarms, not repeating ones,
 * so drift never accumulates and a changed timezone is picked up on the next cycle.
 */
class CheckInAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: CheckInScheduler by inject()
    private val notifier: CheckInNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK_IN) return

        val slot = CheckInSlot.fromString(intent.getStringExtra(EXTRA_SLOT))
        if (slot == null) {
            Napier.w("Check-in alarm fired without a recognisable slot; ignoring")
            return
        }

        Napier.i("Check-in alarm fired for ${slot.toStorageString()}")

        notifier.notifyCheckIn(slot)

        // Re-arm before returning. The calculator compares strictly against "now", so this
        // lands on tomorrow rather than re-firing the alarm we are currently handling.
        scheduler.schedule(slot)
    }

    companion object {
        const val ACTION_CHECK_IN = "com.wellnesswingman.action.CHECK_IN"
        const val EXTRA_SLOT = "slot"
    }
}
