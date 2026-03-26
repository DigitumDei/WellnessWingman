package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.domain.testutil.FakePolarSyncRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolarInsightServiceTest {

    @Test
    fun `getDayContexts includes each requested day and groups records by local date`() = runTest {
        val service = PolarInsightService(
            FakePolarSyncRepository(
                activities = listOf(
                    storedActivity(date = LocalDate(2025, 3, 3), recordId = 1, totalSteps = 8000),
                    storedActivity(date = LocalDate(2025, 3, 3), recordId = 2, totalSteps = 9000),
                    storedActivity(date = LocalDate(2025, 3, 4), recordId = 3, totalSteps = 7000)
                ),
                sleepResults = listOf(
                    storedSleep(date = LocalDate(2025, 3, 4), recordId = 4, durationSeconds = 28800)
                ),
                trainingSessions = listOf(
                    storedTraining(date = LocalDate(2025, 3, 5), recordId = 5, durationSeconds = 3600)
                ),
                nightlyRecharge = listOf(
                    storedRecharge(date = LocalDate(2025, 3, 5), recordId = 6, recoveryIndicator = 72)
                )
            )
        )

        val contexts = service.getDayContexts(
            startDate = LocalDate(2025, 3, 3),
            endDateExclusive = LocalDate(2025, 3, 6)
        )

        assertEquals(
            listOf(LocalDate(2025, 3, 3), LocalDate(2025, 3, 4), LocalDate(2025, 3, 5)),
            contexts.map { it.date }
        )
        assertEquals(2, contexts.first().activities.size)
        assertEquals(1, contexts[1].sleepResults.size)
        assertEquals(1, contexts[2].trainingSessions.size)
        assertEquals(1, contexts[2].nightlyRecharge.size)
    }

    @Test
    fun `getDayContexts filters out records outside requested range`() = runTest {
        val service = PolarInsightService(
            FakePolarSyncRepository(
                activities = listOf(
                    storedActivity(date = LocalDate(2025, 3, 2), recordId = 1, totalSteps = 3000),
                    storedActivity(date = LocalDate(2025, 3, 3), recordId = 2, totalSteps = 8000),
                    storedActivity(date = LocalDate(2025, 3, 6), recordId = 3, totalSteps = 9000)
                )
            )
        )

        val contexts = service.getDayContexts(
            startDate = LocalDate(2025, 3, 3),
            endDateExclusive = LocalDate(2025, 3, 6)
        )

        assertEquals(listOf(LocalDate(2025, 3, 3), LocalDate(2025, 3, 4), LocalDate(2025, 3, 5)), contexts.map { it.date })
        assertEquals(1, contexts.first().activities.size)
        assertTrue(contexts.drop(1).all { it.activities.isEmpty() })
    }

    private fun storedActivity(date: LocalDate, recordId: Long, totalSteps: Int) = StoredPolarActivity(
        recordId = recordId,
        externalId = "activity:$recordId",
        source = "Polar",
        localDate = date,
        startedAt = null,
        syncedAt = Clock.System.now(),
        data = PolarDailyActivity(
            date = date.toString(),
            totalSteps = totalSteps,
            stepSampleStartTime = "00:00:00",
            stepSampleIntervalMs = 60000,
            stepSamples = listOf(totalSteps)
        )
    )

    private fun storedSleep(date: LocalDate, recordId: Long, durationSeconds: Long) = StoredPolarSleepResult(
        recordId = recordId,
        externalId = "sleep:$recordId",
        source = "Polar",
        localDate = date,
        startedAt = "${date}T00:00:00Z",
        endedAt = "${date}T08:00:00Z",
        syncedAt = Clock.System.now(),
        data = PolarSleepResult(
            date = date.toString(),
            sleepStart = "${date}T00:00:00Z",
            sleepEnd = "${date}T08:00:00Z",
            durationSeconds = durationSeconds,
            deepSleepSeconds = 7200,
            remSleepSeconds = 5400,
            lightSleepSeconds = 14400,
            awakeSeconds = 1800,
            efficiencyPercent = 91.0,
            continuityIndex = 4.1,
            interruptionCount = 2,
            longInterruptionCount = 0,
            sleepScore = 84.0,
            remScore = 80.0,
            deepSleepScore = 82.0,
            scoreRate = 4
        )
    )

    private fun storedTraining(date: LocalDate, recordId: Long, durationSeconds: Long) = StoredPolarTrainingSession(
        recordId = recordId,
        externalId = "training:$recordId",
        source = "Polar",
        localDate = date,
        startedAt = "${date}T08:00:00",
        syncedAt = Clock.System.now(),
        data = PolarTrainingSession(
            id = "training-$recordId",
            startTime = "${date}T08:00:00",
            durationSeconds = durationSeconds,
            sportId = "1",
            calories = 430,
            distanceMeters = 5000.0,
            averageHeartRate = 148,
            maxHeartRate = 172,
            trainingBenefit = "Tempo run"
        )
    )

    private fun storedRecharge(date: LocalDate, recordId: Long, recoveryIndicator: Int) = StoredPolarNightlyRecharge(
        recordId = recordId,
        externalId = "recharge:$recordId",
        source = "Polar",
        localDate = date,
        syncedAt = Clock.System.now(),
        data = PolarNightlyRecharge(
            date = date.toString(),
            ansStatus = 4.2,
            ansRate = 3,
            recoveryIndicator = recoveryIndicator,
            recoveryIndicatorSubLevel = 2,
            hrvRmssd = 42,
            hrvMeanRri = 900,
            baselineRmssd = 40,
            baselineRmssdSd = 5,
            baselineRri = 880,
            baselineRriSd = 30
        )
    )
}
