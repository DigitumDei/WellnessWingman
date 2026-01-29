package com.wellnesswingman.util

import kotlinx.datetime.*

/**
 * Utility functions for date/time operations.
 */
object DateTimeUtil {

    /**
     * Gets the start and end of a day in milliseconds since epoch.
     * Returns (startOfDay, endOfDay) where endOfDay is exclusive.
     */
    fun getDayBounds(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        val startOfDay = date.atStartOfDayIn(timeZone)
        val endOfDay = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        return Pair(startOfDay.toEpochMilliseconds(), endOfDay.toEpochMilliseconds())
    }

    /**
     * Gets the start and end of a week in milliseconds since epoch.
     */
    fun getWeekBounds(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        // Find the Monday of the week containing this date
        val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
        val daysToSubtract = dayOfWeek - 1
        val monday = date.minus(daysToSubtract, DateTimeUnit.DAY)

        val startOfWeek = monday.atStartOfDayIn(timeZone)
        val endOfWeek = monday.plus(7, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

        return Pair(startOfWeek.toEpochMilliseconds(), endOfWeek.toEpochMilliseconds())
    }

    /**
     * Gets the start and end of a month in milliseconds since epoch.
     */
    fun getMonthBounds(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        val firstDayOfMonth = LocalDate(date.year, date.month, 1)
        val firstDayOfNextMonth = if (date.monthNumber == 12) {
            LocalDate(date.year + 1, 1, 1)
        } else {
            LocalDate(date.year, date.monthNumber + 1, 1)
        }

        val startOfMonth = firstDayOfMonth.atStartOfDayIn(timeZone)
        val endOfMonth = firstDayOfNextMonth.atStartOfDayIn(timeZone)

        return Pair(startOfMonth.toEpochMilliseconds(), endOfMonth.toEpochMilliseconds())
    }

    /**
     * Converts an Instant to LocalDate in the specified timezone.
     */
    fun toLocalDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate {
        return instant.toLocalDateTime(timeZone).date
    }

    /**
     * Formats an Instant as a human-readable date/time string.
     */
    fun formatDateTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val localDateTime = instant.toLocalDateTime(timeZone)
        return "${localDateTime.date} ${localDateTime.time}"
    }

    /**
     * Formats an Instant as a time string (HH:mm).
     */
    fun formatTime(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val time = instant.toLocalDateTime(timeZone).time
        return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }

    /**
     * Formats a LocalDate as a string (YYYY-MM-DD).
     */
    fun formatDate(date: LocalDate): String {
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
    }
}
