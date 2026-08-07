package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

class SqlDelightDailyCheckInRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightDailyCheckInRepository

    private val date = LocalDate(2026, 8, 7)

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightDailyCheckInRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private fun checkIn(
        slot: CheckInSlot,
        responseText: String,
        capturedAt: Instant = Instant.fromEpochMilliseconds(1_785_000_000_000),
        inputSource: CheckInInputSource = CheckInInputSource.TYPED,
        onDate: LocalDate = date
    ) = DailyCheckIn(
        checkInDate = onDate,
        slot = slot,
        capturedAt = capturedAt,
        responseText = responseText,
        inputSource = inputSource
    )

    @Test
    fun `saveCheckIn round-trips all fields`() = runTest {
        val capturedAt = Instant.fromEpochMilliseconds(1_785_012_345_678)
        repository.saveCheckIn(
            checkIn(
                slot = CheckInSlot.MORNING,
                responseText = "Slept badly, feeling flat.",
                capturedAt = capturedAt,
                inputSource = CheckInInputSource.VOICE
            )
        )

        val retrieved = repository.getCheckIn(date, CheckInSlot.MORNING)

        assertNotNull(retrieved)
        assertEquals(date, retrieved.checkInDate)
        assertEquals(CheckInSlot.MORNING, retrieved.slot)
        assertEquals(capturedAt, retrieved.capturedAt)
        assertEquals("Slept badly, feeling flat.", retrieved.responseText)
        assertEquals(CheckInInputSource.VOICE, retrieved.inputSource)
        assertNull(retrieved.conversationExternalId)
    }

    @Test
    fun `saving the same slot twice replaces rather than duplicates`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.EVENING, "First answer"))
        repository.saveCheckIn(checkIn(CheckInSlot.EVENING, "Corrected answer"))

        val forDate = repository.getCheckInsForDate(date)

        assertEquals(1, forDate.size)
        assertEquals("Corrected answer", forDate.single().responseText)
    }

    @Test
    fun `morning and evening coexist on the same day`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Slept well"))
        repository.saveCheckIn(checkIn(CheckInSlot.EVENING, "Good day"))

        val forDate = repository.getCheckInsForDate(date)

        assertEquals(2, forDate.size)
        assertEquals(
            setOf(CheckInSlot.MORNING, CheckInSlot.EVENING),
            forDate.map { it.slot }.toSet()
        )
    }

    @Test
    fun `check-ins are scoped to their own day`() = runTest {
        val otherDate = LocalDate(2026, 8, 8)
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Today"))
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Tomorrow", onDate = otherDate))

        assertEquals("Today", repository.getCheckInsForDate(date).single().responseText)
        assertEquals("Tomorrow", repository.getCheckInsForDate(otherDate).single().responseText)
    }

    @Test
    fun `attachConversation links a chat thread without touching the answer`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Slept badly"))

        repository.attachConversation(date, CheckInSlot.MORNING, "checkin-2026-08-07-morning")

        val retrieved = repository.getCheckIn(date, CheckInSlot.MORNING)

        assertNotNull(retrieved)
        assertEquals("checkin-2026-08-07-morning", retrieved.conversationExternalId)
        assertEquals("Slept badly", retrieved.responseText)
    }

    @Test
    fun `getCheckInsForDateRange returns only check-ins inside the range`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Before", onDate = LocalDate(2026, 8, 5)))
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Inside", onDate = LocalDate(2026, 8, 6)))
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "After", onDate = LocalDate(2026, 8, 9)))

        val inRange = repository.getCheckInsForDateRange(
            startDate = LocalDate(2026, 8, 6),
            endDate = LocalDate(2026, 8, 8)
        )

        assertEquals(listOf("Inside"), inRange.map { it.responseText })
    }

    @Test
    fun `deleteOldCheckIns removes only check-ins before the cutoff`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Old", onDate = LocalDate(2026, 8, 1)))
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Kept", onDate = LocalDate(2026, 8, 7)))

        repository.deleteOldCheckIns(beforeDate = LocalDate(2026, 8, 7))

        assertNull(repository.getCheckIn(LocalDate(2026, 8, 1), CheckInSlot.MORNING))
        assertNotNull(repository.getCheckIn(LocalDate(2026, 8, 7), CheckInSlot.MORNING))
    }

    @Test
    fun `deleteCheckIn removes only the requested slot`() = runTest {
        repository.saveCheckIn(checkIn(CheckInSlot.MORNING, "Morning"))
        repository.saveCheckIn(checkIn(CheckInSlot.EVENING, "Evening"))

        repository.deleteCheckIn(date, CheckInSlot.MORNING)

        assertNull(repository.getCheckIn(date, CheckInSlot.MORNING))
        assertNotNull(repository.getCheckIn(date, CheckInSlot.EVENING))
    }
}
