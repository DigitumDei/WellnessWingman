package com.wellnesswingman.ui.screens.chat

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.HealthChatConversation
import com.wellnesswingman.domain.chat.HealthChatService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

class HealthChatListViewModel(
    private val healthChatService: HealthChatService,
) : ScreenModel {
    private val _uiState = MutableStateFlow<HealthChatListUiState>(HealthChatListUiState.Loading)
    val uiState: StateFlow<HealthChatListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launch {
            _uiState.value = HealthChatListUiState.Loading
            runCatching { healthChatService.listConversations() }
                .onSuccess { _uiState.value = HealthChatListUiState.Success(it) }
                .onFailure { _uiState.value = HealthChatListUiState.Error(it.message ?: "Unable to load conversations") }
        }
    }

    fun newConversationExternalId(): String = "chat-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}"

    fun deleteConversation(conversationId: Long) {
        screenModelScope.launch {
            runCatching { healthChatService.deleteConversation(conversationId) }
                .onSuccess { refresh() }
                .onFailure { _uiState.value = HealthChatListUiState.Error(it.message ?: "Unable to delete conversation") }
        }
    }
}

sealed interface HealthChatListUiState {
    data object Loading : HealthChatListUiState
    data class Success(val conversations: List<HealthChatConversation>) : HealthChatListUiState
    data class Error(val message: String) : HealthChatListUiState
}
