package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import kotlinx.datetime.LocalDate
import kotlin.test.*

class PendingCheckInStoreTest {

    @Test
    fun `a notification's date is carried through`() {
        // The bug this exists to prevent: answering last night's notification this morning
        // recorded the check-in against today.
        val store = PendingCheckInStore()

        store.request(CheckInSlot.EVENING, "2026-08-10")

        val pending = store.consume()
        assertNotNull(pending)
        assertEquals(CheckInSlot.EVENING, pending.slot)
        assertEquals(LocalDate(2026, 8, 10), pending.date)
    }

    @Test
    fun `an absent date means today`() {
        val store = PendingCheckInStore()

        store.request(CheckInSlot.MORNING, null as String?)

        assertNull(store.consume()?.date, "Null date is resolved to today downstream")
    }

    @Test
    fun `an unparseable date falls back to today rather than failing`() {
        // A malformed deep link should still open the check-in, not drop it on the floor.
        val store = PendingCheckInStore()

        store.request(CheckInSlot.MORNING, "not-a-date")

        val pending = store.consume()
        assertNotNull(pending)
        assertEquals(CheckInSlot.MORNING, pending.slot)
        assertNull(pending.date)
    }

    @Test
    fun `a blank date is treated as absent`() {
        val store = PendingCheckInStore()

        store.request(CheckInSlot.EVENING, "  ")

        assertNull(store.consume()?.date)
    }

    @Test
    fun `consuming clears the request so it is not replayed`() {
        // A configuration change must not push the check-in screen a second time.
        val store = PendingCheckInStore()
        store.request(CheckInSlot.MORNING, "2026-08-10")

        assertNotNull(store.consume())
        assertNull(store.consume())
    }

    @Test
    fun `the latest request wins`() {
        val store = PendingCheckInStore()

        store.request(CheckInSlot.MORNING, "2026-08-09")
        store.request(CheckInSlot.EVENING, "2026-08-10")

        val pending = store.consume()
        assertEquals(CheckInSlot.EVENING, pending?.slot)
        assertEquals(LocalDate(2026, 8, 10), pending?.date)
    }
}
