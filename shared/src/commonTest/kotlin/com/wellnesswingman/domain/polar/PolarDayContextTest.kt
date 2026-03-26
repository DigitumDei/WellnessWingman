package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.PolarDailyActivity
import com.wellnesswingman.data.model.polar.PolarNightlyRecharge
import com.wellnesswingman.data.model.polar.PolarSleepResult
import com.wellnesswingman.data.model.polar.PolarTrainingSession
import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolarDayContextTest {

    @Test
    fun `buildPromptLines includes Polar metrics when enabled`() {
        val context = PolarDayContext(
            date = LocalDate(2025, 3, 3),
            activities = listOf(
                StoredPolarActivity(
                    recordId = 1,
                    externalId = "activity:1",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = null,
                    syncedAt = Clock.System.now(),
                    data = PolarDailyActivity("2025-03-03", 8123, "00:00:00", 60000, listOf(8123))
                )
            ),
            sleepResults = listOf(
                StoredPolarSleepResult(
                    recordId = 2,
                    externalId = "sleep:2",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = "2025-03-02T23:00:00Z",
                    endedAt = "2025-03-03T07:00:00Z",
                    syncedAt = Clock.System.now(),
                    data = PolarSleepResult("2025-03-03", "2025-03-02T23:00:00Z", "2025-03-03T07:00:00Z", 28800, 7200, 5400, 14400, 1800, 91.0, 4.1, 2, 0, 84.0, 80.0, 82.0, 4)
                )
            ),
            trainingSessions = listOf(
                StoredPolarTrainingSession(
                    recordId = 3,
                    externalId = "training:3",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = "2025-03-03T08:00:00",
                    syncedAt = Clock.System.now(),
                    data = PolarTrainingSession("session-3", "2025-03-03T08:00:00", 3600, "1", 430, 5000.0, 148, 172, "Tempo run")
                )
            ),
            nightlyRecharge = listOf(
                StoredPolarNightlyRecharge(
                    recordId = 4,
                    externalId = "recharge:4",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    syncedAt = Clock.System.now(),
                    data = PolarNightlyRecharge("2025-03-03", 4.2, 3, 72, 2, 42, 900, 40, 5, 880, 30)
                )
            )
        )

        val lines = context.buildPromptLines(includeSleep = true, includeExercise = true)

        assertEquals(4, lines.size)
        assertTrue(lines.any { it.contains("Steps (Polar): 8123") })
        assertTrue(lines.any { it.contains("Sleep (Polar): 8.0h") })
        assertTrue(lines.any { it.contains("Recovery (Polar Nightly Recharge):") })
        assertTrue(lines.any { it.contains("Exercise (Polar): 60.0 min") })
    }

    @Test
    fun `buildPromptLines omits sleep and exercise when disabled`() {
        val context = PolarDayContext(
            date = LocalDate(2025, 3, 3),
            activities = listOf(
                StoredPolarActivity(
                    recordId = 1,
                    externalId = "activity:1",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = null,
                    syncedAt = Clock.System.now(),
                    data = PolarDailyActivity("2025-03-03", 5000, "00:00:00", 60000, listOf(5000))
                )
            ),
            sleepResults = listOf(
                StoredPolarSleepResult(
                    recordId = 2,
                    externalId = "sleep:2",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = "2025-03-02T23:00:00Z",
                    endedAt = "2025-03-03T07:00:00Z",
                    syncedAt = Clock.System.now(),
                    data = PolarSleepResult("2025-03-03", "2025-03-02T23:00:00Z", "2025-03-03T07:00:00Z", 28800, 7200, 5400, 14400, 1800, 91.0, 4.1, 2, 0, 84.0, 80.0, 82.0, 4)
                )
            ),
            trainingSessions = listOf(
                StoredPolarTrainingSession(
                    recordId = 3,
                    externalId = "training:3",
                    source = "Polar",
                    localDate = LocalDate(2025, 3, 3),
                    startedAt = "2025-03-03T08:00:00",
                    syncedAt = Clock.System.now(),
                    data = PolarTrainingSession("session-3", "2025-03-03T08:00:00", 3600, "1", 430, 5000.0, 148, 172, "Tempo run")
                )
            )
        )

        val lines = context.buildPromptLines(includeSleep = false, includeExercise = false)

        assertEquals(listOf("  - Steps (Polar): 5000"), lines)
    }
}
