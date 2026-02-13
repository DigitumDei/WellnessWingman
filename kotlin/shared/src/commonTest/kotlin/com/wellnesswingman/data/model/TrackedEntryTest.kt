package com.wellnesswingman.data.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*

class TrackedEntryTest {

    @Test
    fun `TrackedEntry can be created with default values`() {
        val entry = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now()
        )

        assertEquals(0, entry.entryId)
        assertNull(entry.externalId)
        assertEquals(EntryType.MEAL, entry.entryType)
        assertEquals(ProcessingStatus.PENDING, entry.processingStatus)
        assertNull(entry.userNotes)
        assertNull(entry.blobPath)
    }

    @Test
    fun `TrackedEntry copy creates new instance with modified fields`() {
        val original = TrackedEntry(
            entryId = 1,
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now(),
            userNotes = "Original"
        )

        val copied = original.copy(userNotes = "Modified")

        assertEquals(1, copied.entryId)
        assertEquals("Modified", copied.userNotes)
        assertEquals(original.entryType, copied.entryType)
        assertEquals(original.capturedAt, copied.capturedAt)
    }

    @Test
    fun `EntryType enum has all expected values`() {
        val types = EntryType.values()

        assertTrue(types.contains(EntryType.MEAL))
        assertTrue(types.contains(EntryType.EXERCISE))
        assertTrue(types.contains(EntryType.SLEEP))
        assertTrue(types.contains(EntryType.UNKNOWN))
    }

    @Test
    fun `ProcessingStatus enum has all expected values`() {
        val statuses = ProcessingStatus.values()

        assertTrue(statuses.contains(ProcessingStatus.PENDING))
        assertTrue(statuses.contains(ProcessingStatus.PROCESSING))
        assertTrue(statuses.contains(ProcessingStatus.COMPLETED))
        assertTrue(statuses.contains(ProcessingStatus.FAILED))
        assertTrue(statuses.contains(ProcessingStatus.SKIPPED))
    }

    @Test
    fun `TrackedEntry equality works correctly`() {
        val now = Clock.System.now()
        val entry1 = TrackedEntry(
            entryId = 1,
            entryType = EntryType.MEAL,
            capturedAt = now
        )
        val entry2 = TrackedEntry(
            entryId = 1,
            entryType = EntryType.MEAL,
            capturedAt = now
        )
        val entry3 = TrackedEntry(
            entryId = 2,
            entryType = EntryType.MEAL,
            capturedAt = now
        )

        assertEquals(entry1, entry2)
        assertNotEquals(entry1, entry3)
    }

    @Test
    fun `TrackedEntry can store all optional fields`() {
        val entry = TrackedEntry(
            entryId = 1,
            externalId = "ext-123",
            entryType = EntryType.EXERCISE,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.COMPLETED,
            userNotes = "Test notes",
            blobPath = "/path/to/photo.jpg",
            capturedAtTimeZoneId = "America/New_York",
            capturedAtOffsetMinutes = -300
        )

        assertEquals("ext-123", entry.externalId)
        assertEquals("Test notes", entry.userNotes)
        assertEquals("/path/to/photo.jpg", entry.blobPath)
        assertEquals("America/New_York", entry.capturedAtTimeZoneId)
        assertEquals(-300, entry.capturedAtOffsetMinutes)
    }
}
