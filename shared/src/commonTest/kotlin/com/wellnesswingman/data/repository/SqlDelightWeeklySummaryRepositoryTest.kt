package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.*

class SqlDelightWeeklySummaryRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightWeeklySummaryRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightWeeklySummaryRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `insertSummary persists userComments and payloadJson`() = runTest {
        val weekStart = LocalDate(2025, 3, 1)
        val payloadJson = """{"schemaVersion":"1.1","weekStartDate":"2025-03-01","highlights":[]}"""
        val summary = WeeklySummary(
            weekStartDate = weekStart,
            highlights = "Good week",
            recommendations = "Drink more water",
            mealCount = 15,
            exerciseCount = 3,
            sleepCount = 7,
            otherCount = 0,
            totalEntries = 25,
            generatedAt = Clock.System.now(),
            userComments = "Felt great this week",
            payloadJson = payloadJson
        )

        val id = repository.insertSummary(summary)
        val retrieved = repository.getSummaryById(id)

        assertNotNull(retrieved)
        assertEquals("Felt great this week", retrieved.userComments)
        assertEquals(payloadJson, retrieved.payloadJson)
    }

    @Test
    fun `insertSummary persists null userComments and null payloadJson`() = runTest {
        val weekStart = LocalDate(2025, 3, 8)
        val summary = WeeklySummary(
            weekStartDate = weekStart,
            highlights = "Average week",
            recommendations = "Keep it up",
            userComments = null,
            payloadJson = null
        )

        val id = repository.insertSummary(summary)
        val retrieved = repository.getSummaryById(id)

        assertNotNull(retrieved)
        assertNull(retrieved.userComments)
        assertNull(retrieved.payloadJson)
    }

    @Test
    fun `getSummaryForWeek returns correct summary with new fields`() = runTest {
        val weekStart = LocalDate(2025, 3, 3)
        val summary = WeeklySummary(
            weekStartDate = weekStart,
            highlights = "Good week",
            userComments = "Weekly comment",
            payloadJson = """{"schemaVersion":"1.1","weekStartDate":"2025-03-03"}"""
        )

        repository.insertSummary(summary)
        val retrieved = repository.getSummaryForWeek(weekStart)

        assertNotNull(retrieved)
        assertEquals(weekStart, retrieved.weekStartDate)
        assertEquals("Weekly comment", retrieved.userComments)
        assertEquals("""{"schemaVersion":"1.1","weekStartDate":"2025-03-03"}""", retrieved.payloadJson)
    }

    @Test
    fun `updateUserComments sets new comment on existing summary`() = runTest {
        val weekStart = LocalDate(2025, 3, 10)
        repository.insertSummary(
            WeeklySummary(weekStartDate = weekStart, highlights = "Good week", userComments = null)
        )

        repository.updateUserComments(weekStart, "Updated comment")

        val retrieved = repository.getSummaryForWeek(weekStart)
        assertNotNull(retrieved)
        assertEquals("Updated comment", retrieved.userComments)
    }

    @Test
    fun `updateUserComments with null clears existing comment`() = runTest {
        val weekStart = LocalDate(2025, 3, 17)
        repository.insertSummary(
            WeeklySummary(weekStartDate = weekStart, highlights = "Good week", userComments = "Initial comment")
        )

        repository.updateUserComments(weekStart, null)

        val retrieved = repository.getSummaryForWeek(weekStart)
        assertNotNull(retrieved)
        assertNull(retrieved.userComments)
    }

    @Test
    fun `insertSummary returns auto-incremented id`() = runTest {
        val id1 = repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 1), highlights = "W1"))
        val id2 = repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 8), highlights = "W2"))

        assertTrue(id1 > 0)
        assertTrue(id2 > id1)
    }

    @Test
    fun `getSummaryForWeek returns null for non-existent week`() = runTest {
        assertNull(repository.getSummaryForWeek(LocalDate(2025, 12, 1)))
    }

    @Test
    fun `getAllSummaries returns all inserted summaries`() = runTest {
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 1), highlights = "W1"))
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 8), highlights = "W2"))
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 15), highlights = "W3"))

        val all = repository.getAllSummaries()
        assertEquals(3, all.size)
    }

    @Test
    fun `deleteSummaryByWeek removes the summary`() = runTest {
        val weekStart = LocalDate(2025, 2, 1)
        repository.insertSummary(WeeklySummary(weekStartDate = weekStart, highlights = "Test"))

        repository.deleteSummaryByWeek(weekStart)

        assertNull(repository.getSummaryForWeek(weekStart))
    }

    @Test
    fun `updateSummaryByWeek updates highlights and payloadJson`() = runTest {
        val weekStart = LocalDate(2025, 3, 24)
        repository.insertSummary(
            WeeklySummary(weekStartDate = weekStart, highlights = "Old highlights", payloadJson = null)
        )

        repository.updateSummaryByWeek(
            weekStart,
            WeeklySummary(
                weekStartDate = weekStart,
                highlights = "New highlights",
                recommendations = "New recs",
                payloadJson = """{"updated":true}"""
            )
        )

        val retrieved = repository.getSummaryForWeek(weekStart)
        assertNotNull(retrieved)
        assertEquals("New highlights", retrieved.highlights)
        assertEquals("""{"updated":true}""", retrieved.payloadJson)
    }

    @Test
    fun `getSummariesForDateRange returns summaries in range`() = runTest {
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 6), highlights = "W1"))
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 13), highlights = "W2"))
        repository.insertSummary(WeeklySummary(weekStartDate = LocalDate(2025, 1, 20), highlights = "W3"))

        val range = repository.getSummariesForDateRange(LocalDate(2025, 1, 6), LocalDate(2025, 1, 13))
        assertEquals(2, range.size)
    }

    @Test
    fun `updateSummary updates highlights and recommendations`() = runTest {
        val weekStart = LocalDate(2025, 4, 7)
        val id = repository.insertSummary(
            WeeklySummary(weekStartDate = weekStart, highlights = "Old", recommendations = "Old recs")
        )

        repository.updateSummary(
            WeeklySummary(
                summaryId = id,
                weekStartDate = weekStart,
                highlights = "New highlights",
                recommendations = "New recommendations",
                payloadJson = """{"v":2}"""
            )
        )

        val retrieved = repository.getSummaryById(id)
        assertNotNull(retrieved)
        assertEquals("New highlights", retrieved.highlights)
        assertEquals("New recommendations", retrieved.recommendations)
        assertEquals("""{"v":2}""", retrieved.payloadJson)
    }
}
