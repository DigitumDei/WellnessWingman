package com.wellnesswingman.ui.screens.chat

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.data.model.HealthChatMessage
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.HealthChatRepository
import com.wellnesswingman.domain.chat.ChatConversationResult
import com.wellnesswingman.domain.chat.ChatRequest
import com.wellnesswingman.domain.chat.ChatResult
import com.wellnesswingman.domain.chat.HealthChatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthChatThreadViewModel(
    private val conversationExternalId: String,
    private val healthChatService: HealthChatService,
    private val healthChatRepository: HealthChatRepository,
    private val settingsRepository: AppSettingsRepository,
) : ScreenModel {
    private val _uiState = MutableStateFlow<HealthChatThreadUiState>(HealthChatThreadUiState.Loading)
    val uiState: StateFlow<HealthChatThreadUiState> = _uiState.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            when (val result = healthChatService.getConversation(conversationExternalId)) {
                is ChatConversationResult.Found -> showConversation(result.conversation)
                ChatConversationResult.NotFound -> _uiState.value = HealthChatThreadUiState.Empty
            }
        }
    }

    fun updateDraft(content: String) {
        _draft.value = content
    }

    fun send() {
        val content = _draft.value
        if (content.isBlank() || _isSending.value) return
        screenModelScope.launch {
            _isSending.value = true
            val provider = settingsRepository.getSelectedProvider()
            val model = settingsRepository.getModel(provider).orEmpty()
            when (val result = healthChatService.sendMessage(ChatRequest(conversationExternalId, content.trim(), provider, model))) {
                is ChatResult.Success -> {
                    _draft.value = ""
                    showConversation(result.conversation)
                }
                ChatResult.ApiKeyMissing -> _uiState.value = HealthChatThreadUiState.ApiKeyMissing
                is ChatResult.ProviderError -> _uiState.value = HealthChatThreadUiState.Error(result.message)
            }
            _isSending.value = false
        }
    }

    fun recheckConfiguration() {
        screenModelScope.launch {
            val provider = settingsRepository.getSelectedProvider()
            val key = settingsRepository.getApiKey(provider)
            if (key.isNullOrBlank()) return@launch
            _uiState.value = HealthChatThreadUiState.Loading
            load()
        }
    }

    private suspend fun showConversation(conversation: HealthChatConversation) {
        _uiState.value = HealthChatThreadUiState.Success(
            conversation,
            healthChatRepository.getMessagesForConversation(conversation.conversationId),
        )
    }
}

sealed interface HealthChatThreadUiState {
    data object Loading : HealthChatThreadUiState
    data object Empty : HealthChatThreadUiState
    data class Success(val conversation: HealthChatConversation, val messages: List<HealthChatMessage>) : HealthChatThreadUiState
    data object ApiKeyMissing : HealthChatThreadUiState
    data class Error(val message: String) : HealthChatThreadUiState
}
