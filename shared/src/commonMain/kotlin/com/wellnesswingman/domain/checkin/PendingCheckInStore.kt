package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory bridge between the check-in notification deep link and the UI.
 *
 * Mirrors [com.wellnesswingman.domain.oauth.PendingOAuthResultStore]: MainActivity delivers the
 * slot from the deep link, and the composition consumes it to navigate to the check-in screen.
 */
class PendingCheckInStore {

    private val _requestedSlot = MutableStateFlow<CheckInSlot?>(null)
    val requestedSlot: StateFlow<CheckInSlot?> = _requestedSlot.asStateFlow()

    /** Called from MainActivity when a check-in deep link arrives. */
    fun request(slot: CheckInSlot) {
        _requestedSlot.value = slot
    }

    /**
     * Returns the pending slot and clears it, so a configuration change does not navigate to
     * the check-in screen a second time.
     */
    fun consume(): CheckInSlot? {
        val current = _requestedSlot.value
        _requestedSlot.value = null
        return current
    }
}
