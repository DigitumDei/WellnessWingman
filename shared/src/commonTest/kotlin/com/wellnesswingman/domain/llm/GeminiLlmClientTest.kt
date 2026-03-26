package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.model.llm.ToolDefinition
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GeminiLlmClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `generateCompletion executes tool loop and returns final text`() = runTest {
        val responses = ArrayDeque(
            listOf(
                """{
                    "candidates": [{
                        "content": {
                            "role": "model",
                            "parts": [{
                                "functionCall": {
                                    "id": "call-1",
                                    "name": "lookup_calories",
                                    "args": {"food": "apple"}
                                }
                            }]
                        }
                    }],
                    "usageMetadata": {
                        "promptTokenCount": 10,
                        "candidatesTokenCount": 4,
                        "totalTokenCount": 14
                    }
                }""".trimIndent(),
                """{
                    "candidates": [{
                        "content": {
                            "role": "model",
                            "parts": [{
                                "text": "Estimated calories: 95"
                            }]
                        }
                    }],
                    "usageMetadata": {
                        "promptTokenCount": 6,
                        "candidatesTokenCount": 5,
                        "totalTokenCount": 11
                    }
                }""".trimIndent()
            )
        )

        val httpClient = HttpClient(MockEngine {
            respond(
                content = responses.removeFirst(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        var capturedFood: String? = null
        val client = GeminiLlmClient(
            apiKey = "test-key",
            model = "gemini-1.5-flash",
            httpClient = httpClient
        )

        val result = client.generateCompletion(
            prompt = "How many calories are in an apple?",
            tools = listOf(
                ToolDefinition(
                    name = "lookup_calories",
                    description = "Looks up calorie estimates for a food.",
                    parametersSchema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                    }
                )
            ),
            toolExecutor = { toolCall ->
                capturedFood = toolCall.arguments["food"]?.toString()?.trim('"')
                com.wellnesswingman.data.model.llm.ToolResult(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    content = buildJsonObject {
                        put("calories", JsonPrimitive(95))
                    }
                )
            }
        )

        assertEquals("apple", capturedFood)
        assertEquals("Estimated calories: 95", result.content)
        assertEquals(16, result.diagnostics.promptTokens)
        assertEquals(9, result.diagnostics.completionTokens)
        assertEquals(25, result.diagnostics.totalTokens)
    }

    @Test
    fun `generateCompletion without tools returns plain response`() = runTest {
        val httpClient = HttpClient(MockEngine {
            respond(
                content = """{
                    "candidates": [{
                        "content": {
                            "role": "model",
                            "parts": [{"text": "Plain completion"}]
                        }
                    }]
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val result = GeminiLlmClient(
            apiKey = "test-key",
            httpClient = httpClient
        ).generateCompletion("hello")

        assertFalse(result.content.isBlank())
        assertEquals("Plain completion", result.content)
    }

    @Test
    fun `gemini part round trips thought signature`() {
        val payload = Json.encodeToString(
            GeminiPart(
                functionCall = GeminiFunctionCall(
                    id = "call-1",
                    name = "lookup_calories",
                    args = buildJsonObject {
                        put("food", JsonPrimitive("apple"))
                    }
                ),
                thoughtSignature = "signature-1"
            )
        )

        val decoded = Json.decodeFromString<GeminiPart>(payload)

        assertEquals("signature-1", decoded.thoughtSignature)
        assertEquals("lookup_calories", decoded.functionCall?.name)
    }
}
