package com.wellnesswingman.domain.checkin

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Works out when a check-in should next fire.
 *
 * Deliberately platform-neutral and free of Android types so the scheduling arithmetic — which
 * is where day boundaries, timezone changes and already-passed times actually go wrong — can be
 * tested directly rather than only on a device.
 */
object CheckInScheduleCalculator {

    /**
     * Parses an "HH:mm" 24-hour preference string.
     *
     * Returns null rather than throwing: the value comes from stored settings, and a corrupt
     * preference should disable a check-in, not crash the app on launch.
     */
    fun parseTimeOfDay(value: String): LocalTime? {
        val parts = value.split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null

        return LocalTime(hour, minute)
    }

    /**
     * Returns the next instant at which [timeOfDay] occurs in [timeZone], strictly after [now].
     *
     * If today's occurrence has already passed, this returns tomorrow's. The comparison is
     * strict, so re-arming immediately after an alarm fires moves to the next day rather than
     * re-firing the same one.
     */
    fun nextOccurrence(
        now: Instant,
        timeOfDay: LocalTime,
        timeZone: TimeZone
    ): Instant {
        val today = now.toLocalDateTime(timeZone).date
        val todaysOccurrence = today.atTime(timeOfDay).toInstant(timeZone)

        if (todaysOccurrence > now) {
            return todaysOccurrence
        }

        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return tomorrow.atTime(timeOfDay).toInstant(timeZone)
    }

    /**
     * Convenience overload taking the raw "HH:mm" setting. Returns null when the setting cannot
     * be parsed.
     */
    fun nextOccurrence(
        now: Instant,
        timeOfDaySetting: String,
        timeZone: TimeZone
    ): Instant? {
        val timeOfDay = parseTimeOfDay(timeOfDaySetting) ?: return null
        return nextOccurrence(now, timeOfDay, timeZone)
    }

    /**
     * Resolves the next occurrence against the system clock and the device's current timezone,
     * as epoch milliseconds.
     *
     * Exists so platform schedulers — which deal in epoch millis anyway — need no kotlinx-datetime
     * dependency of their own. The testable arithmetic stays in the pure overloads above; this is
     * only the impure edge that reads the clock.
     */
    /**
     * Today's local date as `YYYY-MM-DD`.
     *
     * Exists for the same reason as [nextOccurrenceEpochMillis]: platform code stamps the day a
     * notification is raised for, and does so without needing a kotlinx-datetime dependency.
     */
    fun todayIsoDate(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    fun nextOccurrenceEpochMillis(timeOfDaySetting: String): Long? {
        return nextOccurrence(
            now = Clock.System.now(),
            timeOfDaySetting = timeOfDaySetting,
            timeZone = TimeZone.currentSystemDefault()
        )?.toEpochMilliseconds()
    }
}
