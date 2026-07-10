package com.wellnesswingman.domain.chat

import com.wellnesswingman.data.model.ChatMessageStatus
import com.wellnesswingman.data.model.ChatRole
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.model.llm.LlmChatMessage
import com.wellnesswingman.data.model.llm.LlmChatRole
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.domain.llm.LlmDiagnostics
import com.wellnesswingman.domain.llm.ToolRegistry
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

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

class HealthChatService(
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
- Check recent weight history records
- Review recent tracked entries and their analyses
- Access saved nutritional profiles for packaged foods

When a user asks about their data, use the appropriate tool instead of guessing. Always be supportive and encouraging.

IMPORTANT LIMITATIONS:
- Do not provide medical diagnoses, prescribe medications, or replace professional medical advice.
- Encourage users to consult healthcare professionals for medical concerns.
- Do not make specific health claims that require clinical validation.
        """.trimIndent()
    }

    suspend fun sendMessage(request: ChatRequest): ChatResult {
        return try {
            val now = Clock.System.now()

            val conversation = getOrCreateConversation(request, now)
            val conversationId = conversation.conversationId

            val userMessageId = healthChatRepository.insertMessage(
                conversationId = conversationId,
                role = ChatRole.USER,
                content = request.messageContent,
                createdAt = now,
                provider = request.provider.name.lowercase(),
                model = request.model,
                status = ChatMessageStatus.PENDING,
            )
            healthChatRepository.updateMessageStatus(userMessageId, ChatMessageStatus.COMPLETED)

            if (!llmClientFactory.hasApiKey(request.provider)) {
                healthChatRepository.touchConversation(conversationId, Clock.System.now())
                return ChatResult.ApiKeyMissing
            }

            val history = healthChatRepository.getMessagesForConversation(conversationId)
            val boundedHistory = history.takeLast(MAX_HISTORY_MESSAGES)
            val llmMessages = boundedHistory.map { it.toLlmChatMessage() }

            val llmClient = llmClientFactory.create(request.provider)
            val result = llmClient.generateChatResponse(
                messages = llmMessages,
                systemInstruction = SYSTEM_INSTRUCTION,
                tools = toolRegistry.definitions(),
                toolExecutor = toolRegistry::execute,
            )

            val responseNow = Clock.System.now()
            val assistantMessageId = healthChatRepository.insertMessage(
                conversationId = conversationId,
                role = ChatRole.ASSISTANT,
                content = result.content,
                createdAt = responseNow,
                provider = request.provider.name.lowercase(),
                model = result.diagnostics.model.ifBlank { request.model },
                status = ChatMessageStatus.COMPLETED,
            )

            val assistantMessage = HealthChatMessage(
                messageId = assistantMessageId,
                conversationId = conversationId,
                role = ChatRole.ASSISTANT,
                content = result.content,
                createdAt = responseNow,
                provider = request.provider.name.lowercase(),
                model = result.diagnostics.model.ifBlank { request.model },
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
            throw e
        } catch (e: Exception) {
            Napier.e("Chat failed", e)
            ChatResult.ProviderError(
                message = e.message ?: "Unknown error",
                conversationId = 0L,
            )
        }
    }

    suspend fun getConversation(conversationExternalId: String): ChatConversationResult {
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

private fun HealthChatMessage.toLlmChatMessage(): LlmChatMessage {
    val llmRole = when (role) {
        ChatRole.USER -> LlmChatRole.USER
        ChatRole.ASSISTANT -> LlmChatRole.ASSISTANT
        ChatRole.TOOL -> LlmChatRole.TOOL
    }
    return LlmChatMessage(
        role = llmRole,
        content = content,
        toolCallId = null,
        toolName = null,
        toolResultJson = toolResultJson,
    )
}
