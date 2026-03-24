package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.polar.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PolarApiClientTest {

    private fun createClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // --- Auth header and query params ---

    @Test
    fun `requests include Bearer token and date query params`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val http = createClient { request ->
            capturedRequest = request
            respond("""{"activityDays":[]}""", HttpStatusCode.OK, jsonHeaders)
        }

        val client = PolarApiClient(http)
        client.getActivities("my-token", "2025-01-01", "2025-01-31")

        val req = capturedRequest!!
        assertEquals("Bearer my-token", req.headers[HttpHeaders.Authorization])
        assertTrue(req.url.toString().contains("from=2025-01-01"))
        assertTrue(req.url.toString().contains("to=2025-01-31"))
    }

    // --- Activity endpoint ---

    @Test
    fun `getActivities maps response to domain models`() = runTest {
        val http = createClient {
            respond(
                content = """{
                    "activityDays": [{
                        "date": "2025-03-15",
                        "activitiesPerDevice": [
                            {"activeSteps": 5000, "activeCalories": 200},
                            {"activeSteps": 3000, "activeCalories": 150}
                        ],
                        "activitySamples": {
                            "stepSamples": [
                                {"time": "10:00:00", "steps": 120},
                                {"time": "11:00:00", "steps": 340}
                            ]
                        }
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getActivities("token", "2025-03-15", "2025-03-16")
        assertTrue(result.isSuccess)
        val activities = result.getOrThrow()
        assertEquals(1, activities.size)

        val day = activities[0]
        assertEquals("2025-03-15", day.date)
        assertEquals(8000, day.totalSteps)
        assertEquals(350, day.activeCalories)
        assertEquals(2, day.stepSamples.size)
        assertEquals(120, day.stepSamples[0].steps)
    }

    @Test
    fun `getActivities skips days with null date`() = runTest {
        val http = createClient {
            respond(
                content = """{"activityDays": [{"activitiesPerDevice": [{"activeSteps": 100}]}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getActivities("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    // --- Sleep endpoint ---

    @Test
    fun `getSleep includes features query params`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val http = createClient { request ->
            capturedRequest = request
            respond("""{"nightSleeps":[]}""", HttpStatusCode.OK, jsonHeaders)
        }

        PolarApiClient(http).getSleep("token", "2025-03-15", "2025-03-16")

        val url = capturedRequest!!.url.toString()
        assertTrue(url.contains("features=sleep-result"), "Missing sleep-result feature")
        assertTrue(url.contains("features=sleep-evaluation"), "Missing sleep-evaluation feature")
        assertTrue(url.contains("features=sleep-score"), "Missing sleep-score feature")
    }

    @Test
    fun `getSleep maps full response to domain models`() = runTest {
        val http = createClient {
            respond(
                content = """{
                    "nightSleeps": [{
                        "sleepDate": "2025-03-22",
                        "sleepResult": {
                            "hypnogram": {
                                "sleepStart": "2025-03-21T21:42:19.418+02:00",
                                "sleepEnd": "2025-03-22T06:29:19.418+02:00"
                            }
                        },
                        "sleepEvaluation": {
                            "phaseDurations": {
                                "wake": "1020s",
                                "rem": "5970s",
                                "light": "18210s",
                                "deep": "6420s"
                            },
                            "interruptions": {
                                "totalCount": 22,
                                "longCount": 2
                            },
                            "analysis": {
                                "efficiencyPercent": 96.77,
                                "continuityIndex": 4.8
                            }
                        },
                        "sleepScore": {
                            "sleepScore": 90.84,
                            "remScore": 77.97,
                            "n3Score": 85.58,
                            "scoreRate": 5
                        }
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getSleep("token", "2025-03-22", "2025-03-23")
        assertTrue(result.isSuccess)
        val sleep = result.getOrThrow()[0]

        assertEquals("2025-03-22", sleep.date)
        assertEquals("2025-03-21T21:42:19.418+02:00", sleep.sleepStart)
        assertEquals("2025-03-22T06:29:19.418+02:00", sleep.sleepEnd)
        assertEquals(31620L, sleep.durationSeconds)
        assertEquals(6420L, sleep.deepSleepSeconds)
        assertEquals(5970L, sleep.remSleepSeconds)
        assertEquals(18210L, sleep.lightSleepSeconds)
        assertEquals(1020L, sleep.awakeSeconds)
        assertEquals(96.77, sleep.efficiencyPercent)
        assertEquals(4.8, sleep.continuityIndex)
        assertEquals(22, sleep.interruptionCount)
        assertEquals(2, sleep.longInterruptionCount)
        assertEquals(90.84, sleep.sleepScore)
        assertEquals(77.97, sleep.remScore)
        assertEquals(85.58, sleep.deepSleepScore)
        assertEquals(5, sleep.scoreRate)
    }

    // --- Training sessions endpoint ---

    @Test
    fun `getTrainingSessions maps response to domain models`() = runTest {
        val http = createClient {
            respond(
                content = """{
                    "trainingSessions": [{
                        "identifier": {"id": "sess-1"},
                        "startTime": "2025-03-15T08:01:03",
                        "durationMillis": 2294836,
                        "sport": {"id": "27"},
                        "calories": 472,
                        "distanceMeters": 4972.0,
                        "hrAvg": 160,
                        "hrMax": 183,
                        "trainingBenefit": "TRAINING_BENEFIT_TEMPO_AND_MAXIMUM_TRAINING"
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getTrainingSessions("token", "2025-03-15T00:00:00", "2025-03-16T00:00:00")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()[0]

        assertEquals("sess-1", session.id)
        assertEquals("2025-03-15T08:01:03", session.startTime)
        assertEquals(2294L, session.durationSeconds) // 2294836ms / 1000
        assertEquals("27", session.sportId)
        assertEquals(472, session.calories)
        assertEquals(4972.0, session.distanceMeters)
        assertEquals(160, session.averageHeartRate)
        assertEquals(183, session.maxHeartRate)
        assertEquals("TRAINING_BENEFIT_TEMPO_AND_MAXIMUM_TRAINING", session.trainingBenefit)
    }

    @Test
    fun `getTrainingSessions skips entries with null identifier`() = runTest {
        val http = createClient {
            respond(
                content = """{"trainingSessions": [{"calories": 100}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getTrainingSessions("token", "2025-01-01T00:00:00", "2025-01-02T00:00:00")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    // --- Nightly recharge endpoint ---

    @Test
    fun `getNightlyRecharge maps response to domain models`() = runTest {
        val http = createClient {
            respond(
                content = """{
                    "nightlyRechargeResults": [{
                        "sleepResultDate": "2025-03-15",
                        "ansStatus": -4.49,
                        "ansRate": 2,
                        "recoveryIndicator": 3,
                        "recoveryIndicatorSubLevel": 37,
                        "meanNightlyRecoveryRmssd": 30,
                        "meanNightlyRecoveryRri": 758,
                        "meanBaselineRmssd": 0,
                        "sdBaselineRmssd": 4,
                        "meanBaselineRri": 0,
                        "sdBaselineRri": 51
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getNightlyRecharge("token", "2025-03-15", "2025-03-16")
        assertTrue(result.isSuccess)
        val recharge = result.getOrThrow()[0]

        assertEquals("2025-03-15", recharge.date)
        assertEquals(-4.49, recharge.ansStatus)
        assertEquals(2, recharge.ansRate)
        assertEquals(3, recharge.recoveryIndicator)
        assertEquals(37, recharge.recoveryIndicatorSubLevel)
        assertEquals(30, recharge.hrvRmssd)
        assertEquals(758, recharge.hrvMeanRri)
        assertEquals(0, recharge.baselineRmssd)
        assertEquals(4, recharge.baselineRmssdSd)
        assertEquals(0, recharge.baselineRri)
        assertEquals(51, recharge.baselineRriSd)
    }

    // --- Error mapping ---

    @Test
    fun `401 response returns Unauthorized error`() = runTest {
        val http = createClient {
            respond("""{"error":"invalid_token"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }

        val result = PolarApiClient(http).getActivities("bad-token", "2025-01-01", "2025-01-02")
        assertTrue(result.isFailure)
        assertIs<PolarApiError.Unauthorized>(result.exceptionOrNull())
    }

    @Test
    fun `429 response returns RateLimited error`() = runTest {
        val http = createClient {
            respond("""{"error":"rate_limit"}""", HttpStatusCode.TooManyRequests, jsonHeaders)
        }

        val result = PolarApiClient(http).getSleep("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isFailure)
        assertIs<PolarApiError.RateLimited>(result.exceptionOrNull())
    }

    @Test
    fun `500 response returns ServerError`() = runTest {
        val http = createClient {
            respond("""{"error":"internal"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }

        val result = PolarApiClient(http).getTrainingSessions("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<PolarApiError.ServerError>(error)
        assertEquals(500, error.statusCode)
    }

    @Test
    fun `404 response returns empty list instead of error`() = runTest {
        val http = createClient {
            respond("", HttpStatusCode.NotFound)
        }

        val result = PolarApiClient(http).getTrainingSessions("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `network exception returns NetworkError`() = runTest {
        val http = createClient {
            throw RuntimeException("Connection refused")
        }

        val result = PolarApiClient(http).getNightlyRecharge("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isFailure)
        assertIs<PolarApiError.NetworkError>(result.exceptionOrNull())
    }

    // --- Empty and partial responses ---

    @Test
    fun `empty response lists are handled gracefully`() = runTest {
        val http = createClient {
            respond("""{"activityDays":[]}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result = PolarApiClient(http).getActivities("token", "2025-01-01", "2025-01-02")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `sleep without sleepEvaluation is skipped as unscored`() = runTest {
        val http = createClient {
            respond(
                content = """{"nightSleeps": [{"sleepDate": "2025-03-15"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getSleep("token", "2025-03-15", "2025-03-16")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `training session with missing optional fields uses defaults`() = runTest {
        val http = createClient {
            respond(
                content = """{"trainingSessions": [{"identifier": {"id": "s1"}}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getTrainingSessions("token", "2025-01-01T00:00:00", "2025-01-02T00:00:00")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()[0]
        assertEquals("s1", session.id)
        assertEquals("", session.startTime)
        assertEquals(0L, session.durationSeconds)
        assertEquals("", session.sportId)
        assertEquals(0, session.calories)
        assertEquals(0.0, session.distanceMeters)
        assertEquals(0, session.averageHeartRate)
        assertEquals(0, session.maxHeartRate)
        assertEquals("", session.trainingBenefit)
    }
}
