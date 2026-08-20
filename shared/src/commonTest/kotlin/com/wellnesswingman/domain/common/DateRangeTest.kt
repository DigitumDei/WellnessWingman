package com.wellnesswingman.domain.common

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateRangeTest {

    @Test
    fun `accepts a one-day range`() {
        val range = DateRange.of(LocalDate(2024, 1, 15), LocalDate(2024, 1, 15))

        assertEquals(LocalDate(2024, 1, 15), range.start)
        assertEquals(LocalDate(2024, 1, 15), range.end)
        assertEquals(LocalDate(2024, 1, 16), range.endExclusive)
    }

    @Test
    fun `accepts an ordinary multi-day range`() {
        val range = DateRange.of(LocalDate(2024, 1, 10), LocalDate(2024, 1, 20))

        assertEquals(LocalDate(2024, 1, 10), range.start)
        assertEquals(LocalDate(2024, 1, 20), range.end)
        assertEquals(LocalDate(2024, 1, 21), range.endExclusive)
    }

    @Test
    fun `rejects reversed bounds`() {
        assertFailsWith<IllegalArgumentException> {
            DateRange.of(LocalDate(2024, 1, 20), LocalDate(2024, 1, 10))
        }
    }

    @Test
    fun `endExclusive crosses a month boundary`() {
        val range = DateRange.of(LocalDate(2024, 1, 31), LocalDate(2024, 1, 31))

        assertEquals(LocalDate(2024, 2, 1), range.endExclusive)
    }

    @Test
    fun `endExclusive crosses a year boundary`() {
        val range = DateRange.of(LocalDate(2024, 12, 31), LocalDate(2024, 12, 31))

        assertEquals(LocalDate(2025, 1, 1), range.endExclusive)
    }

    @Test
    fun `endExclusive crosses a leap-day boundary`() {
        val range = DateRange.of(LocalDate(2024, 2, 29), LocalDate(2024, 2, 29))

        assertEquals(LocalDate(2024, 3, 1), range.endExclusive)
    }

    @Test
    fun `endExclusive stays within February in a non-leap year`() {
        val range = DateRange.of(LocalDate(2023, 2, 28), LocalDate(2023, 2, 28))

        assertEquals(LocalDate(2023, 3, 1), range.endExclusive)
    }

    @Test
    fun `instant bounds use a fixed-offset non-UTC zone`() {
        val range = DateRange.of(LocalDate(2024, 3, 15), LocalDate(2024, 3, 16))

        val (start, endExclusive) = range.toInstantBounds(TimeZone.of("Asia/Tokyo"))

        // JST = UTC+9 with no DST
        assertEquals(Instant.parse("2024-03-14T15:00:00Z"), start)
        assertEquals(Instant.parse("2024-03-16T15:00:00Z"), endExclusive)
    }

    @Test
    fun `instant bounds cover a DST spring-forward day as a 23-hour span`() {
        // Europe/Berlin: 2024-03-31 02:00 CET -> 03:00 CEST
        val range = DateRange.of(LocalDate(2024, 3, 31), LocalDate(2024, 3, 31))

        val (start, endExclusive) = range.toInstantBounds(TimeZone.of("Europe/Berlin"))

        assertEquals(Instant.parse("2024-03-30T23:00:00Z"), start)
        assertEquals(Instant.parse("2024-03-31T22:00:00Z"), endExclusive)
    }

    @Test
    fun `instant bounds cover a DST fall-back day as a 25-hour span`() {
        // Europe/Berlin: 2024-10-27 03:00 CEST -> 02:00 CET
        val range = DateRange.of(LocalDate(2024, 10, 27), LocalDate(2024, 10, 27))

        val (start, endExclusive) = range.toInstantBounds(TimeZone.of("Europe/Berlin"))

        assertEquals(Instant.parse("2024-10-26T22:00:00Z"), start)
        assertEquals(Instant.parse("2024-10-27T23:00:00Z"), endExclusive)
    }

    @Test
    fun `instant bounds span a DST transition across a multi-day range`() {
        // Start before the 2024-03-31 spring-forward, end after it.
        val range = DateRange.of(LocalDate(2024, 3, 30), LocalDate(2024, 3, 31))

        val (start, endExclusive) = range.toInstantBounds(TimeZone.of("Europe/Berlin"))

        assertEquals(Instant.parse("2024-03-29T23:00:00Z"), start)
        assertEquals(Instant.parse("2024-03-31T22:00:00Z"), endExclusive)
    }
}
