package com.wellnesswingman.data.repository

/**
 * LLM provider options.
 */
enum class LlmProvider {
    OPENAI,
    GEMINI,
    OPENROUTER
}

const val MAX_GOALS_AND_PREFERENCES_LENGTH = 2_000

/**
 * Repository interface for app settings and secure storage.
 */
interface AppSettingsRepository {
    fun getApiKey(provider: LlmProvider): String?
    fun setApiKey(provider: LlmProvider, apiKey: String)
    fun removeApiKey(provider: LlmProvider)
    fun getSelectedProvider(): LlmProvider
    fun setSelectedProvider(provider: LlmProvider)
    fun getModel(provider: LlmProvider): String?
    fun setModel(provider: LlmProvider, model: String)
    fun clear()

    // User Profile
    fun getHeight(): Double?
    fun setHeight(height: Double)
    fun getHeightUnit(): String
    fun setHeightUnit(unit: String)   // "cm" or "in"
    fun getSex(): String?
    fun setSex(sex: String)
    fun getCurrentWeight(): Double?
    fun setCurrentWeight(weight: Double)
    fun getWeightUnit(): String
    fun setWeightUnit(unit: String)   // "kg" or "lbs"
    fun getDateOfBirth(): String?     // ISO date string "YYYY-MM-DD"
    fun setDateOfBirth(dob: String)
    fun getActivityLevel(): String?
    fun setActivityLevel(level: String)
    /** User-authored goals and preferences used to personalize analysis prompts. */
    fun getGoalsAndPreferences(): String? = null
    fun setGoalsAndPreferences(text: String) = Unit
    fun clearHeight()
    fun clearCurrentWeight()
    fun clearProfileData()

    // Image Retention
    fun getImageRetentionThresholdDays(): Int
    fun setImageRetentionThresholdDays(days: Int)

    // Daily Check-Ins
    fun isMorningCheckInEnabled(): Boolean
    fun setMorningCheckInEnabled(enabled: Boolean)
    fun getMorningCheckInTime(): String   // "HH:mm", 24-hour
    fun setMorningCheckInTime(time: String)
    fun isEveningCheckInEnabled(): Boolean
    fun setEveningCheckInEnabled(enabled: Boolean)
    fun getEveningCheckInTime(): String   // "HH:mm", 24-hour
    fun setEveningCheckInTime(time: String)

    // Polar Integration
    fun getPolarAccessToken(): String?
    fun setPolarAccessToken(token: String)
    fun getPolarRefreshToken(): String?
    fun setPolarRefreshToken(token: String)
    fun getPolarTokenExpiresAt(): Long
    fun setPolarTokenExpiresAt(expiresAt: Long)
    fun getPolarUserId(): String?
    fun setPolarUserId(userId: String)
    fun getPendingOAuthState(): String?
    fun setPendingOAuthState(state: String)
    fun getPendingOAuthSessionId(): String?
    fun setPendingOAuthSessionId(sessionId: String)
    fun clearPendingOAuthSession()
    fun clearPolarTokens()
    fun isPolarConnected(): Boolean
}
