package com.wellnesswingman.domain.llm

import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.LlmProvider
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LlmClientFactoryTest {

    @Test
    fun `create with OPENROUTER returns OpenRouterLlmClient`() {
        val settingsRepo = FakeAppSettingsRepository(
            apiKey = "sk-or-v1-test-key",
            model = "openai/gpt-4o-mini"
        )
        val factory = LlmClientFactory(settingsRepo)
        val client = factory.create(LlmProvider.OPENROUTER)
        assertIs<OpenRouterLlmClient>(client)
    }

    @Test
    fun `create with OPENROUTER missing api key throws`() {
        val settingsRepo = FakeAppSettingsRepository(apiKey = null, model = "openai/gpt-4o-mini")
        val factory = LlmClientFactory(settingsRepo)
        assertFailsWith<IllegalStateException> {
            factory.create(LlmProvider.OPENROUTER)
        }
    }

    @Test
    fun `create with OPENROUTER missing model throws`() {
        val settingsRepo = FakeAppSettingsRepository(apiKey = "sk-or-v1-test-key", model = null)
        val factory = LlmClientFactory(settingsRepo)
        assertFailsWith<IllegalStateException> {
            factory.create(LlmProvider.OPENROUTER)
        }
    }

    private class FakeAppSettingsRepository(
        private val apiKey: String?,
        private val model: String?
    ) : AppSettingsRepository {
        override fun getApiKey(provider: LlmProvider): String? = apiKey
        override fun setApiKey(provider: LlmProvider, apiKey: String) {}
        override fun removeApiKey(provider: LlmProvider) {}
        override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENROUTER
        override fun setSelectedProvider(provider: LlmProvider) {}
        override fun getModel(provider: LlmProvider): String? = model
        override fun setModel(provider: LlmProvider, model: String) {}
        override fun clear() {}
        override fun getHeight(): Double? = null
        override fun setHeight(height: Double) {}
        override fun getHeightUnit(): String = "cm"
        override fun setHeightUnit(unit: String) {}
        override fun getSex(): String? = null
        override fun setSex(sex: String) {}
        override fun getCurrentWeight(): Double? = null
        override fun setCurrentWeight(weight: Double) {}
        override fun getWeightUnit(): String = "kg"
        override fun setWeightUnit(unit: String) {}
        override fun getDateOfBirth(): String? = null
        override fun setDateOfBirth(dob: String) {}
        override fun getActivityLevel(): String? = null
        override fun setActivityLevel(level: String) {}
        override fun clearHeight() {}
        override fun clearCurrentWeight() {}
        override fun clearProfileData() {}
        override fun getImageRetentionThresholdDays(): Int = 30
        override fun setImageRetentionThresholdDays(days: Int) {}
        override fun isMorningCheckInEnabled(): Boolean = false
        override fun setMorningCheckInEnabled(enabled: Boolean) {}
        override fun getMorningCheckInTime(): String = "07:00"
        override fun setMorningCheckInTime(time: String) {}
        override fun isEveningCheckInEnabled(): Boolean = false
        override fun setEveningCheckInEnabled(enabled: Boolean) {}
        override fun getEveningCheckInTime(): String = "21:00"
        override fun setEveningCheckInTime(time: String) {}
        override fun getPolarAccessToken(): String? = null
        override fun setPolarAccessToken(token: String) {}
        override fun getPolarRefreshToken(): String? = null
        override fun setPolarRefreshToken(token: String) {}
        override fun getPolarTokenExpiresAt(): Long = 0L
        override fun setPolarTokenExpiresAt(expiresAt: Long) {}
        override fun getPolarUserId(): String? = null
        override fun setPolarUserId(userId: String) {}
        override fun getPendingOAuthState(): String? = null
        override fun setPendingOAuthState(state: String) {}
        override fun getPendingOAuthSessionId(): String? = null
        override fun setPendingOAuthSessionId(sessionId: String) {}
        override fun clearPendingOAuthSession() {}
        override fun clearPolarTokens() {}
        override fun isPolarConnected(): Boolean = false
    }
}
