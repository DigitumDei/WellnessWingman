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
    fun clearHeight()
    fun clearCurrentWeight()
    fun clearProfileData()
}
