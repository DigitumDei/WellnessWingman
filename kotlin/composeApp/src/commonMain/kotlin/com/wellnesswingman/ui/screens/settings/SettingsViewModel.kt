package com.wellnesswingman.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        screenModelScope.launch {
            try {
                val selectedProvider = appSettingsRepository.getSelectedProvider()
                val openAiKey = appSettingsRepository.getApiKey(LlmProvider.OPENAI) ?: ""
                val geminiKey = appSettingsRepository.getApiKey(LlmProvider.GEMINI) ?: ""

                _uiState.value = SettingsUiState(
                    selectedProvider = selectedProvider,
                    openAiApiKey = openAiKey,
                    geminiApiKey = geminiKey
                )
            } catch (e: Exception) {
                Napier.e("Failed to load settings", e)
            }
        }
    }

    fun updateOpenAiApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(openAiApiKey = apiKey)
    }

    fun updateGeminiApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(geminiApiKey = apiKey)
    }

    fun selectProvider(provider: LlmProvider) {
        _uiState.value = _uiState.value.copy(selectedProvider = provider)
    }

    fun saveSettings() {
        screenModelScope.launch {
            try {
                val state = _uiState.value

                // Save API keys
                if (state.openAiApiKey.isNotBlank()) {
                    appSettingsRepository.setApiKey(LlmProvider.OPENAI, state.openAiApiKey)
                }
                if (state.geminiApiKey.isNotBlank()) {
                    appSettingsRepository.setApiKey(LlmProvider.GEMINI, state.geminiApiKey)
                }

                // Save selected provider
                appSettingsRepository.setSelectedProvider(state.selectedProvider)

                _uiState.value = state.copy(saveSuccess = true)

                Napier.i("Settings saved successfully")
            } catch (e: Exception) {
                Napier.e("Failed to save settings", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SettingsUiState(
    val selectedProvider: LlmProvider = LlmProvider.OPENAI,
    val openAiApiKey: String = "",
    val geminiApiKey: String = "",
    val saveSuccess: Boolean = false,
    val error: String? = null
)
