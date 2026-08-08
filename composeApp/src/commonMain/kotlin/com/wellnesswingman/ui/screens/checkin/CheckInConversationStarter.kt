package com.wellnesswingman.ui.screens.checkin

import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.domain.chat.ChatRequest
import com.wellnesswingman.domain.chat.ChatResult
import com.wellnesswingman.domain.chat.HealthChatService

sealed interface CheckInConversationResult {
    data object Started : CheckInConversationResult
    data object ApiKeyMissing : CheckInConversationResult
    data class Failed(val message: String) : CheckInConversationResult
}

/**
 * Starts a health chat about a check-in.
 *
 * A narrow seam over [HealthChatService]: the check-in screen only ever needs "open a
 * conversation about this answer", not the whole chat surface. Keeping it narrow also means the
 * check-in flow can be tested without standing up a ToolRegistry and its five repositories.
 */
interface CheckInConversationStarter {
    suspend fun start(
        conversationExternalId: String,
        openingMessage: String,
        title: String
    ): CheckInConversationResult
}

class HealthChatCheckInConversationStarter(
    private val healthChatService: HealthChatService,
    private val appSettingsRepository: AppSettingsRepository
) : CheckInConversationStarter {

    override suspend fun start(
        conversationExternalId: String,
        openingMessage: String,
        title: String
    ): CheckInConversationResult {
        val provider = appSettingsRepository.getSelectedProvider()
        val model = appSettingsRepository.getModel(provider).orEmpty()

        val result = healthChatService.sendMessage(
            ChatRequest(
                conversationExternalId = conversationExternalId,
                messageContent = openingMessage,
                provider = provider,
                model = model
            )
        )

        return when (result) {
            is ChatResult.Success -> {
                // sendMessage titles a new conversation from the first 80 characters of the
                // message, which would be the framing text. Give it a readable name instead.
                healthChatService.renameConversation(
                    conversationId = result.conversation.conversationId,
                    title = title
                )
                CheckInConversationResult.Started
            }
            ChatResult.ApiKeyMissing -> CheckInConversationResult.ApiKeyMissing
            is ChatResult.ProviderError -> CheckInConversationResult.Failed(result.message)
        }
    }
}
