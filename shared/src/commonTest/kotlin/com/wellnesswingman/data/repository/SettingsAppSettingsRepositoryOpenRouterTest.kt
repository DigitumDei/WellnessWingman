package com.wellnesswingman.data.repository

import com.russhwolf.settings.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsAppSettingsRepositoryOpenRouterTest {

    private lateinit var settings: Settings
    private lateinit var repo: SettingsAppSettingsRepository

    @BeforeTest
    fun setup() {
        settings = mockk(relaxed = true)
        repo = SettingsAppSettingsRepository(settings)
    }

    @Test
    fun `getApiKey returns null for OPENROUTER when not set`() {
        every { settings.getStringOrNull("apikey_OPENROUTER") } returns null
        assertNull(repo.getApiKey(LlmProvider.OPENROUTER))
    }

    @Test
    fun `setApiKey writes key for OPENROUTER`() {
        repo.setApiKey(LlmProvider.OPENROUTER, "or-key-123")
        verify { settings.putString("apikey_OPENROUTER", "or-key-123") }
    }

    @Test
    fun `getApiKey reads back stored OPENROUTER key`() {
        every { settings.getStringOrNull("apikey_OPENROUTER") } returns "or-key-123"
        assertEquals("or-key-123", repo.getApiKey(LlmProvider.OPENROUTER))
    }

    @Test
    fun `getModel returns DEFAULT_OPENROUTER_MODEL for OPENROUTER when unset`() {
        every { settings.getString("model_OPENROUTER", "openai/gpt-4o-mini") } returns "openai/gpt-4o-mini"
        assertEquals("openai/gpt-4o-mini", repo.getModel(LlmProvider.OPENROUTER))
    }

    @Test
    fun `setModel writes model for OPENROUTER`() {
        repo.setModel(LlmProvider.OPENROUTER, "anthropic/claude-3-opus")
        verify { settings.putString("model_OPENROUTER", "anthropic/claude-3-opus") }
    }

    @Test
    fun `getModel reads back stored OPENROUTER model`() {
        every { settings.getString("model_OPENROUTER", "openai/gpt-4o-mini") } returns "anthropic/claude-3-opus"
        assertEquals("anthropic/claude-3-opus", repo.getModel(LlmProvider.OPENROUTER))
    }
}
