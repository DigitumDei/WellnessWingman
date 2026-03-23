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
    fun `getSleep maps ISO 8601 durations to seconds`() = runTest {
        val http = createClient {
            respond(
                content = """{
                    "nightSleeps": [{
                        "sleepDate": "2025-03-15",
                        "duration": "PT8H30M",
                        "deepSleep": "PT1H45M",
                        "remSleep": "PT2H",
                        "lowLightSleep": "PT3H15M",
                        "awakeTime": "PT30M"
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getSleep("token", "2025-03-15", "2025-03-16")
        assertTrue(result.isSuccess)
        val sleep = result.getOrThrow()[0]

        assertEquals("2025-03-15", sleep.date)
        assertEquals(30600L, sleep.durationSeconds)       // 8h30m
        assertEquals(6300L, sleep.deepSleepSeconds)        // 1h45m
        assertEquals(7200L, sleep.remSleepSeconds)          // 2h
        assertEquals(11700L, sleep.lightSleepSeconds)       // 3h15m
        assertEquals(1800L, sleep.awakeSeconds)              // 30m
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
                        "recoveryIndicatorSubLevel": 37
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
    fun `partial sleep response defaults missing durations to zero`() = runTest {
        val http = createClient {
            respond(
                content = """{"nightSleeps": [{"sleepDate": "2025-03-15"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val result = PolarApiClient(http).getSleep("token", "2025-03-15", "2025-03-16")
        assertTrue(result.isSuccess)
        val sleep = result.getOrThrow()[0]
        assertEquals(0L, sleep.durationSeconds)
        assertEquals(0L, sleep.deepSleepSeconds)
        assertEquals(0L, sleep.remSleepSeconds)
        assertEquals(0L, sleep.lightSleepSeconds)
        assertEquals(0L, sleep.awakeSeconds)
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
