package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarMetricFamily
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarSyncCheckpoint
import com.wellnesswingman.data.model.polar.PolarTrainingSession
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `upsertSleepResults replaces existing record with same date`() = runTest {
        val firstSync = Instant.parse("2026-03-24T10:00:00Z")
        val secondSync = Instant.parse("2026-03-24T12:00:00Z")
        val sleep = PolarSleepResult(
            date = "2026-03-23",
            sleepStart = "2026-03-22T23:00:00Z",
            sleepEnd = "2026-03-23T07:00:00Z",
            durationSeconds = 28800,
            deepSleepSeconds = 5400,
            remSleepSeconds = 7200,
            lightSleepSeconds = 14400,
            awakeSeconds = 1800,
            efficiencyPercent = 93.0,
            continuityIndex = 4.0,
            interruptionCount = 3,
            longInterruptionCount = 1,
            sleepScore = 82.0,
            remScore = 78.0,
            deepSleepScore = 80.0,
            scoreRate = 4
        )

        repository.upsertSleepResults(listOf(sleep), firstSync)
        repository.upsertSleepResults(listOf(sleep.copy(sleepScore = 90.0)), secondSync)

        val stored = repository.getSleepResults(
            startDate = LocalDate.parse("2026-03-23"),
            endDateExclusive = LocalDate.parse("2026-03-24")
        )

        assertEquals(1, stored.size)
        assertEquals(90.0, stored.single().data.sleepScore)
        assertEquals(secondSync, stored.single().syncedAt)
    }

    @Test
    fun `upsertTrainingSessions persists and retrieves by date range`() = runTest {
        val syncedAt = Instant.parse("2026-03-24T09:00:00Z")
        val session = PolarTrainingSession(
            id = "session-abc",
            startTime = "2026-03-23T07:30:00",
            durationSeconds = 3600,
            sportId = "1",
            calories = 350,
            distanceMeters = 4500.0,
            averageHeartRate = 145,
            maxHeartRate = 172,
            trainingBenefit = "Aerobic"
        )

        repository.upsertTrainingSessions(listOf(session), syncedAt)

        val stored = repository.getTrainingSessions(
            startDate = LocalDate.parse("2026-03-23"),
            endDateExclusive = LocalDate.parse("2026-03-24")
        )

        assertEquals(1, stored.size)
        with(stored.single()) {
            assertEquals(session.id, externalId)
            assertEquals(350, data.calories)
            assertEquals(syncedAt, this.syncedAt)
        }
    }

    @Test
    fun `upsertTrainingSessions replaces existing session with same id`() = runTest {
        val session = PolarTrainingSession(
            id = "session-abc",
            startTime = "2026-03-23T07:30:00",
            durationSeconds = 3600,
            sportId = "1",
            calories = 350,
            distanceMeters = 4500.0,
            averageHeartRate = 145,
            maxHeartRate = 172,
            trainingBenefit = "Aerobic"
        )

        repository.upsertTrainingSessions(listOf(session), Instant.parse("2026-03-24T09:00:00Z"))
        repository.upsertTrainingSessions(listOf(session.copy(calories = 400)), Instant.parse("2026-03-24T11:00:00Z"))

        val stored = repository.getTrainingSessions(
            startDate = LocalDate.parse("2026-03-23"),
            endDateExclusive = LocalDate.parse("2026-03-24")
        )

        assertEquals(1, stored.size)
        assertEquals(400, stored.single().data.calories)
    }

    @Test
    fun `upsertNightlyRecharge persists and retrieves by date range`() = runTest {
        val syncedAt = Instant.parse("2026-03-24T08:00:00Z")
        val recharge = PolarNightlyRecharge(
            date = "2026-03-23",
            ansStatus = 1.5,
            ansRate = 3,
            recoveryIndicator = 72,
            recoveryIndicatorSubLevel = 2,
            hrvRmssd = 58,
            hrvMeanRri = 910,
            baselineRmssd = 52,
            baselineRmssdSd = 6,
            baselineRri = 875,
            baselineRriSd = 28
        )

        repository.upsertNightlyRecharge(listOf(recharge), syncedAt)

        val stored = repository.getNightlyRecharge(
            startDate = LocalDate.parse("2026-03-23"),
            endDateExclusive = LocalDate.parse("2026-03-24")
        )

        assertEquals(1, stored.size)
        with(stored.single()) {
            assertEquals(72, data.recoveryIndicator)
            assertEquals(58, data.hrvRmssd)
            assertEquals(syncedAt, this.syncedAt)
        }
    }

    @Test
    fun `getAllCheckpoints returns all stored checkpoints`() = runTest {
        repository.updateCheckpoint(PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.ACTIVITY,
            lastSyncCursor = "2026-03-23",
            lastSuccessfulSyncAt = Instant.parse("2026-03-24T10:00:00Z")
        ))
        repository.updateCheckpoint(PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.SLEEP,
            lastSyncCursor = "2026-03-23",
            lastSuccessfulSyncAt = Instant.parse("2026-03-24T10:00:00Z")
        ))

        val all = repository.getAllCheckpoints()

        assertEquals(2, all.size)
        assertTrue(all.any { it.metricFamily == PolarMetricFamily.ACTIVITY })
        assertTrue(all.any { it.metricFamily == PolarMetricFamily.SLEEP })
    }

    @Test
    fun `clearCheckpoint removes only the specified family`() = runTest {
        repository.updateCheckpoint(PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.ACTIVITY,
            lastSyncCursor = "2026-03-23",
            lastSuccessfulSyncAt = Instant.parse("2026-03-24T10:00:00Z")
        ))
        repository.updateCheckpoint(PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.SLEEP,
            lastSyncCursor = "2026-03-23",
            lastSuccessfulSyncAt = Instant.parse("2026-03-24T10:00:00Z")
        ))

        repository.clearCheckpoint(PolarMetricFamily.ACTIVITY)

        assertNull(repository.getCheckpoint(PolarMetricFamily.ACTIVITY))
        assertNotNull(repository.getCheckpoint(PolarMetricFamily.SLEEP))
    }

    @Test
    fun `clearAll removes all metric records and checkpoints`() = runTest {
        val syncedAt = Instant.parse("2026-03-24T10:00:00Z")
        repository.upsertActivities(
            listOf(PolarDailyActivity("2026-03-23", 5000, "00:00:00", 60000, listOf(5000))),
            syncedAt
        )
        repository.updateCheckpoint(PolarSyncCheckpoint(
            metricFamily = PolarMetricFamily.ACTIVITY,
            lastSyncCursor = "2026-03-23",
            lastSuccessfulSyncAt = syncedAt
        ))

        repository.clearAll()

        val activities = repository.getActivities(
            startDate = LocalDate.parse("2026-03-01"),
            endDateExclusive = LocalDate.parse("2026-04-01")
        )
        assertTrue(activities.isEmpty())
        assertNull(repository.getCheckpoint(PolarMetricFamily.ACTIVITY))
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
