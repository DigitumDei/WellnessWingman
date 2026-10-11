package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.PolarOAuthConfig
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages Polar OAuth flow: authorization URL building, session redemption,
 * token refresh, and disconnect.
 */
class PolarOAuthRepository(
    private val settings: AppSettingsRepository,
    private val config: PolarOAuthConfig,
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val refreshDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val redemptionMutex = Mutex()
    private val refreshMutex = Mutex()
    private val tokenMutex = Mutex()
    private var activeRedemptionSessionId: String? = null

    companion object {
        private const val POLAR_AUTH_URL = "https://auth.polar.com/oauth/authorize"
        private const val REFRESH_TIMEOUT_MS = 30_000L

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
     * Generates a Polar authorization URL and stores the state for later validation.
     * Returns the full URL the user should be directed to.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun buildAuthorizationUrl(): String {
        val state = Uuid.random().toString()
        settings.setPendingOAuthState(state)

        val callbackUrl = "${config.brokerBaseUrl}/oauth/callback"
        val scope = "activity:read training_sessions:read profile:read sleep:read nightly_recharge:read"
        return io.ktor.http.URLBuilder(POLAR_AUTH_URL).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", callbackUrl)
            parameters.append("scope", scope)
            parameters.append("state", state)
        }.buildString()
    }

    /**
     * Redeems a session from the broker and stores the tokens locally.
     * Validates that the returned state matches what we generated.
     */
    suspend fun redeemSession(sessionId: String, returnedState: String): Result<String> {
        // Guard against concurrent redemption of the same session.
        // Both WellnessWingmanApp (process-death recovery) and PolarSettingsViewModel
        // (normal flow) may call this; the broker rejects the second attempt with 410.
        redemptionMutex.withLock {
            if (activeRedemptionSessionId == sessionId) {
                Napier.w("Redemption already in progress for session $sessionId")
                return Result.failure(IllegalStateException("Redemption already in progress"))
            }
            activeRedemptionSessionId = sessionId
        }

        // Validate state
        val expectedState = settings.getPendingOAuthState()
        if (expectedState == null || expectedState != returnedState) {
            activeRedemptionSessionId = null
            return Result.failure(IllegalStateException("OAuth state mismatch"))
        }

        return try {
            val response = httpClient.post("${config.brokerBaseUrl}/oauth/redeem") {
                contentType(ContentType.Application.Json)
                setBody(RedeemRequest(sessionId))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Napier.e("Redeem failed (${response.status}): $errorBody")
                return Result.failure(Exception("Redeem failed: ${response.status}"))
            }

            val redeemResponse: RedeemResponse = response.body()
            val tokens = redeemResponse.tokens

            // Store tokens
            val accessToken = tokens["access_token"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("No access_token in response"))
            val refreshToken = tokens["refresh_token"]?.jsonPrimitive?.content
            val expiresIn = tokens["expires_in"]?.jsonPrimitive?.long ?: 0L
            val userId = tokens["x_user_id"]?.jsonPrimitive?.content
                ?: tokens["user_id"]?.jsonPrimitive?.content
                ?: ""

            tokenMutex.withLock {
                if (settings.getPendingOAuthState() != expectedState) {
                    return Result.failure(IllegalStateException("OAuth state changed during redemption"))
                }
                settings.setPolarAccessToken(accessToken)
                if (refreshToken != null) settings.setPolarRefreshToken(refreshToken)
                settings.setPolarTokenExpiresAt(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + (expiresIn * 1000)
                )
                if (userId.isNotEmpty()) settings.setPolarUserId(userId)
            }

            Napier.i("Polar OAuth tokens stored successfully (userId=$userId)")
            Result.success(userId)
        } catch (e: Exception) {
            Napier.e("Failed to redeem OAuth session", e)
            Result.failure(e)
        } finally {
            activeRedemptionSessionId = null
        }
    }

    /**
     * Refreshes the Polar access token via the broker's stateless refresh proxy.
     */
    suspend fun refreshTokens(): Result<Unit> {
        currentCoroutineContext().ensureActive()
        val (accessToken, refreshToken) = tokenMutex.withLock {
            settings.getPolarAccessToken() to (settings.getPolarRefreshToken()
                ?: return Result.failure(IllegalStateException("No refresh token available")))
        }

        return refreshMutex.withLock {
            // Another caller may already have renewed this session while we waited.
            val updatedSession = tokenMutex.withLock {
                if (isCurrentSession(accessToken, refreshToken)) {
                    null
                } else if (settings.isPolarConnected() && settings.getPolarRefreshToken() != null) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Polar connection changed during token refresh"))
                }
            }
            if (updatedSession != null) return@withLock updatedSession

            // Once sent, Polar may rotate the refresh token. Finish saving the response even
            // if a screen is disposed or WorkManager stops the sync, then honour cancellation.
            // Bound the protected section so an unresponsive broker cannot hold the lock forever.
            val result = withContext(NonCancellable + refreshDispatcher) {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    refreshTokensLocked(accessToken, refreshToken)
                } ?: Result.failure(IllegalStateException("Polar token refresh timed out. Sync will retry."))
            }
            currentCoroutineContext().ensureActive()
            result
        }
    }

    private suspend fun refreshTokensLocked(accessToken: String?, refreshToken: String): Result<Unit> {
        return try {
            val response = httpClient.post("${config.brokerBaseUrl}/oauth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                // Never log broker bodies: they can contain credentials, including inside
                // the JSON-encoded detail field returned by the refresh proxy.
                Napier.w("Polar token refresh failed (HTTP ${response.status.value})")
                return tokenMutex.withLock {
                    if (!isCurrentSession(accessToken, refreshToken)) {
                        Result.failure(IllegalStateException("Polar connection changed during token refresh"))
                    } else if ((response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) &&
                        oauthErrorCode(errorBody) == "invalid_grant"
                    ) {
                        Napier.w("Polar refresh token expired or revoked; reconnection required")
                        settings.clearPolarTokens()
                        Result.failure(IllegalStateException("Polar session expired or was revoked. Reconnect your Polar account."))
                    } else {
                        Result.failure(Exception("Refresh failed: ${response.status}"))
                    }
                }
            }

            val tokens: JsonObject = response.body()
            val newAccessToken = tokens["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("No access_token in refresh response"))
            val newRefreshToken = tokens["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val expiresIn = tokens["expires_in"]?.jsonPrimitive?.long ?: 0L

            // A disconnect or a newly redeemed session takes precedence over this request.
            tokenMutex.withLock {
                if (!isCurrentSession(accessToken, refreshToken)) {
                    return@withLock Result.failure(IllegalStateException("Polar connection changed during token refresh"))
                }
                if (newRefreshToken != null) settings.setPolarRefreshToken(newRefreshToken)
                settings.setPolarAccessToken(newAccessToken)
                settings.setPolarTokenExpiresAt(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + (expiresIn * 1000)
                )

                Napier.i("Polar tokens refreshed successfully")
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Parsing/HTTP exceptions can include the token response in their message.
            Napier.e("Failed to refresh Polar tokens (${e::class.simpleName})")
            Result.failure(IllegalStateException("Could not refresh Polar session. Sync will retry."))
        }
    }

    private fun isCurrentSession(accessToken: String?, refreshToken: String): Boolean =
        settings.getPolarAccessToken() == accessToken && settings.getPolarRefreshToken() == refreshToken

    private fun oauthErrorCode(body: String): String? {
        val error = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null
        val code = (error["error"] as? JsonPrimitive)?.contentOrNull
        if (code != "Refresh failed") return code

        // The deployed broker wraps Polar's JSON error as a string in detail.
        val detail = (error["detail"] as? JsonPrimitive)?.contentOrNull ?: return null
        val upstream = runCatching { Json.parseToJsonElement(detail) as? JsonObject }.getOrNull()
        return (upstream?.get("error") as? JsonPrimitive)?.contentOrNull
    }

    /**
     * Disconnects from Polar by clearing all stored tokens.
     */
    suspend fun disconnect() = tokenMutex.withLock {
        settings.clearPolarTokens()
        Napier.i("Polar account disconnected")
    }
}

@Serializable
private data class RedeemRequest(val session_id: String)

@Serializable
private data class RefreshRequest(val refresh_token: String)

@Serializable
private data class RedeemResponse(
    val tokens: JsonObject,
    val state: String = ""
)
