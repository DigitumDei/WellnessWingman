package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.*

class DayCheckInPlannerTest {

    private val today = LocalDate(2026, 8, 9)
    private val yesterday = LocalDate(2026, 8, 8)

    private val bothEnabled = mapOf(
        CheckInSlot.MORNING to CheckInSlotSetting(enabled = true, timeOfDay = LocalTime(7, 0)),
        CheckInSlot.EVENING to CheckInSlotSetting(enabled = true, timeOfDay = LocalTime(21, 0))
    )

    private fun checkIn(
        slot: CheckInSlot,
        date: LocalDate = today,
        text: String = "Slept badly"
    ) = DailyCheckIn(
        checkInDate = date,
        slot = slot,
        capturedAt = Instant.fromEpochMilliseconds(1_785_000_000_000),
        responseText = text
    )

    private fun plan(
        date: LocalDate = today,
        now: LocalTime,
        settings: Map<CheckInSlot, CheckInSlotSetting> = bothEnabled,
        checkIns: List<DailyCheckIn> = emptyList()
    ) = DayCheckInPlanner.plan(date, today, now, settings, checkIns)

    @Test
    fun `no slots before the prompt window opens`() {
        // 05:30, morning is 07:00, so the 06:00 window has not opened.
        assertTrue(plan(now = LocalTime(5, 30)).isEmpty())
    }

    @Test
    fun `morning slot appears an hour before its time`() {
        val slots = plan(now = LocalTime(6, 0))

        assertEquals(1, slots.size)
        assertEquals(CheckInSlot.MORNING, slots.single().slot)
        assertTrue(slots.single().isPrompt, "An unanswered slot is an invitation to check in")
        assertFalse(slots.single().isAnswered)
    }

    @Test
    fun `morning slot stays available later in the day`() {
        // The point of not closing the window: a dismissed notification should not make the
        // check-in unreachable from the day screen.
        val slots = plan(now = LocalTime(14, 0))

        assertEquals(listOf(CheckInSlot.MORNING), slots.map { it.slot })
    }

    @Test
    fun `evening slot joins once its own window opens`() {
        val slots = plan(now = LocalTime(20, 0))

        assertEquals(listOf(CheckInSlot.MORNING, CheckInSlot.EVENING), slots.map { it.slot })
    }

    @Test
    fun `morning always precedes evening`() {
        val slots = plan(
            now = LocalTime(22, 0),
            checkIns = listOf(checkIn(CheckInSlot.EVENING, text = "Good day"))
        )

        assertEquals(listOf(CheckInSlot.MORNING, CheckInSlot.EVENING), slots.map { it.slot })
    }

    @Test
    fun `an answered slot shows the answer instead of a prompt`() {
        val slots = plan(
            now = LocalTime(9, 0),
            checkIns = listOf(checkIn(CheckInSlot.MORNING, text = "Slept well"))
        )

        val morning = slots.single { it.slot == CheckInSlot.MORNING }
        assertTrue(morning.isAnswered)
        assertFalse(morning.isPrompt)
        assertEquals("Slept well", morning.checkIn?.responseText)
    }

    @Test
    fun `answered check-ins show on past days`() {
        val slots = plan(
            date = yesterday,
            now = LocalTime(9, 0),
            checkIns = listOf(checkIn(CheckInSlot.MORNING, date = yesterday, text = "Rough night"))
        )

        assertEquals(1, slots.size)
        assertTrue(slots.single().isAnswered)
    }

    @Test
    fun `no blank prompt on a past day`() {
        // Capture always writes to the current day, so offering a blank slot on a past date
        // would record the answer against the wrong day.
        val slots = plan(date = yesterday, now = LocalTime(22, 0))

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `a past day shows only what was answered, never an empty second slot`() {
        val slots = plan(
            date = yesterday,
            now = LocalTime(22, 0),
            checkIns = listOf(checkIn(CheckInSlot.EVENING, date = yesterday, text = "Long day"))
        )

        assertEquals(listOf(CheckInSlot.EVENING), slots.map { it.slot })
    }

    @Test
    fun `a disabled slot is never prompted`() {
        val slots = plan(
            now = LocalTime(22, 0),
            settings = mapOf(
                CheckInSlot.MORNING to CheckInSlotSetting(enabled = false, timeOfDay = LocalTime(7, 0)),
                CheckInSlot.EVENING to CheckInSlotSetting(enabled = true, timeOfDay = LocalTime(21, 0))
            )
        )

        assertEquals(listOf(CheckInSlot.EVENING), slots.map { it.slot })
    }

    @Test
    fun `a disabled slot still shows an answer already given`() {
        // Turning the reminder off should not erase a check-in from the day's record.
        val slots = plan(
            now = LocalTime(9, 0),
            settings = mapOf(
                CheckInSlot.MORNING to CheckInSlotSetting(enabled = false, timeOfDay = LocalTime(7, 0))
            ),
            checkIns = listOf(checkIn(CheckInSlot.MORNING, text = "Answered before I turned it off"))
        )

        assertEquals(listOf(CheckInSlot.MORNING), slots.map { it.slot })
        assertTrue(slots.single().isAnswered)
    }

    @Test
    fun `an unparseable time never prompts`() {
        val slots = plan(
            now = LocalTime(22, 0),
            settings = mapOf(
                CheckInSlot.MORNING to CheckInSlotSetting(enabled = true, timeOfDay = null)
            )
        )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `an early scheduled time opens at the start of the day rather than wrapping`() {
        // 00:30 minus an hour would be 23:30 the previous day; it must not become available
        // all through the preceding evening.
        assertTrue(DayCheckInPlanner.isPromptOpen(LocalTime(0, 0), LocalTime(0, 30)))
        assertTrue(DayCheckInPlanner.isPromptOpen(LocalTime(23, 59), LocalTime(0, 30)))
    }

    @Test
    fun `the window opens exactly on the lead boundary`() {
        assertFalse(DayCheckInPlanner.isPromptOpen(LocalTime(5, 59), LocalTime(7, 0)))
        assertTrue(DayCheckInPlanner.isPromptOpen(LocalTime(6, 0), LocalTime(7, 0)))
    }

    @Test
    fun `both slots answered shows both`() {
        val slots = plan(
            now = LocalTime(22, 0),
            checkIns = listOf(
                checkIn(CheckInSlot.MORNING, text = "Slept fine"),
                checkIn(CheckInSlot.EVENING, text = "Good day")
            )
        )

        assertEquals(2, slots.size)
        assertTrue(slots.all { it.isAnswered })
    }
}
