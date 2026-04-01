package com.wellnesswingman.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeConverterTest {

    @Test
    fun `getUtcBoundsForLocalDay rolls over month end correctly`() {
        val date = LocalDate(2026, 3, 31)
        val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(date, TimeZone.UTC)

        assertEquals(LocalDate(2026, 3, 31), start.toLocalDateTime(TimeZone.UTC).date)
        assertEquals(LocalDate(2026, 4, 1), end.toLocalDateTime(TimeZone.UTC).date)
    }

    @Test
    fun `getUtcBoundsForLocalDay rolls over year end correctly`() {
        val date = LocalDate(2026, 12, 31)
        val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(date, TimeZone.UTC)

        assertEquals(LocalDate(2026, 12, 31), start.toLocalDateTime(TimeZone.UTC).date)
        assertEquals(LocalDate(2027, 1, 1), end.toLocalDateTime(TimeZone.UTC).date)
    }

    @Test
    fun `getUtcBoundsForLocalDay handles non-UTC timezone correctly`() {
        val date = LocalDate(2026, 3, 31)
        val timeZone = TimeZone.of("America/New_York")
        val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(date, timeZone)

        assertEquals(date, start.toLocalDateTime(timeZone).date)
        assertEquals(LocalDate(2026, 4, 1), end.toLocalDateTime(timeZone).date)
    }

    @Test
    fun `getUtcBoundsForLocalDay returns correct UTC instants for non-UTC timezone`() {
        // America/New_York is UTC-4 on 2026-03-31 (EDT).
        // Local midnight maps to 04:00 UTC, so the day bounds in UTC are
        // [2026-03-31T04:00:00Z, 2026-04-01T04:00:00Z).
        val date = LocalDate(2026, 3, 31)
        val timeZone = TimeZone.of("America/New_York")
        val (start, end) = DateTimeConverter.getUtcBoundsForLocalDay(date, timeZone)

        assertEquals(Instant.parse("2026-03-31T04:00:00Z"), start)
        assertEquals(Instant.parse("2026-04-01T04:00:00Z"), end)
    }
}
