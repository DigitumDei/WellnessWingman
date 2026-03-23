package com.wellnesswingman.data.model.polar

/**
 * Normalized daily activity summary from the Polar API.
 */
data class PolarDailyActivity(
    val date: String,
    val totalSteps: Int,
    val activeCalories: Int,
    val stepSamples: List<PolarStepSample>
)

data class PolarStepSample(
    val time: String,
    val steps: Int
)

/**
 * Normalized sleep result from the Polar API.
 * All durations are in seconds.
 */
data class PolarSleepResult(
    val date: String,
    val durationSeconds: Long,
    val deepSleepSeconds: Long,
    val remSleepSeconds: Long,
    val lightSleepSeconds: Long,
    val awakeSeconds: Long
)

/**
 * Normalized training session from the Polar API.
 */
data class PolarTrainingSession(
    val id: String,
    val startTime: String,
    val durationSeconds: Long,
    val sport: String,
    val calories: Int,
    val distanceMeters: Double,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val trainingLoad: Double
)

/**
 * Normalized nightly recharge result from the Polar API.
 */
data class PolarNightlyRecharge(
    val date: String,
    val ansStatus: String,
    val ansRate: Double,
    val recoveryIndicator: String,
    val recoveryIndicatorSubLevel: String
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
}
