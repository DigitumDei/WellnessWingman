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

        private const val KEY_IMAGE_RETENTION_DAYS = "image_retention_days"

        // Polar Integration keys
        private const val KEY_POLAR_ACCESS_TOKEN = "polar_access_token"
        private const val KEY_POLAR_REFRESH_TOKEN = "polar_refresh_token"
        private const val KEY_POLAR_TOKEN_EXPIRES_AT = "polar_token_expires_at"
        private const val KEY_POLAR_USER_ID = "polar_user_id"
        private const val KEY_PENDING_OAUTH_STATE = "polar_pending_oauth_state"

        private const val DEFAULT_HEIGHT_UNIT = "cm"
        private const val DEFAULT_WEIGHT_UNIT = "kg"
        private const val DEFAULT_IMAGE_RETENTION_DAYS = 30
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

    override fun clearHeight() {
        settings.remove(KEY_PROFILE_HEIGHT)
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

    override fun getImageRetentionThresholdDays(): Int {
        return settings.getInt(KEY_IMAGE_RETENTION_DAYS, DEFAULT_IMAGE_RETENTION_DAYS)
    }

    override fun setImageRetentionThresholdDays(days: Int) {
        settings[KEY_IMAGE_RETENTION_DAYS] = days
    }

    // Polar Integration

    override fun getPolarAccessToken(): String? =
        settings.getStringOrNull(KEY_POLAR_ACCESS_TOKEN)

    override fun setPolarAccessToken(token: String) {
        settings[KEY_POLAR_ACCESS_TOKEN] = token
    }

    override fun getPolarRefreshToken(): String? =
        settings.getStringOrNull(KEY_POLAR_REFRESH_TOKEN)

    override fun setPolarRefreshToken(token: String) {
        settings[KEY_POLAR_REFRESH_TOKEN] = token
    }

    override fun getPolarTokenExpiresAt(): Long =
        settings.getLong(KEY_POLAR_TOKEN_EXPIRES_AT, 0L)

    override fun setPolarTokenExpiresAt(expiresAt: Long) {
        settings[KEY_POLAR_TOKEN_EXPIRES_AT] = expiresAt
    }

    override fun getPolarUserId(): String? =
        settings.getStringOrNull(KEY_POLAR_USER_ID)

    override fun setPolarUserId(userId: String) {
        settings[KEY_POLAR_USER_ID] = userId
    }

    override fun getPendingOAuthState(): String? =
        settings.getStringOrNull(KEY_PENDING_OAUTH_STATE)

    override fun setPendingOAuthState(state: String) {
        settings[KEY_PENDING_OAUTH_STATE] = state
    }

    override fun clearPolarTokens() {
        settings.remove(KEY_POLAR_ACCESS_TOKEN)
        settings.remove(KEY_POLAR_REFRESH_TOKEN)
        settings.remove(KEY_POLAR_TOKEN_EXPIRES_AT)
        settings.remove(KEY_POLAR_USER_ID)
        settings.remove(KEY_PENDING_OAUTH_STATE)
    }

    override fun isPolarConnected(): Boolean =
        getPolarAccessToken() != null
}
