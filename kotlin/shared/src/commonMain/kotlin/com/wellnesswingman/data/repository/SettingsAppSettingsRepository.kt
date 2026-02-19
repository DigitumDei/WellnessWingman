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
        private const val KEY_PREFIX_MODEL = "model_"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val DEFAULT_PROVIDER = "OPENAI"
        private const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        private const val DEFAULT_GEMINI_MODEL = "gemini-1.5-flash"

        // User Profile keys
        private const val KEY_PROFILE_HEIGHT = "profile_height"
        private const val KEY_PROFILE_HEIGHT_UNIT = "profile_height_unit"
        private const val KEY_PROFILE_SEX = "profile_sex"
        private const val KEY_PROFILE_CURRENT_WEIGHT = "profile_current_weight"
        private const val KEY_PROFILE_WEIGHT_UNIT = "profile_weight_unit"
        private const val KEY_PROFILE_DOB = "profile_dob"
        private const val KEY_PROFILE_ACTIVITY_LEVEL = "profile_activity_level"

        private const val DEFAULT_HEIGHT_UNIT = "cm"
        private const val DEFAULT_WEIGHT_UNIT = "kg"
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

    override fun getModel(provider: LlmProvider): String? {
        val defaultModel = when (provider) {
            LlmProvider.OPENAI -> DEFAULT_OPENAI_MODEL
            LlmProvider.GEMINI -> DEFAULT_GEMINI_MODEL
        }
        return settings.getString("$KEY_PREFIX_MODEL${provider.name}", defaultModel)
    }

    override fun setModel(provider: LlmProvider, model: String) {
        settings["$KEY_PREFIX_MODEL${provider.name}"] = model
    }

    override fun clear() {
        settings.clear()
    }

    // User Profile

    override fun getHeight(): Double? {
        val value = settings.getDoubleOrNull(KEY_PROFILE_HEIGHT)
        return if (value != null && value > 0) value else null
    }

    override fun setHeight(height: Double) {
        settings[KEY_PROFILE_HEIGHT] = height
    }

    override fun getHeightUnit(): String {
        return settings.getString(KEY_PROFILE_HEIGHT_UNIT, DEFAULT_HEIGHT_UNIT)
    }

    override fun setHeightUnit(unit: String) {
        settings[KEY_PROFILE_HEIGHT_UNIT] = unit
    }

    override fun getSex(): String? {
        return settings.getStringOrNull(KEY_PROFILE_SEX)
    }

    override fun setSex(sex: String) {
        settings[KEY_PROFILE_SEX] = sex
    }

    override fun getCurrentWeight(): Double? {
        val value = settings.getDoubleOrNull(KEY_PROFILE_CURRENT_WEIGHT)
        return if (value != null && value > 0) value else null
    }

    override fun setCurrentWeight(weight: Double) {
        settings[KEY_PROFILE_CURRENT_WEIGHT] = weight
    }

    override fun getWeightUnit(): String {
        return settings.getString(KEY_PROFILE_WEIGHT_UNIT, DEFAULT_WEIGHT_UNIT)
    }

    override fun setWeightUnit(unit: String) {
        settings[KEY_PROFILE_WEIGHT_UNIT] = unit
    }

    override fun getDateOfBirth(): String? {
        return settings.getStringOrNull(KEY_PROFILE_DOB)
    }

    override fun setDateOfBirth(dob: String) {
        settings[KEY_PROFILE_DOB] = dob
    }

    override fun getActivityLevel(): String? {
        return settings.getStringOrNull(KEY_PROFILE_ACTIVITY_LEVEL)
    }

    override fun setActivityLevel(level: String) {
        settings[KEY_PROFILE_ACTIVITY_LEVEL] = level
    }

    override fun clearCurrentWeight() {
        settings.remove(KEY_PROFILE_CURRENT_WEIGHT)
    }

    override fun clearProfileData() {
        settings.remove(KEY_PROFILE_HEIGHT)
        settings.remove(KEY_PROFILE_HEIGHT_UNIT)
        settings.remove(KEY_PROFILE_SEX)
        settings.remove(KEY_PROFILE_CURRENT_WEIGHT)
        settings.remove(KEY_PROFILE_WEIGHT_UNIT)
        settings.remove(KEY_PROFILE_DOB)
        settings.remove(KEY_PROFILE_ACTIVITY_LEVEL)
    }
}
