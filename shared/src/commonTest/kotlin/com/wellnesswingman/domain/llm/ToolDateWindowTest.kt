package com.wellnesswingman.domain.llm

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.*

class ToolDateWindowTest {

    private val today = LocalDate(2026, 8, 8)

    private fun resolve(
        start: String? = null,
        end: String? = null,
        maxSpanDays: Int = 92,
        defaultSpanDays: Int = ToolDateWindows.DEFAULT_SPAN_DAYS
    ) = ToolDateWindows.resolve(start, end, today, maxSpanDays, defaultSpanDays)

    private fun window(
        start: String? = null,
        end: String? = null,
        maxSpanDays: Int = 92,
        defaultSpanDays: Int = ToolDateWindows.DEFAULT_SPAN_DAYS
    ): ToolDateWindow {
        val result = resolve(start, end, maxSpanDays, defaultSpanDays)
        assertIs<ToolDateWindowResult.Resolved>(result)
        return result.window
    }

    @Test
    fun `an explicit range is honoured exactly`() {
        val w = window("2026-07-20", "2026-07-26")

        assertEquals(LocalDate(2026, 7, 20), w.startDate)
        assertEquals(LocalDate(2026, 7, 26), w.endDate)
        assertEquals(7, w.days)
        assertFalse(w.clamped)
        assertFalse(w.swapped)
    }

    @Test
    fun `two weeks ago resolves to a real week in the past`() {
        // The case that motivated all of this: a focused period well outside "recent N entries".
        val w = window("2026-07-20", "2026-07-26")

        assertTrue(w.endDate < today)
        assertEquals(7, w.days)
    }

    @Test
    fun `no bounds defaults to the last week ending today`() {
        val w = window()

        assertEquals(today, w.endDate)
        assertEquals(LocalDate(2026, 8, 2), w.startDate)
        assertEquals(ToolDateWindows.DEFAULT_SPAN_DAYS, w.days)
    }

    @Test
    fun `start only runs from that date up to today`() {
        val w = window(start = "2026-08-01")

        assertEquals(LocalDate(2026, 8, 1), w.startDate)
        assertEquals(today, w.endDate)
    }

    @Test
    fun `end only walks back by the default span`() {
        val w = window(end = "2026-07-31", defaultSpanDays = 7)

        assertEquals(LocalDate(2026, 7, 25), w.startDate)
        assertEquals(LocalDate(2026, 7, 31), w.endDate)
    }

    @Test
    fun `a start in the future does not invert the range`() {
        val w = window(start = "2026-09-01")

        assertTrue(w.startDate <= w.endDate)
    }

    @Test
    fun `reversed bounds are exchanged and flagged`() {
        val w = window("2026-07-26", "2026-07-20")

        assertEquals(LocalDate(2026, 7, 20), w.startDate)
        assertEquals(LocalDate(2026, 7, 26), w.endDate)
        assertTrue(w.swapped, "The exchange must be visible to the caller")
    }

    @Test
    fun `an over-wide range is narrowed to the most recent span and flagged`() {
        val w = window("2026-01-01", "2026-08-08", maxSpanDays = 30)

        assertEquals(30, w.days)
        assertEquals(LocalDate(2026, 8, 8), w.endDate, "Clamping keeps the most recent end")
        assertTrue(w.clamped)
        assertEquals(220, w.requestedDays)
    }

    @Test
    fun `requestedDays is absent when nothing was clamped`() {
        val w = window("2026-08-01", "2026-08-07", maxSpanDays = 30)

        assertFalse(w.clamped)
        assertNull(w.requestedDays)
    }

    @Test
    fun `a range exactly at the cap is not clamped`() {
        val w = window("2026-07-10", "2026-08-08", maxSpanDays = 30)

        assertEquals(30, w.days)
        assertFalse(w.clamped)
    }

    @Test
    fun `a single day is a valid one-day window`() {
        val w = window("2026-07-25", "2026-07-25")

        assertEquals(1, w.days)
        assertEquals(listOf(LocalDate(2026, 7, 25)), w.eachDate())
    }

    @Test
    fun `unparseable dates are rejected rather than silently defaulted`() {
        // Defaulting a typo to "last 7 days" would answer a question the user never asked.
        assertIs<ToolDateWindowResult.Invalid>(resolve(start = "last tuesday"))
        assertIs<ToolDateWindowResult.Invalid>(resolve(end = "2026-13-45"))
        assertIs<ToolDateWindowResult.Invalid>(resolve(start = "07/20/2026"))
    }

    @Test
    fun `blank arguments are treated as absent, not invalid`() {
        val result = resolve(start = "", end = "  ")

        assertIs<ToolDateWindowResult.Resolved>(result)
        assertEquals(today, result.window.endDate)
    }

    @Test
    fun `a full timestamp is accepted and truncated to its date`() {
        val w = window("2026-07-20T08:30:00Z", "2026-07-26T21:00:00Z")

        assertEquals(LocalDate(2026, 7, 20), w.startDate)
        assertEquals(LocalDate(2026, 7, 26), w.endDate)
    }

    @Test
    fun `eachDate covers every day inclusive of both bounds`() {
        val dates = window("2026-07-20", "2026-07-23").eachDate()

        assertEquals(4, dates.size)
        assertEquals(LocalDate(2026, 7, 20), dates.first())
        assertEquals(LocalDate(2026, 7, 23), dates.last())
    }

    @Test
    fun `millisecond bounds are half-open so adjacent windows do not overlap`() {
        val zone = TimeZone.UTC
        val first = window("2026-07-20", "2026-07-21")
        val second = window("2026-07-22", "2026-07-23")

        // The entry queries use capturedAt >= start AND capturedAt < end.
        assertEquals(first.endMillisExclusive(zone), second.startMillis(zone))
    }

    @Test
    fun `endDateExclusive is the day after the inclusive end`() {
        val w = window("2026-07-20", "2026-07-26")

        assertEquals(LocalDate(2026, 7, 27), w.endDateExclusive)
    }

    @Test
    fun `windows resolve against the supplied timezone`() {
        val w = window("2026-07-20", "2026-07-20")

        val utcStart = w.startMillis(TimeZone.UTC)
        val sydneyStart = w.startMillis(TimeZone.of("Australia/Sydney"))

        assertNotEquals(utcStart, sydneyStart)
    }
}
