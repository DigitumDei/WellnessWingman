package com.wellnesswingman.domain.llm

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * A resolved, inclusive date range for a tool call, together with what had to be adjusted to
 * produce it.
 *
 * The adjustment flags exist so the tool result can tell the model what actually happened. A
 * window that was silently clamped looks identical to one that was honoured, which is how a
 * model ends up confidently reporting "that's everything you ate" over a truncated range.
 */
data class ToolDateWindow(
    val startDate: LocalDate,
    /** Inclusive. */
    val endDate: LocalDate,
    /** True when the caller supplied start after end and the two were exchanged. */
    val swapped: Boolean = false,
    /** True when the requested span exceeded the tool's maximum and was narrowed. */
    val clamped: Boolean = false,
    /** The span originally asked for, when [clamped]. */
    val requestedDays: Int? = null
) {
    /** Inclusive day count. */
    val days: Int get() = startDate.daysUntil(endDate) + 1

    fun startMillis(timeZone: TimeZone): Long =
        startDate.atStartOfDayIn(timeZone).toEpochMilliseconds()

    /** Exclusive upper bound, matching the `capturedAt < ?` form of the entry queries. */
    fun endMillisExclusive(timeZone: TimeZone): Long =
        endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()

    fun startInstant(timeZone: TimeZone): Instant = startDate.atStartOfDayIn(timeZone)

    fun endInstantExclusive(timeZone: TimeZone): Instant =
        endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

    /** Exclusive end date, for APIs such as PolarInsightService.getDayContexts. */
    val endDateExclusive: LocalDate get() = endDate.plus(1, DateTimeUnit.DAY)

    fun eachDate(): List<LocalDate> = (0 until days).map { startDate.plus(it, DateTimeUnit.DAY) }
}

sealed interface ToolDateWindowResult {
    data class Resolved(val window: ToolDateWindow) : ToolDateWindowResult

    /** The caller supplied something unparseable; the message is returned to the model. */
    data class Invalid(val message: String) : ToolDateWindowResult
}

/**
 * Resolves the `startDate` / `endDate` arguments shared by the date-ranged tools.
 *
 * Every ranged tool routes through here so the defaulting, clamping and reporting behave
 * identically across tools — the model only has to learn the convention once.
 */
object ToolDateWindows {

    /** Used when the caller gives neither bound. */
    const val DEFAULT_SPAN_DAYS = 7

    fun parseDate(raw: String?): LocalDate? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Tolerate a full timestamp; the model sometimes echoes back a capturedAt value.
        return runCatching { LocalDate.parse(value.substringBefore('T')) }.getOrNull()
    }

    /**
     * @param maxSpanDays the widest window this tool will serve. Exceeding it narrows to the
     *   most recent [maxSpanDays] of the requested range rather than failing, so a broad
     *   question still gets an answer — flagged as clamped.
     */
    fun resolve(
        startRaw: String?,
        endRaw: String?,
        today: LocalDate,
        maxSpanDays: Int,
        defaultSpanDays: Int = DEFAULT_SPAN_DAYS
    ): ToolDateWindowResult {
        if (startRaw != null && startRaw.isNotBlank() && parseDate(startRaw) == null) {
            return ToolDateWindowResult.Invalid(
                "startDate '$startRaw' is not a valid date. Use YYYY-MM-DD."
            )
        }
        if (endRaw != null && endRaw.isNotBlank() && parseDate(endRaw) == null) {
            return ToolDateWindowResult.Invalid(
                "endDate '$endRaw' is not a valid date. Use YYYY-MM-DD."
            )
        }

        val requestedStart = parseDate(startRaw)
        val requestedEnd = parseDate(endRaw)

        var start: LocalDate
        var end: LocalDate

        when {
            requestedStart != null && requestedEnd != null -> {
                start = requestedStart
                end = requestedEnd
            }
            requestedStart != null -> {
                start = requestedStart
                end = today
                // An open-ended range starting in the future would otherwise invert.
                if (end < start) end = start
            }
            requestedEnd != null -> {
                end = requestedEnd
                start = end.plus(-(defaultSpanDays - 1).toLong(), DateTimeUnit.DAY)
            }
            else -> {
                end = today
                start = today.plus(-(defaultSpanDays - 1).toLong(), DateTimeUnit.DAY)
            }
        }

        // Exchanging rather than failing: the intent is unambiguous, and the flag keeps it visible.
        var swapped = false
        if (start > end) {
            val original = start
            start = end
            end = original
            swapped = true
        }

        val requestedDays = start.daysUntil(end) + 1
        var clamped = false
        if (requestedDays > maxSpanDays) {
            start = end.plus(-(maxSpanDays - 1).toLong(), DateTimeUnit.DAY)
            clamped = true
        }

        return ToolDateWindowResult.Resolved(
            ToolDateWindow(
                startDate = start,
                endDate = end,
                swapped = swapped,
                clamped = clamped,
                requestedDays = if (clamped) requestedDays else null
            )
        )
    }
}
