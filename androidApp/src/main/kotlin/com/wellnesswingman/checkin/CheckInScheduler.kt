package com.wellnesswingman.checkin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.domain.checkin.CheckInScheduleCalculator
import com.wellnesswingman.domain.checkin.CheckInScheduling
import io.github.aakira.napier.Napier

/**
 * Arms the morning and evening check-in alarms.
 *
 * Uses [AlarmManager.setAndAllowWhileIdle] rather than WorkManager or an exact alarm:
 *
 * - WorkManager makes no wall-clock promise. That is fine for a 12-hour Polar sync but wrong
 *   for a 7am check-in, which under Doze could arrive hours late.
 * - Exact alarms need SCHEDULE_EXACT_ALARM, and Play policy does not auto-grant the Android 13+
 *   USE_EXACT_ALARM to a wellness app, so exactness would cost a trip to system settings.
 *
 * `setAndAllowWhileIdle` pierces Doze without any special permission and typically lands within
 * about ten minutes, which is the right precision for a check-in rather than an alarm clock.
 *
 * Each alarm is one-shot and re-armed by [CheckInAlarmReceiver] after it fires, so there is no
 * repeating alarm whose drift would need correcting.
 */
class CheckInScheduler(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val notifier: CheckInNotifier
) : CheckInScheduling {

    /**
     * Re-arms both slots from current settings. Safe to call repeatedly: each slot's pending
     * intent is a stable, updating one, so re-arming replaces rather than stacks alarms.
     */
    override fun rescheduleAll() {
        schedule(CheckInSlot.MORNING)
        schedule(CheckInSlot.EVENING)
    }

    override fun canDeliverNotifications(): Boolean = notifier.hasNotificationPermission()

    fun schedule(slot: CheckInSlot) {
        val enabled = when (slot) {
            CheckInSlot.MORNING -> appSettingsRepository.isMorningCheckInEnabled()
            CheckInSlot.EVENING -> appSettingsRepository.isEveningCheckInEnabled()
        }

        if (!enabled) {
            cancel(slot)
            return
        }

        val timeSetting = when (slot) {
            CheckInSlot.MORNING -> appSettingsRepository.getMorningCheckInTime()
            CheckInSlot.EVENING -> appSettingsRepository.getEveningCheckInTime()
        }

        val nextOccurrenceMillis = CheckInScheduleCalculator.nextOccurrenceEpochMillis(timeSetting)

        if (nextOccurrenceMillis == null) {
            // A corrupt stored preference should disable the check-in, not crash on launch.
            Napier.w("Unparseable ${slot.toStorageString()} check-in time '$timeSetting'; not scheduling")
            cancel(slot)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager == null) {
            Napier.w("AlarmManager unavailable; cannot schedule ${slot.toStorageString()} check-in")
            return
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextOccurrenceMillis,
            pendingIntent(slot, cancelOnly = false)!!
        )

        Napier.i("Scheduled ${slot.toStorageString()} check-in for epochMillis=$nextOccurrenceMillis")
    }

    fun cancel(slot: CheckInSlot) {
        val existing = pendingIntent(slot, cancelOnly = true) ?: return

        context.getSystemService(AlarmManager::class.java)?.cancel(existing)
        existing.cancel()

        Napier.i("Cancelled ${slot.toStorageString()} check-in alarm")
    }

    /**
     * @param cancelOnly when true, returns null instead of creating an alarm that does not
     *   already exist. Avoids registering a pending intent purely in order to cancel it.
     */
    private fun pendingIntent(slot: CheckInSlot, cancelOnly: Boolean): PendingIntent? {
        val intent = Intent(context, CheckInAlarmReceiver::class.java).apply {
            action = CheckInAlarmReceiver.ACTION_CHECK_IN
            putExtra(CheckInAlarmReceiver.EXTRA_SLOT, slot.toStorageString())
        }

        var flags = if (cancelOnly) {
            PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getBroadcast(context, requestCodeFor(slot), intent, flags)
    }

    private fun requestCodeFor(slot: CheckInSlot): Int = when (slot) {
        CheckInSlot.MORNING -> REQUEST_CODE_MORNING
        CheckInSlot.EVENING -> REQUEST_CODE_EVENING
    }

    companion object {
        private const val REQUEST_CODE_MORNING = 3001
        private const val REQUEST_CODE_EVENING = 3002
    }
}
