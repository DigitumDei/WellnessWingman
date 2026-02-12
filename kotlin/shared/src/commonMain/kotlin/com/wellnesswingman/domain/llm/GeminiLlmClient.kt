package com.wellnesswingman.domain.llm

import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Google Gemini implementation of LlmClient using Ktor HTTP client.
 * Uses direct API calls since the official SDK is Android-only.
 */
class GeminiLlmClient(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
    private val httpClient: HttpClient = createDefaultHttpClient()
) : LlmClient {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

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

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        jsonSchema: String?
    ): LlmAnalysisResult {
        val startTime = Clock.System.now()

        // Encode image as base64
        val base64Image = Base64.encode(imageBytes)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiPart.InlineData(
                            mimeType = "image/jpeg",
                            data = base64Image
                        ))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = if (jsonSchema != null) "application/json" else null
            )
        )

        val httpResponse = httpClient.post(
            "$BASE_URL/models/$model:generateContent"
        ) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(request)
        }

        val endTime = Clock.System.now()

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Napier.e("Gemini API error ${httpResponse.status}: $errorBody")
            throw Exception("Gemini API error ${httpResponse.status}: $errorBody")
        }

        val response: GeminiResponse = httpResponse.body()

        val content = sanitize(
            response.candidates.firstOrNull()
                ?.content?.parts?.firstNotNullOfOrNull { it.text }
                ?: ""
        )

        return LlmAnalysisResult(
            content = content,
            diagnostics = LlmDiagnostics(
                promptTokens = response.usageMetadata?.promptTokenCount ?: 0,
                completionTokens = response.usageMetadata?.candidatesTokenCount ?: 0,
                totalTokens = response.usageMetadata?.totalTokenCount ?: 0,
                model = model,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
    }

    private fun sanitize(content: String): String {
        return content.trim().removePrefix("```json").removeSuffix("```").trim()
    }

    override suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String {
        // Gemini doesn't have built-in audio transcription like Whisper
        // This would need to be implemented via Google Cloud Speech-to-Text
        throw UnsupportedOperationException("Audio transcription not supported by Gemini. Use OpenAI Whisper instead.")
    }

    override suspend fun generateCompletion(
        prompt: String,
        jsonSchema: String?
    ): LlmAnalysisResult {
        val startTime = Clock.System.now()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = if (jsonSchema != null) "application/json" else null
            )
        )

        val httpResponse = httpClient.post(
            "$BASE_URL/models/$model:generateContent"
        ) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(request)
        }

        val endTime = Clock.System.now()

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            Napier.e("Gemini API error ${httpResponse.status}: $errorBody")
            throw Exception("Gemini API error ${httpResponse.status}: $errorBody")
        }

        val response: GeminiResponse = httpResponse.body()

        val content = sanitize(
            response.candidates.firstOrNull()
                ?.content?.parts?.firstNotNullOfOrNull { it.text }
                ?: ""
        )

        return LlmAnalysisResult(
            content = content,
            diagnostics = LlmDiagnostics(
                promptTokens = response.usageMetadata?.promptTokenCount ?: 0,
                completionTokens = response.usageMetadata?.candidatesTokenCount ?: 0,
                totalTokens = response.usageMetadata?.totalTokenCount ?: 0,
                model = model,
                latencyMs = (endTime - startTime).inWholeMilliseconds
            )
        )
    }
}

// Gemini API request/response models

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
) {
    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String
    )
}

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)
