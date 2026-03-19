package com.wellnesswingman.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.PolarOAuthConfig
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.PolarOAuthRepository
import com.wellnesswingman.domain.oauth.PendingOAuthResultStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class PolarSettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val polarOAuthRepository: PolarOAuthRepository,
    private val pendingOAuthResultStore: PendingOAuthResultStore,
    private val config: PolarOAuthConfig
) : ScreenModel {

    private val _uiState = MutableStateFlow(PolarSettingsUiState())
    val uiState: StateFlow<PolarSettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentState()
        observeOAuthResults()
    }

    private fun loadCurrentState() {
        _uiState.value = _uiState.value.copy(
            isConnected = appSettingsRepository.isPolarConnected(),
            polarUserId = appSettingsRepository.getPolarUserId() ?: "",
            isConfigured = config.isConfigured
        )
    }

    private fun observeOAuthResults() {
        screenModelScope.launch {
            pendingOAuthResultStore.result.filterNotNull().collect { result ->
                val consumed = pendingOAuthResultStore.consume() ?: return@collect
                redeemSession(consumed.sessionId, consumed.state)
            }
        }
    }

    fun onConnectClicked() {
        if (!config.isConfigured) {
            _uiState.value = _uiState.value.copy(
                error = "Polar OAuth is not configured. Set polar.client.id and polar.broker.base.url in local.properties."
            )
            return
        }
        val authUrl = polarOAuthRepository.buildAuthorizationUrl()
        _uiState.value = _uiState.value.copy(authUrl = authUrl)
    }

    fun onAuthUrlLaunched() {
        _uiState.value = _uiState.value.copy(authUrl = null)
    }

    fun onDisconnectClicked() {
        polarOAuthRepository.disconnect()
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            polarUserId = "",
            error = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun redeemSession(sessionId: String, state: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        screenModelScope.launch {
            val result = polarOAuthRepository.redeemSession(sessionId, state)
            result.fold(
                onSuccess = { userId ->
                    _uiState.value = _uiState.value.copy(
                        isConnected = true,
                        polarUserId = userId,
                        isLoading = false,
                        error = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to connect"
                    )
                }
            )
        }
    }
}

data class PolarSettingsUiState(
    val isConnected: Boolean = false,
    val polarUserId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val authUrl: String? = null,
    val isConfigured: Boolean = false
)
