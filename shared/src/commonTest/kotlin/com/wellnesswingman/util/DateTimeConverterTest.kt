package com.wellnesswingman.util

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
}
