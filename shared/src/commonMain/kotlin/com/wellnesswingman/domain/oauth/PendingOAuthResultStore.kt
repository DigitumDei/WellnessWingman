package com.wellnesswingman.domain.oauth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory bridge between Activity (deep link receiver) and ViewModel (token redeemer).
 *
 * Works like a condition variable: MainActivity delivers the OAuth result
 * from the deep link, and PolarSettingsViewModel consumes it to trigger
 * session redemption.
 */
class PendingOAuthResultStore {

    private val _result = MutableStateFlow<OAuthDeepLinkResult?>(null)
    val result: StateFlow<OAuthDeepLinkResult?> = _result.asStateFlow()

    /**
     * Called from MainActivity when a deep link arrives.
     */
    fun deliver(sessionId: String, state: String) {
        _result.value = OAuthDeepLinkResult(sessionId = sessionId, state = state)
    }

    /**
     * Called from ViewModel after processing the result.
     * Returns the result and clears it so it's not processed twice.
     */
    fun consume(): OAuthDeepLinkResult? {
        val current = _result.value
        _result.value = null
        return current
    }
}

data class OAuthDeepLinkResult(
    val sessionId: String,
    val state: String
)
