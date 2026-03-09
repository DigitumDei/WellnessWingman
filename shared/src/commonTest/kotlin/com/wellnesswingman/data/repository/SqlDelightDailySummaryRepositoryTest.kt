package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.*

class SqlDelightDailySummaryRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightDailySummaryRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightDailySummaryRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `insertSummary persists payloadJson`() = runTest {
        val date = LocalDate(2025, 3, 1)
        val payloadJson = """{"date":"2025-03-01","summary":"Good day","highlights":["Ate well"]}"""
        val id = repository.insertSummary(
            DailySummary(
                summaryDate = date,
                highlights = "Ate well",
                recommendations = "Keep it up",
                generatedAt = Clock.System.now(),
                payloadJson = payloadJson
            )
        )

        val retrieved = repository.getSummaryById(id)
        assertNotNull(retrieved)
        assertEquals(payloadJson, retrieved.payloadJson)
    }

    @Test
    fun `insertSummary persists null payloadJson`() = runTest {
        val date = LocalDate(2025, 3, 2)
        val id = repository.insertSummary(DailySummary(summaryDate = date, highlights = "Test", payloadJson = null))

        val retrieved = repository.getSummaryById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.payloadJson)
    }

    @Test
    fun `getSummaryForDate returns payloadJson`() = runTest {
        val date = LocalDate(2025, 3, 3)
        val payloadJson = """{"date":"2025-03-03","summary":"Great day"}"""
        repository.insertSummary(DailySummary(summaryDate = date, highlights = "Good", payloadJson = payloadJson))

        val retrieved = repository.getSummaryForDate(date)
        assertNotNull(retrieved)
        assertEquals(date, retrieved.summaryDate)
        assertEquals(payloadJson, retrieved.payloadJson)
    }

    @Test
    fun `getSummaryForDate returns null for non-existent date`() = runTest {
        assertNull(repository.getSummaryForDate(LocalDate(2025, 12, 31)))
    }

    @Test
    fun `upsertSummary inserts new summary with payloadJson`() = runTest {
        val date = LocalDate(2025, 3, 4)
        repository.upsertSummary(
            DailySummary(summaryId = 0, summaryDate = date, highlights = "Upserted", payloadJson = """{"upserted":true}""")
        )

        val retrieved = repository.getSummaryForDate(date)
        assertNotNull(retrieved)
        assertEquals("Upserted", retrieved.highlights)
        assertEquals("""{"upserted":true}""", retrieved.payloadJson)
    }

    @Test
    fun `upsertSummary updates existing summary payloadJson`() = runTest {
        val date = LocalDate(2025, 3, 5)
        val id = repository.insertSummary(
            DailySummary(summaryDate = date, highlights = "Original", payloadJson = """{"version":1}""")
        )

        repository.upsertSummary(
            DailySummary(summaryId = id, summaryDate = date, highlights = "Updated", payloadJson = """{"version":2}""")
        )

        val retrieved = repository.getSummaryForDate(date)
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.highlights)
        assertEquals("""{"version":2}""", retrieved.payloadJson)
    }

    @Test
    fun `updateUserComments sets new comment`() = runTest {
        val date = LocalDate(2025, 3, 6)
        repository.insertSummary(DailySummary(summaryDate = date, highlights = "Test", userComments = null))

        repository.updateUserComments(date, "New comment")

        val retrieved = repository.getSummaryForDate(date)
        assertNotNull(retrieved)
        assertEquals("New comment", retrieved.userComments)
    }

    @Test
    fun `updateUserComments with null clears comment`() = runTest {
        val date = LocalDate(2025, 3, 7)
        repository.insertSummary(
            DailySummary(summaryDate = date, highlights = "Test", userComments = "Existing comment")
        )

        repository.updateUserComments(date, null)

        val retrieved = repository.getSummaryForDate(date)
        assertNotNull(retrieved)
        assertNull(retrieved.userComments)
    }

    @Test
    fun `getSummaryById returns null for non-existent id`() = runTest {
        assertNull(repository.getSummaryById(99999))
    }

    @Test
    fun `deleteSummaryByDate removes summary`() = runTest {
        val date = LocalDate(2025, 3, 8)
        repository.insertSummary(DailySummary(summaryDate = date, highlights = "Test"))

        repository.deleteSummaryByDate(date)

        assertNull(repository.getSummaryForDate(date))
    }

    @Test
    fun `getSummariesForDateRange returns summaries in range`() = runTest {
        repository.insertSummary(DailySummary(summaryDate = LocalDate(2025, 3, 1), highlights = "Day 1"))
        repository.insertSummary(DailySummary(summaryDate = LocalDate(2025, 3, 5), highlights = "Day 5"))
        repository.insertSummary(DailySummary(summaryDate = LocalDate(2025, 3, 10), highlights = "Day 10"))

        val range = repository.getSummariesForDateRange(LocalDate(2025, 3, 1), LocalDate(2025, 3, 7))
        assertEquals(2, range.size)
    }

    @Test
    fun `insertSummary round-trips userComments`() = runTest {
        val date = LocalDate(2025, 3, 9)
        val id = repository.insertSummary(
            DailySummary(summaryDate = date, highlights = "Test", userComments = "My note for today")
        )

        val retrieved = repository.getSummaryById(id)
        assertNotNull(retrieved)
        assertEquals("My note for today", retrieved.userComments)
    }

    @Test
    fun `getAllSummaries returns all inserted summaries`() = runTest {
        repository.insertSummary(DailySummary(summaryDate = LocalDate(2025, 3, 1), highlights = "D1"))
        repository.insertSummary(DailySummary(summaryDate = LocalDate(2025, 3, 2), highlights = "D2"))

        val all = repository.getAllSummaries()
        assertEquals(2, all.size)
    }
}
