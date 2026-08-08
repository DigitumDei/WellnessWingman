package com.wellnesswingman.domain.chat

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.model.llm.LlmChatMessage
import com.wellnesswingman.data.model.llm.LlmChatRole
import com.wellnesswingman.data.model.llm.ToolCall
import com.wellnesswingman.data.model.llm.ToolResult
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolExecutor
import com.wellnesswingman.domain.llm.ToolRegistry
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ChatRequest(
    val conversationExternalId: String,
    val messageContent: String,
    val provider: LlmProvider,
    val model: String,
)

sealed interface ChatResult {
    data class Success(
        val conversation: HealthChatConversation,
        val assistantMessage: HealthChatMessage,
        val diagnostics: LlmDiagnostics,
    ) : ChatResult

    data object ApiKeyMissing : ChatResult

    data class ProviderError(
        val message: String,
        val conversationId: Long,
    ) : ChatResult
}

sealed interface ChatConversationResult {
    data class Found(val conversation: HealthChatConversation) : ChatConversationResult
    data object NotFound : ChatConversationResult
}

open class HealthChatService(
    private val healthChatRepository: HealthChatRepository,
    private val llmClientFactory: LlmClientFactory,
    private val toolRegistry: ToolRegistry,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        internal const val MAX_HISTORY_MESSAGES = 50

        val SYSTEM_INSTRUCTION: String = """
You are a helpful, supportive health and wellness assistant. You help users track nutrition, exercise, sleep, and general wellness using their personal data.

Available tools allow you to:
- Look up user profile information such as sex, date of birth, height, weight, and activity level
- See what date ranges contain data, and get a per-day overview of any period
- Retrieve tracked entries for a date range, with paging, and full analysis for specific entries
- Read generated daily and weekly summaries, including the user's own comments
- Read Polar wearable data (steps, sleep, training sessions) for a date range
- Get calorie and macro totals over a range, broken down per day or per week
- Check weight history for a date range or a number of recent days
- Access saved nutritional profiles for packaged foods

When a user asks about their data, use the appropriate tool instead of guessing. Always be supportive and encouraging.

WORKING WITH DATES AND PERIODS:
- You do not inherently know today's date. Call get_data_availability first whenever a question
  involves a past period, a relative time reference ("last week", "two weeks ago", "in March"),
  or when you need to know how far back data exists.
- Resolve relative references into explicit YYYY-MM-DD ranges before calling other tools.
- For a broad or vague period, start with get_daily_overview to see which days actually have
  data, then use get_entries or get_entry_details on the days that matter. Do not pull every
  entry in a wide range.
- For trends, averages, or "how much did I eat that week", prefer get_nutrition_totals over
  fetching individual meals and adding them up yourself.
- Ranged results report the window they actually served. If a result says "clamped": true, your
  requested range was too wide and you saw only part of it. If it says "truncated": true, more
  entries match than were returned — use "nextOffset" to page. Never describe a clamped or
  truncated result as if it were the complete picture; say what period you actually looked at.
- If a range genuinely contains no data, say so plainly rather than implying the user did nothing.

IMPORTANT LIMITATIONS:
- Do not provide medical diagnoses, prescribe medications, or replace professional medical advice.
- Encourage users to consult healthcare professionals for medical concerns.
- Do not make specific health claims that require clinical validation.
- Never expose unnecessary personal or sensitive data in responses. Only share information directly relevant to the user's question.
- If a user indicates a medical emergency (e.g., chest pain, severe injury, difficulty breathing), instruct them to call local emergency services immediately.
        """.trimIndent()
    }

    open suspend fun sendMessage(request: ChatRequest): ChatResult {
        var conversationId = 0L
        var userMessageId = 0L
        return try {
            val now = Clock.System.now()

            val conversation = getOrCreateConversation(request, now)
            conversationId = conversation.conversationId

            userMessageId = healthChatRepository.transaction { scope ->
                scope.insertMessage(
                    conversationId = conversationId,
                    role = ChatRole.USER,
                    content = request.messageContent,
                    createdAt = now,
                    provider = request.provider.name.lowercase(),
                    model = request.model,
                    status = ChatMessageStatus.PENDING,
                )
            }

            if (!llmClientFactory.hasApiKey(request.provider)) {
                healthChatRepository.transaction { scope ->
                    scope.updateMessageStatus(userMessageId, ChatMessageStatus.ERROR)
                    scope.touchConversation(conversationId, Clock.System.now())
                }
                return ChatResult.ApiKeyMissing
            }

            val history = healthChatRepository.getMessagesForConversation(conversationId)
                .filter { it.status == ChatMessageStatus.COMPLETED || it.messageId == userMessageId }
            val boundedHistory = trimHistoryPreservingToolGroups(history, MAX_HISTORY_MESSAGES)
            val llmMessages = boundedHistory.map { it.toLlmChatMessage() }

            val llmClient = llmClientFactory.create(request.provider)
            val capturingExecutor = CapturingToolExecutor(toolRegistry::execute)
            val result = llmClient.generateChatResponse(
                messages = llmMessages,
                systemInstruction = SYSTEM_INSTRUCTION,
                tools = toolRegistry.definitions(),
                toolExecutor = capturingExecutor::execute,
                onToolRoundCompleted = capturingExecutor::finishRound,
            )

            capturingExecutor.finish()

            val responseNow = Clock.System.now()
            val responseModel = result.diagnostics.model.ifBlank { request.model }

            var finalAssistantMessageId = 0L
            healthChatRepository.transaction { scope ->
                scope.updateMessageStatus(userMessageId, ChatMessageStatus.COMPLETED)

                for (round in capturingExecutor.completedRounds) {
                    val roundToolCallsJson = buildJsonArray {
                        round.calls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id ?: "")
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        }
                    }.let { json.encodeToString(JsonElement.serializer(), it) }

                    scope.insertMessage(
                        conversationId = conversationId,
                        role = ChatRole.ASSISTANT,
                        content = "",
                        createdAt = responseNow,
                        provider = request.provider.name.lowercase(),
                        model = responseModel,
                        toolCallsJson = roundToolCallsJson,
                        status = ChatMessageStatus.COMPLETED,
                    )

                    for (toolResult in round.results) {
                        scope.insertMessage(
                            conversationId = conversationId,
                            role = ChatRole.TOOL,
                            content = "",
                            createdAt = responseNow,
                            provider = request.provider.name.lowercase(),
                            model = responseModel,
                            toolResultJson = json.encodeToString(ToolResult.serializer(), toolResult),
                            status = ChatMessageStatus.COMPLETED,
                        )
                    }
                }

                finalAssistantMessageId = scope.insertMessage(
                    conversationId = conversationId,
                    role = ChatRole.ASSISTANT,
                    content = result.content,
                    createdAt = responseNow,
                    provider = request.provider.name.lowercase(),
                    model = responseModel,
                    status = ChatMessageStatus.COMPLETED,
                )
            }

            val assistantMessage = HealthChatMessage(
                messageId = finalAssistantMessageId,
                conversationId = conversationId,
                role = ChatRole.ASSISTANT,
                content = result.content,
                createdAt = responseNow,
                provider = request.provider.name.lowercase(),
                model = responseModel,
                status = ChatMessageStatus.COMPLETED,
            )

            if (conversation.title.isBlank()) {
                val title = request.messageContent.take(80)
                healthChatRepository.renameConversation(conversationId, title, responseNow)
            } else {
                healthChatRepository.touchConversation(conversationId, responseNow)
            }

            val updatedConversation = healthChatRepository.getConversationById(conversationId)
                ?: conversation.copy(updatedAt = responseNow)

            Napier.i("Chat message sent successfully in conversation $conversationId")
            ChatResult.Success(
                conversation = updatedConversation,
                assistantMessage = assistantMessage,
                diagnostics = result.diagnostics,
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                healthChatRepository.transaction { scope ->
                    if (userMessageId > 0L) {
                        scope.updateMessageStatus(userMessageId, ChatMessageStatus.ERROR)
                    }
                }
            }
            throw e
        } catch (e: Exception) {
            Napier.e("Chat failed", e)
            healthChatRepository.transaction { scope ->
                if (userMessageId > 0L) {
                    scope.updateMessageStatus(userMessageId, ChatMessageStatus.ERROR)
                }
                if (userMessageId > 0L) {
                    scope.insertMessage(
                        conversationId = conversationId,
                        role = ChatRole.ASSISTANT,
                        content = e.message ?: "Unknown error",
                        createdAt = Clock.System.now(),
                        provider = request.provider.name.lowercase(),
                        model = request.model,
                        status = ChatMessageStatus.ERROR,
                    )
                }
            }
            ChatResult.ProviderError(
                message = e.message ?: "Unknown error",
                conversationId = conversationId,
            )
        }
    }

    open suspend fun getConversation(conversationExternalId: String): ChatConversationResult {
        val conversation = healthChatRepository.getConversationByExternalId(conversationExternalId)
        return if (conversation != null) {
            ChatConversationResult.Found(conversation)
        } else {
            ChatConversationResult.NotFound
        }
    }

    suspend fun listConversations(): List<HealthChatConversation> {
        return healthChatRepository.getAllConversations()
    }

    suspend fun deleteConversation(conversationId: Long) {
        healthChatRepository.deleteConversation(conversationId)
    }

    suspend fun renameConversation(conversationId: Long, title: String) {
        healthChatRepository.renameConversation(
            id = conversationId,
            title = title.trim().take(80),
            updatedAt = Clock.System.now(),
        )
    }

    private suspend fun getOrCreateConversation(
        request: ChatRequest,
        now: Instant,
    ): HealthChatConversation {
        val existing = healthChatRepository.getConversationByExternalId(request.conversationExternalId)
        if (existing != null) {
            return existing
        }

        val conversationId = healthChatRepository.createConversation(
            externalId = request.conversationExternalId,
            title = "",
            provider = request.provider.name.lowercase(),
            model = request.model,
            createdAt = now,
            updatedAt = now,
        )

        return HealthChatConversation(
            conversationId = conversationId,
            externalId = request.conversationExternalId,
            title = "",
            provider = request.provider.name.lowercase(),
            model = request.model,
            createdAt = now,
            updatedAt = now,
        )
    }
}

private fun trimHistoryPreservingToolGroups(
    history: List<HealthChatMessage>,
    maxMessages: Int,
): List<HealthChatMessage> {
    if (history.size <= maxMessages) return history

    val trimmed = history.takeLast(maxMessages)

    if (trimmed.firstOrNull()?.role != ChatRole.TOOL) return trimmed

    val startIndex = history.size - trimmed.size
    var scanIdx = startIndex - 1
    while (scanIdx >= 0 && history[scanIdx].role == ChatRole.TOOL) {
        scanIdx--
    }
    val toolGroupStart = scanIdx + 1

    val groupStart = if (scanIdx >= 0 &&
        history[scanIdx].role == ChatRole.ASSISTANT &&
        history[scanIdx].toolCallsJson != null
    ) {
        scanIdx
    } else {
        toolGroupStart
    }

    return if (groupStart < startIndex) {
        history.subList(groupStart, history.size)
    } else {
        trimmed
    }
}

private class CapturingToolExecutor(
    private val delegate: ToolExecutor,
) {
    data class ToolRound(
        val calls: List<ToolCall>,
        val results: List<ToolResult>,
    )

    val completedRounds = mutableListOf<ToolRound>()
    private val pendingCalls = mutableListOf<ToolCall>()
    private val pendingResults = mutableListOf<ToolResult>()

    fun finish() {
        finishRound()
    }

    fun finishRound() {
        if (pendingCalls.isNotEmpty()) {
            completedRounds.add(ToolRound(pendingCalls.toList(), pendingResults.toList()))
            pendingCalls.clear()
            pendingResults.clear()
        }
    }

    suspend fun execute(toolCall: ToolCall): ToolResult {
        val result = runCatching { delegate(toolCall) }
        pendingCalls.add(toolCall)
        return result.fold(
            onSuccess = { it.also { pendingResults.add(it) } },
            onFailure = { error ->
                if (error is CancellationException) throw error
                val errorResult = ToolResult(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    content = JsonPrimitive(error.message ?: "Tool execution failed."),
                    isError = true,
                )
                pendingResults.add(errorResult)
                errorResult
            }
        )
    }
}

private fun HealthChatMessage.toLlmChatMessage(): LlmChatMessage {
    val json = Json { ignoreUnknownKeys = true }
    val llmRole = when (role) {
        ChatRole.USER -> LlmChatRole.USER
        ChatRole.ASSISTANT -> LlmChatRole.ASSISTANT
        ChatRole.TOOL -> LlmChatRole.TOOL
    }
    return when (role) {
        ChatRole.ASSISTANT -> LlmChatMessage(
            role = llmRole,
            content = content,
            toolCalls = toolCallsJson?.let {
                json.decodeFromString<List<ToolCall>>(it)
            },
        )
        ChatRole.TOOL -> {
            val toolResult = toolResultJson?.let {
                json.decodeFromString<ToolResult>(it)
            }
            val wireFormat = if (toolResult != null) {
                buildJsonObject {
                    put("ok", JsonPrimitive(!toolResult.isError))
                    put("content", toolResult.content)
                }.let { json.encodeToString(JsonElement.serializer(), it) }
            } else {
                content
            }
            LlmChatMessage(
                role = llmRole,
                content = content,
                toolCallId = toolResult?.toolCallId,
                toolName = toolResult?.name,
                toolResultJson = wireFormat,
            )
        }
        else -> LlmChatMessage(
            role = llmRole,
            content = content,
        )
    }
}
