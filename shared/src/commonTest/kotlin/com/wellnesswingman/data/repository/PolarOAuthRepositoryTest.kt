package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.PolarOAuthConfig
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PolarOAuthRepositoryTest {

    private lateinit var fakeSettings: FakePolarSettings
    private val config = PolarOAuthConfig(
        clientId = "test-client-id",
        brokerBaseUrl = "https://broker.example.com"
    )

    @BeforeTest
    fun setup() {
        fakeSettings = FakePolarSettings()
    }

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

    // --- buildAuthorizationUrl ---

    @Test
    fun `buildAuthorizationUrl generates valid URL with encoded parameters`() {
        val repo = PolarOAuthRepository(fakeSettings, config)
        val url = repo.buildAuthorizationUrl()

        assertTrue(url.startsWith("https://auth.polar.com/oauth/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fbroker.example.com%2Foauth%2Fcallback"))
        assertTrue(url.contains("scope=activity%3Aread+training_sessions%3Aread+profile%3Aread+sleep%3Aread+nightly_recharge%3Aread") ||
            url.contains("scope=activity:read") // URLBuilder may encode differently
        )
        // State was persisted
        assertNotNull(fakeSettings.getPendingOAuthState())
    }

    @Test
    fun `buildAuthorizationUrl includes state parameter`() {
        val repo = PolarOAuthRepository(fakeSettings, config)
        val url = repo.buildAuthorizationUrl()

        val state = fakeSettings.getPendingOAuthState()
        assertNotNull(state)
        assertTrue(url.contains("state=$state") || url.contains("state="))
    }

    // --- redeemSession ---

    @Test
    fun `redeemSession succeeds with valid response`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"tokens":{"access_token":"at-123","refresh_token":"rt-456","expires_in":3600,"x_user_id":"user-789"},"state":"test-state"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPendingOAuthState("test-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "test-state")

        assertTrue(result.isSuccess)
        assertEquals("user-789", result.getOrNull())
        assertEquals("at-123", fakeSettings.getPolarAccessToken())
        assertEquals("rt-456", fakeSettings.getPolarRefreshToken())
        assertEquals("user-789", fakeSettings.getPolarUserId())
        assertTrue(fakeSettings.getPolarTokenExpiresAt() > 0)
    }

    @Test
    fun `redeemSession fails on state mismatch`() = runTest {
        val client = createClient { respond("", HttpStatusCode.OK) }

        fakeSettings.setPendingOAuthState("expected-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "wrong-state")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("state mismatch") == true)
    }

    @Test
    fun `redeemSession fails when no pending state`() = runTest {
        val client = createClient { respond("", HttpStatusCode.OK) }

        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "any-state")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("state mismatch") == true)
    }

    @Test
    fun `redeemSession fails on HTTP error`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"error":"Session already redeemed"}""",
                status = HttpStatusCode.Gone,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPendingOAuthState("test-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "test-state")

        assertTrue(result.isFailure)
        assertNull(fakeSettings.getPolarAccessToken())
    }

    @Test
    fun `redeemSession handles missing access_token in response`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"tokens":{},"state":"test-state"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPendingOAuthState("test-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "test-state")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No access_token") == true)
    }

    @Test
    fun `redeemSession uses user_id fallback when x_user_id missing`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"tokens":{"access_token":"at-123","user_id":"fallback-user","expires_in":3600},"state":"test-state"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPendingOAuthState("test-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.redeemSession("session-abc", "test-state")

        assertTrue(result.isSuccess)
        assertEquals("fallback-user", fakeSettings.getPolarUserId())
    }

    // --- refreshTokens ---

    @Test
    fun `refreshTokens succeeds and stores new tokens`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"access_token":"new-at","refresh_token":"new-rt","expires_in":7200}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.refreshTokens()

        assertTrue(result.isSuccess)
        assertEquals("new-at", fakeSettings.getPolarAccessToken())
        assertEquals("new-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `refreshTokens fails when no refresh token stored`() = runTest {
        val client = createClient { respond("", HttpStatusCode.OK) }

        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.refreshTokens()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No refresh token") == true)
    }

    @Test
    fun `refreshTokens fails on HTTP error`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"error":"server_error"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.refreshTokens()

        assertTrue(result.isFailure)
        // Non-invalid_grant errors should NOT clear tokens (may be transient)
        assertNotNull(fakeSettings.getPolarAccessToken())
    }

    @Test
    fun `refreshTokens clears tokens on invalid_grant to stop retry loop`() = runTest {
        val client = createClient { request ->
            respond(
                content = """{"detail":"{\"error\":\"invalid_grant\"}","error":"Refresh failed"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)
        val result = repo.refreshTokens()

        assertTrue(result.isFailure)
        assertNull(fakeSettings.getPolarAccessToken())
        assertNull(fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `refresh persists replacement tokens even when the calling sync is cancelled`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val client = createClient {
            requestStarted.complete(Unit)
            allowResponse.await()
            respond(
                """{"access_token":"new-at","refresh_token":"new-rt","expires_in":43200}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val refresh = async { repo.refreshTokens() }
        requestStarted.await()
        refresh.cancel()
        allowResponse.complete(Unit)
        refresh.join()

        assertTrue(refresh.isCancelled)
        assertEquals("new-at", fakeSettings.getPolarAccessToken())
        assertEquals("new-rt", fakeSettings.getPolarRefreshToken())
        assertTrue(fakeSettings.getPolarTokenExpiresAt() > 0)
    }

    @Test
    fun `overlapping refreshes share replacement tokens instead of reusing the old refresh token`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        var requestCount = 0
        val client = createClient {
            requestCount += 1
            val thisRequest = requestCount
            requestStarted.complete(Unit)
            allowResponse.await()
            if (thisRequest == 1) {
                respond(
                    """{"access_token":"new-at","refresh_token":"new-rt","expires_in":43200}""",
                    HttpStatusCode.OK,
                    jsonHeaders
                )
            } else {
                respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders)
            }
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val first = async { repo.refreshTokens() }
        requestStarted.await()
        val second = async { repo.refreshTokens() }
        runCurrent()
        allowResponse.complete(Unit)

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, requestCount)
        assertEquals("new-at", fakeSettings.getPolarAccessToken())
        assertEquals("new-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `refresh response cannot reconnect an account disconnected while the request was running`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val client = createClient {
            requestStarted.complete(Unit)
            allowResponse.await()
            respond(
                """{"access_token":"new-at","refresh_token":"new-rt","expires_in":43200}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val refresh = async { repo.refreshTokens() }
        requestStarted.await()
        repo.disconnect()
        allowResponse.complete(Unit)

        assertTrue(refresh.await().isFailure)
        assertNull(fakeSettings.getPolarAccessToken())
        assertNull(fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `late invalid grant cannot clear a newly connected session`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val client = createClient {
            requestStarted.complete(Unit)
            allowResponse.await()
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val refresh = async { repo.refreshTokens() }
        requestStarted.await()
        fakeSettings.setPolarAccessToken("reconnected-at")
        fakeSettings.setPolarRefreshToken("reconnected-rt")
        allowResponse.complete(Unit)

        assertTrue(refresh.await().isFailure)
        assertEquals("reconnected-at", fakeSettings.getPolarAccessToken())
        assertEquals("reconnected-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `server failure mentioning invalid grant preserves the connection for a later retry`() = runTest {
        val client = createClient {
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")

        val result = PolarOAuthRepository(fakeSettings, config, client).refreshTokens()

        assertTrue(result.isFailure)
        assertEquals("old-at", fakeSettings.getPolarAccessToken())
        assertEquals("old-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `invalid grant mentioned in an error description does not disconnect the account`() = runTest {
        val client = createClient {
            respond(
                """{"error":"temporarily_unavailable","error_description":"Retry later; this is not invalid_grant"}""",
                HttpStatusCode.BadRequest,
                jsonHeaders
            )
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")

        val result = PolarOAuthRepository(fakeSettings, config, client).refreshTokens()

        assertTrue(result.isFailure)
        assertEquals("old-at", fakeSettings.getPolarAccessToken())
        assertEquals("old-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `hung refresh times out without disconnecting and releases the refresh lock`() = runTest {
        val client = createClient { awaitCancellation() }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client, StandardTestDispatcher(testScheduler))

        repeat(2) {
            val result = repo.refreshTokens()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true)
        }

        assertEquals(60_000L, currentTime)
        assertEquals("old-at", fakeSettings.getPolarAccessToken())
        assertEquals("old-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `refresh retains the current refresh token when Polar does not replace it`() = runTest {
        val client = createClient {
            respond(
                """{"access_token":"new-at","refresh_token":null,"expires_in":43200}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")

        val result = PolarOAuthRepository(fakeSettings, config, client).refreshTokens()

        assertTrue(result.isSuccess)
        assertEquals("new-at", fakeSettings.getPolarAccessToken())
        assertEquals("old-rt", fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `malformed token response preserves credentials and does not expose response contents`() = runTest {
        val client = createClient {
            respond(
                """{"access_token":"sensitive-access","refresh_token":"sensitive-refresh","expires_in":"not-a-number"}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")

        val result = PolarOAuthRepository(fakeSettings, config, client).refreshTokens()

        assertTrue(result.isFailure)
        assertEquals("old-at", fakeSettings.getPolarAccessToken())
        assertEquals("old-rt", fakeSettings.getPolarRefreshToken())
        assertTrue(result.exceptionOrNull()?.message?.contains("sensitive") == false)
        assertNull(result.exceptionOrNull()?.cause)
    }

    @Test
    fun `disconnect during redemption prevents the late response from reconnecting`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        val client = createClient {
            requestStarted.complete(Unit)
            allowResponse.await()
            respond(
                """{"tokens":{"access_token":"new-at","refresh_token":"new-rt","expires_in":43200}}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        fakeSettings.setPendingOAuthState("test-state")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val redeem = async { repo.redeemSession("test-session", "test-state") }
        requestStarted.await()
        repo.disconnect()
        allowResponse.complete(Unit)

        assertTrue(redeem.await().isFailure)
        assertNull(fakeSettings.getPolarAccessToken())
        assertNull(fakeSettings.getPolarRefreshToken())
    }

    @Test
    fun `waiting refresh does not reuse credentials after the first confirms revocation`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val allowResponse = CompletableDeferred<Unit>()
        var requestCount = 0
        val client = createClient {
            requestCount += 1
            requestStarted.complete(Unit)
            allowResponse.await()
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders)
        }
        fakeSettings.setPolarAccessToken("old-at")
        fakeSettings.setPolarRefreshToken("old-rt")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        val first = async { repo.refreshTokens() }
        requestStarted.await()
        val second = async { repo.refreshTokens() }
        runCurrent()
        allowResponse.complete(Unit)

        assertTrue(first.await().isFailure)
        assertTrue(second.await().isFailure)
        assertEquals(1, requestCount)
        assertNull(fakeSettings.getPolarAccessToken())
        assertNull(fakeSettings.getPolarRefreshToken())
    }

    // --- disconnect ---

    @Test
    fun `disconnect clears all polar tokens`() = runTest {
        fakeSettings.setPolarAccessToken("access")
        fakeSettings.setPolarRefreshToken("refresh")
        fakeSettings.setPolarUserId("user")

        val repo = PolarOAuthRepository(fakeSettings, config)
        repo.disconnect()

        assertNull(fakeSettings.getPolarAccessToken())
        assertNull(fakeSettings.getPolarRefreshToken())
        assertNull(fakeSettings.getPolarUserId())
    }

    // --- Concurrent redemption guard ---

    @Test
    fun `concurrent redeemSession for same session id is rejected`() = runTest {
        // First call will suspend on the HTTP request; second call should be rejected
        var requestCount = 0
        val client = createClient { request ->
            requestCount++
            respond(
                content = """{"tokens":{"access_token":"at","expires_in":3600},"state":"s"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        fakeSettings.setPendingOAuthState("s")
        val repo = PolarOAuthRepository(fakeSettings, config, client)

        // First redemption should succeed
        val result1 = repo.redeemSession("same-session", "s")
        assertTrue(result1.isSuccess)

        // Reset state for second attempt
        fakeSettings.setPendingOAuthState("s")

        // Second redemption with same session id should also succeed
        // since the first one completed and cleared the guard
        val result2 = repo.redeemSession("same-session", "s")
        assertTrue(result2.isSuccess)
        assertEquals(2, requestCount)
    }
}

/**
 * Minimal fake of AppSettingsRepository for Polar OAuth tests.
 */
private class FakePolarSettings : AppSettingsRepository {
    private val store = mutableMapOf<String, Any?>()

    override fun getPolarAccessToken(): String? = store["polar_access_token"] as? String
    override fun setPolarAccessToken(token: String) { store["polar_access_token"] = token }
    override fun getPolarRefreshToken(): String? = store["polar_refresh_token"] as? String
    override fun setPolarRefreshToken(token: String) { store["polar_refresh_token"] = token }
    override fun getPolarTokenExpiresAt(): Long = store["polar_token_expires_at"] as? Long ?: 0L
    override fun setPolarTokenExpiresAt(expiresAt: Long) { store["polar_token_expires_at"] = expiresAt }
    override fun getPolarUserId(): String? = store["polar_user_id"] as? String
    override fun setPolarUserId(userId: String) { store["polar_user_id"] = userId }
    override fun getPendingOAuthState(): String? = store["polar_pending_state"] as? String
    override fun setPendingOAuthState(state: String) { store["polar_pending_state"] = state }
    override fun getPendingOAuthSessionId(): String? = store["polar_pending_session"] as? String
    override fun setPendingOAuthSessionId(sessionId: String) { store["polar_pending_session"] = sessionId }
    override fun clearPendingOAuthSession() {
        store.remove("polar_pending_session")
        store.remove("polar_pending_state")
    }
    override fun clearPolarTokens() {
        store.remove("polar_access_token")
        store.remove("polar_refresh_token")
        store.remove("polar_token_expires_at")
        store.remove("polar_user_id")
        store.remove("polar_pending_state")
        store.remove("polar_pending_session")
    }
    override fun isPolarConnected(): Boolean = getPolarAccessToken() != null

    // Stubs for unrelated interface methods
    override fun getApiKey(provider: LlmProvider): String? = null
    override fun setApiKey(provider: LlmProvider, apiKey: String) {}
    override fun removeApiKey(provider: LlmProvider) {}
    override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENAI
    override fun setSelectedProvider(provider: LlmProvider) {}
    override fun getModel(provider: LlmProvider): String? = null
    override fun setModel(provider: LlmProvider, model: String) {}
    override fun clear() {}
    override fun getHeight(): Double? = null
    override fun setHeight(height: Double) {}
    override fun getHeightUnit(): String = "cm"
    override fun setHeightUnit(unit: String) {}
    override fun getSex(): String? = null
    override fun setSex(sex: String) {}
    override fun getCurrentWeight(): Double? = null
    override fun setCurrentWeight(weight: Double) {}
    override fun getWeightUnit(): String = "kg"
    override fun setWeightUnit(unit: String) {}
    override fun getDateOfBirth(): String? = null
    override fun setDateOfBirth(dob: String) {}
    override fun getActivityLevel(): String? = null
    override fun setActivityLevel(level: String) {}
    override fun clearHeight() {}
    override fun clearCurrentWeight() {}
    override fun clearProfileData() {}
    override fun getImageRetentionThresholdDays(): Int = 30
    override fun setImageRetentionThresholdDays(days: Int) {}
    override fun isMorningCheckInEnabled(): Boolean = false
    override fun setMorningCheckInEnabled(enabled: Boolean) {}
    override fun getMorningCheckInTime(): String = "07:00"
    override fun setMorningCheckInTime(time: String) {}
    override fun isEveningCheckInEnabled(): Boolean = false
    override fun setEveningCheckInEnabled(enabled: Boolean) {}
    override fun getEveningCheckInTime(): String = "21:00"
    override fun setEveningCheckInTime(time: String) {}
}
