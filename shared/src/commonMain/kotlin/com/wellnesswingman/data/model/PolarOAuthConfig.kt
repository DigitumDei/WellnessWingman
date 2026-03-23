package com.wellnesswingman.data.model

/**
 * Configuration for Polar OAuth integration.
 * Populated from BuildConfig on Android, stub on other platforms.
 */
data class PolarOAuthConfig(
    val clientId: String,
    val brokerBaseUrl: String
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && brokerBaseUrl.isNotBlank()
}
