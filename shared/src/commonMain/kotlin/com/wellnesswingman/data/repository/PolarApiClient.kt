package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.polar.*
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * Stateless client for the Polar AccessLink v4 data endpoints.
 *
 * Each method takes an [accessToken] directly — the caller (typically the sync
 * orchestrator) is responsible for obtaining and refreshing tokens.
 *
 * All date parameters use ISO 8601 `YYYY-MM-DD` format.
 * `from` is inclusive, `to` is exclusive.
 */
class PolarApiClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://www.polaraccesslink.com/v4/data"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
        }
    }

    /**
     * Fetches daily activity summaries for the given date range.
     * Maximum range: 90 days.
     */
    suspend fun getActivities(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarDailyActivity>> = executeApiCall("activity/list", accessToken, from, to) { response ->
        val dto: PolarActivityListResponse = response.body()
        dto.activityDays.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches sleep results for the given date range.
     * Maximum range: 30 days.
     */
    suspend fun getSleep(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarSleepResult>> = executeApiCall("sleeps", accessToken, from, to) { response ->
        val dto: PolarSleepListResponse = response.body()
        dto.sleeps.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches training sessions for the given date range.
     */
    suspend fun getTrainingSessions(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarTrainingSession>> = executeApiCall("training-sessions", accessToken, from, to) { response ->
        val dto: PolarTrainingListResponse = response.body()
        dto.trainingSessions.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches nightly recharge results for the given date range.
     * Maximum range: 28 days.
     */
    suspend fun getNightlyRecharge(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarNightlyRecharge>> = executeApiCall("nightly-recharge-results", accessToken, from, to) { response ->
        val dto: PolarNightlyRechargeListResponse = response.body()
        dto.nightlyRechargeResults.mapNotNull { it.toDomain() }
    }

    // --- Internal helpers ---

    private suspend fun <T> executeApiCall(
        path: String,
        accessToken: String,
        from: String,
        to: String,
        transform: suspend (HttpResponse) -> T
    ): Result<T> {
        return try {
            val response = httpClient.get("$BASE_URL/$path") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                url {
                    parameters.append("from", from)
                    parameters.append("to", to)
                }
            }

            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    val body = response.bodyAsText()
                    Napier.w("Polar API 401 on $path: $body")
                    Result.failure(PolarApiError.Unauthorized(body))
                }
                response.status == HttpStatusCode.TooManyRequests -> {
                    val body = response.bodyAsText()
                    Napier.w("Polar API 429 on $path: $body")
                    Result.failure(PolarApiError.RateLimited(body))
                }
                response.status.value in 500..599 -> {
                    val body = response.bodyAsText()
                    Napier.e("Polar API ${response.status.value} on $path: $body")
                    Result.failure(PolarApiError.ServerError(response.status.value, body))
                }
                response.status.isSuccess() -> {
                    Result.success(transform(response))
                }
                else -> {
                    val body = response.bodyAsText()
                    Napier.e("Polar API unexpected ${response.status.value} on $path: $body")
                    Result.failure(PolarApiError.ServerError(response.status.value, body))
                }
            }
        } catch (e: PolarApiError) {
            Result.failure(e)
        } catch (e: Exception) {
            Napier.e("Polar API network error on $path", e)
            Result.failure(PolarApiError.NetworkError(e))
        }
    }
}

// --- DTO → Domain mapping ---

private fun PolarActivityDayDto.toDomain(): PolarDailyActivity? {
    val d = date ?: return null
    val totalSteps = activitiesPerDevice.sumOf { it.activeSteps ?: 0 }
    val totalCalories = activitiesPerDevice.sumOf { it.activeCalories ?: 0 }
    val samples = activitySamples?.stepSamples?.mapNotNull { sample ->
        val time = sample.time ?: return@mapNotNull null
        PolarStepSample(time = time, steps = sample.steps ?: 0)
    } ?: emptyList()

    return PolarDailyActivity(
        date = d,
        totalSteps = totalSteps,
        activeCalories = totalCalories,
        stepSamples = samples
    )
}

private fun PolarSleepDto.toDomain(): PolarSleepResult? {
    val d = sleepResultDate ?: return null
    return PolarSleepResult(
        date = d,
        durationSeconds = parseIso8601DurationToSeconds(duration),
        deepSleepSeconds = parseIso8601DurationToSeconds(deepSleep),
        remSleepSeconds = parseIso8601DurationToSeconds(remSleep),
        lightSleepSeconds = parseIso8601DurationToSeconds(lowLightSleep),
        awakeSeconds = parseIso8601DurationToSeconds(awakeTime)
    )
}

private fun PolarTrainingSessionDto.toDomain(): PolarTrainingSession? {
    val sessionId = id ?: return null
    return PolarTrainingSession(
        id = sessionId,
        startTime = startTime ?: "",
        durationSeconds = parseIso8601DurationToSeconds(duration),
        sport = sport ?: "",
        calories = calories ?: 0,
        distanceMeters = distance ?: 0.0,
        averageHeartRate = heartRate?.average ?: 0,
        maxHeartRate = heartRate?.maximum ?: 0,
        trainingLoad = trainingLoad ?: 0.0
    )
}

private fun PolarNightlyRechargeDto.toDomain(): PolarNightlyRecharge? {
    val d = sleepResultDate ?: return null
    return PolarNightlyRecharge(
        date = d,
        ansStatus = ansStatus ?: "",
        ansRate = ansRate ?: 0.0,
        recoveryIndicator = recoveryIndicator ?: "",
        recoveryIndicatorSubLevel = recoveryIndicatorSubLevel ?: ""
    )
}

/**
 * Parses an ISO 8601 duration string (e.g. "PT8H30M") to total seconds.
 * Returns 0 if the input is null or unparseable.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun parseIso8601DurationToSeconds(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return try {
        Duration.parseIsoString(iso).inWholeSeconds
    } catch (_: Exception) {
        0L
    }
}
