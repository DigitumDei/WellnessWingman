package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SqlDelightTrackedEntryRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightTrackedEntryRepository

    @BeforeTest
    fun setup() {
        // Create in-memory SQLite database
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightTrackedEntryRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `insertEntry creates new entry and returns id`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now(),
            userNotes = "Test meal"
        )

        val id = repository.insertEntry(entry)

        assertTrue(id > 0)
    }

    @Test
    fun `getEntryById returns correct entry`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.EXERCISE,
            capturedAt = Clock.System.now(),
            userNotes = "Morning run"
        )

        val id = repository.insertEntry(entry)
        val retrieved = repository.getEntryById(id)

        assertNotNull(retrieved)
        assertEquals(id, retrieved.entryId)
        assertEquals(EntryType.EXERCISE, retrieved.entryType)
        assertEquals("Morning run", retrieved.userNotes)
    }

    @Test
    fun `getEntryById returns null for non-existent id`() = runTest {
        val retrieved = repository.getEntryById(999)

        assertNull(retrieved)
    }

    @Test
    fun `getEntryByBlobPath returns the entry with a matching blob path`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.UNKNOWN,
            capturedAt = Clock.System.now(),
            blobPath = "/app/data/photos/photo_123.jpg"
        )
        repository.insertEntry(entry)

        val retrieved = repository.getEntryByBlobPath("/app/data/photos/photo_123.jpg")

        assertNotNull(retrieved)
        assertEquals(entry.blobPath, retrieved.blobPath)
    }

    @Test
    fun `getEntryByBlobPath returns null when no entry matches`() = runTest {
        assertNull(repository.getEntryByBlobPath("/app/data/photos/missing.jpg"))
    }

    @Test
    fun `getAllEntries returns all entries sorted by capture time desc`() = runTest {
        val now = Clock.System.now()
        val entry1 = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = now.minus(2.hours)
        )
        val entry2 = TrackedEntry(
            entryType = EntryType.EXERCISE,
            capturedAt = now.minus(1.hours)
        )
        val entry3 = TrackedEntry(
            entryType = EntryType.SLEEP,
            capturedAt = now
        )

        repository.insertEntry(entry1)
        repository.insertEntry(entry2)
        repository.insertEntry(entry3)

        val all = repository.getAllEntries()

        assertEquals(3, all.size)
        // Most recent first
        assertEquals(EntryType.SLEEP, all[0].entryType)
        assertEquals(EntryType.EXERCISE, all[1].entryType)
        assertEquals(EntryType.MEAL, all[2].entryType)
    }

    @Test
    fun `getEntriesForDay returns entries in time range`() = runTest {
        val now = Clock.System.now()
        val dayStart = now.minus(12.hours)
        val dayEnd = now

        val entry1 = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = now.minus(2.days) // Outside range
        )
        val entry2 = TrackedEntry(
            entryType = EntryType.EXERCISE,
            capturedAt = now.minus(6.hours) // Inside range
        )
        val entry3 = TrackedEntry(
            entryType = EntryType.SLEEP,
            capturedAt = now.minus(3.hours) // Inside range
        )

        repository.insertEntry(entry1)
        repository.insertEntry(entry2)
        repository.insertEntry(entry3)

        val inRange = repository.getEntriesForDay(
            dayStart.toEpochMilliseconds(),
            dayEnd.toEpochMilliseconds()
        )

        assertEquals(2, inRange.size)
        assertTrue(inRange.all {
            it.capturedAt >= dayStart && it.capturedAt <= dayEnd
        })
    }

    @Test
    fun `getEntriesInRange includes exact start and excludes exact end`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")

        repository.insertEntry(
            TrackedEntry(entryType = EntryType.MEAL, capturedAt = rangeStart)
        )
        repository.insertEntry(
            TrackedEntry(entryType = EntryType.MEAL, capturedAt = Instant.parse("2024-01-11T12:00:00Z"))
        )
        repository.insertEntry(
            TrackedEntry(entryType = EntryType.SLEEP, capturedAt = rangeEnd)
        )
        repository.insertEntry(
            TrackedEntry(entryType = EntryType.EXERCISE, capturedAt = Instant.parse("2024-01-09T23:59:00Z"))
        )

        val inRange = repository.getEntriesInRange(rangeStart, rangeEnd)

        assertEquals(2, inRange.size)
        assertTrue(inRange.all { it.capturedAt >= rangeStart && it.capturedAt < rangeEnd })
    }

    @Test
    fun `getEntriesInRange orders chronologically with entryId tie-break`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val early = Instant.parse("2024-01-10T20:00:00Z")
        val tie = Instant.parse("2024-01-11T08:00:00Z")

        val id3 = repository.insertEntry(TrackedEntry(entryType = EntryType.SLEEP, capturedAt = early))
        val id1 = repository.insertEntry(TrackedEntry(entryType = EntryType.MEAL, capturedAt = tie))
        val id2 = repository.insertEntry(TrackedEntry(entryType = EntryType.MEAL, capturedAt = tie))

        val inRange = repository.getEntriesInRange(rangeStart, rangeEnd)

        assertEquals(listOf(id3, id1, id2), inRange.map { it.entryId })
    }

    @Test
    fun `getEntriesInRange returns empty for an empty range`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-10T00:00:00Z")

        repository.insertEntry(
            TrackedEntry(entryType = EntryType.MEAL, capturedAt = rangeStart)
        )

        val inRange = repository.getEntriesInRange(rangeStart, rangeEnd)

        assertTrue(inRange.isEmpty())
    }

    @Test
    fun `updateEntryStatus modifies status`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now(),
            processingStatus = ProcessingStatus.PENDING
        )

        val id = repository.insertEntry(entry)
        repository.updateEntryStatus(id, ProcessingStatus.COMPLETED)
        val retrieved = repository.getEntryById(id)

        assertNotNull(retrieved)
        assertEquals(ProcessingStatus.COMPLETED, retrieved.processingStatus)
    }

    @Test
    fun `updateUserNotes modifies notes`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now(),
            userNotes = "Original notes"
        )

        val id = repository.insertEntry(entry)
        repository.updateUserNotes(id, "Updated notes")
        val retrieved = repository.getEntryById(id)

        assertNotNull(retrieved)
        assertEquals("Updated notes", retrieved.userNotes)
    }

    @Test
    fun `deleteEntry removes entry from database`() = runTest {
        val entry = TrackedEntry(
            entryType = EntryType.MEAL,
            capturedAt = Clock.System.now()
        )

        val id = repository.insertEntry(entry)
        repository.deleteEntry(id)
        val retrieved = repository.getEntryById(id)

        assertNull(retrieved)
    }

    @Test
    fun `getEntriesByStatus returns only matching status`() = runTest {
        repository.insertEntry(
            TrackedEntry(
                entryType = EntryType.MEAL,
                capturedAt = Clock.System.now(),
                processingStatus = ProcessingStatus.PENDING
            )
        )
        repository.insertEntry(
            TrackedEntry(
                entryType = EntryType.EXERCISE,
                capturedAt = Clock.System.now(),
                processingStatus = ProcessingStatus.COMPLETED
            )
        )
        repository.insertEntry(
            TrackedEntry(
                entryType = EntryType.SLEEP,
                capturedAt = Clock.System.now(),
                processingStatus = ProcessingStatus.PENDING
            )
        )

        val pending = repository.getEntriesByStatus(ProcessingStatus.PENDING)

        assertEquals(2, pending.size)
        assertTrue(pending.all { it.processingStatus == ProcessingStatus.PENDING })
    }

    @Test
    fun `getPendingEntries returns only pending entries`() = runTest {
        repository.insertEntry(
            TrackedEntry(
                entryType = EntryType.MEAL,
                capturedAt = Clock.System.now(),
                processingStatus = ProcessingStatus.PENDING
            )
        )
        repository.insertEntry(
            TrackedEntry(
                entryType = EntryType.EXERCISE,
                capturedAt = Clock.System.now(),
                processingStatus = ProcessingStatus.COMPLETED
            )
        )

        val pending = repository.getPendingEntries()

        assertEquals(1, pending.size)
        assertEquals(ProcessingStatus.PENDING, pending[0].processingStatus)
    }
}
