package com.wellnesswingman.domain.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * An inclusive, validated range of local calendar days.
 *
 * Shared by every feature that selects data by local date (for example the
 * date-ranged health report and the date-filtered backup export). It is a
 * pure value object: it never reads the device time zone itself, so all
 * conversions are explicit and deterministic.
 */
data class DateRange private constructor(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(end >= start) {
            "DateRange end ($end) must not be before start ($start)"
        }
    }

    /**
     * The calendar day after [end], the exclusive upper bound of half-open
     * range queries. Crosses month, year, and leap-day boundaries correctly.
     */
    val endExclusive: LocalDate get() = end.plus(1, DateTimeUnit.DAY)

    /**
     * Converts this inclusive day range into half-open instant bounds
     * `[start-of-start-day, start-of-end-exclusive-day)` in the supplied
     * [timeZone].
     *
     * The time zone is an explicit parameter because the caller decides how
     * the range is interpreted; DST transitions legitimately produce 23- or
     * 25-hour spans.
     */
    fun toInstantBounds(timeZone: TimeZone): Pair<Instant, Instant> =
        start.atStartOfDayIn(timeZone) to endExclusive.atStartOfDayIn(timeZone)

    companion object {
        /**
         * Creates a range from an inclusive [start] to an inclusive [end].
         *
         * @throws IllegalArgumentException when [end] is before [start].
         */
        fun of(start: LocalDate, end: LocalDate): DateRange = DateRange(start, end)
    }
}
