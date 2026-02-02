package com.wellnesswingman.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.platform.DiagnosticShare
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val diagnosticShare: DiagnosticShare
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
                val openAiModel = appSettingsRepository.getModel(LlmProvider.OPENAI) ?: "gpt-4o-mini"
                val geminiModel = appSettingsRepository.getModel(LlmProvider.GEMINI) ?: "gemini-1.5-flash"

                _uiState.value = SettingsUiState(
                    selectedProvider = selectedProvider,
                    openAiApiKey = openAiKey,
                    openAiModel = openAiModel,
                    geminiApiKey = geminiKey,
                    geminiModel = geminiModel
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

    fun updateOpenAiModel(model: String) {
        _uiState.value = _uiState.value.copy(openAiModel = model)
    }

    fun updateGeminiModel(model: String) {
        _uiState.value = _uiState.value.copy(geminiModel = model)
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

                // Save models
                appSettingsRepository.setModel(LlmProvider.OPENAI, state.openAiModel)
                appSettingsRepository.setModel(LlmProvider.GEMINI, state.geminiModel)

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

    fun shareDiagnosticLogs() {
        try {
            Napier.i("Sharing diagnostic logs")
            diagnosticShare.shareDiagnosticLogs()
        } catch (e: Exception) {
            Napier.e("Failed to share diagnostic logs", e)
        }
    }
}

data class SettingsUiState(
    val selectedProvider: LlmProvider = LlmProvider.OPENAI,
    val openAiApiKey: String = "",
    val openAiModel: String = "gpt-4o-mini",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val saveSuccess: Boolean = false,
    val error: String? = null
)
