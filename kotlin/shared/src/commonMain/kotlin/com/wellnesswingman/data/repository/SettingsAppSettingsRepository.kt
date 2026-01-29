package com.wellnesswingman.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * Implementation of AppSettingsRepository using multiplatform-settings.
 * This provides secure storage on each platform:
 * - Android: EncryptedSharedPreferences
 * - iOS: Keychain
 * - Desktop: OS-specific secure storage
 */
class SettingsAppSettingsRepository(
    private val settings: Settings
) : AppSettingsRepository {

    companion object {
        private const val KEY_PREFIX_API_KEY = "apikey_"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val DEFAULT_PROVIDER = "OPENAI"
    }

    override fun getApiKey(provider: LlmProvider): String? {
        return settings.getStringOrNull("$KEY_PREFIX_API_KEY${provider.name}")
    }

    override fun setApiKey(provider: LlmProvider, apiKey: String) {
        settings["$KEY_PREFIX_API_KEY${provider.name}"] = apiKey
    }

    override fun removeApiKey(provider: LlmProvider) {
        settings.remove("$KEY_PREFIX_API_KEY${provider.name}")
    }

    override fun getSelectedProvider(): LlmProvider {
        val providerName = settings.getString(KEY_SELECTED_PROVIDER, DEFAULT_PROVIDER)
        return try {
            LlmProvider.valueOf(providerName)
        } catch (e: IllegalArgumentException) {
            LlmProvider.OPENAI
        }
    }

    override fun setSelectedProvider(provider: LlmProvider) {
        settings[KEY_SELECTED_PROVIDER] = provider.name
    }

    override fun clear() {
        settings.clear()
    }
}
