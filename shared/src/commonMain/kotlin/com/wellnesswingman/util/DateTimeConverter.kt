package com.wellnesswingman.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue

/**
 * Provides helpers for normalizing timestamps between UTC and local timezones.
 */
object DateTimeConverter {

    /**
     * Converts the provided UTC instant into its original local representation
     * based on stored timezone metadata.
     *
     * Falls back to the local timezone if metadata is incomplete.
     */
    fun toOriginalLocal(
        utcTimestamp: Instant,
        timeZoneId: String?,
        offsetMinutes: Int?,
        fallbackTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): LocalDateTime {
        // Try to resolve by timezone ID first
        val tz = resolveTimeZone(timeZoneId)
        if (tz != null) {
            return utcTimestamp.toLocalDateTime(tz)
        }

        // Fall back to offset-based conversion
        if (offsetMinutes != null) {
            val offsetSeconds = offsetMinutes * 60L
            val localEpochSeconds = utcTimestamp.epochSeconds + offsetSeconds
            return Instant.fromEpochSeconds(localEpochSeconds).toLocalDateTime(TimeZone.UTC)
        }

        // Fall back to provided fallback timezone
        return utcTimestamp.toLocalDateTime(fallbackTimeZone)
    }

    /**
     * Resolves a TimeZone either by timezone ID.
     * Returns null if the timezone ID is invalid.
     */
    fun resolveTimeZone(timeZoneId: String?): TimeZone? {
        if (timeZoneId.isNullOrBlank()) {
            return null
        }

        return try {
            TimeZone.of(timeZoneId)
        } catch (e: Exception) {
            // Catch any exception (IllegalArgumentException, etc.) for invalid timezone IDs
            null
        }
    }

    /**
     * Returns the UTC bounds (start inclusive, end exclusive) for the local day
     * represented by the provided date.
     */
    fun getUtcBoundsForLocalDay(
        localDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Pair<Instant, Instant> {
        val utcStart = localDate.atStartOfDayIn(timeZone)
        val nextDay = LocalDate(localDate.year, localDate.monthNumber, localDate.dayOfMonth)
            .let { LocalDate(it.year, it.monthNumber, it.dayOfMonth + 1) }
        val utcEnd = nextDay.atStartOfDayIn(timeZone)
        return utcStart to utcEnd
    }

    /**
     * Returns the UTC offset in minutes for the provided timezone at the supplied instant.
     */
    fun getUtcOffsetMinutes(timeZone: TimeZone, utcTimestamp: Instant): Int {
        val localDateTime = utcTimestamp.toLocalDateTime(timeZone)
        val utcDateTime = utcTimestamp.toLocalDateTime(TimeZone.UTC)

        // Calculate the difference in minutes
        val localInstant = localDateTime.toInstant(TimeZone.UTC)
        val utcInstant = utcDateTime.toInstant(TimeZone.UTC)

        return ((localInstant.epochSeconds - utcInstant.epochSeconds) / 60).toInt()
    }

    /**
     * Captures the timezone identifier and offset metadata for a given UTC instant.
     */
    fun captureTimeZoneMetadata(
        utcTimestamp: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): TimeZoneMetadata {
        val offsetMinutes = getUtcOffsetMinutes(timeZone, utcTimestamp)
        return TimeZoneMetadata(
            timeZoneId = timeZone.id,
            offsetMinutes = offsetMinutes
        )
    }

    /**
     * Formats an offset in minutes into ±HH:mm format.
     */
    fun formatOffset(offsetMinutes: Int): String {
        val sign = if (offsetMinutes >= 0) "+" else "-"
        val absMinutes = offsetMinutes.absoluteValue
        val hours = absMinutes / 60
        val minutes = absMinutes % 60
        return "$sign${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}

/**
 * Holds timezone metadata captured at a specific instant.
 */
data class TimeZoneMetadata(
    val timeZoneId: String,
    val offsetMinutes: Int
)
