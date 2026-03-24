package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.polar.*
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlin.coroutines.cancellation.CancellationException
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

/**
 * Stateless client for the Polar AccessLink v4 data endpoints.
 *
 * Each method takes an [accessToken] directly — the caller (typically the sync
 * orchestrator) is responsible for obtaining and refreshing tokens.
 *
 * Most endpoints use ISO 8601 `YYYY-MM-DD` date params.
 * Training sessions requires full datetime `YYYY-MM-DDTHH:MM:SS`.
 * `from` is inclusive, `to` is exclusive.
 */
class PolarApiClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://www.polaraccesslink.com/v4/data"

        private val ACTIVITY_FEATURES = listOf("samples")
        private val SLEEP_FEATURES = listOf("sleep-result", "sleep-evaluation", "sleep-score")

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
     * Maximum range: 28 days.
     */
    suspend fun getActivities(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarDailyActivity>> = executeApiCall(
        path = "activity/list",
        accessToken = accessToken,
        from = from,
        to = to,
        emptyResult = emptyList(),
        extraParams = mapOf("features" to ACTIVITY_FEATURES)
    ) { response ->
        val dto: PolarActivityListResponse = response.body()
        dto.activityDays.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches sleep results for the given date range.
     * Maximum range: 1 day (API constraint when features are requested).
     * The caller must paginate day-by-day for multi-day ranges.
     */
    suspend fun getSleep(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarSleepResult>> = executeApiCall(
        path = "sleeps",
        accessToken = accessToken,
        from = from,
        to = to,
        emptyResult = emptyList(),
        extraParams = mapOf("features" to SLEEP_FEATURES)
    ) { response ->
        val dto: PolarSleepListResponse = response.body()
        dto.nightSleeps.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches training sessions for the given date range.
     * Note: this endpoint requires full datetime params (e.g. `2025-03-21T00:00:00`).
     */
    suspend fun getTrainingSessions(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarTrainingSession>> = executeApiCall("training-sessions/list", accessToken, from, to, emptyList()) { response ->
        val dto: PolarTrainingListResponse = response.body()
        dto.trainingSessions.mapNotNull { it.toDomain() }
    }

    /**
     * Fetches the user's physical profile (height, weight, resting HR, VO2max, etc.).
     * No date range required — returns the current profile state.
     */
    suspend fun getUserProfile(
        accessToken: String
    ): Result<PolarUserProfile> {
        return try {
            val response = httpClient.get("$BASE_URL/user/account-data") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }

            when {
                response.status == HttpStatusCode.Unauthorized -> {
                    val body = response.bodyAsText()
                    Napier.w("Polar API 401 on user/account-data: $body")
                    Result.failure(PolarApiError.Unauthorized(body))
                }
                response.status == HttpStatusCode.TooManyRequests -> {
                    val body = response.bodyAsText()
                    Napier.w("Polar API 429 on user/account-data: $body")
                    Result.failure(PolarApiError.RateLimited(body))
                }
                response.status.value in 500..599 -> {
                    val body = response.bodyAsText()
                    Napier.e("Polar API ${response.status.value} on user/account-data: $body")
                    Result.failure(PolarApiError.ServerError(response.status.value, body))
                }
                response.status.isSuccess() -> {
                    val dto: PolarAccountDataResponse = response.body()
                    val profile = dto.physicalInformation?.toDomain()
                    if (profile != null) {
                        Result.success(profile)
                    } else {
                        Result.failure(PolarApiError.ServerError(200, "Missing physicalInformation"))
                    }
                }
                else -> {
                    val body = response.bodyAsText()
                    Napier.e("Polar API unexpected ${response.status.value} on user/account-data: $body")
                    Result.failure(PolarApiError.ServerError(response.status.value, body))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Polar API network error on user/account-data", e)
            Result.failure(PolarApiError.NetworkError(e))
        }
    }

    /**
     * Fetches nightly recharge results for the given date range.
     * Maximum range: 28 days.
     */
    suspend fun getNightlyRecharge(
        accessToken: String,
        from: String,
        to: String
    ): Result<List<PolarNightlyRecharge>> = executeApiCall("nightly-recharge-results", accessToken, from, to, emptyList()) { response ->
        val dto: PolarNightlyRechargeListResponse = response.body()
        dto.nightlyRechargeResults.mapNotNull { it.toDomain() }
    }

    // --- Internal helpers ---

    private suspend fun <T> executeApiCall(
        path: String,
        accessToken: String,
        from: String,
        to: String,
        emptyResult: T,
        extraParams: Map<String, List<String>> = emptyMap(),
        transform: suspend (HttpResponse) -> T
    ): Result<T> {
        return try {
            val response = httpClient.get("$BASE_URL/$path") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                url {
                    parameters.append("from", from)
                    parameters.append("to", to)
                    extraParams.forEach { (key, values) ->
                        values.forEach { parameters.append(key, it) }
                    }
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
                response.status == HttpStatusCode.NotFound -> {
                    Napier.d("Polar API 404 on $path — no data for range")
                    Result.success(emptyResult)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Polar API network error on $path", e)
            Result.failure(PolarApiError.NetworkError(e))
        }
    }
}

// --- DTO → Domain mapping ---

private fun PolarActivityDayDto.toDomain(): PolarDailyActivity? {
    val d = date ?: return null
    // Merge step samples across all devices (typically just one)
    val allStepSamples = activitiesPerDevice
        .flatMap { it.activitySamples }
        .mapNotNull { it.stepSamples }
    val firstSamples = allStepSamples.firstOrNull()
    val steps = firstSamples?.steps ?: emptyList()

    return PolarDailyActivity(
        date = d,
        totalSteps = steps.sum(),
        stepSampleStartTime = firstSamples?.startTime ?: "00:00:00",
        stepSampleIntervalMs = firstSamples?.interval ?: 60000L,
        stepSamples = steps
    )
}

private fun PolarSleepDto.toDomain(): PolarSleepResult? {
    val d = sleepDate ?: return null
    val eval = sleepEvaluation ?: return null
    val phases = eval.phaseDurations ?: return null
    val deepSec = parsePolarDurationToSeconds(phases.deep)
    val remSec = parsePolarDurationToSeconds(phases.rem)
    val lightSec = parsePolarDurationToSeconds(phases.light)
    val awakeSec = parsePolarDurationToSeconds(phases.wake)
    val totalSec = deepSec + remSec + lightSec + awakeSec

    return PolarSleepResult(
        date = d,
        sleepStart = sleepResult?.hypnogram?.sleepStart ?: "",
        sleepEnd = sleepResult?.hypnogram?.sleepEnd ?: "",
        durationSeconds = totalSec,
        deepSleepSeconds = deepSec,
        remSleepSeconds = remSec,
        lightSleepSeconds = lightSec,
        awakeSeconds = awakeSec,
        efficiencyPercent = eval.analysis?.efficiencyPercent ?: 0.0,
        continuityIndex = eval.analysis?.continuityIndex ?: 0.0,
        interruptionCount = eval.interruptions?.totalCount ?: 0,
        longInterruptionCount = eval.interruptions?.longCount ?: 0,
        sleepScore = sleepScore?.sleepScore ?: 0.0,
        remScore = sleepScore?.remScore ?: 0.0,
        deepSleepScore = sleepScore?.n3Score ?: 0.0,
        scoreRate = sleepScore?.scoreRate ?: 0
    )
}

private fun PolarTrainingSessionDto.toDomain(): PolarTrainingSession? {
    val sessionId = identifier?.id ?: return null
    return PolarTrainingSession(
        id = sessionId,
        startTime = startTime ?: "",
        durationSeconds = (durationMillis ?: 0L) / 1000L,
        sportId = sport?.id ?: "",
        calories = calories ?: 0,
        distanceMeters = distanceMeters ?: 0.0,
        averageHeartRate = hrAvg ?: 0,
        maxHeartRate = hrMax ?: 0,
        trainingBenefit = trainingBenefit ?: ""
    )
}

private fun PolarNightlyRechargeDto.toDomain(): PolarNightlyRecharge? {
    val d = sleepResultDate ?: return null
    return PolarNightlyRecharge(
        date = d,
        ansStatus = ansStatus ?: 0.0,
        ansRate = ansRate ?: 0,
        recoveryIndicator = recoveryIndicator ?: 0,
        recoveryIndicatorSubLevel = recoveryIndicatorSubLevel ?: 0,
        hrvRmssd = meanNightlyRecoveryRmssd ?: 0,
        hrvMeanRri = meanNightlyRecoveryRri ?: 0,
        baselineRmssd = meanBaselineRmssd ?: 0,
        baselineRmssdSd = sdBaselineRmssd ?: 0,
        baselineRri = meanBaselineRri ?: 0,
        baselineRriSd = sdBaselineRri ?: 0
    )
}

private fun PolarPhysicalInfoDto.toDomain(): PolarUserProfile {
    return PolarUserProfile(
        birthday = birthday ?: "",
        sex = sex ?: "",
        heightCm = height ?: 0.0,
        weightKg = weight ?: 0.0,
        restingHeartRate = restingHeartRate ?: 0,
        maxHeartRate = maximumHeartRate ?: 0,
        vo2Max = vo2Max ?: 0,
        trainingBackground = trainingBackground ?: "",
        sleepGoalSeconds = sleepGoal?.toLongOrNull() ?: 0L,
        weeklyRecoveryTimeHours = weeklyRecoveryTimeSum ?: 0.0
    )
}

/**
 * Parses a Polar-style duration string (e.g. "220s", "3.5s") to whole seconds.
 * Returns 0 if the input is null or unparseable.
 */
private fun parsePolarDurationToSeconds(duration: String?): Long {
    if (duration.isNullOrBlank()) return 0L
    return try {
        duration.removeSuffix("s").toDouble().toLong()
    } catch (_: Exception) {
        0L
    }
}
