package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightEntryAnalysisRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var entryRepository: SqlDelightTrackedEntryRepository
    private lateinit var analysisRepository: SqlDelightEntryAnalysisRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        entryRepository = SqlDelightTrackedEntryRepository(database)
        analysisRepository = SqlDelightEntryAnalysisRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private suspend fun insertEntry(
        capturedAt: Instant,
        entryType: EntryType = EntryType.MEAL
    ): Long = entryRepository.insertEntry(
        TrackedEntry(entryType = entryType, capturedAt = capturedAt)
    )

    private suspend fun insertAnalysis(
        entryId: Long,
        capturedAt: Instant,
        insightsJson: String = "{}"
    ): Long = analysisRepository.insertAnalysis(
        EntryAnalysis(entryId = entryId, capturedAt = capturedAt, insightsJson = insightsJson)
    )

    @Test
    fun `excludes parents outside the entry range`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val before = insertEntry(Instant.parse("2024-01-09T23:59:00Z"))
        val inRange = insertEntry(Instant.parse("2024-01-11T00:00:00Z"))
        val atEnd = insertEntry(Instant.parse("2024-01-12T00:00:00Z"))
        val after = insertEntry(Instant.parse("2024-01-12T00:00:01Z"))
        insertAnalysis(before, Instant.parse("2024-01-10T01:00:00Z"))
        insertAnalysis(inRange, Instant.parse("2024-01-11T01:00:00Z"))
        insertAnalysis(atEnd, Instant.parse("2024-01-12T01:00:00Z"))
        insertAnalysis(after, Instant.parse("2024-01-12T01:00:01Z"))

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(inRange, result.single().entryId)
    }

    @Test
    fun `omits entries that have no analysis`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        insertEntry(Instant.parse("2024-01-11T00:00:00Z"))
        insertEntry(Instant.parse("2024-01-11T12:00:00Z"))

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns only the latest of multiple analyses per entry`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val entryId = insertEntry(Instant.parse("2024-01-11T00:00:00Z"))
        insertAnalysis(entryId, Instant.parse("2024-01-11T01:00:00Z"), "old")
        insertAnalysis(entryId, Instant.parse("2024-01-11T03:00:00Z"), "new")

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals("new", result.single().insightsJson)
    }

    @Test
    fun `includes an in-range entry whose latest re-analysis occurred after the range`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val entryId = insertEntry(Instant.parse("2024-01-11T00:00:00Z"))
        insertAnalysis(entryId, Instant.parse("2024-01-09T00:00:00Z"), "old")
        insertAnalysis(entryId, Instant.parse("2024-01-13T00:00:00Z"), "re-analysis after range")

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals("re-analysis after range", result.single().insightsJson)
    }

    @Test
    fun `resolves capturedAt ties by later analysisId`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val entryId = insertEntry(Instant.parse("2024-01-11T00:00:00Z"))
        val tie = Instant.parse("2024-01-11T02:00:00Z")
        insertAnalysis(entryId, tie, "first")
        val secondId = insertAnalysis(entryId, tie, "second")

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(secondId, result.single().analysisId)
        assertEquals("second", result.single().insightsJson)
    }

    @Test
    fun `orders results chronologically by parent entry`() = runTest {
        val rangeStart = Instant.parse("2024-01-10T00:00:00Z")
        val rangeEnd = Instant.parse("2024-01-12T00:00:00Z")
        val first = insertEntry(Instant.parse("2024-01-10T08:00:00Z"))
        val second = insertEntry(Instant.parse("2024-01-11T08:00:00Z"))
        insertAnalysis(first, Instant.parse("2024-01-10T09:00:00Z"))
        insertAnalysis(second, Instant.parse("2024-01-11T09:00:00Z"))

        val result = analysisRepository.getLatestAnalysesForEntries(rangeStart, rangeEnd)

        assertEquals(listOf(first, second), result.map { it.entryId })
    }
}
