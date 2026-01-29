package com.wellnesswingman.data.repository

/**
 * LLM provider options.
 */
enum class LlmProvider {
    OPENAI,
    GEMINI
}

/**
 * Repository interface for app settings and secure storage.
 */
interface AppSettingsRepository {
    fun getApiKey(provider: LlmProvider): String?
    fun setApiKey(provider: LlmProvider, apiKey: String)
    fun removeApiKey(provider: LlmProvider)
    fun getSelectedProvider(): LlmProvider
    fun setSelectedProvider(provider: LlmProvider)
    fun clear()
}
