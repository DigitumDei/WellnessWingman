package com.wellnesswingman.data.model.polar

import kotlinx.serialization.Serializable

/**
 * Normalized daily activity summary from the Polar API.
 *
 * Requires the `features` query param: samples.
 * Step samples are a dense time-series: one value per [stepSampleIntervalMs]
 * starting at [stepSampleStartTime].
 */
@Serializable
data class PolarDailyActivity(
    val date: String,
    val totalSteps: Int,
    // Dense step time-series
    val stepSampleStartTime: String,
    val stepSampleIntervalMs: Long,
    val stepSamples: List<Int>
)

/**
 * Normalized sleep result from the Polar API.
 * All durations are in seconds.
 *
 * Requires the `features` query param: sleep-result, sleep-evaluation, sleep-score.
 * With features enabled, the API limits date range to 1 day per request.
 */
@Serializable
data class PolarSleepResult(
    val date: String,
    // Timing
    val sleepStart: String,
    val sleepEnd: String,
    // Phase durations (seconds)
    val durationSeconds: Long,
    val deepSleepSeconds: Long,
    val remSleepSeconds: Long,
    val lightSleepSeconds: Long,
    val awakeSeconds: Long,
    // Quality metrics
    val efficiencyPercent: Double,
    val continuityIndex: Double,
    val interruptionCount: Int,
    val longInterruptionCount: Int,
    // Polar sleep scores (0–100 scale, scoreRate is 1–5)
    val sleepScore: Double,
    val remScore: Double,
    val deepSleepScore: Double,
    val scoreRate: Int
)

/**
 * Normalized training session from the Polar API.
 */
@Serializable
data class PolarTrainingSession(
    val id: String,
    val startTime: String,
    val durationSeconds: Long,
    val sportId: String,
    val calories: Int,
    val distanceMeters: Double,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val trainingBenefit: String
)

/**
 * Normalized nightly recharge result from the Polar API.
 *
 * HRV values are measured during the first hours of sleep (recovery window).
 * Baseline values require ~28 days of continuous data to establish.
 */
@Serializable
data class PolarNightlyRecharge(
    val date: String,
    val ansStatus: Double,
    val ansRate: Int,
    val recoveryIndicator: Int,
    val recoveryIndicatorSubLevel: Int,
    /** RMSSD during the recovery window, in milliseconds. Primary HRV metric. */
    val hrvRmssd: Int,
    /** Mean R-R interval during recovery, in milliseconds. */
    val hrvMeanRri: Int,
    /** Personal RMSSD baseline (0 until ~28 days of data). */
    val baselineRmssd: Int,
    /** Standard deviation of personal RMSSD baseline. */
    val baselineRmssdSd: Int,
    /** Personal R-R interval baseline (0 until ~28 days of data). */
    val baselineRri: Int,
    /** Standard deviation of personal R-R interval baseline. */
    val baselineRriSd: Int
)

/**
 * User physical profile from the Polar API.
 * Fetched once via [PolarApiClient.getUserProfile] — does not require date params.
 */
@Serializable
data class PolarUserProfile(
    val birthday: String,
    val sex: String,
    val heightCm: Double,
    val weightKg: Double,
    val restingHeartRate: Int,
    val maxHeartRate: Int,
    val vo2Max: Int,
    val trainingBackground: String,
    /** Sleep goal in seconds (e.g. 25200 = 7 hours). */
    val sleepGoalSeconds: Long,
    /** Weekly recovery time sum in hours. */
    val weeklyRecoveryTimeHours: Double
)

/**
 * Sealed error hierarchy for Polar API failures.
 * Works with [Result.failure] for structured error handling.
 */
sealed class PolarApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Access token is invalid or expired — caller should refresh and retry. */
    data class Unauthorized(val body: String = "") : PolarApiError("Polar API returned 401: $body")

    /** Rate limit exceeded — caller should back off. */
    data class RateLimited(val body: String = "") : PolarApiError("Polar API rate limit exceeded: $body")

    /** Server-side error (5xx). */
    data class ServerError(val statusCode: Int, val body: String = "") : PolarApiError("Polar API server error $statusCode: $body")

    /** Network-level failure (DNS, timeout, connection reset, etc.). */
    data class NetworkError(override val cause: Throwable) : PolarApiError("Polar API network error: ${cause.message}", cause)

    /** Response was 2xx but the body was missing required fields or was unparseable. */
    data class InvalidResponse(val detail: String) : PolarApiError("Polar API invalid response: $detail")
}
