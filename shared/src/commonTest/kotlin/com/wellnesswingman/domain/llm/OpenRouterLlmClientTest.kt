package com.wellnesswingman.domain.llm

import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.wellnesswingman.data.model.llm.ToolDefinition
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterLlmClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `generateCompletion replays assistant tool call message without dropping metadata`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            when (requests.size) {
                1 -> toolCallCompletion(
                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = null as String?,
                        toolCalls = listOf(
                            OpenAiToolCall.Function(
                                id = ToolId("call-1"),
                                function = FunctionCall(
                                    nameOrNull = "lookup_calories",
                                    argumentsOrNull = """{"food":"apple"}"""
                                )
                            )
                        )
                    )
                )
                else -> finalCompletion("""{"answer":"95 calories"}""")
            }
        }

        var capturedFood: String? = null
        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            model = "openai/gpt-4o-mini",
            client = api
        )

        val result = client.generateCompletion(
            prompt = "How many calories are in an apple?",
            tools = listOf(
                ToolDefinition(
                    name = "lookup_calories",
                    description = "Looks up calories.",
                    parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
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
        assertEquals("""{"answer":"95 calories"}""", result.content)
        assertEquals(2, requests.size)
        assertEquals(ModelId("openai/gpt-4o-mini"), requests[0].model)

        val secondRequestMessages = requests[1].messages
        assertEquals(3, secondRequestMessages.size)
        assertNull(secondRequestMessages[1].content)
        assertEquals("lookup_calories", (secondRequestMessages[1].toolCalls?.singleOrNull() as? OpenAiToolCall.Function)?.function?.nameOrNull)
        assertEquals("call-1", secondRequestMessages[2].toolCallId?.id)
        assertEquals("lookup_calories", secondRequestMessages[2].name)
    }

    @Test
    fun `generateCompletion converts malformed tool arguments into tool error response`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            when (requests.size) {
                1 -> toolCallCompletion(
                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = null as String?,
                        toolCalls = listOf(
                            OpenAiToolCall.Function(
                                id = ToolId("call-1"),
                                function = FunctionCall(
                                    nameOrNull = "lookup_calories",
                                    argumentsOrNull = """{"food":"""
                                )
                            )
                        )
                    )
                )
                else -> finalCompletion("done")
            }
        }

        var executorInvoked = false
        val result = OpenRouterLlmClient(
            apiKey = "test-key",
            client = api
        ).generateCompletion(
            prompt = "hello",
            tools = listOf(
                ToolDefinition(
                    name = "lookup_calories",
                    description = "Looks up calories.",
                    parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
                )
            ),
            toolExecutor = {
                executorInvoked = true
                error("should not be called")
            }
        )

        assertFalse(executorInvoked)
        assertEquals("done", result.content)
        assertEquals(2, requests.size)
        assertTrue(requests[1].messages[2].content.orEmpty().contains("\"ok\":false"))
    }

    @Test
    fun `generateCompletion rethrows cancellation from tool executor`() = runTest {
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } returns toolCallCompletion(
            ChatMessage(
                role = ChatRole.Assistant,
                content = null as String?,
                toolCalls = listOf(
                    OpenAiToolCall.Function(
                        id = ToolId("call-1"),
                        function = FunctionCall(
                            nameOrNull = "lookup_calories",
                            argumentsOrNull = """{"food":"apple"}"""
                        )
                    )
                )
            )
        )

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            client = api
        )

        assertFailsWith<CancellationException> {
            client.generateCompletion(
                prompt = "hello",
                tools = listOf(
                    ToolDefinition(
                        name = "lookup_calories",
                        description = "Looks up calories.",
                        parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
                    )
                ),
                toolExecutor = {
                    throw CancellationException("cancelled")
                }
            )
        }
    }

    @Test
    fun `generateCompletion fails after exceeding max tool rounds`() = runTest {
        var requestCount = 0
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requestCount += 1
            toolCallCompletion(
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = null as String?,
                    toolCalls = listOf(
                        OpenAiToolCall.Function(
                            id = ToolId("call-1"),
                            function = FunctionCall(
                                nameOrNull = "lookup_calories",
                                argumentsOrNull = """{"food":"apple"}"""
                            )
                        )
                    )
                )
            )
        }

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            client = api
        )

        val error = assertFailsWith<IllegalStateException> {
            client.generateCompletion(
                prompt = "hello",
                tools = listOf(
                    ToolDefinition(
                        name = "lookup_calories",
                        description = "Looks up calories.",
                        parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
                    )
                ),
                toolExecutor = {
                    com.wellnesswingman.data.model.llm.ToolResult(
                        toolCallId = it.id,
                        name = it.name,
                        content = JsonPrimitive("ok")
                    )
                }
            )
        }

        assertTrue(error.message.orEmpty().contains("exceeded 5 rounds"))
        assertEquals(6, requestCount)
    }

    @Test
    fun `transcribeAudio sends JSON request with base64 audio to OpenRouter`() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val httpClient = HttpClient(MockEngine { request: HttpRequestData ->
            capturedRequests += request
            respond(
                content = """{"text": "hello world"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        })

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            httpClient = httpClient
        )

        val result = client.transcribeAudio(byteArrayOf(0x00, 0x01), "audio/m4a")

        assertEquals("hello world", result)
        assertEquals(1, capturedRequests.size)

        val request = capturedRequests.single()
        assertTrue(
            request.url.toString().contains("openrouter.ai/api/v1/audio/transcriptions"),
            "URL should point to OpenRouter transcription endpoint"
        )
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            ContentType.Application.Json.toString(),
            request.headers[HttpHeaders.ContentType]
        )
        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
        assertEquals("https://wellnesswingman.com", request.headers["HTTP-Referer"])
        assertEquals("WellnessWingman", request.headers["X-Title"])

        val body = when (val b = request.body) {
            is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> b.readFrom().readRemaining().readText()
            is OutgoingContent.WriteChannelContent -> "<write-channel-content>"
            is OutgoingContent.NoContent -> ""
            else -> b.toString()
        }
        assertTrue(body.contains(""""model":"openai/whisper-1""""), "Body should contain model")
        assertTrue(body.contains(""""input_audio""""), "Body should contain input_audio object")
        assertTrue(body.contains(""""data":"AAE=""""), "Body should contain base64-encoded audio data")
        assertTrue(body.contains(""""format":"m4a""""), "Body should contain audio format")
    }

    @Test
    fun `transcribeAudio maps mime type to correct format`() = runTest {
        val requests = mutableListOf<String>()
        val httpClient = HttpClient(MockEngine { request: HttpRequestData ->
            requests += when (val body = request.body) {
                is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                is OutgoingContent.ReadChannelContent -> body.readFrom().readRemaining().readText()
                is OutgoingContent.WriteChannelContent -> "<write-channel-content>"
                is OutgoingContent.NoContent -> ""
                else -> body.toString()
            }
            respond(
                content = """{"text": "transcribed"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        })

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            httpClient = httpClient
        )

        client.transcribeAudio(byteArrayOf(0x00), "audio/wav")
        assertTrue(requests.single().contains(""""format":"wav""""), "Should use wav format for audio/wav")

        requests.clear()
        client.transcribeAudio(byteArrayOf(0x00), "audio/mp3")
        assertTrue(requests.single().contains(""""format":"mp3""""), "Should use mp3 format for audio/mp3")

        requests.clear()
        client.transcribeAudio(byteArrayOf(0x00), "audio/m4a")
        assertTrue(requests.single().contains(""""format":"m4a""""), "Should use m4a format for audio/m4a")

        requests.clear()
        client.transcribeAudio(byteArrayOf(0x00), "unknown/type")
        assertTrue(requests.single().contains(""""format":"m4a""""), "Should default to m4a for unknown types")
    }

    @Test
    fun `transcribeAudio throws on API error response`() = runTest {
        val httpClient = HttpClient(MockEngine {
            respond(
                content = """{"error": "bad request"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders
            )
        })

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            httpClient = httpClient
        )

        val error = assertFailsWith<Exception> {
            client.transcribeAudio(byteArrayOf(0x00), "audio/m4a")
        }

        assertTrue(error.message.orEmpty().contains("400"), "Error should mention HTTP status")
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `analyzeImage sends TextPart and ImagePart with base64 data URI`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            finalCompletion("""{"analysis":"a cat wearing a hat"}""")
        }

        val imageBytes = byteArrayOf(0x89, 0x50, 0x4E, 0x47)
        val prompt = "Describe this image in detail"
        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            model = "openai/gpt-4o-mini",
            client = api
        )

        val result = client.analyzeImage(
            imageBytes = imageBytes,
            prompt = prompt,
            jsonSchema = null,
            tools = emptyList(),
            toolExecutor = null
        )

        assertEquals("""{"analysis":"a cat wearing a hat"}""", result.content)
        assertEquals("openai/gpt-4o-mini", result.diagnostics.model)
        assertTrue(result.diagnostics.latencyMs >= 0)
        assertEquals(6, result.diagnostics.promptTokens)
        assertEquals(5, result.diagnostics.completionTokens)
        assertEquals(11, result.diagnostics.totalTokens)

        assertEquals(1, requests.size)
        val messages = requests[0].messages
        assertEquals(1, messages.size)

        val message = messages[0]
        assertEquals(ChatRole.User, message.role)

        val content = message.messageContent
        assertTrue(content is ListContent, "Content should be ListContent for multi-part messages")

        val parts = (content as ListContent).content
        assertEquals(2, parts.size, "Should have TextPart and ImagePart")

        val textPart = parts[0]
        assertTrue(textPart is TextPart, "First part should be TextPart")
        assertEquals(prompt, (textPart as TextPart).text)

        val imagePart = parts[1]
        assertTrue(imagePart is ImagePart, "Second part should be ImagePart")
        val expectedUrl = "data:image/jpeg;base64,${Base64.encode(imageBytes)}"
        assertEquals(expectedUrl, (imagePart as ImagePart).imageUrl.url)
        assertNull((imagePart as ImagePart).imageUrl.detail)
    }

    private fun toolCallCompletion(message: ChatMessage) = ChatCompletion(
        id = "chatcmpl-tool",
        created = 1L,
        model = ModelId("openai/gpt-4o-mini"),
        choices = listOf(
            ChatChoice(
                index = 0,
                message = message,
                finishReason = FinishReason("tool_calls")
            )
        ),
        usage = Usage(promptTokens = 10, completionTokens = 4, totalTokens = 14)
    )

    private fun finalCompletion(content: String) = ChatCompletion(
        id = "chatcmpl-final",
        created = 2L,
        model = ModelId("openai/gpt-4o-mini"),
        choices = listOf(
            ChatChoice(
                index = 0,
                message = ChatMessage(
                    role = ChatRole.Assistant,
                    content = content
                ),
                finishReason = FinishReason("stop")
            )
        ),
        usage = Usage(promptTokens = 6, completionTokens = 5, totalTokens = 11)
    )
}
