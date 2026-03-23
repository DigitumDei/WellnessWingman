package com.wellnesswingman.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolarOAuthConfigTest {

    @Test
    fun `isConfigured returns true when both fields are non-blank`() {
        val config = PolarOAuthConfig(clientId = "test-id", brokerBaseUrl = "https://broker.example.com")
        assertTrue(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when clientId is blank`() {
        val config = PolarOAuthConfig(clientId = "", brokerBaseUrl = "https://broker.example.com")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when brokerBaseUrl is blank`() {
        val config = PolarOAuthConfig(clientId = "test-id", brokerBaseUrl = "")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when both fields are blank`() {
        val config = PolarOAuthConfig(clientId = "", brokerBaseUrl = "")
        assertFalse(config.isConfigured)
    }
}
