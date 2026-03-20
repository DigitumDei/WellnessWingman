package com.wellnesswingman.data.repository

import com.russhwolf.settings.Settings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Polar-specific methods in SettingsAppSettingsRepository.
 */
class SettingsAppSettingsRepositoryPolarTest {

    private lateinit var repo: SettingsAppSettingsRepository

    @BeforeTest
    fun setup() {
        repo = SettingsAppSettingsRepository(Settings())
        repo.clear()
    }

    // --- Polar tokens ---

    @Test
    fun `polar access token round-trips`() {
        assertNull(repo.getPolarAccessToken())
        repo.setPolarAccessToken("access-123")
        assertEquals("access-123", repo.getPolarAccessToken())
    }

    @Test
    fun `polar refresh token round-trips`() {
        assertNull(repo.getPolarRefreshToken())
        repo.setPolarRefreshToken("refresh-456")
        assertEquals("refresh-456", repo.getPolarRefreshToken())
    }

    @Test
    fun `polar token expires at round-trips`() {
        assertEquals(0L, repo.getPolarTokenExpiresAt())
        repo.setPolarTokenExpiresAt(999_999L)
        assertEquals(999_999L, repo.getPolarTokenExpiresAt())
    }

    @Test
    fun `polar user id round-trips`() {
        assertNull(repo.getPolarUserId())
        repo.setPolarUserId("user-789")
        assertEquals("user-789", repo.getPolarUserId())
    }

    // --- Pending OAuth state ---

    @Test
    fun `pending OAuth state round-trips`() {
        assertNull(repo.getPendingOAuthState())
        repo.setPendingOAuthState("state-abc")
        assertEquals("state-abc", repo.getPendingOAuthState())
    }

    @Test
    fun `pending OAuth session id round-trips`() {
        assertNull(repo.getPendingOAuthSessionId())
        repo.setPendingOAuthSessionId("session-def")
        assertEquals("session-def", repo.getPendingOAuthSessionId())
    }

    @Test
    fun `clearPendingOAuthSession clears session id and state`() {
        repo.setPendingOAuthSessionId("session-def")
        repo.setPendingOAuthState("state-abc")

        repo.clearPendingOAuthSession()

        assertNull(repo.getPendingOAuthSessionId())
        assertNull(repo.getPendingOAuthState())
    }

    // --- isPolarConnected ---

    @Test
    fun `isPolarConnected returns false when no access token`() {
        assertFalse(repo.isPolarConnected())
    }

    @Test
    fun `isPolarConnected returns true when access token exists`() {
        repo.setPolarAccessToken("token")
        assertTrue(repo.isPolarConnected())
    }

    // --- clearPolarTokens ---

    @Test
    fun `clearPolarTokens removes all Polar fields`() {
        repo.setPolarAccessToken("access")
        repo.setPolarRefreshToken("refresh")
        repo.setPolarTokenExpiresAt(12345L)
        repo.setPolarUserId("user")
        repo.setPendingOAuthState("state")
        repo.setPendingOAuthSessionId("session")

        repo.clearPolarTokens()

        assertNull(repo.getPolarAccessToken())
        assertNull(repo.getPolarRefreshToken())
        assertEquals(0L, repo.getPolarTokenExpiresAt())
        assertNull(repo.getPolarUserId())
        assertNull(repo.getPendingOAuthState())
        assertNull(repo.getPendingOAuthSessionId())
        assertFalse(repo.isPolarConnected())
    }
}
