package com.wellnesswingman.util

import kotlinx.datetime.*
import kotlin.test.*

class DateTimeUtilTest {

    @Test
    fun `formatDate returns correct format`() {
        val date = LocalDate(2024, 1, 15)
        val formatted = DateTimeUtil.formatDate(date)

        // The format should be like "Jan 15, 2024" or similar locale-dependent format
        assertTrue(formatted.contains("15"))
        assertTrue(formatted.contains("2024"))
    }

    @Test
    fun `formatDateTime returns correct format with timezone`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val formatted = DateTimeUtil.formatDateTime(instant, timezone)

        // Should contain date and time information
        assertTrue(formatted.contains("14") || formatted.contains("2")) // hour (24h or 12h format)
        assertTrue(formatted.contains("30")) // minutes
        assertTrue(formatted.contains("2024"))
    }

    @Test
    fun `formatTime returns correct format`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val formatted = DateTimeUtil.formatTime(instant, timezone)

        // Should contain time information
        assertTrue(formatted.contains("14") || formatted.contains("2")) // hour
        assertTrue(formatted.contains("30")) // minutes
    }

    @Test
    fun `startOfDay returns instant at midnight`() {
        val date = LocalDate(2024, 1, 15)
        val timezone = TimeZone.UTC
        val startOfDay = DateTimeUtil.startOfDay(date, timezone)

        val dateTime = startOfDay.toLocalDateTime(timezone)
        assertEquals(0, dateTime.hour)
        assertEquals(0, dateTime.minute)
        assertEquals(0, dateTime.second)
        assertEquals(date, dateTime.date)
    }

    @Test
    fun `endOfDay returns instant at end of day`() {
        val date = LocalDate(2024, 1, 15)
        val timezone = TimeZone.UTC
        val endOfDay = DateTimeUtil.endOfDay(date, timezone)

        val dateTime = endOfDay.toLocalDateTime(timezone)
        assertEquals(23, dateTime.hour)
        assertEquals(59, dateTime.minute)
        assertEquals(59, dateTime.second)
        assertEquals(date, dateTime.date)
    }

    @Test
    fun `toLocalDate extracts correct date from instant`() {
        val instant = Instant.parse("2024-01-15T14:30:00Z")
        val timezone = TimeZone.UTC
        val date = DateTimeUtil.toLocalDate(instant, timezone)

        assertEquals(LocalDate(2024, 1, 15), date)
    }

    @Test
    fun `isToday returns true for today's date`() {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(timezone).date

        assertTrue(DateTimeUtil.isToday(today, timezone))
    }

    @Test
    fun `isToday returns false for yesterday`() {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val yesterday = now.toLocalDateTime(timezone).date.minus(1, DateTimeUnit.DAY)

        assertFalse(DateTimeUtil.isToday(yesterday, timezone))
    }

    @Test
    fun `isToday returns false for tomorrow`() {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val tomorrow = now.toLocalDateTime(timezone).date.plus(1, DateTimeUnit.DAY)

        assertFalse(DateTimeUtil.isToday(tomorrow, timezone))
    }

    @Test
    fun `daysAgo calculates correct number of days`() {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(timezone).date
        val threeDaysAgo = today.minus(3, DateTimeUnit.DAY)

        assertEquals(3, DateTimeUtil.daysAgo(threeDaysAgo, timezone))
    }

    @Test
    fun `daysAgo returns 0 for today`() {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(timezone).date

        assertEquals(0, DateTimeUtil.daysAgo(today, timezone))
    }
}
