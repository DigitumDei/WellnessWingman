package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil

/** A check-in slot as the day screen should present it. */
data class DayCheckInSlot(
    val slot: CheckInSlot,
    /** The stored answer, when the user has already checked in. */
    val checkIn: DailyCheckIn?,
    /** True when this blank slot is for a past day rather than today. */
    val isBackfill: Boolean = false
) {
    val isAnswered: Boolean get() = checkIn != null

    /** A blank slot is an invitation to check in; an answered one is a record. */
    val isPrompt: Boolean get() = checkIn == null
}

/** Whether a slot is on, and when it is scheduled. */
data class CheckInSlotSetting(
    val enabled: Boolean,
    val timeOfDay: LocalTime?
)

/**
 * Decides which check-in slots the day screen shows for a given date.
 *
 * Answered check-ins always appear, on any date — they are part of that day's record.
 *
 * On today, a blank slot appears from an hour before its scheduled time onward. The lead time
 * exists so a check-in can be started early; it then stays for the rest of the day rather than
 * vanishing, so a dismissed notification does not leave the check-in unreachable from the
 * screen it belongs on.
 *
 * On recent past days a blank slot is also offered, so a check-in missed yesterday can still be
 * answered — realising in the morning that you skipped the evening before is the ordinary case,
 * not an edge one. The time-of-day rule does not apply there, since the day is already over.
 *
 * Backfill is bounded by [BACKFILL_DAYS]: recalling how you slept last month is not something
 * anyone can do usefully, and unanswered prompts stretching back forever would make every past
 * day read as incomplete.
 *
 * Future dates never get a blank slot.
 */
object DayCheckInPlanner {

    const val PROMPT_LEAD_MINUTES = 60

    /** How many days back a missed check-in can still be answered. */
    const val BACKFILL_DAYS = 7

    fun plan(
        date: LocalDate,
        today: LocalDate,
        now: LocalTime,
        settings: Map<CheckInSlot, CheckInSlotSetting>,
        checkIns: List<DailyCheckIn>
    ): List<DayCheckInSlot> {
        val answered = checkIns.associateBy { it.slot }

        // Morning before evening, regardless of when they were answered.
        return listOf(CheckInSlot.MORNING, CheckInSlot.EVENING).mapNotNull { slot ->
            val existing = answered[slot]
            if (existing != null) return@mapNotNull DayCheckInSlot(slot, existing)

            if (!isBackfillable(date, today)) return@mapNotNull null

            val setting = settings[slot] ?: return@mapNotNull null
            if (!setting.enabled) return@mapNotNull null

            if (date == today) {
                // Today is still in progress, so a slot only opens near its scheduled time.
                val scheduled = setting.timeOfDay ?: return@mapNotNull null
                if (!isPromptOpen(now, scheduled)) return@mapNotNull null
            }

            DayCheckInSlot(slot, checkIn = null, isBackfill = date != today)
        }
    }

    /**
     * Whether a blank slot may be offered for [date]: today, or within the backfill window.
     * Never the future — there is nothing to report on a day that has not happened.
     */
    fun isBackfillable(date: LocalDate, today: LocalDate): Boolean {
        if (date > today) return false

        val daysAgo = date.daysUntil(today)
        return daysAgo <= BACKFILL_DAYS
    }

    /**
     * True once the prompt window has opened for [scheduled] and for the remainder of the day.
     *
     * Compared in minutes-of-day so a lead time that would run before midnight simply opens at
     * the start of the day rather than wrapping into the previous one.
     */
    fun isPromptOpen(now: LocalTime, scheduled: LocalTime): Boolean {
        val nowMinutes = now.hour * 60 + now.minute
        val scheduledMinutes = scheduled.hour * 60 + scheduled.minute
        val opensAt = (scheduledMinutes - PROMPT_LEAD_MINUTES).coerceAtLeast(0)

        return nowMinutes >= opensAt
    }
}
