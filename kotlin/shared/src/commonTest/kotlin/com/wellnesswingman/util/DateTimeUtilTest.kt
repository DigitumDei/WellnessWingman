package com.wellnesswingman.util

import kotlinx.datetime.*
import kotlin.test.*

class DateTimeUtilTest {

    @Test
    fun `formatDate returns correct format`() {
        val date = LocalDate(2024, 1, 15)
        val formatted = DateTimeUtil.formatDate(date)

        assertEquals("2024-01-15", formatted)
    }

    @Test
    fun `formatDateTime returns correct format`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val formatted = DateTimeUtil.formatDateTime(instant, timezone)

        // Should contain date and time information
        assertTrue(formatted.contains("2024-01-15"))
        assertTrue(formatted.contains("14:30"))
    }

    @Test
    fun `formatTime returns correct format`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val formatted = DateTimeUtil.formatTime(instant, timezone)

        assertEquals("14:30", formatted)
    }

    @Test
    fun `getDayBounds returns correct milliseconds`() {
        val date = LocalDate(2024, 1, 15)
        val timezone = TimeZone.UTC
        val (start, end) = DateTimeUtil.getDayBounds(date, timezone)

        val startInstant = Instant.fromEpochMilliseconds(start)
        val endInstant = Instant.fromEpochMilliseconds(end)

        val startDate = startInstant.toLocalDateTime(timezone)
        val endDate = endInstant.toLocalDateTime(timezone)

        assertEquals(0, startDate.hour)
        assertEquals(0, startDate.minute)
        assertEquals(date, startDate.date)
        assertEquals(date.plus(1, DateTimeUnit.DAY), endDate.date)
    }

    @Test
    fun `getWeekBounds returns Monday to Monday`() {
        // Jan 15, 2024 is a Monday
        val monday = LocalDate(2024, 1, 15)
        val timezone = TimeZone.UTC
        val (start, end) = DateTimeUtil.getWeekBounds(monday, timezone)

        val startInstant = Instant.fromEpochMilliseconds(start)
        val endInstant = Instant.fromEpochMilliseconds(end)

        val startDate = startInstant.toLocalDateTime(timezone).date
        val endDate = endInstant.toLocalDateTime(timezone).date

        assertEquals(monday, startDate)
        assertEquals(monday.plus(7, DateTimeUnit.DAY), endDate)
    }

    @Test
    fun `getWeekBounds from Wednesday returns previous Monday`() {
        // Jan 17, 2024 is a Wednesday
        val wednesday = LocalDate(2024, 1, 17)
        val timezone = TimeZone.UTC
        val (start, _) = DateTimeUtil.getWeekBounds(wednesday, timezone)

        val startInstant = Instant.fromEpochMilliseconds(start)
        val startDate = startInstant.toLocalDateTime(timezone).date

        // Should be Monday Jan 15
        assertEquals(LocalDate(2024, 1, 15), startDate)
        assertEquals(DayOfWeek.MONDAY, startDate.dayOfWeek)
    }

    @Test
    fun `getMonthBounds returns first day of month to first day of next month`() {
        val date = LocalDate(2024, 1, 15)
        val timezone = TimeZone.UTC
        val (start, end) = DateTimeUtil.getMonthBounds(date, timezone)

        val startInstant = Instant.fromEpochMilliseconds(start)
        val endInstant = Instant.fromEpochMilliseconds(end)

        val startDate = startInstant.toLocalDateTime(timezone).date
        val endDate = endInstant.toLocalDateTime(timezone).date

        assertEquals(LocalDate(2024, 1, 1), startDate)
        assertEquals(LocalDate(2024, 2, 1), endDate)
    }

    @Test
    fun `getMonthBounds handles December correctly`() {
        val date = LocalDate(2024, 12, 15)
        val timezone = TimeZone.UTC
        val (start, end) = DateTimeUtil.getMonthBounds(date, timezone)

        val startInstant = Instant.fromEpochMilliseconds(start)
        val endInstant = Instant.fromEpochMilliseconds(end)

        val startDate = startInstant.toLocalDateTime(timezone).date
        val endDate = endInstant.toLocalDateTime(timezone).date

        assertEquals(LocalDate(2024, 12, 1), startDate)
        assertEquals(LocalDate(2025, 1, 1), endDate)
    }

    @Test
    fun `toLocalDate extracts correct date from instant`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val date = DateTimeUtil.toLocalDate(instant, timezone)

        assertEquals(LocalDate(2024, 1, 15), date)
    }

    @Test
    fun `toLocalDate handles timezone correctly`() {
        // 11 PM UTC on Jan 15 is Jan 16 in +05:00 timezone
        val instant = Instant.parse("2024-01-15T23:00:00Z")
        val utcDate = DateTimeUtil.toLocalDate(instant, TimeZone.UTC)

        assertEquals(LocalDate(2024, 1, 15), utcDate)
    }
}
