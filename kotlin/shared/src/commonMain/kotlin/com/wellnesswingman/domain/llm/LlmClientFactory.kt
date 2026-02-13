package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider

/**
 * Factory for creating LLM client instances.
 */
class LlmClientFactory(
    private val settingsRepository: AppSettingsRepository
) {

    /**
     * Creates an LLM client for the specified provider.
     */
    fun create(provider: LlmProvider): LlmClient {
        val apiKey = settingsRepository.getApiKey(provider)
            ?: throw IllegalStateException("API key not configured for $provider")

        val model = settingsRepository.getModel(provider)
            ?: throw IllegalStateException("Model not configured for $provider")

        return when (provider) {
            LlmProvider.OPENAI -> OpenAiLlmClient(
                apiKey = apiKey,
                model = model
            )
            LlmProvider.GEMINI -> GeminiLlmClient(
                apiKey = apiKey,
                model = model
            )
        }
    }

    /**
     * Creates an LLM client using the currently selected provider.
     */
    fun createForCurrentProvider(): LlmClient {
        val provider = settingsRepository.getSelectedProvider()
        return create(provider)
    }

    /**
     * Checks if an API key is configured for the given provider.
     */
    fun hasApiKey(provider: LlmProvider): Boolean {
        return settingsRepository.getApiKey(provider) != null
    }

    /**
     * Checks if the current provider has an API key configured.
     */
    fun hasCurrentApiKey(): Boolean {
        val provider = settingsRepository.getSelectedProvider()
        return hasApiKey(provider)
    }
}
