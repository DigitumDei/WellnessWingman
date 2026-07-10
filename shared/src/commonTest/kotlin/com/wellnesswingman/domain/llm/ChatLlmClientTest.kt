package com.wellnesswingman.domain.llm

import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole as OpenAiChatRole
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.wellnesswingman.data.model.llm.LlmChatMessage
import com.wellnesswingman.data.model.llm.LlmChatRole
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolDefinition
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatLlmClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `openai chat preserves serialized history with user assistant and tool turns`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            finalCompletion("Hello! How can I help you today?")
        }

        val client = OpenAiLlmClient(
            apiKey = "test-key",
            model = "gpt-4o-mini",
            client = api
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "What is the weather?"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "Let me check that for you."),
            LlmChatMessage(role = LlmChatRole.TOOL, toolCallId = "call-1", toolName = "get_weather", toolResultJson = """{"ok":true,"content":{"temp":72}}"""),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "The weather is 72 degrees."),
            LlmChatMessage(role = LlmChatRole.USER, content = "Thanks!")
        )

        val result = client.generateChatResponse(
            messages = messages,
            systemInstruction = "You are a helpful weather assistant."
        )

        assertEquals("Hello! How can I help you today?", result.content)
        assertEquals(1, requests.size)

        val sentMessages = requests[0].messages
        assertTrue(sentMessages.isNotEmpty())

        assertEquals(OpenAiChatRole.System, sentMessages[0].role)
        assertEquals("You are a helpful weather assistant.", sentMessages[0].content)

        assertEquals(OpenAiChatRole.User, sentMessages[1].role)
        assertEquals("What is the weather?", sentMessages[1].content)

        assertEquals(OpenAiChatRole.Assistant, sentMessages[2].role)
        assertEquals("Let me check that for you.", sentMessages[2].content)

        assertEquals(OpenAiChatRole.Tool, sentMessages[3].role)
        assertEquals("call-1", sentMessages[3].toolCallId?.id)
        assertEquals("get_weather", sentMessages[3].name)

        assertEquals(OpenAiChatRole.Assistant, sentMessages[4].role)
        assertEquals("The weather is 72 degrees.", sentMessages[4].content)

        assertEquals(OpenAiChatRole.User, sentMessages[5].role)
        assertEquals("Thanks!", sentMessages[5].content)
    }

    @Test
    fun `openai chat continues tool response with new tool execution`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            when (requests.size) {
                1 -> toolCallCompletion(
                    ChatMessage(
                        role = OpenAiChatRole.Assistant,
                        content = null as String?,
                        toolCalls = listOf(
                            OpenAiToolCall.Function(
                                id = ToolId("call-2"),
                                function = FunctionCall(
                                    nameOrNull = "get_temperature",
                                    argumentsOrNull = """{"city":"London"}"""
                                )
                            )
                        )
                    )
                )
                else -> finalCompletion("The temperature in London is 15°C.")
            }
        }

        val client = OpenAiLlmClient(
            apiKey = "test-key",
            model = "gpt-4o-mini",
            client = api
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "What is the weather in London?"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, toolCalls = listOf(
                ToolCall(id = "call-1", name = "get_weather", arguments = buildJsonObject {
                    put("city", JsonPrimitive("London"))
                })
            )),
            LlmChatMessage(role = LlmChatRole.TOOL, toolCallId = "call-1", toolName = "get_weather", toolResultJson = """{"ok":true,"content":{"condition":"cloudy"}}"""),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "Let me get the temperature too."),
            LlmChatMessage(role = LlmChatRole.USER, content = "Please check the exact temperature.")
        )

        var capturedCity: String? = null
        val result = client.generateChatResponse(
            messages = messages,
            tools = listOf(
                ToolDefinition(
                    name = "get_temperature",
                    description = "Gets temperature.",
                    parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
                )
            ),
            toolExecutor = { toolCall ->
                capturedCity = toolCall.arguments["city"]?.toString()?.trim('"')
                com.wellnesswingman.data.model.llm.ToolResult(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    content = buildJsonObject {
                        put("temp", JsonPrimitive(15))
                    }
                )
            }
        )

        assertEquals("London", capturedCity)
        assertEquals("The temperature in London is 15°C.", result.content)
        assertEquals(2, requests.size)

        val secondRequestMessages = requests[1].messages
        val assistantToolCalls = secondRequestMessages.filter { it.role == OpenAiChatRole.Assistant && it.toolCalls != null }
        val toolResults = secondRequestMessages.filter { it.role == OpenAiChatRole.Tool }
        assertTrue(toolResults.any { it.toolCallId?.id == "call-1" }, "Should contain prior tool result")
        assertTrue(assistantToolCalls.any { it.toolCalls?.any { tc -> (tc as? OpenAiToolCall.Function)?.function?.nameOrNull == "get_weather" } == true }, "Should contain prior assistant tool call")
    }

    @Test
    fun `openai chat with empty history returns response`() = runTest {
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } returns finalCompletion("Hi there!")

        val result = OpenAiLlmClient(
            apiKey = "test-key",
            client = api
        ).generateChatResponse(
            messages = listOf(
                LlmChatMessage(role = LlmChatRole.USER, content = "Hello")
            ),
            systemInstruction = "Be friendly."
        )

        assertEquals("Hi there!", result.content)
    }

    @Test
    fun `openai chat without system instruction omits system message`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            finalCompletion("Sure!")
        }

        OpenAiLlmClient(
            apiKey = "test-key",
            client = api
        ).generateChatResponse(
            messages = listOf(
                LlmChatMessage(role = LlmChatRole.USER, content = "Help me")
            )
        )

        val roles = requests[0].messages.map { it.role }
        assertTrue(roles.none { it == OpenAiChatRole.System }, "No system message should be present")
    }

    @Test
    fun `gemini chat preserves serialized history with user model and tool turns`() = runTest {
        val requests = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                """{
                    "candidates": [{
                        "content": {
                            "role": "model",
                            "parts": [{"text": "I can help with that!"}]
                        }
                    }],
                    "usageMetadata": {
                        "promptTokenCount": 15,
                        "candidatesTokenCount": 8,
                        "totalTokenCount": 23
                    }
                }""".trimIndent()
            )
        )

        val httpClient = mockGeminiClient(requests, responses)

        val client = GeminiLlmClient(
            apiKey = "test-key",
            model = "gemini-1.5-flash",
            httpClient = httpClient
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "Hello"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "Hi! How can I help?"),
            LlmChatMessage(role = LlmChatRole.USER, content = "What is my weight?")
        )

        val result = client.generateChatResponse(
            messages = messages,
            systemInstruction = "You are a health assistant."
        )

        assertEquals("I can help with that!", result.content)
        assertEquals(1, requests.size)

        val body = requests[0]
        assertTrue(body.contains("\"system_instruction\""), "Should have system instruction")
        assertTrue(body.contains("health assistant"), "System instruction should contain custom text")
        assertTrue(body.contains("\"role\":\"user\""), "Should contain user role")
        assertTrue(body.contains("\"role\":\"model\""), "Should contain model role")
        assertTrue(body.contains("\"text\":\"Hello\""), "Should include first user message")
        assertTrue(body.contains("\"text\":\"Hi! How can I help?\""), "Should include assistant response")
        assertTrue(body.contains("\"text\":\"What is my weight?\""), "Should include second user message")
    }

    @Test
    fun `gemini chat continues tool response with existing tool call history`() = runTest {
        val requests = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                """{
                    "candidates": [{
                        "content": {
                            "role": "model",
                            "parts": [{"text": "Your weight is 75 kg."}]
                        }
                    }],
                    "usageMetadata": {
                        "promptTokenCount": 20,
                        "candidatesTokenCount": 5,
                        "totalTokenCount": 25
                    }
                }""".trimIndent()
            )
        )

        val httpClient = mockGeminiClient(requests, responses)

        val client = GeminiLlmClient(
            apiKey = "test-key",
            httpClient = httpClient
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "What is my weight?"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, toolCalls = listOf(
                ToolCall(id = "fc-1", name = "get_user_profile", arguments = buildJsonObject { })
            )),
            LlmChatMessage(
                role = LlmChatRole.TOOL,
                toolCallId = "fc-1",
                toolName = "get_user_profile",
                toolResultJson = """{"ok":true,"content":{"currentWeight":75}}"""
            )
        )

        val result = client.generateChatResponse(messages = messages)

        assertEquals("Your weight is 75 kg.", result.content)
        assertEquals(1, requests.size)

        val body = requests[0]
        assertTrue(body.contains("\"functionCall\""), "Should include prior function call")
        assertTrue(body.contains("\"fc-1\""), "Should include prior function call id")
        assertTrue(body.contains("\"functionResponse\""), "Should include prior function response")
        assertTrue(body.contains("\"currentWeight\":75"), "Should include tool result content")
    }

    @Test
    fun `openrouter chat preserves serialized history with user assistant and tool turns`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            finalCompletion("You're welcome!")
        }

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            model = "openai/gpt-4o-mini",
            client = api
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "Thanks for the help"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "You're welcome!"),
            LlmChatMessage(role = LlmChatRole.USER, content = "Can you help with one more thing?")
        )

        val result = client.generateChatResponse(
            messages = messages,
            systemInstruction = "You are supportive."
        )

        assertEquals("You're welcome!", result.content)
        assertEquals(1, requests.size)

        val sent = requests[0].messages
        assertEquals(OpenAiChatRole.System, sent[0].role)
        assertEquals("You are supportive.", sent[0].content)
        assertEquals(OpenAiChatRole.User, sent[1].role)
        assertEquals("Thanks for the help", sent[1].content)
        assertEquals(OpenAiChatRole.Assistant, sent[2].role)
        assertEquals("You're welcome!", sent[2].content)
        assertEquals(OpenAiChatRole.User, sent[3].role)
        assertEquals("Can you help with one more thing?", sent[3].content)
    }

    @Test
    fun `openrouter chat continues tool response with prior tool calls in history`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            when (requests.size) {
                1 -> toolCallCompletion(
                    ChatMessage(
                        role = OpenAiChatRole.Assistant,
                        content = null as String?,
                        toolCalls = listOf(
                            OpenAiToolCall.Function(
                                id = ToolId("call-3"),
                                function = FunctionCall(
                                    nameOrNull = "get_calories",
                                    argumentsOrNull = """{"food":"banana"}"""
                                )
                            )
                        )
                    )
                )
                else -> finalCompletion("A banana has 105 calories.")
            }
        }

        val client = OpenRouterLlmClient(
            apiKey = "test-key",
            model = "openai/gpt-4o-mini",
            client = api
        )

        val messages = listOf(
            LlmChatMessage(role = LlmChatRole.USER, content = "How many calories in an apple?"),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, toolCalls = listOf(
                ToolCall(id = "call-1", name = "lookup_calories", arguments = buildJsonObject {
                    put("food", JsonPrimitive("apple"))
                })
            )),
            LlmChatMessage(
                role = LlmChatRole.TOOL,
                toolCallId = "call-1",
                toolName = "lookup_calories",
                toolResultJson = """{"ok":true,"content":{"calories":95}}"""
            ),
            LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "An apple has 95 calories."),
            LlmChatMessage(role = LlmChatRole.USER, content = "What about a banana?")
        )

        var capturedFood: String? = null
        val result = client.generateChatResponse(
            messages = messages,
            tools = listOf(
                ToolDefinition(
                    name = "get_calories",
                    description = "Gets calories.",
                    parametersSchema = buildJsonObject { put("type", JsonPrimitive("object")) }
                )
            ),
            toolExecutor = { toolCall ->
                capturedFood = toolCall.arguments["food"]?.toString()?.trim('"')
                com.wellnesswingman.data.model.llm.ToolResult(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    content = buildJsonObject { put("calories", JsonPrimitive(105)) }
                )
            }
        )

        assertEquals("banana", capturedFood)
        assertEquals("A banana has 105 calories.", result.content)
        assertEquals(2, requests.size)

        val secondMessages = requests[1].messages
        val toolMsg = secondMessages.find { it.role == OpenAiChatRole.Tool && it.toolCallId?.id == "call-1" }
        assertNotNull(toolMsg, "Prior tool result must be present in the request")
    }

    @Test
    fun `llm chat message serialization round trips all roles`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val userMsg = LlmChatMessage(role = LlmChatRole.USER, content = "Hello")
        val userJson = json.encodeToString(LlmChatMessage.serializer(), userMsg)
        val userDecoded = json.decodeFromString(LlmChatMessage.serializer(), userJson)
        assertEquals(LlmChatRole.USER, userDecoded.role)
        assertEquals("Hello", userDecoded.content)

        val assistantMsg = LlmChatMessage(role = LlmChatRole.ASSISTANT, content = "Hi!")
        val assistantJson = json.encodeToString(LlmChatMessage.serializer(), assistantMsg)
        val assistantDecoded = json.decodeFromString(LlmChatMessage.serializer(), assistantJson)
        assertEquals(LlmChatRole.ASSISTANT, assistantDecoded.role)
        assertEquals("Hi!", assistantDecoded.content)

        val toolMsg = LlmChatMessage(
            role = LlmChatRole.TOOL,
            toolCallId = "call-1",
            toolName = "get_weather",
            toolResultJson = """{"ok":true,"content":{"temp":72}}"""
        )
        val toolJson = json.encodeToString(LlmChatMessage.serializer(), toolMsg)
        val toolDecoded = json.decodeFromString(LlmChatMessage.serializer(), toolJson)
        assertEquals(LlmChatRole.TOOL, toolDecoded.role)
        assertEquals("call-1", toolDecoded.toolCallId)
        assertEquals("get_weather", toolDecoded.toolName)
    }

    @Test
    fun `llm chat message serialization preserves tool calls in assistant message`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val msg = LlmChatMessage(
            role = LlmChatRole.ASSISTANT,
            content = null,
            toolCalls = listOf(
                ToolCall(
                    id = "call-1",
                    name = "get_weather",
                    arguments = buildJsonObject {
                        put("city", JsonPrimitive("London"))
                    }
                )
            )
        )

        val encoded = json.encodeToString(LlmChatMessage.serializer(), msg)
        val decoded = json.decodeFromString(LlmChatMessage.serializer(), encoded)

        assertEquals(LlmChatRole.ASSISTANT, decoded.role)
        assertNotNull(decoded.toolCalls)
        assertEquals(1, decoded.toolCalls!!.size)
        assertEquals("call-1", decoded.toolCalls!![0].id)
        assertEquals("get_weather", decoded.toolCalls!![0].name)
        assertEquals("London", decoded.toolCalls!![0].arguments["city"]?.toString()?.trim('"'))
    }

    @Test
    fun `factory creates openai client for OPENAI provider`() {
        val settingsRepo = FakeAppSettingsRepository(
            apiKey = "sk-test",
            model = "gpt-4o-mini"
        )
        val factory = LlmClientFactory(settingsRepo)
        val client = factory.create(LlmProvider.OPENAI)
        assertIs<OpenAiLlmClient>(client)
    }

    @Test
    fun `factory creates gemini client for GEMINI provider`() {
        val settingsRepo = FakeAppSettingsRepository(
            apiKey = "gemini-test",
            model = "gemini-1.5-flash"
        )
        val factory = LlmClientFactory(settingsRepo)
        val client = factory.create(LlmProvider.GEMINI)
        assertIs<GeminiLlmClient>(client)
    }

    @Test
    fun `factory creates openrouter client for OPENROUTER provider`() {
        val settingsRepo = FakeAppSettingsRepository(
            apiKey = "sk-or-v1-test",
            model = "openai/gpt-4o-mini"
        )
        val factory = LlmClientFactory(settingsRepo)
        val client = factory.create(LlmProvider.OPENROUTER)
        assertIs<OpenRouterLlmClient>(client)
    }

    @Test
    fun `factory reflects provider model in diagnostics`() = runTest {
        val requests = mutableListOf<ChatCompletionRequest>()
        val api = mockk<OpenAI>()
        coEvery { api.chatCompletion(any()) } answers {
            requests += firstArg<ChatCompletionRequest>()
            finalCompletion("ok")
        }

        val result = OpenAiLlmClient(
            apiKey = "test-key",
            model = "gpt-4o",
            client = api
        ).generateChatResponse(
            messages = listOf(
                LlmChatMessage(role = LlmChatRole.USER, content = "test")
            )
        )

        assertEquals("gpt-4o", result.diagnostics.model)
    }

    private fun mockGeminiClient(
        requests: MutableList<String>,
        responses: ArrayDeque<String>
    ): HttpClient = HttpClient(MockEngine { request: HttpRequestData ->
        requests += when (val body = request.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> body.readFrom().readRemaining().readText()
            is OutgoingContent.WriteChannelContent -> "<write-channel-content>"
            is OutgoingContent.NoContent -> ""
            else -> body.toString()
        }
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

    private fun toolCallCompletion(message: ChatMessage) = ChatCompletion(
        id = "chatcmpl-tool",
        created = 1L,
        model = ModelId("gpt-4o-mini"),
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
        model = ModelId("gpt-4o-mini"),
        choices = listOf(
            ChatChoice(
                index = 0,
                message = ChatMessage(
                    role = OpenAiChatRole.Assistant,
                    content = content
                ),
                finishReason = FinishReason("stop")
            )
        ),
        usage = Usage(promptTokens = 6, completionTokens = 5, totalTokens = 11)
    )

    private class FakeAppSettingsRepository(
        private val apiKey: String?,
        private val model: String?
    ) : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = apiKey
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
        override fun getSelectedProvider(): LlmProvider = when {
            apiKey?.startsWith("sk-or") == true -> LlmProvider.OPENROUTER
            apiKey?.startsWith("gemini") == true -> LlmProvider.GEMINI
            else -> LlmProvider.OPENAI
        }
        override fun setSelectedProvider(provider: LlmProvider) {}
        override fun getModel(provider: LlmProvider): String? = model
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
        override fun getPolarAccessToken(): String? = null
        override fun setPolarAccessToken(token: String) {}
        override fun getPolarRefreshToken(): String? = null
        override fun setPolarRefreshToken(token: String) {}
        override fun getPolarTokenExpiresAt(): Long = 0L
        override fun setPolarTokenExpiresAt(expiresAt: Long) {}
        override fun getPolarUserId(): String? = null
        override fun setPolarUserId(userId: String) {}
        override fun getPendingOAuthState(): String? = null
        override fun setPendingOAuthState(state: String) {}
        override fun getPendingOAuthSessionId(): String? = null
        override fun setPendingOAuthSessionId(sessionId: String) {}
        override fun clearPendingOAuthSession() {}
        override fun clearPolarTokens() {}
        override fun isPolarConnected(): Boolean = false
    }
}
