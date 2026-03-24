package com.wellnesswingman.domain.polar

import com.wellnesswingman.data.model.PolarOAuthConfig
import com.wellnesswingman.data.model.polar.*
import com.wellnesswingman.data.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolarSyncOrchestratorTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `sync imports records and updates checkpoints`() = runTest {
        val settings = FakeAppSettingsRepository().apply {
            setPolarAccessToken("token")
            setPolarRefreshToken("refresh")
            setPolarTokenExpiresAt(Long.MAX_VALUE)
            setPolarUserId("polar-user-1")
        }
        val syncRepository = InMemoryPolarSyncRepository()
        val orchestrator = PolarSyncOrchestrator(
            appSettingsRepository = settings,
            polarOAuthRepository = PolarOAuthRepository(settings, PolarOAuthConfig("client", "https://broker.test"), createNoOpHttpClient()),
            polarApiClient = PolarApiClient(createSyncHttpClient()),
            polarSyncRepository = syncRepository
        )

        val result = orchestrator.sync(PolarSyncTrigger.MANUAL_REFRESH)

        assertEquals(PolarSyncOutcome.SUCCESS, result.outcome)
        assertTrue(result.metricResults.sumOf { it.importedCount } >= 5)
        assertEquals(1, syncRepository.activities.size)
        assertEquals(1, syncRepository.sleepResults.size)
        assertEquals(1, syncRepository.trainingSessions.size)
        assertEquals(1, syncRepository.nightlyRecharge.size)
        assertNotNull(syncRepository.getCheckpoint(PolarMetricFamily.ACTIVITY))
        assertNotNull(syncRepository.getCheckpoint(PolarMetricFamily.USER_PROFILE))
        assertEquals(182.0, settings.getHeight())
    }

    @Test
    fun `partial failure keeps prior checkpoint and existing data`() = runTest {
        val settings = FakeAppSettingsRepository().apply {
            setPolarAccessToken("token")
            setPolarRefreshToken("refresh")
            setPolarTokenExpiresAt(Long.MAX_VALUE)
            setPolarUserId("polar-user-1")
        }
        val syncRepository = InMemoryPolarSyncRepository().apply {
            updateCheckpoint(
                PolarSyncCheckpoint(
                    metricFamily = PolarMetricFamily.ACTIVITY,
                    lastSyncCursor = "2026-03-20",
                    lastSuccessfulSyncAt = Instant.parse("2026-03-20T10:00:00Z")
                )
            )
            upsertActivities(
                listOf(
                    PolarDailyActivity(
                        date = "2026-03-20",
                        totalSteps = 900,
                        stepSampleStartTime = "00:00:00",
                        stepSampleIntervalMs = 60000,
                        stepSamples = listOf(900)
                    )
                ),
                Instant.parse("2026-03-20T10:00:00Z")
            )
        }
        val orchestrator = PolarSyncOrchestrator(
            appSettingsRepository = settings,
            polarOAuthRepository = PolarOAuthRepository(settings, PolarOAuthConfig("client", "https://broker.test"), createNoOpHttpClient()),
            polarApiClient = PolarApiClient(createPartialFailureHttpClient()),
            polarSyncRepository = syncRepository
        )

        val result = orchestrator.sync(PolarSyncTrigger.MANUAL_REFRESH)

        assertEquals(PolarSyncOutcome.PARTIAL_FAILURE, result.outcome)
        val activityResult = result.metricResults.first { it.metricFamily == PolarMetricFamily.ACTIVITY }
        assertNotNull(activityResult.failureMessage)
        assertEquals(1, syncRepository.activities.size)
        assertEquals("2026-03-20", syncRepository.getCheckpoint(PolarMetricFamily.ACTIVITY)?.lastSyncCursor)
        assertNotNull(syncRepository.getCheckpoint(PolarMetricFamily.ACTIVITY)?.lastFailureMessage)
    }

    private fun createSyncHttpClient(): HttpClient {
        return HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/activity/list") ->
                    respond(
                        """{"activityDays":[{"date":"2026-03-23","activitiesPerDevice":[{"activitySamples":[{"stepSamples":{"startTime":"00:00:00","interval":60000,"steps":[1200]}}]}]}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                request.url.encodedPath.endsWith("/sleeps") ->
                    respond(
                        """{"nightSleeps":[{"sleepDate":"2026-03-23","sleepResult":{"hypnogram":{"sleepStart":"2026-03-22T22:00:00Z","sleepEnd":"2026-03-23T06:00:00Z"}},"sleepEvaluation":{"phaseDurations":{"wake":"600s","rem":"5400s","light":"16200s","deep":"6600s"},"interruptions":{"totalCount":4,"longCount":1},"analysis":{"efficiencyPercent":95.0,"continuityIndex":4.5}},"sleepScore":{"sleepScore":85.0,"remScore":80.0,"n3Score":82.0,"scoreRate":4}}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                request.url.encodedPath.endsWith("/training-sessions/list") ->
                    respond(
                        """{"trainingSessions":[{"identifier":{"id":"session-1"},"startTime":"2026-03-23T07:30:00","durationMillis":3600000,"sport":{"id":"1"},"calories":400,"distanceMeters":5000.0,"hrAvg":150,"hrMax":175,"trainingBenefit":"Benefit"}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                request.url.encodedPath.endsWith("/nightly-recharge-results") ->
                    respond(
                        """{"nightlyRechargeResults":[{"sleepResultDate":"2026-03-23","ansStatus":1.0,"ansRate":3,"recoveryIndicator":70,"recoveryIndicatorSubLevel":2,"meanNightlyRecoveryRmssd":55,"meanNightlyRecoveryRri":900,"meanBaselineRmssd":50,"sdBaselineRmssd":5,"meanBaselineRri":880,"sdBaselineRri":30}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                request.url.encodedPath.endsWith("/user/account-data") ->
                    respond(
                        """{"physicalInformation":{"birthday":"1992-04-05","sex":"FEMALE","height":182.0,"weight":72.0,"restingHeartRate":50,"maximumHeartRate":185,"vo2Max":47,"trainingBackground":"REGULAR","sleepGoal":"28800","weeklyRecoveryTimeSum":14.5}}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    private fun createPartialFailureHttpClient(): HttpClient {
        return HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/activity/list") ->
                    respond("""{"error":"server"}""", HttpStatusCode.InternalServerError, jsonHeaders)
                request.url.encodedPath.endsWith("/sleeps") ->
                    respond("""{"nightSleeps":[]}""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/training-sessions/list") ->
                    respond("""{"trainingSessions":[]}""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/nightly-recharge-results") ->
                    respond("""{"nightlyRechargeResults":[]}""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath.endsWith("/user/account-data") ->
                    respond(
                        """{"physicalInformation":{"birthday":"1992-04-05","sex":"FEMALE","height":182.0,"weight":72.0,"restingHeartRate":50,"maximumHeartRate":185,"vo2Max":47,"trainingBackground":"REGULAR","sleepGoal":"28800","weeklyRecoveryTimeSum":14.5}}""",
                        HttpStatusCode.OK,
                        jsonHeaders
                    )
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    private fun createNoOpHttpClient(): HttpClient {
        return HttpClient(MockEngine {
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }
}

private class InMemoryPolarSyncRepository : PolarSyncRepository {
    val activities = linkedMapOf<String, StoredPolarActivity>()
    val sleepResults = linkedMapOf<String, StoredPolarSleepResult>()
    val trainingSessions = linkedMapOf<String, StoredPolarTrainingSession>()
    val nightlyRecharge = linkedMapOf<String, StoredPolarNightlyRecharge>()
    private val profiles = linkedMapOf<String, StoredPolarUserProfile>()
    private val checkpoints = linkedMapOf<PolarMetricFamily, PolarSyncCheckpoint>()
    private var nextId = 1L

    override suspend fun upsertActivities(activities: List<PolarDailyActivity>, syncedAt: Instant): Int {
        activities.forEach { activity ->
            val key = "activity:${activity.date}"
            this.activities[key] = StoredPolarActivity(nextId++, key, "Polar", LocalDate.parse(activity.date), "${activity.date}T${activity.stepSampleStartTime}", syncedAt, activity)
        }
        return activities.size
    }

    override suspend fun getActivities(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarActivity> =
        activities.values.filter { it.localDate >= startDate && it.localDate < endDateExclusive }

    override suspend fun upsertSleepResults(results: List<PolarSleepResult>, syncedAt: Instant): Int {
        results.forEach { sleep ->
            val key = "sleep:${sleep.date}"
            sleepResults[key] = StoredPolarSleepResult(nextId++, key, "Polar", LocalDate.parse(sleep.date), sleep.sleepStart, sleep.sleepEnd, syncedAt, sleep)
        }
        return results.size
    }

    override suspend fun getSleepResults(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarSleepResult> =
        sleepResults.values.filter { it.localDate >= startDate && it.localDate < endDateExclusive }

    override suspend fun upsertTrainingSessions(sessions: List<PolarTrainingSession>, syncedAt: Instant): Int {
        sessions.forEach { session ->
            trainingSessions[session.id] = StoredPolarTrainingSession(nextId++, session.id, "Polar", LocalDate.parse(session.startTime.take(10)), session.startTime, syncedAt, session)
        }
        return sessions.size
    }

    override suspend fun getTrainingSessions(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarTrainingSession> =
        trainingSessions.values.filter { it.localDate >= startDate && it.localDate < endDateExclusive }

    override suspend fun upsertNightlyRecharge(results: List<PolarNightlyRecharge>, syncedAt: Instant): Int {
        results.forEach { recharge ->
            val key = "nightly-recharge:${recharge.date}"
            nightlyRecharge[key] = StoredPolarNightlyRecharge(nextId++, key, "Polar", LocalDate.parse(recharge.date), syncedAt, recharge)
        }
        return results.size
    }

    override suspend fun getNightlyRecharge(startDate: LocalDate, endDateExclusive: LocalDate): List<StoredPolarNightlyRecharge> =
        nightlyRecharge.values.filter { it.localDate >= startDate && it.localDate < endDateExclusive }

    override suspend fun upsertUserProfile(userId: String, profile: PolarUserProfile, syncedAt: Instant) {
        val key = "profile:$userId"
        profiles[key] = StoredPolarUserProfile(nextId++, key, "Polar", LocalDate.parse("2026-03-24"), syncedAt, profile)
    }

    override suspend fun getUserProfile(userId: String): StoredPolarUserProfile? = profiles["profile:$userId"]

    override suspend fun getCheckpoint(metricFamily: PolarMetricFamily): PolarSyncCheckpoint? = checkpoints[metricFamily]

    override suspend fun getAllCheckpoints(): List<PolarSyncCheckpoint> = checkpoints.values.toList()

    override suspend fun updateCheckpoint(checkpoint: PolarSyncCheckpoint) {
        checkpoints[checkpoint.metricFamily] = checkpoint
    }

    override suspend fun clearCheckpoint(metricFamily: PolarMetricFamily) {
        checkpoints.remove(metricFamily)
    }

    override suspend fun clearAll() {
        activities.clear()
        sleepResults.clear()
        trainingSessions.clear()
        nightlyRecharge.clear()
        profiles.clear()
        checkpoints.clear()
    }
}

private class FakeAppSettingsRepository : AppSettingsRepository {
    private val stringValues = mutableMapOf<String, String>()
    private val doubleValues = mutableMapOf<String, Double>()
    private val longValues = mutableMapOf<String, Long>()

    override fun getApiKey(provider: LlmProvider): String? = null
    override fun setApiKey(provider: LlmProvider, apiKey: String) = Unit
    override fun removeApiKey(provider: LlmProvider) = Unit
    override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENAI
    override fun setSelectedProvider(provider: LlmProvider) = Unit
    override fun getModel(provider: LlmProvider): String? = null
    override fun setModel(provider: LlmProvider, model: String) = Unit
    override fun clear() = Unit
    override fun getHeight(): Double? = doubleValues["height"]
    override fun setHeight(height: Double) { doubleValues["height"] = height }
    override fun getHeightUnit(): String = "cm"
    override fun setHeightUnit(unit: String) = Unit
    override fun getSex(): String? = stringValues["sex"]
    override fun setSex(sex: String) { stringValues["sex"] = sex }
    override fun getCurrentWeight(): Double? = doubleValues["weight"]
    override fun setCurrentWeight(weight: Double) { doubleValues["weight"] = weight }
    override fun getWeightUnit(): String = "kg"
    override fun setWeightUnit(unit: String) = Unit
    override fun getDateOfBirth(): String? = stringValues["dob"]
    override fun setDateOfBirth(dob: String) { stringValues["dob"] = dob }
    override fun getActivityLevel(): String? = null
    override fun setActivityLevel(level: String) = Unit
    override fun clearHeight() { doubleValues.remove("height") }
    override fun clearCurrentWeight() { doubleValues.remove("weight") }
    override fun clearProfileData() = Unit
    override fun getImageRetentionThresholdDays(): Int = 30
    override fun setImageRetentionThresholdDays(days: Int) = Unit
    override fun getPolarAccessToken(): String? = stringValues["polar_access_token"]
    override fun setPolarAccessToken(token: String) { stringValues["polar_access_token"] = token }
    override fun getPolarRefreshToken(): String? = stringValues["polar_refresh_token"]
    override fun setPolarRefreshToken(token: String) { stringValues["polar_refresh_token"] = token }
    override fun getPolarTokenExpiresAt(): Long = longValues["polar_expires_at"] ?: 0L
    override fun setPolarTokenExpiresAt(expiresAt: Long) { longValues["polar_expires_at"] = expiresAt }
    override fun getPolarUserId(): String? = stringValues["polar_user_id"]
    override fun setPolarUserId(userId: String) { stringValues["polar_user_id"] = userId }
    override fun getPendingOAuthState(): String? = null
    override fun setPendingOAuthState(state: String) = Unit
    override fun getPendingOAuthSessionId(): String? = null
    override fun setPendingOAuthSessionId(sessionId: String) = Unit
    override fun clearPendingOAuthSession() = Unit
    override fun clearPolarTokens() {
        stringValues.remove("polar_access_token")
        stringValues.remove("polar_refresh_token")
        longValues.remove("polar_expires_at")
        stringValues.remove("polar_user_id")
    }
    override fun isPolarConnected(): Boolean = getPolarAccessToken() != null
}
