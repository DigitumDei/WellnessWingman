package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.polar.StoredPolarActivity
import com.wellnesswingman.data.model.polar.StoredPolarNightlyRecharge
import com.wellnesswingman.data.model.polar.StoredPolarSleepResult
import com.wellnesswingman.data.model.polar.StoredPolarTrainingSession
import com.wellnesswingman.data.repository.PolarSyncRepository
import kotlin.math.round
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Bridges synced Polar records into product-facing day and week surfaces without
 * collapsing them into the manual / screenshot tracking pipeline.
 *
 * Precedence rule:
 * Manual or LLM-derived tracked entries remain the primary source for narrative
 * sleep / exercise data when they exist for the same day. Polar data supplements
 * gaps and always remains explicitly attributed as Polar.
 */
class PolarInsightService(
    private val polarSyncRepository: PolarSyncRepository
) {
    suspend fun getDayContext(date: LocalDate): PolarDayContext {
        val endExclusive = date.plus(DatePeriod(days = 1))
        return PolarDayContext(
            date = date,
            activities = polarSyncRepository.getActivities(date, endExclusive),
            sleepResults = polarSyncRepository.getSleepResults(date, endExclusive),
            trainingSessions = polarSyncRepository.getTrainingSessions(date, endExclusive),
            nightlyRecharge = polarSyncRepository.getNightlyRecharge(date, endExclusive)
        )
    }

    suspend fun getDayContexts(startDate: LocalDate, endDateExclusive: LocalDate): List<PolarDayContext> {
        val activities = polarSyncRepository.getActivities(startDate, endDateExclusive).groupBy { it.localDate }
        val sleepResults = polarSyncRepository.getSleepResults(startDate, endDateExclusive).groupBy { it.localDate }
        val trainingSessions = polarSyncRepository.getTrainingSessions(startDate, endDateExclusive).groupBy { it.localDate }
        val nightlyRecharge = polarSyncRepository.getNightlyRecharge(startDate, endDateExclusive).groupBy { it.localDate }

        val dates = linkedSetOf<LocalDate>()
        var current = startDate
        while (current < endDateExclusive) {
            dates += current
            current = current.plus(DatePeriod(days = 1))
        }
        return dates.sorted().map { date ->
            PolarDayContext(
                date = date,
                activities = activities[date].orEmpty(),
                sleepResults = sleepResults[date].orEmpty(),
                trainingSessions = trainingSessions[date].orEmpty(),
                nightlyRecharge = nightlyRecharge[date].orEmpty()
            )
        }
    }
}

data class PolarDayContext(
    val date: LocalDate,
    val activities: List<StoredPolarActivity> = emptyList(),
    val sleepResults: List<StoredPolarSleepResult> = emptyList(),
    val trainingSessions: List<StoredPolarTrainingSession> = emptyList(),
    val nightlyRecharge: List<StoredPolarNightlyRecharge> = emptyList()
) {
    val hasData: Boolean
        get() = activities.isNotEmpty() || sleepResults.isNotEmpty() || trainingSessions.isNotEmpty() || nightlyRecharge.isNotEmpty()

    val totalSteps: Int?
        get() = activities.maxOfOrNull { it.data.totalSteps }

    val sleepDurationHours: Double?
        get() = sleepResults.maxByOrNull { it.data.durationSeconds }?.data?.durationSeconds?.div(3600.0)

    val sleepScore: Double?
        get() = sleepResults.maxByOrNull { it.data.sleepScore }?.data?.sleepScore

    val exerciseSessionCount: Int
        get() = trainingSessions.size

    fun buildPromptLines(
        includeSleep: Boolean,
        includeExercise: Boolean
    ): List<String> {
        if (!hasData) return emptyList()

        return buildList {
            totalSteps?.let { add("  - Steps (Polar): $it") }
            if (includeSleep) {
                sleepResults.maxByOrNull { it.data.durationSeconds }?.data?.let { sleep ->
                    add(
                        buildString {
                            append("  - Sleep (Polar): ${formatHours(sleep.durationSeconds)}h")
                            append(", sleep score ${sleep.sleepScore.toInt()}")
                            append(", efficiency ${sleep.efficiencyPercent.toInt()}%")
                        }
                    )
                }
            }
            nightlyRecharge.maxByOrNull { it.data.recoveryIndicator }?.data?.let { recharge ->
                add(
                    "  - Recovery (Polar Nightly Recharge): ANS rate ${recharge.ansRate}/5, recovery ${recharge.recoveryIndicator}, HRV RMSSD ${recharge.hrvRmssd} ms"
                )
            }
            if (includeExercise) {
                trainingSessions.forEach { session ->
                    add(
                        buildString {
                            append("  - Exercise (Polar): ${formatMinutes(session.data.durationSeconds)} min")
                            if (session.data.calories > 0) append(", ${session.data.calories} kcal")
                            if (session.data.distanceMeters > 0) append(", ${(session.data.distanceMeters / 1000.0)} km")
                            if (session.data.averageHeartRate > 0) append(", avg HR ${session.data.averageHeartRate}")
                            session.data.trainingBenefit.takeIf { it.isNotBlank() }?.let { append(", benefit: $it") }
                        }
                    )
                }
            }
        }
    }

    private fun formatHours(seconds: Long): String = formatOneDecimal(seconds / 3600.0)

    private fun formatMinutes(seconds: Long): String = formatOneDecimal(seconds / 60.0)

    private fun formatOneDecimal(value: Double): String = (round(value * 10.0) / 10.0).toString()
}
