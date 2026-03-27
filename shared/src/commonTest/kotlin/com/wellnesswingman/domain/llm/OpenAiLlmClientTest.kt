package com.wellnesswingman.domain.llm

import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.ToolCall as OpenAiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.wellnesswingman.data.model.llm.ToolDefinition
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiLlmClientTest {

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
        val client = OpenAiLlmClient(
            apiKey = "test-key",
            model = "gpt-4o-mini",
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
        val result = OpenAiLlmClient(
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

        val client = OpenAiLlmClient(
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

        val client = OpenAiLlmClient(
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
                    role = ChatRole.Assistant,
                    content = content
                ),
                finishReason = FinishReason("stop")
            )
        ),
        usage = Usage(promptTokens = 6, completionTokens = 5, totalTokens = 11)
    )
}
