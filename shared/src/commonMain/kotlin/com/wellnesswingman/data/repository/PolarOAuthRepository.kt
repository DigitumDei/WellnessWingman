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
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val POLAR_AUTH_URL = "https://auth.polar.com/oauth/authorize"

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
        return "$POLAR_AUTH_URL" +
            "?response_type=code" +
            "&client_id=${config.clientId}" +
            "&redirect_uri=${callbackUrl}" +
            "&state=$state"
    }

    /**
     * Redeems a session from the broker and stores the tokens locally.
     * Validates that the returned state matches what we generated.
     */
    suspend fun redeemSession(sessionId: String, returnedState: String): Result<String> {
        // Validate state
        val expectedState = settings.getPendingOAuthState()
        if (expectedState == null || expectedState != returnedState) {
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

            settings.setPolarAccessToken(accessToken)
            if (refreshToken != null) settings.setPolarRefreshToken(refreshToken)
            settings.setPolarTokenExpiresAt(
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + (expiresIn * 1000)
            )
            if (userId.isNotEmpty()) settings.setPolarUserId(userId)

            Napier.i("Polar OAuth tokens stored successfully (userId=$userId)")
            Result.success(userId)
        } catch (e: Exception) {
            Napier.e("Failed to redeem OAuth session", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes the Polar access token via the broker's stateless refresh proxy.
     */
    suspend fun refreshTokens(): Result<Unit> {
        val refreshToken = settings.getPolarRefreshToken()
            ?: return Result.failure(IllegalStateException("No refresh token available"))

        return try {
            val response = httpClient.post("${config.brokerBaseUrl}/oauth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Napier.e("Refresh failed (${response.status}): $errorBody")
                return Result.failure(Exception("Refresh failed: ${response.status}"))
            }

            val tokens: JsonObject = response.body()
            val accessToken = tokens["access_token"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("No access_token in refresh response"))
            val newRefreshToken = tokens["refresh_token"]?.jsonPrimitive?.content
            val expiresIn = tokens["expires_in"]?.jsonPrimitive?.long ?: 0L

            settings.setPolarAccessToken(accessToken)
            if (newRefreshToken != null) settings.setPolarRefreshToken(newRefreshToken)
            settings.setPolarTokenExpiresAt(
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + (expiresIn * 1000)
            )

            Napier.i("Polar tokens refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Failed to refresh Polar tokens", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnects from Polar by clearing all stored tokens.
     */
    fun disconnect() {
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
