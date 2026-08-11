package com.wellnesswingman.domain.checkin

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.*

class CheckInScheduleCalculatorTest {

    private val utc = TimeZone.UTC

    private fun instantAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone = utc
    ) = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    @Test
    fun `parses a valid 24-hour time`() {
        assertEquals(LocalTime(7, 0), CheckInScheduleCalculator.parseTimeOfDay("07:00"))
        assertEquals(LocalTime(21, 30), CheckInScheduleCalculator.parseTimeOfDay("21:30"))
        assertEquals(LocalTime(0, 0), CheckInScheduleCalculator.parseTimeOfDay("00:00"))
        assertEquals(LocalTime(23, 59), CheckInScheduleCalculator.parseTimeOfDay("23:59"))
    }

    @Test
    fun `returns null for unparseable settings rather than throwing`() {
        // A corrupt preference should disable a check-in, not crash the app on launch.
        assertNull(CheckInScheduleCalculator.parseTimeOfDay(""))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("7"))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("07:00:00"))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("24:00"))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("07:60"))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("-1:00"))
        assertNull(CheckInScheduleCalculator.parseTimeOfDay("ab:cd"))
    }

    @Test
    fun `schedules later today when the time has not yet passed`() {
        val now = instantAt(2026, 8, 7, 5, 30)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc)

        assertEquals(instantAt(2026, 8, 7, 7, 0), next)
    }

    @Test
    fun `rolls to tomorrow when the time has already passed today`() {
        val now = instantAt(2026, 8, 7, 9, 15)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc)

        assertEquals(instantAt(2026, 8, 8, 7, 0), next)
    }

    @Test
    fun `rolls to tomorrow when re-arming exactly at the fire time`() {
        // The alarm receiver re-arms the moment it fires. A non-strict comparison here would
        // reschedule the same instant and fire in a loop.
        val fireTime = instantAt(2026, 8, 7, 7, 0)

        val next = CheckInScheduleCalculator.nextOccurrence(fireTime, LocalTime(7, 0), utc)

        assertEquals(instantAt(2026, 8, 8, 7, 0), next)
    }

    @Test
    fun `crosses a month boundary correctly`() {
        val now = instantAt(2026, 8, 31, 22, 0)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc)

        assertEquals(instantAt(2026, 9, 1, 7, 0), next)
    }

    @Test
    fun `crosses a year boundary correctly`() {
        val now = instantAt(2026, 12, 31, 23, 30)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc)

        assertEquals(instantAt(2027, 1, 1, 7, 0), next)
    }

    @Test
    fun `handles a leap day`() {
        val now = instantAt(2028, 2, 28, 12, 0)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc)

        assertEquals(instantAt(2028, 2, 29, 7, 0), next)
    }

    @Test
    fun `resolves the target time in the given timezone, not UTC`() {
        // 07:00 in Johannesburg (UTC+2) is 05:00 UTC. Scheduling in the wrong zone is exactly
        // the bug that makes a morning check-in arrive in the middle of the night.
        val johannesburg = TimeZone.of("Africa/Johannesburg")
        val now = instantAt(2026, 8, 7, 0, 0, johannesburg)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), johannesburg)

        assertEquals(instantAt(2026, 8, 7, 5, 0, utc), next)
    }

    @Test
    fun `the same wall-clock preference resolves differently after travelling`() {
        // "07:00" must mean 07:00 where the user actually is, which is why the boot and
        // timezone-change receivers re-arm rather than trusting the previously set alarm.
        val now = instantAt(2026, 8, 7, 0, 0)

        val inLondon = CheckInScheduleCalculator.nextOccurrence(
            now,
            LocalTime(7, 0),
            TimeZone.of("Europe/London")
        )
        val inSydney = CheckInScheduleCalculator.nextOccurrence(
            now,
            LocalTime(7, 0),
            TimeZone.of("Australia/Sydney")
        )

        assertNotEquals(inLondon, inSydney)
    }

    @Test
    fun `resolved instant reads back as the requested local time`() {
        val newYork = TimeZone.of("America/New_York")
        val now = instantAt(2026, 8, 7, 3, 0, newYork)

        val next = CheckInScheduleCalculator.nextOccurrence(now, LocalTime(21, 0), newYork)
        val localTime = next.toLocalDateTime(newYork).time

        assertEquals(21, localTime.hour)
        assertEquals(0, localTime.minute)
    }

    @Test
    fun `string overload returns null for a corrupt setting`() {
        val now = instantAt(2026, 8, 7, 5, 0)

        assertNull(CheckInScheduleCalculator.nextOccurrence(now, "not-a-time", utc))
    }

    @Test
    fun `string overload agrees with the parsed overload`() {
        val now = instantAt(2026, 8, 7, 5, 0)

        assertEquals(
            CheckInScheduleCalculator.nextOccurrence(now, LocalTime(7, 0), utc),
            CheckInScheduleCalculator.nextOccurrence(now, "07:00", utc)
        )
    }
}
