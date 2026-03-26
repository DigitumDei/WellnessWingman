package com.wellnesswingman.ui.screens.calendar

import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.domain.polar.PolarDayContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekStateLogicTest {

    @Test
    fun `week counts include supplemental Polar sleep and exercise without double counting tracked days`() {
        val timeZone = TimeZone.UTC
        val trackedExerciseDate = LocalDate(2025, 3, 4)
        val trackedSleepDate = LocalDate(2025, 3, 5)

        val counts = calculateWeekEntryCounts(
            completedEntries = listOf(
                completedEntry(1, EntryType.MEAL, LocalDate(2025, 3, 3), timeZone),
                completedEntry(2, EntryType.EXERCISE, trackedExerciseDate, timeZone),
                completedEntry(3, EntryType.SLEEP, trackedSleepDate, timeZone)
            ),
            polarContexts = listOf(
                PolarDayContext(
                    date = LocalDate(2025, 3, 3),
                    activities = listOf(storedActivity(LocalDate(2025, 3, 3))),
                    nightlyRecharge = listOf(storedNightlyRecharge(LocalDate(2025, 3, 3)))
                ),
                PolarDayContext(
                    date = trackedExerciseDate,
                    trainingSessions = listOf(storedTrainingSession(trackedExerciseDate))
                ),
                PolarDayContext(
                    date = LocalDate(2025, 3, 6),
                    trainingSessions = listOf(
                        storedTrainingSession(LocalDate(2025, 3, 6)),
                        storedTrainingSession(LocalDate(2025, 3, 6), externalId = "train-2")
                    )
                ),
                PolarDayContext(
                    date = trackedSleepDate,
                    sleepResults = listOf(storedSleepResult(trackedSleepDate))
                ),
                PolarDayContext(
                    date = LocalDate(2025, 3, 7),
                    sleepResults = listOf(storedSleepResult(LocalDate(2025, 3, 7)))
                )
            ),
            timeZone = timeZone
        )

        assertEquals(
            EntryCounts(
                mealCount = 1,
                exerciseCount = 3,
                sleepCount = 2,
                otherCount = 1,
                totalEntries = 7
            ),
            counts
        )
    }

    private fun completedEntry(
        id: Long,
        type: EntryType,
        date: LocalDate,
        timeZone: TimeZone
    ) = TrackedEntry(
        entryId = id,
        entryType = type,
        capturedAt = date.atStartOfDayIn(timeZone),
        processingStatus = ProcessingStatus.COMPLETED
    )

    private fun storedActivity(date: LocalDate) = StoredPolarActivity(
        recordId = 1,
        externalId = "activity-$date",
        source = "POLAR",
        localDate = date,
        startedAt = "${date}T00:00:00Z",
        syncedAt = Instant.parse("2025-03-08T00:00:00Z"),
        data = PolarDailyActivity(
            date = date.toString(),
            totalSteps = 8000,
            stepSampleStartTime = "${date}T00:00:00Z",
            stepSampleIntervalMs = 300000,
            stepSamples = emptyList()
        )
    )

    private fun storedNightlyRecharge(date: LocalDate) = StoredPolarNightlyRecharge(
        recordId = 1,
        externalId = "recharge-$date",
        source = "POLAR",
        localDate = date,
        syncedAt = Instant.parse("2025-03-08T00:00:00Z"),
        data = PolarNightlyRecharge(
            date = date.toString(),
            ansStatus = 3.5,
            ansRate = 4,
            recoveryIndicator = 3,
            recoveryIndicatorSubLevel = 2,
            hrvRmssd = 42,
            hrvMeanRri = 900,
            baselineRmssd = 40,
            baselineRmssdSd = 5,
            baselineRri = 880,
            baselineRriSd = 15
        )
    )

    private fun storedSleepResult(date: LocalDate) = StoredPolarSleepResult(
        recordId = 1,
        externalId = "sleep-$date",
        source = "POLAR",
        localDate = date,
        startedAt = "${date}T22:00:00Z",
        endedAt = "${date}T06:00:00Z",
        syncedAt = Instant.parse("2025-03-08T00:00:00Z"),
        data = PolarSleepResult(
            date = date.toString(),
            sleepStart = "${date}T22:00:00Z",
            sleepEnd = "${date}T06:00:00Z",
            durationSeconds = 8 * 60 * 60L,
            deepSleepSeconds = 90 * 60L,
            remSleepSeconds = 100 * 60L,
            lightSleepSeconds = 260 * 60L,
            awakeSeconds = 30 * 60L,
            sleepScore = 82.0,
            efficiencyPercent = 91.0,
            continuityIndex = 4.0,
            interruptionCount = 1,
            longInterruptionCount = 0,
            remScore = 80.0,
            deepSleepScore = 78.0,
            scoreRate = 4
        )
    )

    private fun storedTrainingSession(date: LocalDate, externalId: String = "train-1") = StoredPolarTrainingSession(
        recordId = 1,
        externalId = externalId,
        source = "POLAR",
        localDate = date,
        startedAt = "${date}T06:00:00Z",
        syncedAt = Instant.parse("2025-03-08T00:00:00Z"),
        data = PolarTrainingSession(
            id = externalId,
            startTime = "${date}T06:00:00Z",
            durationSeconds = 45 * 60L,
            sportId = "running",
            calories = 320,
            distanceMeters = 5000.0,
            averageHeartRate = 140,
            maxHeartRate = 165,
            trainingBenefit = "Improved endurance"
        )
    )
}
