package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarUserProfile
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SqlDelightPolarSyncRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: SqlDelightPolarSyncRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightPolarSyncRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `upsertActivities replaces existing record with same dedupe key`() = runTest {
        val firstSyncedAt = Instant.parse("2026-03-24T10:00:00Z")
        val secondSyncedAt = Instant.parse("2026-03-24T12:00:00Z")

        repository.upsertActivities(
            listOf(
                PolarDailyActivity(
                    date = "2026-03-23",
                    totalSteps = 1000,
                    stepSampleStartTime = "00:00:00",
                    stepSampleIntervalMs = 60000,
                    stepSamples = listOf(1000)
                )
            ),
            firstSyncedAt
        )

        repository.upsertActivities(
            listOf(
                PolarDailyActivity(
                    date = "2026-03-23",
                    totalSteps = 1500,
                    stepSampleStartTime = "00:00:00",
                    stepSampleIntervalMs = 60000,
                    stepSamples = listOf(1500)
                )
            ),
            secondSyncedAt
        )

        val stored = repository.getActivities(
            startDate = LocalDate.parse("2026-03-23"),
            endDateExclusive = LocalDate.parse("2026-03-24")
        )

        assertEquals(1, stored.size)
        assertEquals(1500, stored.single().data.totalSteps)
        assertEquals(secondSyncedAt, stored.single().syncedAt)
    }

    @Test
    fun `checkpoints persist independently of metric data`() = runTest {
        val checkpoint = PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.TRAINING,
            lastSyncCursor = "2026-03-24",
            lastSuccessfulSyncAt = Instant.parse("2026-03-24T08:30:00Z")
        )

        repository.updateCheckpoint(checkpoint)

        val stored = repository.getCheckpoint(PolarMetricFamily.TRAINING)

        assertEquals(checkpoint, stored)
    }

    @Test
    fun `user profile persists by polar user id`() = runTest {
        val syncedAt = Instant.parse("2026-03-24T11:00:00Z")

        repository.upsertUserProfile(
            userId = "polar-user-1",
            profile = PolarUserProfile(
                birthday = "1990-01-02",
                sex = "MALE",
                heightCm = 180.0,
                weightKg = 80.5,
                restingHeartRate = 52,
                maxHeartRate = 188,
                vo2Max = 48,
                trainingBackground = "REGULAR",
                sleepGoalSeconds = 28800,
                weeklyRecoveryTimeHours = 12.5
            ),
            syncedAt = syncedAt
        )

        val stored = repository.getUserProfile("polar-user-1")

        assertNotNull(stored)
        assertEquals("1990-01-02", stored.data.birthday)
        assertEquals(syncedAt, stored.syncedAt)
    }
}
