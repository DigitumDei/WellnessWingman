package com.wellnesswingman.domain.oauth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory bridge between Activity (deep link receiver) and the redemption layer.
 *
 * Works like a condition variable: MainActivity delivers the OAuth result
 * from the deep link, and WellnessWingmanApp / PolarSettingsViewModel
 * consume it to trigger session redemption or display errors.
 */
class PendingOAuthResultStore {

    private val _result = MutableStateFlow<OAuthDeepLinkResult?>(null)
    val result: StateFlow<OAuthDeepLinkResult?> = _result.asStateFlow()

    /**
     * Called from MainActivity when a deep link arrives with a successful result.
     */
    fun deliver(sessionId: String, state: String) {
        _result.value = OAuthDeepLinkResult(sessionId = sessionId, state = state)
    }

    /**
     * Called from MainActivity when the deep link carries an error.
     */
    fun deliverError(error: String) {
        _result.value = OAuthDeepLinkResult(error = error)
    }

    /**
     * Called after processing the result.
     * Returns the result and clears it so it's not processed twice.
     */
    fun consume(): OAuthDeepLinkResult? {
        val current = _result.value
        _result.value = null
        return current
    }
}

data class OAuthDeepLinkResult(
    val sessionId: String? = null,
    val state: String? = null,
    val error: String? = null
)
