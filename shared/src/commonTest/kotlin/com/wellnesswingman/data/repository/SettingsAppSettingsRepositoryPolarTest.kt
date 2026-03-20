package com.wellnesswingman.data.repository

import com.russhwolf.settings.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Polar-specific methods in SettingsAppSettingsRepository.
 * Uses a mockk Settings to avoid platform-specific Settings() constructor issues
 * on Android unit test JVM.
 */
class SettingsAppSettingsRepositoryPolarTest {

    private lateinit var settings: Settings
    private lateinit var repo: SettingsAppSettingsRepository

    @BeforeTest
    fun setup() {
        settings = mockk(relaxed = true)
        repo = SettingsAppSettingsRepository(settings)
    }

    // --- Polar tokens ---

    @Test
    fun `getPolarAccessToken returns null when not set`() {
        every { settings.getStringOrNull("polar_access_token") } returns null
        assertNull(repo.getPolarAccessToken())
    }

    @Test
    fun `getPolarAccessToken returns stored value`() {
        every { settings.getStringOrNull("polar_access_token") } returns "access-123"
        assertEquals("access-123", repo.getPolarAccessToken())
    }

    @Test
    fun `setPolarAccessToken writes to settings`() {
        repo.setPolarAccessToken("access-123")
        verify { settings.putString("polar_access_token", "access-123") }
    }

    @Test
    fun `getPolarRefreshToken returns stored value`() {
        every { settings.getStringOrNull("polar_refresh_token") } returns "refresh-456"
        assertEquals("refresh-456", repo.getPolarRefreshToken())
    }

    @Test
    fun `getPolarTokenExpiresAt returns default zero when not set`() {
        every { settings.getLong("polar_token_expires_at", 0L) } returns 0L
        assertEquals(0L, repo.getPolarTokenExpiresAt())
    }

    @Test
    fun `getPolarTokenExpiresAt returns stored value`() {
        every { settings.getLong("polar_token_expires_at", 0L) } returns 999_999L
        assertEquals(999_999L, repo.getPolarTokenExpiresAt())
    }

    @Test
    fun `getPolarUserId returns stored value`() {
        every { settings.getStringOrNull("polar_user_id") } returns "user-789"
        assertEquals("user-789", repo.getPolarUserId())
    }

    // --- Pending OAuth state ---

    @Test
    fun `getPendingOAuthState returns stored value`() {
        every { settings.getStringOrNull("polar_pending_oauth_state") } returns "state-abc"
        assertEquals("state-abc", repo.getPendingOAuthState())
    }

    @Test
    fun `getPendingOAuthSessionId returns stored value`() {
        every { settings.getStringOrNull("polar_pending_oauth_session_id") } returns "session-def"
        assertEquals("session-def", repo.getPendingOAuthSessionId())
    }

    @Test
    fun `clearPendingOAuthSession removes session id and state`() {
        repo.clearPendingOAuthSession()
        verify { settings.remove("polar_pending_oauth_session_id") }
        verify { settings.remove("polar_pending_oauth_state") }
    }

    // --- isPolarConnected ---

    @Test
    fun `isPolarConnected returns false when no access token`() {
        every { settings.getStringOrNull("polar_access_token") } returns null
        assertFalse(repo.isPolarConnected())
    }

    @Test
    fun `isPolarConnected returns true when access token exists`() {
        every { settings.getStringOrNull("polar_access_token") } returns "token"
        assertTrue(repo.isPolarConnected())
    }

    // --- clearPolarTokens ---

    @Test
    fun `clearPolarTokens removes all Polar fields`() {
        repo.clearPolarTokens()
        verify { settings.remove("polar_access_token") }
        verify { settings.remove("polar_refresh_token") }
        verify { settings.remove("polar_token_expires_at") }
        verify { settings.remove("polar_user_id") }
        verify { settings.remove("polar_pending_oauth_state") }
        verify { settings.remove("polar_pending_oauth_session_id") }
    }
}
